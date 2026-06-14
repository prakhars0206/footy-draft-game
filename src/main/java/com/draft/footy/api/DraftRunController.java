package com.draft.footy.api;

import com.draft.footy.*;
import com.draft.footy.persistence.DraftRunEntity;
import com.draft.footy.persistence.DraftSlotEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The stateful draft flow (DESIGN_SPEC §3/§5B/§16). The client holds only the {@code runId} and drives the run
 * through these endpoints; all state is server-authoritative. Ratings are rendered per the run's Show-Ratings
 * mode — in SCOUT the payload carries only a range and in OFF nothing, so the true overall never reaches the client.
 */
@RestController
@RequestMapping("/api/runs")
@CrossOrigin
public class DraftRunController {

    private final DraftRunService service;

    public DraftRunController(DraftRunService service) { this.service = service; }

    // ---- request bodies ----
    public record CreateRunRequest(String formation, Difficulty difficulty, ShowRatings showRatings,
                                   DraftMode draftMode, PlayerRatings playerRatings, LeagueScope leagueScope,
                                   String classicLeague, Integer eraFrom, Integer eraTo, Long seed) { }
    public record DraftRequest(String slotPosition, int sofifaId) { }
    public record MoveRequest(int fromSlot, int toSlot) { }

    // ---- response views ----
    /** Exactly one form is populated per Show-Ratings mode: exact (ON), low/high (SCOUT), or hidden (OFF). */
    public record RatingView(Integer overall, Integer low, Integer high, boolean hidden) { }
    public record SlotView(int index, String position, String line, boolean filled,
                           String name, String nation, List<String> positions, RatingView rating,
                           String sourceClub, String sourceSeason) { }
    public record StrengthView(Integer overall, Integer attack, Integer midfield, Integer defence, Integer gk) { }
    public record RunStateView(String runId, String formation, String difficulty, String showRatings,
                               String draftMode, String playerRatings, String leagueScope, String status,
                               long seed, int rerollsRemaining, int slotsRemaining,
                               StrengthView strength, List<SlotView> slots, SpinInfo currentSpin) { }
    public record SpinInfo(String club, String season, String league) { }
    public record SquadPlayerView(int sofifaId, String name, String nation, List<String> positions,
                                  RatingView rating, List<String> eligibleSlots) { }
    public record SpinView(String club, String season, String league, int strength, String tier,
                           int rerollsRemaining, List<SquadPlayerView> squad) { }
    /** A drafted-from squad revealed after a pick (true ratings) — learn who you passed on. `eligible` = still fits an open slot. */
    public record DeclassifiedPlayer(int sofifaId, String name, String position, String line, int overall,
                                     boolean draftedByYou, boolean eligible) { }
    public record DraftResultView(RunStateView state, String club, String season, List<DeclassifiedPlayer> declassified) { }
    /** A player within a team-viewer XI; stats are null pre-sim (league preview), populated post-sim. */
    public record XiSlotView(String position, String line, String name, int overall,
                             Integer goals, Integer assists, Integer cleanSheets) { }
    public record LeagueTeamView(String team, int strength, String tier, String formation, List<XiSlotView> xi) { }
    public record PreviewView(SeasonViewMapper.OddsView projection, int userOverall, int leagueMean,
                              List<LeagueTeamView> league) { }

    // ---- endpoints ----

    @PostMapping
    public RunStateView create(@RequestBody(required = false) CreateRunRequest req) {
        CreateRunRequest r = req != null ? req : new CreateRunRequest(null, null, null, null, null, null, null, null, null, null);
        var cmd = new DraftRunService.CreateRunCommand(
            r.seed(),
            parseFormation(r.formation()),
            orDefault(r.difficulty(), Difficulty.NORMAL),
            orDefault(r.showRatings(), ShowRatings.ON),
            orDefault(r.draftMode(), DraftMode.SQUAD_FIRST),
            orDefault(r.playerRatings(), PlayerRatings.CAREER),
            orDefault(r.leagueScope(), LeagueScope.WORLD),
            r.classicLeague(), r.eraFrom(), r.eraTo());
        return toState(service.create(cmd));
    }

    @GetMapping("/{id}")
    public RunStateView state(@PathVariable String id) { return toState(service.get(id)); }

