package com.draft.footy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Encodes the invariants validated during the build:
 *  - the seeded RNG makes a season fully reproducible,
 *  - stronger teams earn more points (monotonic calibration),
 *  - a 90-rated team lands near its ~95-point projection,
 *  - structural sanity (20 teams, 38 games).
 *
 * These run against players_22.csv in the project root.
 */
class EngineTest {

    static final Formation F = Formation.F_4_3_3;
    static final Path CSV = Path.of("players_22.csv");
    static final Path MULTI_CSV = Path.of("data/male_players_all.csv");
    static final java.util.Set<String> CANON_LEAGUES =
        java.util.Set.of("Premier League", "La Liga", "Serie A", "Bundesliga", "Ligue 1");

    private List<ClubSeason> clubs() throws Exception {
        assumeTrue(Files.exists(CSV), "players_22.csv must be in the project root");
        return FifaDataLoader.loadTop5(CSV, 22);
    }

    private List<Player> pool(List<ClubSeason> clubs) {
        List<Player> pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
        return pool;
    }

    private Xi buildXi(List<Player> pool, int target) {
        Xi xi = new Xi("test");
        Set<Integer> used = new HashSet<>();
        for (String slot : F.slots()) {
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

    private int simulatePoints(List<ClubSeason> clubs, List<Player> pool, int target, long seed) {
        Xi xi = buildXi(pool, target);
        Random rng = new Random(seed);
        List<Xi> opp = new OpponentPyramid(clubs).generate(rng);
        return new SeasonSimulator().simulate(xi, opp, rng).userStanding().points();
    }

    @Test
    void seededSeasonIsReproducible() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        assertEquals(simulatePoints(clubs, pool, 88, 7L), simulatePoints(clubs, pool, 88, 7L),
            "same seed must yield identical points");
    }

    @Test
    void strongerTeamsEarnMorePoints() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        double weak = avgPoints(clubs, pool, 80, 60);
        double strong = avgPoints(clubs, pool, 90, 60);
        assertTrue(strong > weak + 15, "a 90-rated side should clearly outscore an 80-rated one (" + strong + " vs " + weak + ")");
    }

