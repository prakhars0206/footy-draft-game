package com.draft.footy;

import java.util.ArrayList;
import java.util.List;

/** A concrete eleven: each player assigned to a formation slot. Carries the team-strength numbers. */
public final class Xi {
    public final String name;          // club-season label or "Your XI"
    public final List<Slot> slots = new ArrayList<>();

    public record Slot(String position, Line line, Player player) {}

    public Xi(String name) { this.name = name; }

    void add(String position, Player p) { slots.add(new Slot(position, Line.of(position), p)); }

    private double lineMean(Line line) {
        return slots.stream().filter(s -> s.line() == line)
            .mapToInt(s -> s.player().overall()).average().orElse(0);
    }

    public int attack()   { return (int) Math.round(lineMean(Line.ATT)); }
    public int midfield() { return (int) Math.round(lineMean(Line.MID)); }
    public int defence()  { return (int) Math.round(lineMean(Line.DEF)); }
    public int gk()       { return (int) Math.round(lineMean(Line.GK)); }

    /** Headline overall = mean of all 11. */
    public int overall() {
        return (int) Math.round(slots.stream().mapToInt(s -> s.player().overall()).average().orElse(0));
    }

    /** Attacking power feeding the match model — forwards dominate, midfield contributes. */
    public double attackRating() { return 0.65 * lineMean(Line.ATT) + 0.35 * lineMean(Line.MID); }

    /** Defensive solidity — back line, keeper, and a midfield screen. */
    public double defenceRating() {
        return 0.55 * lineMean(Line.DEF) + 0.25 * lineMean(Line.GK) + 0.20 * lineMean(Line.MID);
    }

    public List<Player> players() {
        List<Player> ps = new ArrayList<>();
        for (Slot s : slots) ps.add(s.player());
        return ps;
    }

    /** Players in defensive slots + the keeper — credited with clean sheets. */
    public List<Player> backline() {
        List<Player> ps = new ArrayList<>();
        for (Slot s : slots) if (s.line() == Line.DEF || s.line() == Line.GK) ps.add(s.player());
        return ps;
    }
}
