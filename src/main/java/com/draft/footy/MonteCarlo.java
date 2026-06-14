package com.draft.footy;

import java.util.List;
import java.util.Random;

/**
 * The "real bookies": instead of a fitted points curve, simulate the user's exact season (same XI, same opponent
 * pyramid) thousands of times — each with its own seeded form/RNG draw — and read the odds straight off the
 * distribution of outcomes. A points-only fast path ({@link MatchEngine#fastScore}) keeps N≈1000 sub-second.
 *
 * Pure engine (no Spring); fully seeded, so the same run reproduces the same distribution.
 */
public final class MonteCarlo {

    private MonteCarlo() { }

    /**
     * Aggregated outcome of N simulated seasons. {@code sortedPoints} (ascending) backs {@link #percentile} so the
     * actual season can be placed in the cloud; the rest is a ready-to-render summary (odds, quantiles, histogram).
     */
    /** A team's "real bookies" projection: expected (mean) points and projected finish (rank by mean points). */
    public record TeamProj(int projectedPoints, int projectedPos) { }

    public record Outcome(
        int sims, double meanPoints,
        int min, int p5, int p25, int median, int p75, int p95, int max,
        double titleOdds, double top4Odds, double top6Odds, double relegationOdds, double unbeatenOdds, double perfectOdds,
        int histMin, int histBinWidth, int[] histogram,
        int[] sortedPoints,
        java.util.Map<Xi, TeamProj> teamProjections   // every team's projection, from the same sims (free)
    ) {
        /** Share of simulated seasons that finished on FEWER points than {@code pts} — "you beat X% of seasons". */
        public int percentile(int pts) {
            int below = 0;
            for (int p : sortedPoints) { if (p < pts) below++; else break; }
            return (int) Math.round(100.0 * below / sims);
        }
    }

    public static Outcome run(Xi user, List<Xi> opponents, int sims, long seed) {
        int n = opponents.size() + 1;                 // user is team 0
        Xi[] teams = new Xi[n];
        teams[0] = user;
        for (int i = 0; i < opponents.size(); i++) teams[i + 1] = opponents.get(i);

        List<List<int[]>> schedule = SeasonSimulator.schedule(n);
        MatchEngine engine = new MatchEngine();

        int[] points = new int[sims];
        long[] teamPointsSum = new long[n];           // accumulate every team's points → per-team mean (projection)
        int title = 0, top4 = 0, top6 = 0, releg = 0, unbeaten = 0, perfect = 0;

        for (int s = 0; s < sims; s++) {
            Random rng = new Random(seed * 1_000_003L + s * 0x9E3779B97F4A7C15L);
            double[] form = new double[n];
            for (int t = 0; t < n; t++) form[t] = rng.nextGaussian() * SeasonSimulator.FORM_SIGMA;

            int[] pts = new int[n], gd = new int[n];
            int userWins = 0, userLosses = 0;
            for (List<int[]> round : schedule) {
                for (int[] fx : round) {
                    int h = fx[0], a = fx[1];
                    int[] sc = engine.fastScore(teams[h], teams[a], form[h], form[a], rng);
                    int hg = sc[0], ag = sc[1];
                    gd[h] += hg - ag; gd[a] += ag - hg;
                    if (hg > ag) { pts[h] += 3; if (h == 0) userWins++; else if (a == 0) userLosses++; }
                    else if (hg < ag) { pts[a] += 3; if (a == 0) userWins++; else if (h == 0) userLosses++; }
                    else { pts[h]++; pts[a]++; }
                }
            }

            for (int t = 0; t < n; t++) teamPointsSum[t] += pts[t];

            int up = pts[0], ugd = gd[0];
            int pos = 1;                              // 1 + teams ranked strictly above the user (points, then GD)
            for (int t = 1; t < n; t++)
                if (pts[t] > up || (pts[t] == up && gd[t] > ugd)) pos++;

            points[s] = up;
            if (pos == 1) title++;
            if (pos <= 4) top4++;
            if (pos <= 6) top6++;
            if (pos >= 18) releg++;
            if (userLosses == 0) unbeaten++;
            if (userWins == 38) perfect++;
        }

        // Per-team projection: expected (mean) points, and projected finish = rank by that mean (1 = best).
        double[] teamMean = new double[n];
        for (int t = 0; t < n; t++) teamMean[t] = (double) teamPointsSum[t] / sims;
        Integer[] order = new Integer[n];
        for (int t = 0; t < n; t++) order[t] = t;
        java.util.Arrays.sort(order, (x, y) -> Double.compare(teamMean[y], teamMean[x]));
        java.util.Map<Xi, TeamProj> projections = new java.util.HashMap<>();
        for (int rank = 0; rank < n; rank++) {
            int t = order[rank];
            projections.put(teams[t], new TeamProj((int) Math.round(teamMean[t]), rank + 1));
        }

        java.util.Arrays.sort(points);
        int min = points[0], max = points[sims - 1];
        int binW = Math.max(1, (int) Math.ceil((max - min + 1) / 22.0));
        int bins = (max - min) / binW + 1;
        int[] hist = new int[bins];
        for (int p : points) hist[(p - min) / binW]++;

        return new Outcome(sims, mean(points),
            min, q(points, 0.05), q(points, 0.25), q(points, 0.50), q(points, 0.75), q(points, 0.95), max,
            (double) title / sims, (double) top4 / sims, (double) top6 / sims,
            (double) releg / sims, (double) unbeaten / sims, (double) perfect / sims,
            min, binW, hist, points, projections);
    }

    private static double mean(int[] a) {
        long sum = 0; for (int v : a) sum += v; return (double) sum / a.length;
    }

    private static int q(int[] sortedAsc, double frac) {
        int i = (int) Math.round(frac * (sortedAsc.length - 1));
        return sortedAsc[Math.max(0, Math.min(sortedAsc.length - 1, i))];
    }
}
