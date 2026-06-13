package com.draft.footy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Simulates an entire league season: every team plays every other home and away, stats tracked for all players. */
public final class SeasonSimulator {

    /**
     * Season-form spread (in overall-rating points). Each team draws a mean-zero gaussian form for the whole
     * season — leaves the long-run average (and the projection) intact while making a single season swing, so
     * teams genuinely over/under-perform. The drama dial: larger = more upsets/surprises.
     */
    static final double FORM_SIGMA = 2.0;

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

    public record Goal(String scorer, int minute, boolean home) {}
    public record MatchResult(String home, String away, int homeGoals, int awayGoals, boolean userMatch,
                              List<Goal> goals) {}
    public record SnapRow(String team, boolean you, int played, int won, int drawn, int lost,
                          int gf, int ga, int gd, int points) {}
    /** One matchday: its 10 fixtures + a snapshot of the league table after they're played. */
    public record Matchday(int number, List<MatchResult> matches, List<SnapRow> table) {}

    public record SeasonResult(
        List<Standing> table, Standing userStanding, int userPosition,
        List<PlayerStat> topScorers, List<PlayerStat> topAssists, List<PlayerStat> goldenGlove,
        PlayerStat playerOfSeason,
        int biggestWinFor, int biggestWinAgainst, int longestWinStreak,
        Map<Xi, List<PlayerStat>> teamStats, List<Matchday> matchdays) {}

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

        // Each team's season form — a good/bad campaign, drawn once (seeded, so still reproducible).
        Map<Xi, Double> form = new HashMap<>();
        for (Xi t : teams) form.put(t, rng.nextGaussian() * FORM_SIGMA);

        // User-team match outcomes in matchday order, for streak / biggest-win extraction.
        List<int[]> userScores = new ArrayList<>(); // {for, against}
        List<Matchday> matchdays = new ArrayList<>();
        int mdNum = 0;

        for (List<int[]> round : schedule(teams.size())) {
            mdNum++;
            List<MatchResult> results = new ArrayList<>();
            for (int[] fixture : round) {
                Xi home = teams.get(fixture[0]), away = teams.get(fixture[1]);
                MatchEngine.Result r = engine.play(home, away, form.get(home), form.get(away), rng);
                record(standings.get(home), r.homeGoals(), r.awayGoals());
                record(standings.get(away), r.awayGoals(), r.homeGoals());

                List<Goal> goals = new ArrayList<>();
                for (MatchEngine.GoalEvent g : r.events()) {
                    Xi team = g.home() ? home : away;
                    stats.get(team).get(g.scorer().id()).goals++;
                    if (g.assist() != null) stats.get(team).get(g.assist().id()).assists++;
                    goals.add(new Goal(g.scorer().name(), g.minute(), g.home()));
                }
                if (r.homeCleanSheet()) for (Player p : home.backline()) stats.get(home).get(p.id()).cleanSheets++;
                if (r.awayCleanSheet()) for (Player p : away.backline()) stats.get(away).get(p.id()).cleanSheets++;

                if (home == userXi) userScores.add(new int[]{r.homeGoals(), r.awayGoals()});
                if (away == userXi) userScores.add(new int[]{r.awayGoals(), r.homeGoals()});

                goals.sort(Comparator.comparingInt(Goal::minute));
                results.add(new MatchResult(home.name, away.name, r.homeGoals(), r.awayGoals(),
                    home == userXi || away == userXi, goals));
            }
            matchdays.add(new Matchday(mdNum, results, snapshot(standings, userXi)));
        }

        List<Standing> table = new ArrayList<>(standings.values());
        table.sort(tableOrder());

        Standing user = standings.get(userXi);
        int pos = table.indexOf(user) + 1;

        Map<Xi, List<PlayerStat>> teamStats = new HashMap<>();
        for (var e : stats.entrySet()) teamStats.put(e.getKey(), new ArrayList<>(e.getValue().values()));

        return new SeasonResult(table, user, pos,
            topBy(stats, s -> s.goals, p -> true),
            topBy(stats, s -> s.assists, p -> true),
            // Golden Glove is the keepers' award — clean sheets are credited to the whole backline, so filter to GK.
            topBy(stats, s -> s.cleanSheets, p -> p.player.primaryLine() == Line.GK),
            playerOfSeason(stats),
            biggest(userScores, true), biggest(userScores, false), longestStreak(userScores),
            teamStats, matchdays);
    }

    private static Comparator<Standing> tableOrder() {
        return Comparator.comparingInt(Standing::points)
            .thenComparingInt(Standing::gd).thenComparingInt((Standing s) -> s.gf).reversed();
    }

    /** Snapshot the current table (sorted), flagging the user's row. */
    private List<SnapRow> snapshot(Map<Xi, Standing> standings, Xi userXi) {
        List<Standing> t = new ArrayList<>(standings.values());
        t.sort(tableOrder());
        List<SnapRow> rows = new ArrayList<>();
        for (Standing s : t)
            rows.add(new SnapRow(s.team.name, s.team == userXi, s.played, s.won, s.drawn, s.lost,
                s.gf, s.ga, s.gd(), s.points()));
        return rows;
    }

    /**
     * Round-robin fixture calendar (circle method) for an even team count: 2·(n-1) matchdays of n/2 games.
     * Single round-robin (each pair once), then mirrored with home/away swapped — same 380 fixtures as the old
     * nested loop, grouped into matchdays. Deterministic (no RNG); home/away alternates for spacing.
     */
    static List<List<int[]>> schedule(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        List<List<int[]>> first = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<int[]> md = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                int a = arr[i], b = arr[n - 1 - i];
                md.add((r + i) % 2 == 0 ? new int[]{a, b} : new int[]{b, a});
            }
            first.add(md);
            int last = arr[n - 1];            // rotate, keeping arr[0] fixed
            for (int i = n - 1; i > 1; i--) arr[i] = arr[i - 1];
            arr[1] = last;
        }
        List<List<int[]>> rounds = new ArrayList<>(first);
        for (List<int[]> md : first) {         // second leg: swap home/away
            List<int[]> mirror = new ArrayList<>();
            for (int[] m : md) mirror.add(new int[]{m[1], m[0]});
            rounds.add(mirror);
        }
        return rounds;
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

    private List<PlayerStat> topBy(Map<Xi, Map<Integer, PlayerStat>> stats,
                                   java.util.function.ToIntFunction<PlayerStat> key,
                                   java.util.function.Predicate<PlayerStat> filter) {
        List<PlayerStat> list = new ArrayList<>();
        for (Map<Integer, PlayerStat> m : stats.values())
            for (PlayerStat s : m.values()) if (filter.test(s)) list.add(s);
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
