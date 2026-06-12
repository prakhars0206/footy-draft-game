package com.draft.footy;

/** Layer 1 — the "bookies". A pure statistical projection from team overall, independent of the match sim. */
public final class Projection {

    public record Odds(int expectedPoints, double winLeague, double top4, double top6, double top10, double relegation) {}

    /** Expected points from overall, anchored on ~ {75->50, 83->74, 90->95}. Clamped to a sane range. */
    public static int expectedPoints(int overall) {
        double pts = 3.0 * overall - 175.0;
        return (int) Math.round(Math.max(20, Math.min(104, pts)));
    }

    public static Odds odds(int overall) {
        int xp = expectedPoints(overall);
        // crude logistic-ish bands around expected points; good enough for a pre-season "what should happen" panel
        return new Odds(
            xp,
            band(xp, 92, 6),   // win league
            band(xp, 74, 8),   // top 4
            band(xp, 66, 9),   // top 6
            band(xp, 52, 10),  // top 10
            1 - band(xp, 38, 8) // relegation = below the survival line
        );
    }

    private static double band(int xp, int centre, double spread) {
        double z = (xp - centre) / spread;
        double p = 1.0 / (1.0 + Math.exp(-z));
        return Math.round(p * 1000) / 1000.0;
    }
}
