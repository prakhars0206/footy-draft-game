package com.draft.footy.api;

import com.draft.footy.*;
import com.draft.footy.persistence.ClubSeasonRepository;
import com.draft.footy.persistence.EngineMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Loads the player pool from the H2-seeded repository once at startup and exposes season simulation. */
@Service
@DependsOn("dataSeeder") // ensure H2 is seeded before we read it back
public class SimulationService {

    private final ClubSeasonRepository repo;

    private List<ClubSeason> clubs;
    private List<Player> pool;
    private PrimeIndex primeIndex;

    public SimulationService(ClubSeasonRepository repo) { this.repo = repo; }

    /**
     * Reads the seeded club-seasons back through the repository (DESIGN_SPEC §16) and builds the engine pool.
     * Runs in {@code @PostConstruct} (after {@code DataSeeder} via {@code @DependsOn}) so the cache is ready
     * before the web server serves. The fetch-join query initialises each roster, so no open transaction is
     * needed during the entity → engine mapping.
     */
    @PostConstruct
    void load() {
        clubs = repo.findAllWithPlayers().stream().map(EngineMapper::toEngine).toList();
        pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
        primeIndex = PrimeIndex.from(clubs);
    }

    /** Simulate one season for an auto-built XI of the given target overall, in a given formation, with a seed. */
    public SeasonSimulator.SeasonResult simulateDemo(int targetOverall, Formation formation, long seed, boolean prime) {
        Xi userXi = buildXi(targetOverall, formation, prime);
        Random rng = new Random(seed);
        List<Xi> opponents = new OpponentPyramid(clubs).generate(rng);
        return new SeasonSimulator().simulate(userXi, opponents, rng);
    }

    public Xi buildDemoXi(int targetOverall, Formation formation, boolean prime) {
        return buildXi(targetOverall, formation, prime);
    }

    public Projection.Odds projection(int overall) { return Projection.odds(overall); }

    /**
     * Builds an XI near a target overall. When {@code prime} is set, each drafted player is swapped for their
     * career-best snapshot (Prime Mode) — applied to the user XI only; opponents keep their sampled season.
     */
    private Xi buildXi(int targetOverall, Formation formation, boolean prime) {
        Xi xi = new Xi("Your XI");
        Set<Integer> used = new HashSet<>();
        for (String slot : formation.slots()) {
            Player best = null; int bestDist = Integer.MAX_VALUE;
            for (Player p : pool) {
                if (used.contains(p.id()) || !p.canPlay(slot)) continue;
                int d = Math.abs(p.overall() - targetOverall);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) { xi.add(slot, prime ? primeIndex.prime(best) : best); used.add(best.id()); }
        }
        return xi;
    }
}