    @Test
    void eliteTeamLandsNearProjection() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        double avg = avgPoints(clubs, pool, 90, 120);
        assertTrue(avg > 86 && avg < 96, "90-rated avg points should sit near the ~91 projection (real champion mark), was " + avg);
    }

    @Test
    void leagueHasTwentyTeamsAndUserPlaysThirtyEight() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        Xi xi = buildXi(pool, 85);
        Random rng = new Random(3L);
        var res = new SeasonSimulator().simulate(xi, new OpponentPyramid(clubs).generate(rng), rng);
        assertEquals(20, res.table().size(), "league should have 20 teams");
        var u = res.userStanding();
        assertEquals(38, u.won + u.drawn + u.lost, "user should play 38 games");
    }

    @Test
    void opponentLeagueNeverRepeatsAClubAcrossEras() throws Exception {
        assumeTrue(Files.exists(MULTI_CSV), "data/male_players_all.csv must be present");
        var clubs = FifaDataLoader.loadAllSeasons(MULTI_CSV);
        var club = java.util.regex.Pattern.compile("^(.*) \\d{4}/\\d{2} \\("); // "Liverpool 2020/21 (4-3-3)" -> "Liverpool"
        for (long seed = 0; seed < 30; seed++) {
            var league = new OpponentPyramid(clubs).generate(new Random(seed));
            java.util.Set<String> seen = new HashSet<>();
            for (Xi xi : league) {
                var m = club.matcher(xi.name);
                assertTrue(m.find(), "unexpected team label: " + xi.name);
                assertTrue(seen.add(m.group(1)), "same club twice in one league: " + m.group(1) + " (seed " + seed + ")");
            }
        }
    }

    @Test
    void goldenGloveIsKeepersOnly() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        Xi xi = buildXi(pool, 85);
        Random rng = new Random(4L);
        var res = new SeasonSimulator().simulate(xi, new OpponentPyramid(clubs).generate(rng), rng);
        assertFalse(res.goldenGlove().isEmpty());
        for (var s : res.goldenGlove())
            assertEquals(Line.GK, s.player.primaryLine(), s.player.name() + " is not a keeper");
    }

    @Test
    void scheduleIsAValidDoubleRoundRobin() {
        int n = 20;
        var sched = SeasonSimulator.schedule(n);
        assertEquals(2 * (n - 1), sched.size(), "38 matchdays for 20 teams");
        int games = 0;
        Set<String> orderedPairs = new HashSet<>();
        for (var md : sched) {
            assertEquals(n / 2, md.size(), "10 games per matchday");
            boolean[] playing = new boolean[n];
            for (int[] g : md) {
                assertFalse(playing[g[0]] || playing[g[1]], "a team plays at most once per matchday");
                playing[g[0]] = playing[g[1]] = true;
                assertTrue(orderedPairs.add(g[0] + ">" + g[1]), "each ordered fixture appears exactly once");
                games++;
            }
            for (boolean b : playing) assertTrue(b, "every team plays every matchday");
        }
        assertEquals(n * (n - 1), games, "380 games total");
    }

    @Test
    void finalMatchdaySnapshotMatchesFinalTable() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        Xi xi = buildXi(pool, 85);
        Random rng = new Random(9L);
        var res = new SeasonSimulator().simulate(xi, new OpponentPyramid(clubs).generate(rng), rng);
        assertEquals(38, res.matchdays().size());
        var lastSnap = res.matchdays().get(37).table();
        assertEquals(res.table().size(), lastSnap.size());
        for (int i = 0; i < lastSnap.size(); i++) {
            assertEquals(res.table().get(i).team.name, lastSnap.get(i).team(), "snapshot order matches final table at " + i);
            assertEquals(res.table().get(i).points(), lastSnap.get(i).points());
        }
        for (var row : lastSnap) assertEquals(38, row.played(), "every team played 38");
    }

    @Test
    void dixonColesTauBoostsLowDrawsAndLeavesOthersAlone() {
        // RHO < 0: the four low-score cells shift mass into the draws (0-0, 1-1); everything else is untouched.
        double l = 1.6, m = 1.3;
        assertTrue(MatchEngine.tau(0, 0, l, m) > 1, "0-0 should be boosted");
        assertTrue(MatchEngine.tau(1, 1, l, m) > 1, "1-1 should be boosted");
        assertTrue(MatchEngine.tau(0, 1, l, m) < 1, "0-1 should be suppressed");
        assertTrue(MatchEngine.tau(1, 0, l, m) < 1, "1-0 should be suppressed");
        assertEquals(1.0, MatchEngine.tau(2, 2, l, m), 1e-12, "non-low scores are unchanged");
        assertEquals(1.0, MatchEngine.tau(3, 0, l, m), 1e-12, "non-low scores are unchanged");
    }

    @Test
    void leagueDrawRateIsRealistic() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        Xi xi = buildXi(pool, 84);
        Random rng = new Random(42L);
        var table = new SeasonSimulator().simulate(xi, new OpponentPyramid(clubs).generate(rng), rng).table();
        int drawnEntries = table.stream().mapToInt(s -> s.drawn).sum();
        int games = table.size() * (table.size() - 1); // home + away round robin
        double drawRate = (drawnEntries / 2.0) / games;
        assertTrue(drawRate > 0.15 && drawRate < 0.35,
            "Dixon-Coles draw rate should sit in a realistic band, was " + drawRate);
    }

    @Test
    void multiSeasonIngestSpansEditionsAndOnlyTop5Leagues() throws Exception {
        assumeTrue(Files.exists(MULTI_CSV), "data/male_players_all.csv (FIFA 15–23 combined export) must be present");
        var clubs = FifaDataLoader.loadAllSeasons(MULTI_CSV);

        long editions = clubs.stream().map(c -> c.season).distinct().count();
        assertTrue(editions >= 8, "combined export should span ~9 editions, saw " + editions);
        assertTrue(clubs.size() > 700 && clubs.size() < 1000,
            "expected ~880 top-5 club-seasons, saw " + clubs.size());
        for (ClubSeason cs : clubs)
            assertTrue(CANON_LEAGUES.contains(cs.league),
                "non-top-5 league leaked in: " + cs.league + " (" + cs.label() + ")");
    }

    @Test
    void primeModeTakesCareerBestSnapshot() throws Exception {
        assumeTrue(Files.exists(MULTI_CSV), "data/male_players_all.csv (FIFA 15–23 combined export) must be present");
        var clubs = FifaDataLoader.loadAllSeasons(MULTI_CSV);
        var pool = pool(clubs);
        PrimeIndex prime = PrimeIndex.from(clubs);

        // True career-best overall per id, computed independently from the ingest.
        java.util.Map<Integer, Integer> maxById = new java.util.HashMap<>();
        java.util.Map<Integer, Long> rowsById = new java.util.HashMap<>();
        for (Player p : pool) {
            maxById.merge(p.id(), p.overall(), Math::max);
            rowsById.merge(p.id(), 1L, Long::sum);
        }
        // Every prime lookup returns that id's peak overall...
        for (Player p : pool)
            assertEquals(maxById.get(p.id()), prime.prime(p).overall(),
                "prime overall must be the career best for " + p.name());
        // ...and the peak rating + positions come from the SAME row (the peak Player IS a real row).
        Player anyMulti = pool.stream().filter(p -> rowsById.get(p.id()) > 1).findFirst().orElseThrow();
        Player peak = prime.prime(anyMulti);
        assertTrue(pool.stream().anyMatch(p -> p.id() == peak.id()
                && p.overall() == peak.overall() && p.positions().equals(peak.positions())),
            "prime snapshot must be a coherent real edition row (rating + positions together)");
    }

    @Test
    void primeModeIsNoOpOnSingleEdition() throws Exception {
        var clubs = clubs(); // players_22.csv — one edition, one row per id
        var pool = pool(clubs);
        PrimeIndex prime = PrimeIndex.from(clubs);
        for (Player p : pool) assertEquals(p, prime.prime(p), "single-edition prime must be a no-op");
    }

    private double avgPoints(List<ClubSeason> clubs, List<Player> pool, int target, int n) {
        long total = 0;
        for (int s = 0; s < n; s++) total += simulatePoints(clubs, pool, target, 1000L + s);
        return total / (double) n;
    }
}
