package com.draft.footy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All players for one club in one season, with the ability to field a best XI. */
public final class ClubSeason {
    public final String club;
    public final String season;
    public final String league;
    public final List<Player> roster;

    // Cache the result so we don't recalculate 7 formations 100 times during Opponent Pyramid sorting
    private Xi cachedOptimalXi;

    public ClubSeason(String club, String season, String league, List<Player> roster) {
        this.club = club; this.season = season; this.league = league;
        this.roster = roster;
    }

    public String label() { return club + " " + season; }

    /** Finds the highest-rated XI across ALL available formations and caches it. */
    public Xi optimalXi() {
        if (cachedOptimalXi == null) {
            Xi best = null;
            for (Formation f : Formation.values()) {
                Xi candidate = buildXi(f);
                if (best == null || candidate.overall() > best.overall()) {
                    best = candidate;
                }
            }
            cachedOptimalXi = best;
        }
        return cachedOptimalXi;
    }

    /** The single strength function used for Opponent Pyramid tiering. */
    public int optimalStrength() {
        return optimalXi().overall();
    }

    /** Fields the strongest XI for a SPECIFIC formation, respecting natural positions and safe fallbacks. */
    public Xi buildXi(Formation formation) {
        // Appending the formation to the name so it shows up beautifully in the final table
        Xi xi = new Xi(label() + " (" + formation.label() + ")");
        Set<Integer> used = new HashSet<>();
        List<String> ordered = new ArrayList<>(formation.slots());
        ordered.sort(Comparator.comparingInt(this::candidateCount)); // most-constrained first

        for (String slot : ordered) {
            Player pick = roster.stream()
                    .filter(p -> !used.contains(p.id()) && p.canPlay(slot))
                    .max(Comparator.comparingInt(Player::overall))
                    .orElseGet(() -> roster.stream() // Fallback 1: Highest rated player OF THE SAME TYPE (Outfield vs GK)
                            .filter(p -> !used.contains(p.id()))
                            .filter(p -> p.primaryPosition().equals("GK") == slot.equals("GK"))
                            .max(Comparator.comparingInt(Player::overall))
                            .orElseGet(() -> roster.stream() // Fallback 2: Sudden death literal remaining
                                    .filter(p -> !used.contains(p.id()))
                                    .max(Comparator.comparingInt(Player::overall))
                                    .orElse(null)));

            if (pick != null) { xi.add(slot, pick); used.add(pick.id()); }
        }
        return xi;
    }

    private int candidateCount(String slot) {
        return (int) roster.stream().filter(p -> p.canPlay(slot)).count();
    }
}