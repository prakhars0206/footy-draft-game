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
 *  - a 90-rated team lands near its ~89-point (real champion) projection,
 *  - structural sanity (20 teams, 38 games).
 *
 * Calibration runs against the multi-era pool (data/male_players_all.csv) — the pool the GAME actually
 * plays — so the assertions track what a player really experiences. The Prime-Mode no-op test is the only
 * one that needs a single edition (players_22.csv).
 */
class EngineTest {

    static final Formation F = Formation.F_4_3_3;
    static final Path CSV = Path.of("players_22.csv");
    static final Path MULTI_CSV = Path.of("data/male_players_all.csv");
    static final java.util.Set<String> CANON_LEAGUES =
        java.util.Set.of("Premier League", "La Liga", "Serie A", "Bundesliga", "Ligue 1");

    // Cached across test methods — the multi-edition export is ~91MB, so load each pool only once.
    private static List<ClubSeason> multiCache, singleCache;

    /** The multi-era pool the GAME actually plays (FIFA 15–23); calibration asserts against this. */
    private List<ClubSeason> clubs() throws Exception {
        assumeTrue(Files.exists(MULTI_CSV), "data/male_players_all.csv (FIFA 15–23 combined export) must be present");
        if (multiCache == null) multiCache = FifaDataLoader.loadAllSeasons(MULTI_CSV);
        return multiCache;
    }

    /** A single FIFA-22 edition — only for the Prime-Mode no-op test (one row per id). */
    private List<ClubSeason> singleEditionClubs() throws Exception {
        assumeTrue(Files.exists(CSV), "players_22.csv must be in the project root");
        if (singleCache == null) singleCache = FifaDataLoader.loadTop5(CSV, 22);
        return singleCache;
    }

    private List<Player> pool(List<ClubSeason> clubs) {
        List<Player> pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
        return pool;
    }

    private Xi buildXi(List<Player> pool, int target) {
        Xi xi = new Xi("test", F);
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

    /**
     * A 90-rated XI in the OPPONENT PYRAMID, which is a far harsher league than any real one — it spans
     * cross-era minnows to juggernauts by design, so the band here sits below the real ~89-point champion
     * mark and should not be compared to it. Whether the constants reproduce actual football is a separate
     * question, answered directly by {@code HistoricalValidation} against real league-seasons.
     */
    @Test
    void eliteTeamLandsNearProjection() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        double avg = avgPoints(clubs, pool, 90, 120);
        assertTrue(avg > 81 && avg < 96, "90-rated avg points should sit near the pyramid's title mark, was " + avg);
    }

    /** The fitted constants loaded, and the model form the engine implements matches what they were fit for. */
    @Test
    void calibrationIsTheFittedOne() {
        assertTrue(Calibration.FITTED,
            "calibration.properties should be on the classpath; running on hand-tuned fallbacks");
        assertEquals("split-scale-v2", Calibration.MODEL_FORM);
        assertTrue(Calibration.SCALE_ATTACK < Calibration.SCALE_DEFENCE,
            "attack should be the more rating-sensitive side (fitted ~20.1 vs ~26.6)");
        assertTrue(GameBalance.scaleAttack() <= Calibration.SCALE_ATTACK,
            "de-shrinking may only tighten the scale, never loosen it");
        assertTrue(GameBalance.scaleDefence() <= Calibration.SCALE_DEFENCE,
            "de-shrinking may only tighten the scale, never loosen it");
        assertTrue(Calibration.HOME_ADV_LOG > 0.15 && Calibration.HOME_ADV_LOG < 0.40,
            "home advantage in log-goals, was " + Calibration.HOME_ADV_LOG);
    }

    /** Home advantage applies to the home rate only (Dixon-Coles), never as a penalty on the away side. */
    @Test
    void homeAdvantageLiftsHomeOnly() {
        double away = MatchEngine.lambda(Calibration.ATTACK_REF, Calibration.DEFENCE_REF, false);
        double home = MatchEngine.lambda(Calibration.ATTACK_REF, Calibration.DEFENCE_REF, true);
        assertEquals(Calibration.BASE_GOALS, away, 1e-9, "an average side away = BASE_GOALS exactly");
        assertEquals(Calibration.BASE_GOALS * Math.exp(Calibration.HOME_ADV_LOG), home, 1e-9);
        assertTrue(home > away);
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
        var clubs = clubs(); // multi-era pool
        var club = java.util.regex.Pattern.compile("^(.*) \\d{4}/\\d{2}$"); // "Liverpool 2020/21" -> "Liverpool"
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
        var clubs = clubs(); // multi-era pool

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
        var clubs = clubs(); // multi-era pool
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
        var clubs = singleEditionClubs(); // players_22.csv — one edition, one row per id
        var pool = pool(clubs);
        PrimeIndex prime = PrimeIndex.from(clubs);
        for (Player p : pool) assertEquals(p, prime.prime(p), "single-edition prime must be a no-op");
    }

    @Test
    void monteCarloIsDeterministicAndOrdered() throws Exception {
        var clubs = clubs(); var pool = pool(clubs);
        var opps = new OpponentPyramid(clubs).generate(new Random(7L));
        var strong = MonteCarlo.run(buildXi(pool, 90), opps, 300, 7L);
        var strongAgain = MonteCarlo.run(buildXi(pool, 90), opps, 300, 7L);
        var weak = MonteCarlo.run(buildXi(pool, 78), opps, 300, 7L);

        assertEquals(strong.median(), strongAgain.median(), "same seed ⇒ identical distribution");
        assertArrayEquals(strong.histogram(), strongAgain.histogram(), "same seed ⇒ identical histogram");
        assertTrue(strong.median() > weak.median() + 10,
            "stronger squad ⇒ higher median (" + strong.median() + " vs " + weak.median() + ")");
        assertTrue(strong.titleOdds() > weak.titleOdds(), "stronger squad ⇒ better title odds");
        assertTrue(strong.top4Odds() >= 0 && strong.top4Odds() <= 1, "odds are probabilities");
        assertTrue(strong.p95() >= strong.p5(), "quantiles ordered");
        assertTrue(strong.percentile(strong.max()) >= strong.percentile(strong.min()), "percentile is monotonic");
    }

    private double avgPoints(List<ClubSeason> clubs, List<Player> pool, int target, int n) {
        long total = 0;
        for (int s = 0; s < n; s++) total += simulatePoints(clubs, pool, target, 1000L + s);
        return total / (double) n;
    }
}
