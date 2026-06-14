package com.draft.footy;

import java.util.Random;

/**
 * Scout mode (DESIGN_SPEC §8): turns a true overall into an asymmetric range — the real value sits somewhere
 * inside the band but not centred, so it can't be reverse-averaged, and the band width itself varies per player.
 *
 * Derived deterministically from {@code (sofifaId, runSeed)} so a player's "intel" is stable across requests
 * without server storage (§16) — and the controller sends ONLY the band, never the true overall.
 */
public final class ScoutRatings {

    private ScoutRatings() { }

    public record Band(int low, int high) {
        @Override public String toString() { return low + "–" + high; } // e.g. 72–88
    }

    public static Band band(int sofifaId, long runSeed, int trueOverall) {
        // Mix the seed and id into one stable stream (golden-ratio multiplier to scatter nearby ids).
        Random r = new Random(runSeed * 2654435761L + sofifaId);
        int width  = 4 + r.nextInt(7);          // 4..10 — varies per player, so elites aren't always obvious
        int below  = r.nextInt(width + 1);       // 0..width below the true value — the asymmetry
        int low    = trueOverall - below;
        int high   = low + width;

        // Clamp to plausible bounds, then guarantee the true value stays inside the displayed band.
        low  = Math.max(1, low);
        high = Math.min(99, high);
        low  = Math.min(low, trueOverall);
        high = Math.max(high, trueOverall);
        return new Band(low, high);
    }
}
