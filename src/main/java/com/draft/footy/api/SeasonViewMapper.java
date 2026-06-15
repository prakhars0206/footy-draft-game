package com.draft.footy.api;

import com.draft.footy.MonteCarlo;
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
                          List<PlayerStatView> players, MonteCarloView monteCarlo) { }
    public record StatRow(String player, String team, int value) { }
    public record OddsView(int expectedPoints, double winLeague, double top4, double relegation) { }
    /** Monte-Carlo "real bookies": odds + a points distribution from N simulated seasons. `percentile` is the
     *  actual season's rank in that cloud (debrief only; null pre-season). */
    public record MonteCarloView(int sims, double mean, int min, int p5, int p25, int median, int p75, int p95, int max,
                                 double title, double top4, double top6, double relegation, double unbeaten, double perfect,
                                 int histMin, int histBinWidth, int[] histogram, Integer percentile) { }

    public static MonteCarloView cloudView(MonteCarlo.TeamCloud c, int sims) { return cloudView(c, sims, null); }

    /** Map a single team's cloud to the view (per-team odds + distribution). `percentile` places its actual season. */
    public static MonteCarloView cloudView(MonteCarlo.TeamCloud c, int sims, Integer percentile) {
        return new MonteCarloView(sims, c.mean(), c.min(), c.p5(), c.p25(), c.median(), c.p75(), c.p95(), c.max(),
            c.title(), c.top4(), c.top6(), c.relegation(), c.unbeaten(), 0.0,
            c.histMin(), c.histBinWidth(), c.histogram(), percentile);
    }

    public static MonteCarloView mcView(MonteCarlo.Outcome o, Integer percentile) {
        if (o == null) return null;
        return new MonteCarloView(o.sims(), o.meanPoints(), o.min(), o.p5(), o.p25(), o.median(), o.p75(), o.p95(), o.max(),
            o.titleOdds(), o.top4Odds(), o.top6Odds(), o.relegationOdds(), o.unbeatenOdds(), o.perfectOdds(),
            o.histMin(), o.histBinWidth(), o.histogram(), percentile);
    }
    /** Player of the Season — full line, so the UI can show why they won it. */
    public record PlayerAward(String player, String team, int goals, int assists, int cleanSheets) { }
    public record SeasonView(
        int overall, int attack, int midfield, int defence, int gk,
        OddsView projection,
        int finishPos, int points, int won, int drawn, int lost, int goalsFor, int goalsAgainst,
        int biggestWin, int longestWinStreak,
        List<TeamRow> table, List<StatRow> goldenBoot, List<StatRow> topAssists, List<StatRow> goldenGlove,
        PlayerAward playerOfSeason, MonteCarloView monteCarlo) { }

    // ---- matchday playback ----
    public record GoalView(String scorer, int minute, boolean home) { }
    public record MatchView(String home, String away, int homeGoals, int awayGoals, boolean userMatch,
                            List<GoalView> goals) { }
    public record SnapRowView(String team, boolean you, int played, int won, int drawn, int lost,
                              int gf, int ga, int gd, int points) { }
    public record MatchdayView(int number, List<MatchView> matches, List<SnapRowView> table) { }
    /** The whole season to play back, plus the final debrief shown at the end. */
    public record SeasonReplayView(List<MatchdayView> matchdays, SeasonView debrief) { }

    public static SeasonView toView(SeasonSimulator.SeasonResult res) { return toView(res, null); }

    public static SeasonView toView(SeasonSimulator.SeasonResult res, MonteCarlo.Outcome mc) {
        Xi userXi = res.userStanding().team;
        var user = res.userStanding();

        int n = res.table().size();
        int totalOverall = res.table().stream().mapToInt(st -> st.team.overall()).sum();
        // Each team's projection: the Monte-Carlo "real bookies" (mean points + rank) when we have it, else the
        // fitted league-aware curve (the stateless demo path, mc == null). Same source as the pre-sim panel.
        Map<Xi, Integer> projPoints = new HashMap<>();
        Map<Xi, Integer> projPos = new HashMap<>();
        if (mc != null) {
            for (var st : res.table()) {
                var tp = mc.teamClouds().get(st.team);
                projPoints.put(st.team, tp.projectedPoints());
                projPos.put(st.team, tp.projectedPos());
            }
        } else {
            for (var st : res.table()) {
                int meanOthers = (int) Math.round((totalOverall - st.team.overall()) / (double) (n - 1));
                projPoints.put(st.team, LeagueProjection.expectedPoints(st.team.overall(), meanOthers));
            }
            List<Xi> byProjected = res.table().stream().map(st -> st.team)
                .sorted(Comparator.comparingInt((Xi t) -> projPoints.get(t)).reversed())
                .toList();
            for (int i = 0; i < byProjected.size(); i++) projPos.put(byProjected.get(i), i + 1);
        }

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
            // Each team's cloud, with its ACTUAL season placed in it (percentile) — drives the click-a-team panel.
            MonteCarloView teamMc = mc == null ? null
                : cloudView(mc.teamClouds().get(s.team), mc.sims(), mc.teamClouds().get(s.team).percentile(s.points()));
            table.add(new TeamRow(i + 1, projPos.getOrDefault(s.team, i + 1), projPoints.getOrDefault(s.team, 0),
                stripFormation(s.team.name), ord.formation(), s.team.overall(),
                s.points(), s.won, s.drawn, s.lost, s.gf, s.ga, s.gd(), s.team == userXi, players, teamMc));
        }

        // Debrief headline projection — Monte-Carlo (expected points + real odds) when present, else the fitted
        // curve, matching whatever the pre-sim panel showed.
        OddsView oddsView;
        if (mc != null) {
            var userTp = mc.teamClouds().get(userXi);
            oddsView = new OddsView(userTp.projectedPoints(), mc.titleOdds(), mc.top4Odds(), mc.relegationOdds());
        } else {
            int userMeanOthers = (int) Math.round((totalOverall - userXi.overall()) / (double) (n - 1));
            Projection.Odds odds = LeagueProjection.odds(userXi.overall(), userMeanOthers);
            oddsView = new OddsView(odds.expectedPoints(), odds.winLeague(), odds.top4(), odds.relegation());
        }

        var pots = res.playerOfSeason();
        PlayerAward potsView = pots == null ? null
            : new PlayerAward(pots.player.name(), pots.team, pots.goals, pots.assists, pots.cleanSheets);

        return new SeasonView(
            userXi.overall(), userXi.attack(), userXi.midfield(), userXi.defence(), userXi.gk(),
            oddsView,
            res.userPosition(), user.points(), user.won, user.drawn, user.lost, user.gf, user.ga,
            res.biggestWinFor(), res.longestWinStreak(),
            table,
            res.topScorers().stream().map(s -> new StatRow(s.player.name(), s.team, s.goals)).limit(10).toList(),
            res.topAssists().stream().map(s -> new StatRow(s.player.name(), s.team, s.assists)).limit(10).toList(),
            res.goldenGlove().stream().map(s -> new StatRow(s.player.name(), s.team, s.cleanSheets)).limit(10).toList(),
            potsView,
            mcView(mc, mc == null ? null : mc.percentile(user.points())));
    }

    public static SeasonReplayView toReplay(SeasonSimulator.SeasonResult res) { return toReplay(res, null); }

    /** Build the playback payload — the matchday-by-matchday log + the final debrief (with Monte-Carlo cloud). */
    public static SeasonReplayView toReplay(SeasonSimulator.SeasonResult res, MonteCarlo.Outcome mc) {
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
        return new SeasonReplayView(mds, toView(res, mc));
    }

    /** "Real Madrid CF 2018/19 (4-3-3)" -> "Real Madrid CF 2018/19" (the formation is its own field). */
    private static String stripFormation(String name) {
        return name.replaceAll(" \\([^)]*\\)$", "");
    }
}
