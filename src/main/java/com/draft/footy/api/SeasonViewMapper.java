package com.draft.footy.api;

import com.draft.footy.Projection;
import com.draft.footy.SeasonSimulator;
import com.draft.footy.Xi;

import java.util.ArrayList;
import java.util.List;

/** Maps a simulated {@link SeasonSimulator.SeasonResult} into the JSON view shared by the demo and draft endpoints. */
public final class SeasonViewMapper {

    private SeasonViewMapper() { }

    public record TeamRow(int pos, String team, int points, int won, int drawn, int lost, int gd, boolean you,
                          List<String> startingXI) { }
    public record StatRow(String player, String team, int value) { }
    public record OddsView(int expectedPoints, double winLeague, double top4, double relegation) { }
    public record SeasonView(
        int overall, int attack, int midfield, int defence, int gk,
        OddsView projection,
        int finishPos, int points, int won, int drawn, int lost, int goalsFor, int goalsAgainst,
        int biggestWin, int longestWinStreak,
        List<TeamRow> table, List<StatRow> goldenBoot, List<StatRow> topAssists) { }

    public static SeasonView toView(SeasonSimulator.SeasonResult res, Projection.Odds odds) {
        Xi userXi = res.userStanding().team; // the user's XI carried by the result — used for the `you` flag
        var user = res.userStanding();

        List<TeamRow> table = new ArrayList<>();
        for (int i = 0; i < res.table().size(); i++) {
            var s = res.table().get(i);
            List<String> lineup = s.team.slots.stream()
                .map(slot -> slot.position() + ": " + slot.player().name() + " (" + slot.player().overall() + ")")
                .toList();
            table.add(new TeamRow(i + 1, s.team.name, s.points(), s.won, s.drawn, s.lost, s.gd(),
                s.team == userXi, lineup));
        }

        return new SeasonView(
            userXi.overall(), userXi.attack(), userXi.midfield(), userXi.defence(), userXi.gk(),
            new OddsView(odds.expectedPoints(), odds.winLeague(), odds.top4(), odds.relegation()),
            res.userPosition(), user.points(), user.won, user.drawn, user.lost, user.gf, user.ga,
            res.biggestWinFor(), res.longestWinStreak(),
            table,
            res.topScorers().stream().map(s -> new StatRow(s.player.name(), s.team, s.goals)).limit(10).toList(),
            res.topAssists().stream().map(s -> new StatRow(s.player.name(), s.team, s.assists)).limit(10).toList());
    }
}
