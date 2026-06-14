package com.draft.footy.api;

import com.draft.footy.*;
import com.draft.footy.persistence.DraftRunEntity;
import com.draft.footy.persistence.DraftRunRepository;
import com.draft.footy.persistence.DraftSlotEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The draft state machine (DESIGN_SPEC §3/§5B/§16), server-authoritative over the {@link DraftRunEntity}
 * aggregate. All randomness derives from {@code run.seed} (+ a per-spin counter) so a run is fully replayable.
 * This pass: World Draft + Squad First.
 */
@Service
@Transactional
public class DraftRunService {

    private final DraftRunRepository runs;
    private final GameCatalog catalog;

    public DraftRunService(DraftRunRepository runs, GameCatalog catalog) {
        this.runs = runs; this.catalog = catalog;
    }

    /** Config carrier from the controller (no web types leak into the entity construction). */
    public record CreateRunCommand(Long seed, Formation formation, Difficulty difficulty, ShowRatings showRatings,
                                   DraftMode draftMode, PlayerRatings playerRatings, LeagueScope leagueScope,
                                   String classicLeague, Integer eraFrom, Integer eraTo) { }

    /** A spin's result: the landed TIER plus the 2–3 clubs of that tier offered for the user to choose from. */
    public record SpinResult(DraftRunEntity run, String tier, List<ClubSeason> clubs) { }

    /** A draft's result: the updated run plus the squad it was drafted from (declassified after the pick). */
    public record DraftResult(DraftRunEntity run, ClubSeason squad, int draftedId) { }

    /** Pre-season preview once the XI is complete: the league you'll face + a league-aware projection. */
    public record LeaguePreview(Projection.Odds projection, int userOverall, int leagueMean, List<Xi> league,
                               MonteCarlo.Outcome monteCarlo, Xi userXi) { }
    /** A simulated season plus its Monte-Carlo distribution (so the debrief can place the season in the cloud). */
    public record SimOutcome(SeasonSimulator.SeasonResult result, MonteCarlo.Outcome monteCarlo) { }

    /** N seasons for the "real bookies" projection — sub-second via MatchEngine.fastScore, stable odds. */
    private static final int MC_SIMS = 1000;

    public DraftRunEntity create(CreateRunCommand cmd) {
        if (cmd.leagueScope() == LeagueScope.CLASSIC)
            throw bad("Classic (single-league) mode is not wired yet — use WORLD this pass.");
        if (cmd.draftMode() == DraftMode.POSITION_FIRST)
            throw bad("Position First is not wired yet — use SQUAD_FIRST this pass.");

        long seed = cmd.seed() != null ? cmd.seed() : new Random().nextLong();
        DraftRunEntity run = new DraftRunEntity(UUID.randomUUID().toString(), seed, cmd.formation(),
            cmd.difficulty(), cmd.showRatings(), cmd.draftMode(), cmd.playerRatings(), cmd.leagueScope(),
            cmd.classicLeague(), cmd.eraFrom(), cmd.eraTo());
        return runs.save(run);
    }

    @Transactional(readOnly = true)
    public DraftRunEntity get(String runId) {
        return runs.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such run: " + runId));
    }

    /** Land on an eligible club-season. A fresh turn's spin is free; re-spinning a squad already up costs a reroll. */
    public SpinResult spin(String runId) {
        DraftRunEntity run = get(runId);
        requireDrafting(run);

        List<ClubSeason> eligible = catalog.eligibleClubSeasons(
            run.getLeagueScope(), run.getClassicLeague(), run.getEraFrom(), run.getEraTo());
        if (eligible.isEmpty()) throw bad("no club-seasons match this run's league/era filter");

        if (run.hasSpin()) { // a board is already on screen -> this is a reroll
            if (run.getRerollsRemaining() <= 0) throw bad("no rerolls left");
            run.setRerollsRemaining(run.getRerollsRemaining() - 1);
        }

        Spin spin = spinChoices(run, eligible);
        run.setSpin(spin.tier(), spin.clubs());
        return new SpinResult(run, spin.tier(), spin.clubs());
    }

