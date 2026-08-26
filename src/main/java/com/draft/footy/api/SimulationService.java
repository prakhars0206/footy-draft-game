package com.draft.footy.api;

import com.draft.footy.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Exposes the stateless demo season simulation, over the shared {@link GameCatalog} pool. */
@Service
public class SimulationService {

    private final GameCatalog catalog;

    public SimulationService(GameCatalog catalog) { this.catalog = catalog; }

    /** Simulate one season for an auto-built XI of the given target overall, in a given formation, with a seed. */
    public SeasonSimulator.SeasonResult simulateDemo(int targetOverall, Formation formation, long seed, boolean prime) {
        Xi userXi = buildXi(targetOverall, formation, prime);
        Random rng = new Random(seed);
        List<Xi> opponents = new OpponentPyramid(catalog.clubs()).generate(rng);
        return new SeasonSimulator().simulate(userXi, opponents, rng);
    }

    /**
     * Builds an XI near a target overall. When {@code prime} is set, each drafted player is swapped for their
     * career-best snapshot (Prime Mode) — applied to the user XI only; opponents keep their sampled season.
     */
    private Xi buildXi(int targetOverall, Formation formation, boolean prime) {
        Xi xi = new Xi("Your XI", formation);
        Set<Integer> used = new HashSet<>();
        for (String slot : formation.slots()) {
            Player best = null; int bestDist = Integer.MAX_VALUE;
            for (Player p : catalog.pool()) {
                if (used.contains(p.id()) || !p.canPlay(slot)) continue;
                int d = Math.abs(p.overall() - targetOverall);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) { xi.add(slot, prime ? catalog.primeIndex().prime(best) : best); used.add(best.id()); }
        }
        return xi;
    }
}
