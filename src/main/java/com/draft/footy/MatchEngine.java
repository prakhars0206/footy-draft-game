package com.draft.footy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Simulates a single match: scoreline via Poisson, then attributes goals/assists to individual players. */
public final class MatchEngine {

    // --- Tunable calibration constants (see Demo calibration sweep) ---
    static final double BASE_GOALS = 1.32;  // league-average goals per team in a balanced game
    static final double SCALE      = 15.5;  // how sharply strength gaps translate to goals (larger = gentler)
    static final double HOME_ADV   = 4.5;   // home edge, in overall-rating points
    static final double MAX_LAMBDA = 4.7;   // clamp to avoid absurd blowouts

    public record GoalEvent(Player scorer, Player assist, int minute, boolean home) {}

    public record Result(Xi home, Xi away, int homeGoals, int awayGoals, List<GoalEvent> events) {
        public boolean homeCleanSheet() { return awayGoals == 0; }
        public boolean awayCleanSheet() { return homeGoals == 0; }
    }

    public Result play(Xi home, Xi away, Random rng) {
        double lambdaHome = lambda(home.attackRating(), away.defenceRating(), +HOME_ADV);
        double lambdaAway = lambda(away.attackRating(), home.defenceRating(), -HOME_ADV);
        int hg = poisson(lambdaHome, rng);
        int ag = poisson(lambdaAway, rng);

        List<GoalEvent> events = new ArrayList<>();
        for (int i = 0; i < hg; i++) events.add(makeGoal(home, true, rng));
        for (int i = 0; i < ag; i++) events.add(makeGoal(away, false, rng));
        return new Result(home, away, hg, ag, events);
    }

    private double lambda(double attack, double defence, double homeAdj) {
        double diff = attack - defence + homeAdj;
        return Math.min(BASE_GOALS * Math.exp(diff / SCALE), MAX_LAMBDA);
    }

    private GoalEvent makeGoal(Xi team, boolean home, Random rng) {
        Player scorer = pick(team, true, rng, null);
        Player assist = rng.nextDouble() < 0.78 ? pick(team, false, rng, scorer) : null;
        int minute = 1 + rng.nextInt(90);
        return new GoalEvent(scorer, assist, minute, home);
    }

    /** Weighted pick of a scorer (attack weights) or assister (assist weights), excluding `exclude`. */
    private Player pick(Xi team, boolean scoring, Random rng, Player exclude) {
        double total = 0;
        for (Xi.Slot s : team.slots) {
            if (s.player().equals(exclude)) continue;
            total += scoring ? s.line().attackWeight() : s.line().assistWeight();
        }

        if (total <= 0) return team.slots.get(0).player(); // Fallback to first attacker

        double roll = rng.nextDouble() * total;

        for (Xi.Slot s : team.slots) {
            if (s.player().equals(exclude)) continue;

            double weight = scoring ? s.line().attackWeight() : s.line().assistWeight();

            // Skip players who mathematically cannot score/assist
            if (weight <= 0) continue;

            roll -= weight;
            if (roll <= 0) return s.player();
        }

        // Failsafe: return the first attacker instead of the keeper
        return team.slots.get(0).player();
    }

    /** Knuth's Poisson sampler on a seeded Random. */
    static int poisson(double lambda, Random rng) {
        double l = Math.exp(-lambda);
        int k = 0; double p = 1.0;
        do { k++; p *= rng.nextDouble(); } while (p > l);
        return k - 1;
    }
}
