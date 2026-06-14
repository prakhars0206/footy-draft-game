package com.draft.footy.api;

import java.util.List;
import java.util.Random;

/**
 * Draft tier bands + how often a spin LANDS on each tier — the difficulty / squad-ceiling knob. Landings are
 * weighted (not uniform over clubs, which would be Steady-heavy) so a well-played draft reaches ~84–86, with
 * the Iconic tier a rare jackpot. Tune the weights to move the ceiling. Bands match {@code DraftRunController.tierLabel}.
 */
final class DraftTiers {
    private DraftTiers() { }

    record Tier(String label, int min, int max, int landingWeight) { }

    static final List<Tier> TIERS = List.of(
        new Tier("ICONIC",   87, 99,  5),
        new Tier("ELITE",    83, 86, 22),
        new Tier("PEDIGREE", 78, 82, 40),
        new Tier("STEADY",   72, 77, 26),
        new Tier("MINNOW",   58, 71,  7)
    );

    static String label(int strength) {
        for (Tier t : TIERS) if (strength >= t.min() && strength <= t.max()) return t.label();
        return TIERS.get(TIERS.size() - 1).label();
    }

    /** Weighted-random pick from the given tiers (by landingWeight) — used with a shrinking list for fallback. */
    static Tier weightedPick(List<Tier> from, Random rng) {
        int total = from.stream().mapToInt(Tier::landingWeight).sum();
        int roll = rng.nextInt(Math.max(1, total));
        for (Tier t : from) { roll -= t.landingWeight(); if (roll < 0) return t; }
        return from.get(from.size() - 1);
    }
}
