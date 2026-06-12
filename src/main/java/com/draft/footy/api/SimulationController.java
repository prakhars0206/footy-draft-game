package com.draft.footy.api;

import com.draft.footy.Formation;
import com.draft.footy.Projection;
import com.draft.footy.SeasonSimulator;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase-1 demo endpoint. Phase 2 splits this into a stateful draft flow:
 *   POST /api/runs                  -> create a DraftRun (returns runId + seed)
 *   POST /api/runs/{id}/spin        -> spin a club-season
 *   POST /api/runs/{id}/draft       -> place a player
 *   POST /api/runs/{id}/simulate    -> run the season
 * (see DESIGN_SPEC §16 — DraftRun as a persisted resource).
 */
@RestController
@RequestMapping("/api")
@CrossOrigin
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) { this.service = service; }

    public record TeamRow(int pos, String team, int points, int won, int drawn, int lost, int gd, boolean you, List<String> startingXI) {}
    public record StatRow(String player, String team, int value) {}
    public record OddsView(int expectedPoints, double winLeague, double top4, double relegation) {}
    public record SeasonView(
        int overall, int attack, int midfield, int defence, int gk,
        OddsView projection,
        int finishPos, int points, int won, int drawn, int lost, int goalsFor, int goalsAgainst,
        int biggestWin, int longestWinStreak,
        List<TeamRow> table, List<StatRow> goldenBoot, List<StatRow> topAssists) {}

    @GetMapping("/season/demo")
    public SeasonView demoSeason(
            @RequestParam(defaultValue = "90") int overall,
            @RequestParam(defaultValue = "4-3-3") String formation,
            @RequestParam(defaultValue = "42") long seed) {

        Formation f = parseFormation(formation);
        var xi = service.buildDemoXi(overall, f);
        var res = service.simulateDemo(overall, f, seed);
        Projection.Odds o = service.projection(xi.overall());
        var user = res.userStanding();

        List<TeamRow> table = new java.util.ArrayList<>();
        for (int i = 0; i < res.table().size(); i++) {
            var s = res.table().get(i);

            // MAP THE STARTING XI TO STRINGS (e.g. "ST: L. Suárez (88)")
            List<String> lineup = s.team.slots.stream()
                    .map(slot -> slot.position() + ": " + slot.player().name() + " (" + slot.player().overall() + ")")
                    .toList();

            table.add(new TeamRow(i + + 1, s.team.name, s.points(), s.won, s.drawn, s.lost, s.gd(), s.team == xi, lineup));
        }

        return new SeasonView(
            xi.overall(), xi.attack(), xi.midfield(), xi.defence(), xi.gk(),
            new OddsView(o.expectedPoints(), o.winLeague(), o.top4(), o.relegation()),
            res.userPosition(), user.points(), user.won, user.drawn, user.lost, user.gf, user.ga,
            res.biggestWinFor(), res.longestWinStreak(),
            table,
            res.topScorers().stream().map(s -> new StatRow(s.player.name(), s.team, s.goals)).limit(10).toList(),
            res.topAssists().stream().map(s -> new StatRow(s.player.name(), s.team, s.assists)).limit(10).toList());
    }

    private Formation parseFormation(String label) {
        for (Formation f : Formation.values()) if (f.label().equals(label)) return f;
        return Formation.F_4_3_3;
    }
}
