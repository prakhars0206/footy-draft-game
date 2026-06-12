package com.draft.footy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All players for one club in one season, with the ability to field a best XI in a given formation. */
public final class ClubSeason {
    public final String club;
    public final String season;
    public final String league;
    public final List<Player> roster;

    public ClubSeason(String club, String season, String league, List<Player> roster) {
        this.club = club; this.season = season; this.league = league;
        this.roster = roster;
    }

    public String label() { return club + " " + season; }

    /**
     * Field the strongest XI for a formation, respecting natural positions:
     * for each slot, pick the highest-rated unused player who can play it; if none, fall back to best remaining.
     * Slots are filled most-constrained-first (fewest eligible candidates) to avoid starving rare positions.
     */
    public Xi bestXi(Formation formation) {
        Xi xi = new Xi(label());
        Set<Integer> used = new HashSet<>();
        List<String> ordered = new ArrayList<>(formation.slots());
        ordered.sort(Comparator.comparingInt(this::candidateCount)); // most-constrained first

        for (String slot : ordered) {
            Player pick = roster.stream()
                .filter(p -> !used.contains(p.id()) && p.canPlay(slot))
                .max(Comparator.comparingInt(Player::overall))
                .orElseGet(() -> roster.stream()
                    .filter(p -> !used.contains(p.id()))
                    .max(Comparator.comparingInt(Player::overall))
                    .orElse(null));
            if (pick != null) { xi.add(slot, pick); used.add(pick.id()); }
        }
        return xi;
    }

    private int candidateCount(String slot) {
        return (int) roster.stream().filter(p -> p.canPlay(slot)).count();
    }

    /** Strength = overall of the best XI in a reference formation. The single "club-season -> strength" function. */
    public int strength(Formation formation) { return bestXi(formation).overall(); }
}
