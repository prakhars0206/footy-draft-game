package com.draft.footy.api;

import com.draft.footy.Projection;

/**
 * League-aware projection (DESIGN_SPEC §6/§7). The pure {@link Projection} curve is calibrated against a
 * reference league; here we shift a team's effective overall by how soft/tough its actual opponents are, so
 * a given rating projects differently in a weak vs a brutal league. Used consistently by the pre-season
 * preview AND the post-sim debrief so the two never disagree.
 */
public final class LeagueProjection {

    private LeagueProjection() { }

    /** The opponent pyramid's tier-weighted target mean — projections are relative to this reference league. */
    public static final int REF_MEAN = 79;

    /** A team's effective overall given the mean strength of the rest of its league. */
    public static int effective(int overall, int meanOfOthers) {
        return overall + (REF_MEAN - meanOfOthers);
    }

    public static int expectedPoints(int overall, int meanOfOthers) {
        return Projection.expectedPoints(effective(overall, meanOfOthers));
    }

    public static Projection.Odds odds(int overall, int meanOfOthers) {
        return Projection.odds(effective(overall, meanOfOthers));
    }
}
