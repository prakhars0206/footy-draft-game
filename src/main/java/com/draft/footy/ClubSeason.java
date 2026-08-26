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

    /** A stable club identity for dedup — folds the spelling drift the same real club shows across editions. */
    public String clubKey() { return normClub(club); }

    /**
     * Normalise a club name for dedup: strip accents (é→e), drop filler/club-type tokens that vary across
     * editions ("de", FC/CF/AC/SS…), then strip the rest. So "Atlético Madrid" == "Atlético de Madrid", and
     * "Paris Saint-Germain" == "Paris Saint Germain", collapse to one club.
     */
    public static String normClub(String name) {
        if (name == null) return "";
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")                                              // drop accent marks
            .toLowerCase()
            .replaceAll("\\b(fc|cf|sc|cd|ac|afc|ss|ssc|as|rc|rcd|ud|sd|cp|club|de|del|da|do|the|deportivo|calcio)\\b", " ")
            .replaceAll("[^a-z0-9]", "");
    }

    /** Finds the XI with the MOST natural fits across all formations. Uses overall as a tiebreaker. */
    public Xi optimalXi() {
        if (cachedOptimalXi == null) {
            Xi best = null;
            int bestNaturalCount = -1;

            for (Formation f : Formation.values()) {
                Xi candidate = buildXi(f);

                // Count how many drafted players are playing a natural position
                int naturalCount = 0;
                for (Xi.Slot s : candidate.slots) {
                    if (s.player().canPlay(s.position())) {
                        naturalCount++;
                    }
                }

                // If it has MORE natural fits, or the SAME natural fits but a higher overall -> it's the new best
                if (best == null ||
                        naturalCount > bestNaturalCount ||
                        (naturalCount == bestNaturalCount && candidate.overall() > best.overall())) {

                    best = candidate;
                    bestNaturalCount = naturalCount;
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
        List<String> slots = formation.slots();
        Player[] slotOf = new Player[slots.size()];

        // Maximum bipartite matching (Kuhn): add players strongest-first so the best are prioritised — an
        // augmenting path RE-ROUTES an already-placed player to another open slot instead of benching the
        // higher-rated newcomer (the old greedy could drop a 91 like Mbappé when forwards shared positions).
        List<Player> byRating = roster.stream()
            .sorted(Comparator.comparingInt(Player::overall).reversed()).toList();
        Set<Integer> used = new HashSet<>();
        for (Player p : byRating) {
            if (augment(p, slots, slotOf, new boolean[slots.size()])) used.add(p.id());
        }

        // Any slot with no natural player gets a safe fallback (keepers stay in goal, outfielders out of it).
        for (int i = 0; i < slots.size(); i++) {
            if (slotOf[i] != null) continue;
            String slot = slots.get(i);
            Player pick = roster.stream().filter(p -> !used.contains(p.id()))
                .filter(p -> p.primaryPosition().equals("GK") == slot.equals("GK"))
                .max(Comparator.comparingInt(Player::overall))
                .orElseGet(() -> roster.stream().filter(p -> !used.contains(p.id()))
                    .max(Comparator.comparingInt(Player::overall)).orElse(null));
            if (pick != null) { slotOf[i] = pick; used.add(pick.id()); }
        }

        Xi xi = new Xi(label(), formation); // formation is a real field on the Xi (see TeamLayout)
        for (int i = 0; i < slots.size(); i++) if (slotOf[i] != null) xi.add(slots.get(i), slotOf[i]);
        return xi;
    }

    /** Kuhn augmenting step: seat {@code p} in a natural slot, re-routing the current occupant if necessary. */
    private boolean augment(Player p, List<String> slots, Player[] slotOf, boolean[] tried) {
        for (int i = 0; i < slots.size(); i++) {
            if (tried[i] || !p.canPlay(slots.get(i))) continue;
            tried[i] = true;
            if (slotOf[i] == null || augment(slotOf[i], slots, slotOf, tried)) {
                slotOf[i] = p;
                return true;
            }
        }
        return false;
    }
}