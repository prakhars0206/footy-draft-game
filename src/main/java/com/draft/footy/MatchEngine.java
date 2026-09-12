package com.draft.footy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Simulates a single match: scoreline via Poisson, then attributes goals/assists to individual players. */
public final class MatchEngine {

    // Every constant below is MEASURED, not hand-tuned — see Calibration (fitted from ~36,000 real
    // top-5-league matches) and GameBalance (the deliberately chosen playability knobs).
    static final double RHO  = Calibration.RHO;
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
        double lambdaHome = lambda(home.attackRating() + homeForm, away.defenceRating() + awayForm, true);
        double lambdaAway = lambda(away.attackRating() + awayForm, home.defenceRating() + homeForm, false);
        int[] score = sampleScore(lambdaHome, lambdaAway, rng);
        int hg = score[0], ag = score[1];

        List<GoalEvent> events = new ArrayList<>();
        for (int i = 0; i < hg; i++) events.add(makeGoal(home, true, rng));
        for (int i = 0; i < ag; i++) events.add(makeGoal(away, false, rng));
        return new Result(home, away, hg, ag, events);
    }

    /**
     * Expected goals for one side. This is the exact functional form the calibration was fitted to
     * ({@link Calibration#MODEL_FORM}) — change it and the constants no longer describe it.
     *
     * <p>Two departures from the old hand-tuned version, both from the fit:
     * <ul>
     *   <li>Attack and defence get <b>separate scales</b>. Squad rating predicts attacking output much
     *       more strongly than defensive solidity, so one shared SCALE mis-states both.</li>
     *   <li>Home advantage applies to the <b>home rate only</b>, rather than as a symmetric bonus and
     *       penalty. That's the Dixon-Coles formulation, and it's how the constant was estimated.</li>
     * </ul>
     */
    static double lambda(double attack, double defence, boolean home) {
        double logRate = (attack - Calibration.ATTACK_REF) / GameBalance.scaleAttack()
                       - (defence - Calibration.DEFENCE_REF) / GameBalance.scaleDefence()
                       + (home ? Calibration.HOME_ADV_LOG : 0.0);
        return Math.min(Calibration.BASE_GOALS * Math.exp(logRate), GameBalance.MAX_LAMBDA);
    }

    /**
     * Scoreline only — no goal attribution or events. Same Dixon-Coles draw as {@link #play}, but cheap enough
     * to run a whole season thousands of times (the Monte-Carlo "real bookies" projection).
     */
    public int[] fastScore(Xi home, Xi away, double homeForm, double awayForm, Random rng) {
        double lh = lambda(home.attackRating() + homeForm, away.defenceRating() + awayForm, true);
        double la = lambda(away.attackRating() + awayForm, home.defenceRating() + homeForm, false);
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