    /** Place a player from the current spin into an open slot of {@code slotPosition} (natural positions only). */
    public DraftResult draft(String runId, String slotPosition, int sofifaId) {
        DraftRunEntity run = get(runId);
        requireDrafting(run);
        if (!run.hasSpin()) throw bad("spin a squad before drafting");

        List<ClubSeason> offers = currentOffers(run);
        ClubSeason squad = offers.stream()
            .filter(cs -> cs.roster.stream().anyMatch(p -> p.id() == sofifaId)).findFirst()
            .orElseThrow(() -> bad("player " + sofifaId + " is not in the current spin"));
        Player picked = squad.roster.stream().filter(p -> p.id() == sofifaId).findFirst().orElseThrow();
        Player active = active(run, picked); // Prime = the peak-year snapshot (its rating AND positions)

        if (!active.canPlay(slotPosition))
            throw bad(active.name() + " can't play " + slotPosition + " (natural positions only)");
        DraftSlotEntity slot = run.openSlots().stream().filter(s -> s.getPosition().equals(slotPosition)).findFirst()
            .orElseThrow(() -> bad("no open " + slotPosition + " slot"));
        if (run.getSlots().stream().anyMatch(s -> s.isFilled() && s.getSofifaId() == sofifaId))
            throw bad(active.name() + " is already in your XI");

        slot.fill(active, squad.club, squad.season);
        run.clearSpin();
        return new DraftResult(run, squad, sofifaId);
    }

    /** The league you'll face + a league-aware projection (DESIGN_SPEC §6/§7). Available once the XI is complete. */
    @Transactional(readOnly = true)
    public LeaguePreview preview(String runId) {
        DraftRunEntity run = get(runId);
        if (!run.isComplete()) throw bad("draft incomplete: complete the XI before previewing the league");
        // Same seed → the same 19 teams simulate() will field (it generates with new Random(seed) first too).
        List<Xi> league = new OpponentPyramid(catalog.clubs()).generate(new Random(run.getSeed()));
        int leagueMean = (int) Math.round(league.stream().mapToInt(Xi::overall).average().orElse(LeagueProjection.REF_MEAN));
        Xi userXi = buildUserXi(run);
        int userOverall = userXi.overall();
        // The "real bookies": simulate this exact season N times for genuine odds + a distribution.
        MonteCarlo.Outcome mc = MonteCarlo.run(userXi, league, MC_SIMS, run.getSeed());
        // League-aware: a softer league makes you effectively stronger, a brutal one weaker, than the reference.
        // leagueMean here is the mean of the 19 opponents = the user's "mean of others" — matches the debrief.
        return new LeaguePreview(LeagueProjection.odds(userOverall, leagueMean), userOverall, leagueMean, league, mc, userXi);
    }

    /** Reposition an already-drafted player from one slot to an open slot they can also play (§5B). */
    public DraftRunEntity move(String runId, int fromSlotIndex, int toSlotIndex) {
        DraftRunEntity run = get(runId);
        requireDrafting(run);
        DraftSlotEntity from = slot(run, fromSlotIndex);
        DraftSlotEntity to = slot(run, toSlotIndex);
        if (!from.isFilled()) throw bad("slot " + fromSlotIndex + " is empty");
        if (to.isFilled()) throw bad("slot " + toSlotIndex + " is already filled");

        Player p = from.toPlayer();
        if (!p.canPlay(to.getPosition()))
            throw bad(p.name() + " can't play " + to.getPosition() + " (natural positions only)");
        to.fill(p, from.getSourceClub(), from.getSourceSeason());
        from.clear();
        return run;
    }

    /** Simulate the season once all 11 are drafted; seeded so it's reproducible (and re-playable). */
    public SimOutcome simulate(String runId) {
        DraftRunEntity run = get(runId);
        if (!run.isComplete())
            throw bad("draft incomplete: " + run.openSlots().size() + " slot(s) still open");

        Xi userXi = buildUserXi(run);
        Random rng = new Random(run.getSeed());
        List<Xi> opponents = new OpponentPyramid(catalog.clubs()).generate(rng);
        SeasonSimulator.SeasonResult result = new SeasonSimulator().simulate(userXi, opponents, rng);
        // Same opponents + same seed base as the pre-season preview → the debrief's odds/cloud match it exactly.
        MonteCarlo.Outcome mc = MonteCarlo.run(userXi, opponents, MC_SIMS, run.getSeed());
        run.setStatus(RunStatus.SIMULATED);
        return new SimOutcome(result, mc);
    }

    // --- helpers ---

