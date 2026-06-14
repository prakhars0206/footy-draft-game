package com.draft.footy.api;

import com.draft.footy.Projection;
import com.draft.footy.SeasonSimulator;
import com.draft.footy.Xi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps a simulated {@link SeasonSimulator.SeasonResult} into the JSON view shared by the demo and draft endpoints. */
public final class SeasonViewMapper {

    private SeasonViewMapper() { }

    /** A player within a team's XI plus their season stats — drives the post-sim team viewer. */
    public record PlayerStatView(String position, String line, String name, int overall,
                                 int goals, int assists, int cleanSheets) { }
    public record TeamRow(int pos, int projectedPos, int projectedPoints, String team, String formation, int strength,
                          int points, int won, int drawn, int lost, int gf, int ga, int gd, boolean you,
                          List<PlayerStatView> players) { }
    public record StatRow(String player, String team, int value) { }
    public record OddsView(int expectedPoints, double winLeague, double top4, double relegation) { }
    /** Player of the Season — full line, so the UI can show why they won it. */
    public record PlayerAward(String player, String team, int goals, int assists, int cleanSheets) { }
    public record SeasonView(
        int overall, int attack, int midfield, int defence, int gk,
        OddsView projection,
        int finishPos, int points, int won, int drawn, int lost, int goalsFor, int goalsAgainst,
        int biggestWin, int longestWinStreak,
        List<TeamRow> table, List<StatRow> goldenBoot, List<StatRow> topAssists, List<StatRow> goldenGlove,
        PlayerAward playerOfSeason) { }

    // ---- matchday playback ----
    public record GoalView(String scorer, int minute, boolean home) { }
    public record MatchView(String home, String away, int homeGoals, int awayGoals, boolean userMatch,
                            List<GoalView> goals) { }
    public record SnapRowView(String team, boolean you, int played, int won, int drawn, int lost,
                              int gf, int ga, int gd, int points) { }
    public record MatchdayView(int number, List<MatchView> matches, List<SnapRowView> table) { }
    /** The whole season to play back, plus the final debrief shown at the end. */
    public record SeasonReplayView(List<MatchdayView> matchdays, SeasonView debrief) { }

    public static SeasonView toView(SeasonSimulator.SeasonResult res) {
        Xi userXi = res.userStanding().team;
        var user = res.userStanding();

        int n = res.table().size();
        int totalOverall = res.table().stream().mapToInt(st -> st.team.overall()).sum();
        // Each team's league-aware projected points: shift its rating by how soft/tough the OTHER teams are.
        Map<Xi, Integer> projPoints = new HashMap<>();
        for (var st : res.table()) {
            int meanOthers = (int) Math.round((totalOverall - st.team.overall()) / (double) (n - 1));
            projPoints.put(st.team, LeagueProjection.expectedPoints(st.team.overall(), meanOthers));
        }
        // Projected table order = rank by those projected points (the bookies' pre-season table).
        List<Xi> byProjected = res.table().stream().map(st -> st.team)
            .sorted(Comparator.comparingInt((Xi t) -> projPoints.get(t)).reversed())
            .toList();
        Map<Xi, Integer> projPos = new HashMap<>();
        for (int i = 0; i < byProjected.size(); i++) projPos.put(byProjected.get(i), i + 1);

        List<TeamRow> table = new ArrayList<>();
        for (int i = 0; i < res.table().size(); i++) {
            var s = res.table().get(i);
            var ord = TeamLayout.inFormationOrder(s.team);
            Map<Integer, SeasonSimulator.PlayerStat> statById = new HashMap<>();
            for (var ps : res.teamStats().getOrDefault(s.team, List.of())) statById.put(ps.player.id(), ps);
            List<PlayerStatView> players = ord.slots().stream().map(sl -> {
                var ps = statById.get(sl.player().id());
                return new PlayerStatView(sl.position(), sl.line().name(), sl.player().name(), sl.player().overall(),
                    ps != null ? ps.goals : 0, ps != null ? ps.assists : 0, ps != null ? ps.cleanSheets : 0);
            }).toList();
            table.add(new TeamRow(i + 1, projPos.getOrDefault(s.team, i + 1), projPoints.getOrDefault(s.team, 0),
                stripFormation(s.team.name), ord.formation(), s.team.overall(),
                s.points(), s.won, s.drawn, s.lost, s.gf, s.ga, s.gd(), s.team == userXi, players));
        }

        // Debrief projection = the SAME league-aware projection the pre-sim panel showed (mean of the other 19).
        int userMeanOthers = (int) Math.round((totalOverall - userXi.overall()) / (double) (n - 1));
        Projection.Odds odds = LeagueProjection.odds(userXi.overall(), userMeanOthers);

        var pots = res.playerOfSeason();
        PlayerAward potsView = pots == null ? null
            : new PlayerAward(pots.player.name(), pots.team, pots.goals, pots.assists, pots.cleanSheets);

        return new SeasonView(
            userXi.overall(), userXi.attack(), userXi.midfield(), userXi.defence(), userXi.gk(),
            new OddsView(odds.expectedPoints(), odds.winLeague(), odds.top4(), odds.relegation()),
            res.userPosition(), user.points(), user.won, user.drawn, user.lost, user.gf, user.ga,
            res.biggestWinFor(), res.longestWinStreak(),
            table,
            res.topScorers().stream().map(s -> new StatRow(s.player.name(), s.team, s.goals)).limit(10).toList(),
            res.topAssists().stream().map(s -> new StatRow(s.player.name(), s.team, s.assists)).limit(10).toList(),
            res.goldenGlove().stream().map(s -> new StatRow(s.player.name(), s.team, s.cleanSheets)).limit(10).toList(),
            potsView);
    }

    /** Build the playback payload — the matchday-by-matchday log + the final debrief. */
    public static SeasonReplayView toReplay(SeasonSimulator.SeasonResult res) {
        List<MatchdayView> mds = new ArrayList<>();
        for (var md : res.matchdays()) {
            List<MatchView> matches = md.matches().stream().map(m -> new MatchView(
                stripFormation(m.home()), stripFormation(m.away()), m.homeGoals(), m.awayGoals(), m.userMatch(),
                m.goals().stream().map(g -> new GoalView(g.scorer(), g.minute(), g.home())).toList())).toList();
            List<SnapRowView> table = md.table().stream().map(s -> new SnapRowView(
                stripFormation(s.team()), s.you(), s.played(), s.won(), s.drawn(), s.lost(),
                s.gf(), s.ga(), s.gd(), s.points())).toList();
            mds.add(new MatchdayView(md.number(), matches, table));
        }
        return new SeasonReplayView(mds, toView(res));
    }

    /** "Real Madrid CF 2018/19 (4-3-3)" -> "Real Madrid CF 2018/19" (the formation is its own field). */
    private static String stripFormation(String name) {
        return name.replaceAll(" \\([^)]*\\)$", "");
    }
}
