package com.draft.footy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Builds the 19-team opponent pool as a realistic strength pyramid sampled from historical club-seasons. */
public final class OpponentPyramid {

    /** A strength band: label, inclusive bounds, and the randomized count range. */
    record Tier(String label, int min, int max, int baseCount, int lo, int hi) {}

    static final List<Tier> TIERS = List.of(
        new Tier("Juggernaut",          87, 99, 1, 0, 2),
        new Tier("Title contender",     83, 86, 3, 2, 4),
        new Tier("European chaser",     78, 82, 5, 4, 6),
        new Tier("Mid-table",           72, 77, 6, 3, 9),  // balancer (flex)
        new Tier("Relegation scrapper", 58, 71, 4, 3, 5)
    );

    static final int LEAGUE_SIZE = 20;     // you + 19 opponents
    static final int OPPONENTS   = LEAGUE_SIZE - 1;

    private final List<ClubSeason> pool;
    public OpponentPyramid(List<ClubSeason> pool) {
        this.pool = pool;
    }

    public List<Xi> generate(Random rng) {
        // Decide counts per tier with bounded randomization; mid-table absorbs the remainder to hit exactly 19.
        int[] counts = new int[TIERS.size()];
        int assigned = 0;
        for (int i = 0; i < TIERS.size(); i++) {
            if (i == 3) continue; // skip the flex tier for now
            Tier t = TIERS.get(i);
            int c = t.lo() + rng.nextInt(t.hi() - t.lo() + 1);
            counts[i] = c; assigned += c;
        }
        counts[3] = Math.max(0, OPPONENTS - assigned);  // mid-table balancer

        List<Xi> opponents = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < TIERS.size(); i++) {
            Tier t = TIERS.get(i);
            List<ClubSeason> band = pool.stream()
                    .filter(cs -> { int s = cs.optimalStrength(); return s >= t.min() && s <= t.max(); })
                    .sorted(Comparator.comparingInt((ClubSeason cs) -> cs.optimalStrength()).reversed())
                    .toList();
            int placed = 0, guard = 0;
            while (placed < counts[i] && guard < 200 && !band.isEmpty()) {
                ClubSeason cs = band.get(rng.nextInt(band.size()));
                guard++;
                // Dedupe by NORMALISED club (not club-season) so one league never fields two eras of the same
                // club — even when the spelling drifts across editions ("Paris Saint-Germain" vs "…Saint Germain").
                if (picked.add(normClub(cs.club))) { opponents.add(cs.optimalXi()); placed++; }
            }
        }
        // Safety: top up to 19 from anywhere if some bands were thin.
        int guard = 0;
        while (opponents.size() < OPPONENTS && !pool.isEmpty() && guard++ < 500) {
            ClubSeason cs = pool.get(rng.nextInt(pool.size()));
            if (picked.add(normClub(cs.club))) opponents.add(cs.optimalXi());
        }
        return opponents.subList(0, Math.min(OPPONENTS, opponents.size()));
    }

    /** Lowercase, strip non-alphanumerics so "Paris Saint-Germain" and "Paris Saint Germain" collapse to one club. */
    private static String normClub(String club) {
        return club.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
