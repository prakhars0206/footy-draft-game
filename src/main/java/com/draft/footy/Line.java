package com.draft.footy;

import java.util.Map;

/** The four positional lines a player/slot belongs to. Drives strength sub-scores and goal-attribution weights. */
public enum Line {
    ATT, MID, DEF, GK;

    private static final Map<String, Line> POS_TO_LINE = Map.ofEntries(
        Map.entry("ST", ATT), Map.entry("CF", ATT), Map.entry("LW", ATT), Map.entry("RW", ATT),
        Map.entry("LF", ATT), Map.entry("RF", ATT),
        Map.entry("CAM", MID), Map.entry("CM", MID), Map.entry("CDM", MID),
        Map.entry("LM", MID), Map.entry("RM", MID),
        Map.entry("CB", DEF), Map.entry("LB", DEF), Map.entry("RB", DEF),
        Map.entry("LWB", DEF), Map.entry("RWB", DEF),
        Map.entry("GK", GK)
    );

    public static Line of(String position) {
        return POS_TO_LINE.getOrDefault(position, MID);
    }

    /** Relative likelihood of scoring a goal, by the line of the slot the player occupies. */
    public double attackWeight() {
        return switch (this) { case ATT -> 10.0; case MID -> 4.0; case DEF -> 1.0; case GK -> 0.0; };
    }

    /** Relative likelihood of providing an assist. */
    public double assistWeight() {
        return switch (this) { case ATT -> 6.0; case MID -> 7.0; case DEF -> 2.0; case GK -> 0.2; };
    }
}
