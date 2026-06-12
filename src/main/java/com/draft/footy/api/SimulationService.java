package com.draft.footy.api;

import com.draft.footy.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Loads player data once at startup and exposes season simulation to the controller. */
@Service
public class SimulationService {

    @Value("${footy.data.csv:players_22.csv}")
    private String csvPath;

    @Value("${footy.data.fifaEdition:22}")
    private int fifaEdition;

    private List<ClubSeason> clubs;
    private List<Player> pool;

    @PostConstruct
    void load() throws Exception {
        // Phase 1b: replace this with JPA + H2 seeding (DESIGN_SPEC §16).
        clubs = FifaDataLoader.loadTop5(Path.of(csvPath), fifaEdition);
        pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
    }

    /** Simulate one season for an auto-built XI of the given target overall, in a given formation, with a seed. */
    public SeasonSimulator.SeasonResult simulateDemo(int targetOverall, Formation formation, long seed) {
        Xi userXi = buildXi(targetOverall, formation);
        Random rng = new Random(seed);
        List<Xi> opponents = new OpponentPyramid(clubs).generate(rng);
        return new SeasonSimulator().simulate(userXi, opponents, rng);
    }

    public Xi buildDemoXi(int targetOverall, Formation formation) { return buildXi(targetOverall, formation); }

    public Projection.Odds projection(int overall) { return Projection.odds(overall); }

    private Xi buildXi(int targetOverall, Formation formation) {
        Xi xi = new Xi("Your XI");
        Set<Integer> used = new HashSet<>();
        for (String slot : formation.slots()) {
            Player best = null; int bestDist = Integer.MAX_VALUE;
            for (Player p : pool) {
                if (used.contains(p.id()) || !p.canPlay(slot)) continue;
                int d = Math.abs(p.overall() - targetOverall);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) { xi.add(slot, best); used.add(best.id()); }
        }
        return xi;
    }
}
