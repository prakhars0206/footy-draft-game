package com.draft.footy.api;

import com.draft.footy.Formation;
import com.draft.footy.Xi;

import java.util.ArrayList;
import java.util.List;

/**
 * Orders an {@link Xi}'s slots into its formation's slot order so the frontend can drop them straight onto the
 * pitch coordinates (index → coord). An {@code Xi} built by {@code optimalXi}/{@code buildXi} stores its slots
 * "most-constrained-first", not formation order, so this re-pairs them by position.
 */
public final class TeamLayout {

    private TeamLayout() { }

    public record Ordered(String formation, List<Xi.Slot> slots) { }

    public static Ordered inFormationOrder(Xi xi) {
        String label = parseFormation(xi.name);
        Formation f = byLabel(label);
        List<Xi.Slot> remaining = new ArrayList<>(xi.slots);
        List<Xi.Slot> ordered = new ArrayList<>();
        for (String pos : f.slots()) {
            Xi.Slot match = remaining.stream().filter(s -> s.position().equals(pos)).findFirst()
                .orElseGet(() -> remaining.isEmpty() ? null : remaining.get(0)); // fallback keeps it total
            if (match != null) { ordered.add(match); remaining.remove(match); }
        }
        return new Ordered(label, ordered);
    }

    /** "Real Madrid CF 2018/19 (4-3-3)" -> "4-3-3". */
    static String parseFormation(String name) {
        int open = name.lastIndexOf('('), close = name.lastIndexOf(')');
        return (open >= 0 && close > open) ? name.substring(open + 1, close) : "4-3-3";
    }

    static Formation byLabel(String label) {
        for (Formation f : Formation.values()) if (f.label().equals(label)) return f;
        return Formation.F_4_3_3;
    }
}
