package com.draft.footy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Runnable proof-of-engine: builds an XI, simulates a full league season, and runs a calibration sweep. */
public final class Demo {

    static final Formation FORMATION = Formation.F_4_3_3;

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "data/male_players_all.csv");
        // A legacy single-season file (players_NN.csv) has no fifa_version column — load it for that edition;
        // otherwise use the multi-edition path (per-row fifa_version).
        var m = java.util.regex.Pattern.compile("players_(\\d+)").matcher(csv.getFileName().toString());
        List<ClubSeason> clubs = m.find()
            ? FifaDataLoader.loadTop5(csv, Integer.parseInt(m.group(1)))
            : FifaDataLoader.loadAllSeasons(csv);
        List<Player> pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
        long seasons = clubs.stream().map(c -> c.season).distinct().count();
        System.out.printf("Loaded %d top-5 club-seasons across %d editions, %d player-seasons.%n%n",
            clubs.size(), seasons, pool.size());

        // ---------- One detailed example season (a strong ~90 XI) ----------
        Xi userXi = buildXi(pool, 90, "Your XI");
        Random rng = new Random(42);
        List<Xi> opponents = new OpponentPyramid(clubs).generate(rng);
        Projection.Odds odds = Projection.odds(userXi.overall());
        SeasonSimulator.SeasonResult res = new SeasonSimulator().simulate(userXi, opponents, rng);

        System.out.println("================ EXAMPLE SEASON ================");
        System.out.printf("Your XI (%s) — Overall %d  [ATT %d  MID %d  DEF %d  GK %d]%n",
            FORMATION.label(), userXi.overall(), userXi.attack(), userXi.midfield(), userXi.defence(), userXi.gk());
        System.out.printf("Pre-season (bookies): projected %d pts | win league %.1f%% | top4 %.1f%% | relegation %.1f%%%n",
            odds.expectedPoints(), odds.winLeague()*100, odds.top4()*100, odds.relegation()*100);

        SeasonSimulator.Standing u = res.userStanding();
        System.out.printf("ACTUAL: %dst-ish (pos %d)  —  %dW %dD %dL  %d pts  (GF %d / GA %d)  %s%n",
            res.userPosition(), res.userPosition(), u.won, u.drawn, u.lost, u.points(), u.gf, u.ga,
            verdict(odds.expectedPoints(), u.points()));
        System.out.printf("Biggest win margin: +%d   Longest win streak: %d%n",
            res.biggestWinFor(), res.longestWinStreak());

        int totalDrawnEntries = 0, totalGf = 0;
        for (SeasonSimulator.Standing s : res.table()) { totalDrawnEntries += s.drawn; totalGf += s.gf; }
        int games = res.table().size() * (res.table().size() - 1); // home+away round robin
        System.out.printf("League draw rate: %.1f%%   avg goals/game: %.2f%n%n",
            100.0 * (totalDrawnEntries / 2.0) / games, (double) totalGf / games);

        System.out.println("Final table (top 6 + your team):");
        List<SeasonSimulator.Standing> t = res.table();
        for (int i = 0; i < Math.min(6, t.size()); i++) printRow(i + 1, t.get(i), userXi);
        if (res.userPosition() > 6) printRow(res.userPosition(), u, userXi);

        System.out.println("\nGolden Boot race (LEAGUE-WIDE — includes opponents):");
        for (int i = 0; i < Math.min(5, res.topScorers().size()); i++) {
            SeasonSimulator.PlayerStat s = res.topScorers().get(i);
            System.out.printf("  %2d goals  %-22s (%s)%n", s.goals, s.player.name(), s.team);
        }
        System.out.println("Top assists (league-wide):");
        for (int i = 0; i < Math.min(3, res.topAssists().size()); i++) {
            SeasonSimulator.PlayerStat s = res.topAssists().get(i);
            System.out.printf("  %2d assists %-22s (%s)%n", s.assists, s.player.name(), s.team);
        }

        // ---------- Calibration sweep ----------
        System.out.println("\n================ CALIBRATION SWEEP ================");
        System.out.println("(per overall: avg points over N seasons, and how often the perfect 38-0-0 happens)");
        int N = 400;
        for (int target : new int[]{75, 80, 83, 86, 90, 92}) {
            Xi xi = buildXi(pool, target, "test");
            long totalPts = 0; int unbeaten = 0; int perfect = 0; int wins38 = 0;
            for (int s = 0; s < N; s++) {
                Random r = new Random(1000L + s);
                List<Xi> opp = new OpponentPyramid(clubs).generate(r);
                SeasonSimulator.Standing st = new SeasonSimulator().simulate(xi, opp, r).userStanding();
                totalPts += st.points();
                if (st.lost == 0) unbeaten++;
                if (st.won == 38) wins38++;
                if (st.won == 38 && st.lost == 0) perfect++;
            }
            System.out.printf("  overall %d  ->  avg %5.1f pts | projected %d | unbeaten %4.1f%% | 38-0 %4.1f%%%n",
                xi.overall(), totalPts / (double) N, Projection.expectedPoints(xi.overall()),
                100.0 * unbeaten / N, 100.0 * perfect / N);
        }
    }

    private static String verdict(int projected, int actual) {
        if (actual >= projected + 6) return "OVERPERFORMED";
        if (actual <= projected - 6) return "UNDERPERFORMED";
        return "as expected";
    }

    private static void printRow(int pos, SeasonSimulator.Standing s, Xi userXi) {
        String mark = (s.team == userXi) ? " <-- you" : "";
        System.out.printf("  %2d. %-26s %2d pts  (%dW %dD %dL, GD %+d)%s%n",
            pos, trim(s.team.name, 26), s.points(), s.won, s.drawn, s.lost, s.gd(), mark);
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    /** Build an XI near a target overall by picking eligible players close to that rating for each slot. */
    private static Xi buildXi(List<Player> pool, int target, String name) {
        Xi xi = new Xi(name);
        Set<Integer> used = new HashSet<>();
        for (String slot : FORMATION.slots()) {
            Player best = null; int bestDist = Integer.MAX_VALUE;
            for (Player p : pool) {
                if (used.contains(p.id()) || !p.canPlay(slot)) continue;
                int d = Math.abs(p.overall() - target);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) { xi.add(slot, best); used.add(best.id()); }
        }
        return xi;
    }
}