    @PostMapping("/{id}/spin")
    public SpinView spin(@PathVariable String id) {
        var result = service.spin(id);
        return toSpin(result.run(), result.squad());
    }

    @PostMapping("/{id}/draft")
    public DraftResultView draft(@PathVariable String id, @RequestBody DraftRequest req) {
        var r = service.draft(id, req.slotPosition(), req.sofifaId());
        return new DraftResultView(toState(r.run()), r.squad().club, r.squad().season, declassify(r));
    }

    @PostMapping("/{id}/move")
    public RunStateView move(@PathVariable String id, @RequestBody MoveRequest req) {
        return toState(service.move(id, req.fromSlot(), req.toSlot()));
    }

    @GetMapping("/{id}/preview")
    public PreviewView preview(@PathVariable String id) {
        var p = service.preview(id);
        var o = p.projection();
        List<LeagueTeamView> league = p.league().stream()
            .map(DraftRunController::toLeagueTeam)
            .sorted((a, b) -> Integer.compare(b.strength(), a.strength()))
            .toList();
        return new PreviewView(new SeasonViewMapper.OddsView(o.expectedPoints(), o.winLeague(), o.top4(), o.relegation()),
            p.userOverall(), p.leagueMean(), league);
    }

    @PostMapping("/{id}/simulate")
    public SeasonViewMapper.SeasonReplayView simulate(@PathVariable String id) {
        return SeasonViewMapper.toReplay(service.simulate(id));
    }

    // ---- rendering ----

    private RunStateView toState(DraftRunEntity run) {
        List<SlotView> slots = new ArrayList<>();
        for (DraftSlotEntity s : run.getSlots()) {
            String line = Line.of(s.getPosition()).name();
            if (s.isFilled())
                // Drafted players are revealed (you know who you picked) — exact rating even in Scout/Off.
                slots.add(new SlotView(s.getSlotIndex(), s.getPosition(), line, true,
                    s.getName(), s.getNation(), s.getPlayerPositions(),
                    new RatingView(s.getOverall(), null, null, false), s.getSourceClub(), s.getSourceSeason()));
            else
                slots.add(new SlotView(s.getSlotIndex(), s.getPosition(), line, false,
                    null, null, null, null, null, null));
        }
        SpinInfo spin = run.hasSpin() ? new SpinInfo(run.getSpinClub(), run.getSpinSeason(), null) : null;
        return new RunStateView(run.getId(), run.getFormation(), run.getDifficulty().name(),
            run.getShowRatings().name(), run.getDraftMode().name(), run.getPlayerRatings().name(),
            run.getLeagueScope().name(), run.getStatus().name(), run.getSeed(),
            run.getRerollsRemaining(), run.openSlots().size(), strength(run), slots, spin);
    }

    /** Reveal the drafted-from squad with TRUE (active) ratings — sorted strongest-first, your pick flagged,
     *  players who no longer fit an open slot marked ineligible (the UI dims them). */
    private List<DeclassifiedPlayer> declassify(DraftRunService.DraftResult r) {
        // Eligibility reflects what was pickable DURING selection: the still-open slots PLUS the slot you just
        // filled (otherwise a backup at your new pick's position would wrongly grey out).
        Set<String> open = r.run().openSlots().stream()
            .map(DraftSlotEntity::getPosition).collect(Collectors.toSet());
        r.run().getSlots().stream()
            .filter(s -> Integer.valueOf(r.draftedId()).equals(s.getSofifaId()))
            .findFirst().ifPresent(s -> open.add(s.getPosition()));
        List<DeclassifiedPlayer> out = new ArrayList<>();
        for (Player base : r.squad().roster) {
            Player a = service.active(r.run(), base);
            boolean drafted = a.id() == r.draftedId();
            boolean eligible = drafted || a.positions().stream().anyMatch(open::contains);
            out.add(new DeclassifiedPlayer(a.id(), a.name(), a.primaryPosition(),
                a.primaryLine().name(), a.overall(), drafted, eligible));
        }
        out.sort((x, y) -> Integer.compare(y.overall(), x.overall()));
        return out;
    }

