package com.draft.footy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The "real bookies": instead of a fitted points curve, simulate the user's exact season (same XI, same opponent
 * pyramid) thousands of times — each with its own seeded form/RNG draw — and read the odds straight off the
 * distribution of outcomes. A points-only fast path ({@link MatchEngine#fastScore}) keeps N≈1000 sub-second.
 *
 * One run yields a full distribution + odds for EVERY team (the league screen can show any team's projection),
 * plus the user-centric {@link Outcome} (percentile-able against the actual season). Pure + seeded → reproducible.
 */
public final class MonteCarlo {

    private MonteCarlo() { }

    /** A single team's projection cloud: expected points + rank, quantiles, odds, and a histogram to render. */
    public record TeamCloud(
        int projectedPoints, int projectedPos,
        double mean, int min, int p5, int p25, int median, int p75, int p95, int max,
        double title, double top4, double top6, double relegation, double unbeaten,
        int histMin, int histBinWidth, int[] histogram
    ) { }

    public record Outcome(
        int sims, double meanPoints,
        int min, int p5, int p25, int median, int p75, int p95, int max,
        double titleOdds, double top4Odds, double top6Odds, double relegationOdds, double unbeatenOdds, double perfectOdds,
        int histMin, int histBinWidth, int[] histogram,
        int[] sortedPoints,
        Map<Xi, TeamCloud> teamClouds   // every team's full projection, from the same sims (free)
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

        int[][] ptsBySim = new int[n][sims];          // every team's points each sim → per-team distribution
        long[] pointsSum = new long[n];
        int[] titleC = new int[n], top4C = new int[n], top6C = new int[n], relegC = new int[n], unbeatenC = new int[n];
        int userPerfect = 0;

        for (int s = 0; s < sims; s++) {
            Random rng = new Random(seed * 1_000_003L + s * 0x9E3779B97F4A7C15L);
            double[] form = new double[n];
            for (int t = 0; t < n; t++) form[t] = rng.nextGaussian() * SeasonSimulator.FORM_SIGMA;

            int[] pts = new int[n], gd = new int[n], gf = new int[n], loss = new int[n];
            int userWins = 0;
            for (List<int[]> round : schedule) {
                for (int[] fx : round) {
                    int h = fx[0], a = fx[1];
                    int[] sc = engine.fastScore(teams[h], teams[a], form[h], form[a], rng);
                    int hg = sc[0], ag = sc[1];
                    gd[h] += hg - ag; gd[a] += ag - hg;
                    gf[h] += hg; gf[a] += ag;
                    if (hg > ag) { pts[h] += 3; loss[a]++; if (h == 0) userWins++; }
                    else if (hg < ag) { pts[a] += 3; loss[h]++; }
                    else { pts[h]++; pts[a]++; }
                }
            }

            // Rank all teams this sim — points, then GD, then GF (matches SeasonSimulator.tableOrder exactly).
            Integer[] ord = new Integer[n];
            for (int t = 0; t < n; t++) ord[t] = t;
            Arrays.sort(ord, (x, y) -> pts[y] != pts[x] ? Integer.compare(pts[y], pts[x])
                : gd[y] != gd[x] ? Integer.compare(gd[y], gd[x]) : Integer.compare(gf[y], gf[x]));

            for (int k = 0; k < n; k++) {
                int t = ord[k];
                int pos = k + 1;
                ptsBySim[t][s] = pts[t];
                pointsSum[t] += pts[t];
                if (pos == 1) titleC[t]++;
                if (pos <= 4) top4C[t]++;
                if (pos <= 6) top6C[t]++;
                if (pos >= 18) relegC[t]++;
                if (loss[t] == 0) unbeatenC[t]++;
            }
            if (userWins == 38) userPerfect++;
        }

        // Projected finish = rank teams by mean points.
        double[] mean = new double[n];
        for (int t = 0; t < n; t++) mean[t] = (double) pointsSum[t] / sims;
        Integer[] byMean = new Integer[n];
        for (int t = 0; t < n; t++) byMean[t] = t;
        Arrays.sort(byMean, (x, y) -> Double.compare(mean[y], mean[x]));
        int[] projPos = new int[n];
        for (int rank = 0; rank < n; rank++) projPos[byMean[rank]] = rank + 1;

        Map<Xi, TeamCloud> clouds = new HashMap<>();
        for (int t = 0; t < n; t++) {
            int[] sorted = ptsBySim[t].clone();
            Arrays.sort(sorted);
            Dist d = distOf(sorted);
            clouds.put(teams[t], new TeamCloud((int) Math.round(mean[t]), projPos[t],
                d.mean, d.min, d.p5, d.p25, d.median, d.p75, d.p95, d.max,
                (double) titleC[t] / sims, (double) top4C[t] / sims, (double) top6C[t] / sims,
                (double) relegC[t] / sims, (double) unbeatenC[t] / sims,
                d.histMin, d.histBinWidth, d.histogram));
        }

        int[] userSorted = ptsBySim[0].clone();
        Arrays.sort(userSorted);
        Dist du = distOf(userSorted);
        return new Outcome(sims, du.mean, du.min, du.p5, du.p25, du.median, du.p75, du.p95, du.max,
            (double) titleC[0] / sims, (double) top4C[0] / sims, (double) top6C[0] / sims,
            (double) relegC[0] / sims, (double) unbeatenC[0] / sims, (double) userPerfect / sims,
            du.histMin, du.histBinWidth, du.histogram, userSorted, clouds);
    }

    private record Dist(double mean, int min, int p5, int p25, int median, int p75, int p95, int max,
                        int histMin, int histBinWidth, int[] histogram) { }

    /** Summary of a sorted-ascending points array: quantiles + a ~22-bin histogram. */
    private static Dist distOf(int[] sortedAsc) {
        int min = sortedAsc[0], max = sortedAsc[sortedAsc.length - 1];
        int binW = Math.max(1, (int) Math.ceil((max - min + 1) / 22.0));
        int[] hist = new int[(max - min) / binW + 1];
        for (int p : sortedAsc) hist[(p - min) / binW]++;
        return new Dist(mean(sortedAsc), min, q(sortedAsc, 0.05), q(sortedAsc, 0.25), q(sortedAsc, 0.50),
            q(sortedAsc, 0.75), q(sortedAsc, 0.95), max, min, binW, hist);
    }

    private static double mean(int[] a) {
        long sum = 0; for (int v : a) sum += v; return (double) sum / a.length;
    }

    private static int q(int[] sortedAsc, double frac) {
        int i = (int) Math.round(frac * (sortedAsc.length - 1));
        return sortedAsc[Math.max(0, Math.min(sortedAsc.length - 1, i))];
    }
}
