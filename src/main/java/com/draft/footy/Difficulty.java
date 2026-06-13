package com.draft.footy;

/** Run difficulty — bundles the reroll budget (DESIGN_SPEC §5A). Hard also forces ratings hidden (handled at config time). */
public enum Difficulty {
    EASY(3), NORMAL(1), HARD(0);

    private final int rerolls;
    Difficulty(int rerolls) { this.rerolls = rerolls; }
    public int rerolls() { return rerolls; }
}
