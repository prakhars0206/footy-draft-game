package com.draft.footy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Simulates an entire league season: every team plays every other home and away, stats tracked for all players. */
public final class SeasonSimulator {

    private final MatchEngine engine = new MatchEngine();

    public static final class Standing {
        public final Xi team;
        public int played, won, drawn, lost, gf, ga;
        Standing(Xi team) { this.team = team; }
        public int points() { return won * 3 + drawn; }
        public int gd() { return gf - ga; }
    }

    public static final class PlayerStat {
        public final Player player; public final String team;
        public int goals, assists, cleanSheets;
        PlayerStat(Player p, String team) { this.player = p; this.team = team; }
    }

    public record SeasonResult(
        List<Standing> table, Standing userStanding, int userPosition,
        List<PlayerStat> topScorers, List<PlayerStat> topAssists, List<PlayerStat> topCleanSheets,
        PlayerStat playerOfSeason,
        int biggestWinFor, int biggestWinAgainst, int longestWinStreak) {}

    public SeasonResult simulate(Xi userXi, List<Xi> opponents, Random rng) {
        List<Xi> teams = new ArrayList<>();
        teams.add(userXi);
        teams.addAll(opponents);

        Map<Xi, Standing> standings = new HashMap<>();
        for (Xi t : teams) standings.put(t, new Standing(t));
        // Stats keyed per TEAM then player — the same real player on two teams is two competitors.
        Map<Xi, Map<Integer, PlayerStat>> stats = new HashMap<>();
        for (Xi t : teams) {
            Map<Integer, PlayerStat> m = new HashMap<>();
            for (Player p : t.players()) m.put(p.id(), new PlayerStat(p, t.name));
            stats.put(t, m);
        }

        // User-team match outcomes in order, for streak / biggest-win extraction.
        List<int[]> userScores = new ArrayList<>(); // {for, against}

        for (Xi home : teams) {
            for (Xi away : teams) {
                if (home == away) continue;
                MatchEngine.Result r = engine.play(home, away, rng);
                record(standings.get(home), r.homeGoals(), r.awayGoals());
                record(standings.get(away), r.awayGoals(), r.homeGoals());

                for (MatchEngine.GoalEvent g : r.events()) {
                    Xi team = g.home() ? home : away;
                    stats.get(team).get(g.scorer().id()).goals++;
                    if (g.assist() != null) stats.get(team).get(g.assist().id()).assists++;
                }
                if (r.homeCleanSheet()) for (Player p : home.backline()) stats.get(home).get(p.id()).cleanSheets++;
                if (r.awayCleanSheet()) for (Player p : away.backline()) stats.get(away).get(p.id()).cleanSheets++;

                if (home == userXi) userScores.add(new int[]{r.homeGoals(), r.awayGoals()});
                if (away == userXi) userScores.add(new int[]{r.awayGoals(), r.homeGoals()});
            }
        }

        List<Standing> table = new ArrayList<>(standings.values());
        table.sort(Comparator.comparingInt(Standing::points)
            .thenComparingInt(Standing::gd).thenComparingInt(s -> s.gf).reversed());

        Standing user = standings.get(userXi);
        int pos = table.indexOf(user) + 1;

        return new SeasonResult(table, user, pos,
            topBy(stats, s -> s.goals), topBy(stats, s -> s.assists), topBy(stats, s -> s.cleanSheets),
            playerOfSeason(stats),
            biggest(userScores, true), biggest(userScores, false), longestStreak(userScores));
    }

    private void record(Standing s, int gf, int ga) {
        s.played++; s.gf += gf; s.ga += ga;
        if (gf > ga) s.won++; else if (gf == ga) s.drawn++; else s.lost++;
    }

    /** Player of the Season — best all-round contribution across the whole league (goals + creation + clean sheets). */
    private PlayerStat playerOfSeason(Map<Xi, Map<Integer, PlayerStat>> stats) {
        PlayerStat best = null;
        double bestScore = -1;
        for (Map<Integer, PlayerStat> m : stats.values())
            for (PlayerStat s : m.values()) {
                double score = s.goals * 4 + s.assists * 3 + s.cleanSheets * 2.0;
                if (score > bestScore) { bestScore = score; best = s; }
            }
        return best;
    }

    private List<PlayerStat> topBy(Map<Xi, Map<Integer, PlayerStat>> stats, java.util.function.ToIntFunction<PlayerStat> key) {
        List<PlayerStat> list = new ArrayList<>();
        for (Map<Integer, PlayerStat> m : stats.values()) list.addAll(m.values());
        list.sort(Comparator.comparingInt(key).reversed());
        return list.subList(0, Math.min(10, list.size()));
    }

    private int biggest(List<int[]> scores, boolean forSide) {
        int best = 0;
        for (int[] s : scores) { int margin = forSide ? s[0] - s[1] : s[1] - s[0]; best = Math.max(best, margin); }
        return best;
    }

    private int longestStreak(List<int[]> scores) {
        int best = 0, cur = 0;
        for (int[] s : scores) { if (s[0] > s[1]) best = Math.max(best, ++cur); else cur = 0; }
        return best;
    }
}
