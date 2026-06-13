package com.draft.footy;

import java.util.Map;

/**
 * Per-position propensity for scoring vs assisting. Used only to attribute goals/assists to players —
 * it does NOT affect scorelines or points, so these can be tuned freely against the leaderboards.
 * Strikers finish; wide players and CAMs create; deep/defensive players do little of either.
 */
public final class ScoringWeights {

    public record Profile(double goal, double assist) {}

    private static final Profile DEFAULT = new Profile(3.0, 4.0);

    private static final Map<String, Profile> P = Map.ofEntries(
            Map.entry("ST",  new Profile(8.9, 4.7)),
            Map.entry("CF",  new Profile(8.5,  5.0)),
            Map.entry("LW",  new Profile(7.3,  8.0)),
            Map.entry("RW",  new Profile(7.3,  8.0)),
            Map.entry("CAM", new Profile(6.2,  8.8)),
            Map.entry("LM",  new Profile(5.3,  6.5)),
            Map.entry("RM",  new Profile(5.3,  6.5)),
            Map.entry("CM",  new Profile(3.4,  5.8)),
            Map.entry("CDM", new Profile(1.6,  3.0)),
            Map.entry("LWB", new Profile(1.3,  4.5)),
            Map.entry("RWB", new Profile(1.3,  4.5)),
            Map.entry("LB",  new Profile(1.1,  4.0)),
            Map.entry("RB",  new Profile(1.1,  4.0)),
            Map.entry("CB",  new Profile(1.0,  1.5)),
            Map.entry("GK",  new Profile(0.02, 0.3))
    );

    public static Profile of(String position) { return P.getOrDefault(position, DEFAULT); }

    private ScoringWeights() {}
}