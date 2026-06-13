package com.draft.footy.api;

import com.draft.footy.*;
import com.draft.footy.persistence.DraftRunEntity;
import com.draft.footy.persistence.DraftSlotEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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
                               StrengthView strength, List<SlotView> slots, SpinInfo currentSpin,
                               SeasonViewMapper.OddsView projection) { }
    public record SpinInfo(String club, String season, String league) { }
    public record SquadPlayerView(int sofifaId, String name, String nation, List<String> positions,
                                  RatingView rating, List<String> eligibleSlots) { }
    public record SpinView(String club, String season, String league, int rerollsRemaining,
                           List<SquadPlayerView> squad) { }

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
    public RunStateView draft(@PathVariable String id, @RequestBody DraftRequest req) {
        return toState(service.draft(id, req.slotPosition(), req.sofifaId()));
    }

    @PostMapping("/{id}/move")
    public RunStateView move(@PathVariable String id, @RequestBody MoveRequest req) {
        return toState(service.move(id, req.fromSlot(), req.toSlot()));
    }

    @PostMapping("/{id}/simulate")
    public SeasonViewMapper.SeasonView simulate(@PathVariable String id) {
        var res = service.simulate(id);
        return SeasonViewMapper.toView(res, Projection.odds(res.userStanding().team.overall()));
    }

    // ---- rendering ----

    private RunStateView toState(DraftRunEntity run) {
        List<SlotView> slots = new ArrayList<>();
        for (DraftSlotEntity s : run.getSlots()) {
            String line = Line.of(s.getPosition()).name();
            if (s.isFilled())
                slots.add(new SlotView(s.getSlotIndex(), s.getPosition(), line, true,
                    s.getName(), s.getNation(), s.getPlayerPositions(),
                    rating(run, s.getSofifaId(), s.getOverall()), s.getSourceClub(), s.getSourceSeason()));
            else
                slots.add(new SlotView(s.getSlotIndex(), s.getPosition(), line, false,
                    null, null, null, null, null, null));
        }
        SpinInfo spin = run.hasSpin() ? new SpinInfo(run.getSpinClub(), run.getSpinSeason(), null) : null;
        return new RunStateView(run.getId(), run.getFormation(), run.getDifficulty().name(),
            run.getShowRatings().name(), run.getDraftMode().name(), run.getPlayerRatings().name(),
            run.getLeagueScope().name(), run.getStatus().name(), run.getSeed(),
            run.getRerollsRemaining(), run.openSlots().size(), strength(run), slots, spin, projection(run));
    }

    /** The bookies' pre-season projection — shown once the XI is complete (the §6 dual-layer reveal). */
    private SeasonViewMapper.OddsView projection(DraftRunEntity run) {
        if (!run.isComplete()) return null;
        Xi xi = new Xi("proj");
        for (DraftSlotEntity s : run.getSlots()) xi.add(s.getPosition(), s.toPlayer());
        Projection.Odds o = Projection.odds(xi.overall());
        return new SeasonViewMapper.OddsView(o.expectedPoints(), o.winLeague(), o.top4(), o.relegation());
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
        rows.sort((a, b) -> Integer.compare(scoutOrExact(b), scoutOrExact(a))); // strongest first (band high in Scout)
        return new SpinView(squad.club, squad.season, squad.league, run.getRerollsRemaining(), rows);
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

    /**
     * Live team strength from the filled slots — only in ON mode, since an exact aggregate would otherwise leak
     * the hidden ratings (§16). A line with no drafted player yet returns null (shown as "—").
     */
    private StrengthView strength(DraftRunEntity run) {
        if (run.getShowRatings() != ShowRatings.ON) return null;
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