    private static final int CHOICES = 3;                 // clubs offered per spin
    private record Spin(String tier, List<ClubSeason> clubs) { }

    /**
     * Spin in two stages: weighted-pick a TIER (the difficulty knob, {@link DraftTiers}), then offer up to 3
     * DISTINCT clubs of that tier to choose from — excluding clubs already drafted from this run (so a single
     * playthrough never repeats a club) and any squad that can't fill an open slot (§5B). Deterministic per spin.
     */
    private Spin spinChoices(DraftRunEntity run, List<ClubSeason> eligible) {
        List<String> open = run.openSlots().stream().map(DraftSlotEntity::getPosition).toList();
        Set<String> draftedFrom = run.getSlots().stream().filter(DraftSlotEntity::isFilled)
            .map(s -> norm(s.getSourceClub())).collect(java.util.stream.Collectors.toSet());
        Random rng = new Random(run.getSeed() * 1000003L + run.nextSpinIndex());

        List<DraftTiers.Tier> remaining = new ArrayList<>(DraftTiers.TIERS);
        while (!remaining.isEmpty()) {
            DraftTiers.Tier tier = DraftTiers.weightedPick(remaining, rng);
            List<ClubSeason> pool = new ArrayList<>(eligible.stream()
                .filter(cs -> { int s = cs.optimalStrength(); return s >= tier.min() && s <= tier.max(); }).toList());
            java.util.Collections.shuffle(pool, rng);
            List<ClubSeason> chosen = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ClubSeason cs : pool) {
                String nc = norm(cs.club);
                if (draftedFrom.contains(nc) || !seen.add(nc)) continue;   // distinct, not already drafted from
                if (!coversAnyOpenSlot(run, cs, open)) continue;
                chosen.add(cs);
                if (chosen.size() == CHOICES) break;
            }
            if (!chosen.isEmpty()) return new Spin(tier.label(), chosen);
            remaining.remove(tier);                                        // tier exhausted — re-pick another
        }
        // Ultra-fallback (shouldn't happen): any usable club anywhere.
        for (ClubSeason cs : eligible)
            if (coversAnyOpenSlot(run, cs, open)) return new Spin(DraftTiers.label(cs.optimalStrength()), List.of(cs));
        return new Spin(DraftTiers.label(eligible.get(0).optimalStrength()), List.of(eligible.get(0)));
    }

    private static String norm(String club) {
        return club == null ? "" : club.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean coversAnyOpenSlot(DraftRunEntity run, ClubSeason cs, List<String> openPositions) {
        for (Player p : cs.roster) {
            Player a = active(run, p);
            for (String pos : openPositions) if (a.canPlay(pos)) return true;
        }
        return false;
    }

    private Xi buildUserXi(DraftRunEntity run) {
        // Carry the formation in the name so the post-sim team viewer lays the XI out correctly (not a default 4-3-3).
        Xi xi = new Xi("Your XI (" + run.getFormation() + ")");
        for (DraftSlotEntity s : run.getSlots()) xi.add(s.getPosition(), s.toPlayer());
        return xi;
    }

    /** The player as they'd be drafted: their peak-year snapshot in Prime mode, otherwise the spun season. */
    public Player active(DraftRunEntity run, Player p) {
        return run.getPlayerRatings() == PlayerRatings.PRIME ? catalog.primeIndex().prime(p) : p;
    }

    /** Reconstruct the clubs offered by the current spin (from the persisted offers). */
    private List<ClubSeason> currentOffers(DraftRunEntity run) {
        List<ClubSeason> out = new ArrayList<>();
        for (var off : run.getSpinOffers())
            catalog.clubs().stream()
                .filter(cs -> cs.club.equals(off.getClub()) && cs.season.equals(off.getSeason()))
                .findFirst().ifPresent(out::add);
        if (out.isEmpty()) throw bad("current spin is no longer available");
        return out;
    }

    private DraftSlotEntity slot(DraftRunEntity run, int index) {
        return run.getSlots().stream().filter(s -> s.getSlotIndex() == index).findFirst()
            .orElseThrow(() -> bad("no slot at index " + index));
    }

    private void requireDrafting(DraftRunEntity run) {
        if (run.getStatus() != RunStatus.DRAFTING)
            throw bad("run is already " + run.getStatus());
    }

    private ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
