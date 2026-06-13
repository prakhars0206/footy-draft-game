package com.draft.footy;

/** Layer 1 — the "bookies". A pure statistical projection from team overall, independent of the match sim. */
public final class Projection {

    public record Odds(int expectedPoints, double winLeague, double top4, double top6, double top10, double relegation) {}

    /**
     * Expected points from overall — fitted to what teams ACTUALLY achieve in the sim (75->~39, 80->~58,
     * 86->~80, 90->~94), rather than a naive line that over-rated weaker sides. Clamped to a realistic
     * 38-game range (a runaway champion tops out near 100, a doomed side bottoms out in the teens).
     */
    public static int expectedPoints(int overall) {
        double pts = 3.65 * overall - 234.5;
        return (int) Math.round(Math.max(14, Math.min(100, pts)));
    }

    public static Odds odds(int overall) {
        int xp = expectedPoints(overall);
        // Logistic bands keyed to real top-flight thresholds — a pre-season "what should happen" picture.
        return new Odds(
            xp,
            band(xp, 90, 6),    // win league (~88-92 pts usually takes it)
            band(xp, 72, 7),    // top 4 (~70-74)
            band(xp, 64, 8),    // top 6
            band(xp, 50, 9),    // top 10
            1 - band(xp, 36, 5) // relegation = below the ~36-38 survival line
        );
    }

    private static double band(int xp, int centre, double spread) {
        double z = (xp - centre) / spread;
        double p = 1.0 / (1.0 + Math.exp(-z));
        return Math.round(p * 1000) / 1000.0;
    }
}