    private static LeagueTeamView toLeagueTeam(Xi xi) {
        var ord = TeamLayout.inFormationOrder(xi); // lay the XI out in its own formation for the pitch viewer
        List<XiSlotView> squad = ord.slots().stream()
            .map(s -> new XiSlotView(s.position(), s.line().name(), s.player().name(), s.player().overall(), null, null, null))
            .toList();
        return new LeagueTeamView(xi.name, xi.overall(), tierLabel(xi.overall()), ord.formation(), squad);
    }

    private static String tierLabel(int strength) {
        if (strength >= 87) return "JUGGERNAUT";
        if (strength >= 83) return "TITLE CONTENDER";
        if (strength >= 78) return "EUROPEAN CHASER";
        if (strength >= 72) return "MID-TABLE";
        return "RELEGATION SCRAPPER";
    }

    private SpinView toSpin(DraftRunEntity run, ClubSeason squad) {
        List<String> openPositions = run.openSlots().stream()
            .map(DraftSlotEntity::getPosition).distinct().toList();
        List<SquadPlayerView> rows = new ArrayList<>();
        for (Player base : squad.roster) {
            Player active = service.active(run, base); // Prime snapshot in Prime mode (rating + positions)
            List<String> eligible = openPositions.stream().filter(active::canPlay).toList();
            rows.add(new SquadPlayerView(active.id(), active.name(), active.nation(), active.positions(),
                rating(run, active.id(), active.overall()), eligible));
        }
        // Eligible (can fill an open slot) first; within each group, OFF/blind mode shuffles deterministically so
        // the row order never leaks who's strongest — otherwise sort strongest-first (band high in Scout).
        boolean blind = run.getShowRatings() == ShowRatings.OFF;
        rows.sort(Comparator
            .comparing((SquadPlayerView v) -> v.eligibleSlots().isEmpty())
            .thenComparingInt(v -> blind ? shuffleKey(run.getSeed(), v.sofifaId()) : -scoutOrExact(v)));
        int strength = squad.optimalStrength();
        return new SpinView(squad.club, squad.season, squad.league, strength, tierLabel(strength),
            run.getRerollsRemaining(), rows);
    }

    /** Renders a rating per the run's Show-Ratings mode — the true overall is omitted in SCOUT and OFF. */
    private RatingView rating(DraftRunEntity run, int sofifaId, int overall) {
        return switch (run.getShowRatings()) {
            case ON -> new RatingView(overall, null, null, false);
            case SCOUT -> {
                var b = ScoutRatings.band(sofifaId, run.getSeed(), overall);
                yield new RatingView(null, b.low(), b.high(), false);
            }
            case OFF -> new RatingView(null, null, null, true);
        };
    }

    private static int scoutOrExact(SquadPlayerView v) {
        if (v.rating().overall() != null) return v.rating().overall();
        if (v.rating().high() != null) return v.rating().high();
        return 0; // OFF mode — order is arbitrary
    }

    /** Stable per-(seed, player) pseudo-random key so OFF mode can shuffle a squad without leaking rating order. */
    private static int shuffleKey(long seed, int sofifaId) {
        return new Random(seed * 2654435761L + sofifaId).nextInt();
    }

    /**
     * Live team strength from the filled slots. Drafted players are revealed (you know your own picks), so this
     * reflects only known players and leaks nothing — shown in every mode. A line with none drafted yet is null.
     */
    private StrengthView strength(DraftRunEntity run) {
        Xi xi = new Xi("strength");
        for (DraftSlotEntity s : run.getSlots()) if (s.isFilled()) xi.add(s.getPosition(), s.toPlayer());
        if (xi.slots.isEmpty()) return new StrengthView(null, null, null, null, null);
        return new StrengthView(xi.overall(),
            lineScore(xi, Line.ATT), lineScore(xi, Line.MID), lineScore(xi, Line.DEF), lineScore(xi, Line.GK));
    }

    private static Integer lineScore(Xi xi, Line line) {
        var vals = xi.slots.stream().filter(s -> s.line() == line).mapToInt(s -> s.player().overall());
        var stats = vals.summaryStatistics();
        return stats.getCount() == 0 ? null : (int) Math.round(stats.getAverage());
    }

    private static <T> T orDefault(T v, T def) { return v != null ? v : def; }

    private Formation parseFormation(String label) {
        if (label != null) for (Formation f : Formation.values()) if (f.label().equals(label)) return f;
        return Formation.F_4_3_3;
    }
}
