package com.draft.footy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Simulates a single match: scoreline via Poisson, then attributes goals/assists to individual players. */
public final class MatchEngine {

    // --- Tunable calibration constants (see Demo calibration sweep) ---
    static final double BASE_GOALS = 1.15;  // league-average goals per team in a balanced game
    static final double SCALE      = 16.5;  // how sharply strength gaps translate to goals (larger = gentler)
    static final double HOME_ADV   = 3.6;   // home edge, in overall-rating points
    static final double MAX_LAMBDA = 2.5;   // clamp to avoid absurd blowouts

    // Dixon-Coles low-score correction: independent Poisson under-predicts 0-0/1-1 draws (scorelines are
    // correlated). RHO < 0 shifts mass from 1-0/0-1 into 0-0/1-1, lifting the draw rate to a realistic band.
    // Attribution is unchanged — this only shapes the scoreline. NOTE: coupled to points (more draws cost
    // favourites), so re-run the calibration sweep after touching it.
    static final double RHO  = -0.11;
    static final int    GRID = 12;          // scoreline cap per side for the joint pmf (Poisson(4.5) tail beyond is ~0)

    public record GoalEvent(Player scorer, Player assist, int minute, boolean home) {}

    public record Result(Xi home, Xi away, int homeGoals, int awayGoals, List<GoalEvent> events) {
        public boolean homeCleanSheet() { return awayGoals == 0; }
        public boolean awayCleanSheet() { return homeGoals == 0; }
    }

    public Result play(Xi home, Xi away, Random rng) { return play(home, away, 0, 0, rng); }

    /**
     * Play with a per-season "form" adjustment on each side (a team in form attacks better AND defends better
     * all year). Mean-zero across teams, so it leaves the long-run average alone but makes any single season
     * swing — the source of genuine over/under-performance drama.
     */
    public Result play(Xi home, Xi away, double homeForm, double awayForm, Random rng) {
        double lambdaHome = lambda(home.attackRating() + homeForm, away.defenceRating() + awayForm, +HOME_ADV);
        double lambdaAway = lambda(away.attackRating() + awayForm, home.defenceRating() + homeForm, -HOME_ADV);
        int[] score = sampleScore(lambdaHome, lambdaAway, rng);
        int hg = score[0], ag = score[1];

        List<GoalEvent> events = new ArrayList<>();
        for (int i = 0; i < hg; i++) events.add(makeGoal(home, true, rng));
        for (int i = 0; i < ag; i++) events.add(makeGoal(away, false, rng));
        return new Result(home, away, hg, ag, events);
    }

    private double lambda(double attack, double defence, double homeAdj) {
        double diff = attack - defence + homeAdj;
        return Math.min(BASE_GOALS * Math.exp(diff / SCALE), MAX_LAMBDA);
    }

    /**
     * Scoreline only — no goal attribution or events. Same Dixon-Coles draw as {@link #play}, but cheap enough
     * to run a whole season thousands of times (the Monte-Carlo "real bookies" projection).
     */
    public int[] fastScore(Xi home, Xi away, double homeForm, double awayForm, Random rng) {
        double lh = lambda(home.attackRating() + homeForm, away.defenceRating() + awayForm, +HOME_ADV);
        double la = lambda(away.attackRating() + awayForm, home.defenceRating() + homeForm, -HOME_ADV);
        return sampleScore(lh, la, rng);
    }

    private GoalEvent makeGoal(Xi team, boolean home, Random rng) {
        Player scorer = pick(team, true, rng, null);
        Player assist = rng.nextDouble() < 0.78 ? pick(team, false, rng, scorer) : null;
        int minute = 1 + rng.nextInt(90);
        return new GoalEvent(scorer, assist, minute, home);
    }

    /**
     * Rating boost on a player's goal/assist share — attribution only (never changes scorelines or points).
     * Baseline-shifted (≈0 at 55, ≈1 at 99) then squared, so higher-rated players grab a clearly larger share:
     * a 90 is ~2× an 80 (vs ~1.3× with the old overall/100² model). For an even steeper edge, cube it instead.
     */
    static double ratingFactor(int overall) {
        double r = Math.max(0.0, (overall - 55) / 45.0);
        return r * r * r;
    }

    /** Weighted pick of a scorer (attack weights) or assister (assist weights), excluding `exclude`. */
    private Player pick(Xi team, boolean scoring, Random rng, Player exclude) {
        double total = 0;
        for (Xi.Slot s : team.slots) {
            if (s.player().equals(exclude)) continue;

            // Base positional weight, sharpened by the player's rating (higher overalls grab far more).
            double baseWeight = scoring ? ScoringWeights.of(s.position()).goal()
                    : ScoringWeights.of(s.position()).assist();
            total += baseWeight * ratingFactor(s.player().overall());
        }

        if (total <= 0) return team.slots.get(0).player(); // Fallback to first attacker

        double roll = rng.nextDouble() * total;

        for (Xi.Slot s : team.slots) {
            if (s.player().equals(exclude)) continue;

            double baseWeight = scoring ? ScoringWeights.of(s.position()).goal()
                    : ScoringWeights.of(s.position()).assist();
            double weight = baseWeight * ratingFactor(s.player().overall());

            // Skip players who mathematically cannot score/assist
            if (weight <= 0) continue;

            roll -= weight;
            if (roll <= 0) return s.player();
        }

        // Failsafe
        return team.slots.get(0).player();
    }

    /**
     * Samples a correlated scoreline from the Dixon-Coles joint distribution:
     * P(i,j) = Poisson(i;lambda)·Poisson(j;mu)·tau(i,j). Builds the (GRID+1)² grid, applies the low-score
     * tau correction, and draws one cell with a single uniform — keeps the season fully seed-reproducible.
     */
    static int[] sampleScore(double lambda, double mu, Random rng) {
        double[] ph = poissonPmf(lambda, GRID);
        double[] pa = poissonPmf(mu, GRID);

        double total = 0;
        for (int i = 0; i <= GRID; i++)
            for (int j = 0; j <= GRID; j++)
                total += ph[i] * pa[j] * tau(i, j, lambda, mu);

        double roll = rng.nextDouble() * total;
        for (int i = 0; i <= GRID; i++)
            for (int j = 0; j <= GRID; j++) {
                roll -= ph[i] * pa[j] * tau(i, j, lambda, mu);
                if (roll <= 0) return new int[]{i, j};
            }
        return new int[]{GRID, GRID}; // failsafe (rounding)
    }

    /** Dixon-Coles dependence factor — adjusts only the four low-score cells; 1 everywhere else. */
    static double tau(int i, int j, double lambda, double mu) {
        if (i == 0 && j == 0) return 1 - lambda * mu * RHO;
        if (i == 0 && j == 1) return 1 + lambda * RHO;
        if (i == 1 && j == 0) return 1 + mu * RHO;
        if (i == 1 && j == 1) return 1 - RHO;
        return 1;
    }

    /** Poisson pmf over 0..cap (unnormalised tail truncation is renormalised by the caller's total). */
    static double[] poissonPmf(double lambda, int cap) {
        double[] p = new double[cap + 1];
        double term = Math.exp(-lambda); // P(0)
        p[0] = term;
        for (int k = 1; k <= cap; k++) { term *= lambda / k; p[k] = term; }
        return p;
    }
}