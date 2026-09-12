package com.draft.footy;

/** Layer 1 — the "bookies". A pure statistical projection from team overall, independent of the match sim. */
public final class Projection {

    public record Odds(int expectedPoints, double winLeague, double top4, double top6, double top10, double relegation) {}

    /**
     * Expected points from overall — least-squares fit to what teams actually achieve in the sim against the
     * opponent pyramid (75->~43, 80->~61, 86->~80, 90->~88). Refit after de-shrinking the fitted scales
     * (see {@link GameBalance#SCALE_DESHRINK}), which restored the spread a conditional-mean regression
     * necessarily loses and made the curve steeper again.
     *
     * <p><b>Demo path only.</b> {@code MonteCarlo} drives every live projection by simulating the user's exact
     * season, so it tracks the engine automatically and needs no refit. This curve survives as the stateless
     * {@code /api/season/demo} fallback. Re-derive with {@code Demo data/male_players_all.csv} if the
     * constants move again.
     */
    public static int expectedPoints(int overall) {
        double pts = 2.95 * overall - 176.0;
        return (int) Math.round(Math.max(14, Math.min(100, pts)));
    }

    public static Odds odds(int overall) {
        int xp = expectedPoints(overall);
        // Bands keyed to the pyramid league's own thresholds, which sit below real top-flight ones
        // because that league is deliberately harsher than any real division.
        return new Odds(
            xp,
            band(xp, 88, 6),    // win league
            band(xp, 70, 7),    // top 4
            band(xp, 62, 8),    // top 6
            band(xp, 50, 9),    // top 10
            1 - band(xp, 32, 5) // relegation = below the survival line
        );
    }

    private static double band(int xp, int centre, double spread) {
        double z = (xp - centre) / spread;
        double p = 1.0 / (1.0 + Math.exp(-z));
        return Math.round(p * 1000) / 1000.0;
    }
}
