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
        assertTrue(avg > 85 && avg < 102, "90-rated avg points should sit near the ~95 projection, was " + avg);
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

    private double avgPoints(List<ClubSeason> clubs, List<Player> pool, int target, int n) {
        long total = 0;
        for (int s = 0; s < n; s++) total += simulatePoints(clubs, pool, target, 1000L + s);
        return total / (double) n;
    }
}
