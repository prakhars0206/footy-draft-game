package com.draft.footy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Career-best ("Prime Mode") lookup over a multi-edition player pool (DESIGN_SPEC §5A/§16).
 *
 * Groups every player row by the stable {@code player_id} and keeps that player's peak-overall row — a single
 * coherent peak season, so the rating AND its positions come from the same edition (no merging of best-rating
 * with unioned positions). On a single-edition pool there is one row per id, so {@link #prime} is a no-op.
 *
 * Invariant #6: only the USER's drafted XI consults this. Opponents are always rated/attributed as their
 * sampled season, regardless of the user's Career/Prime toggle.
 */
public final class PrimeIndex {

    private final Map<Integer, Player> peakById;

    private PrimeIndex(Map<Integer, Player> peakById) { this.peakById = peakById; }

    public static PrimeIndex from(List<ClubSeason> allSeasons) {
        Map<Integer, Player> peak = new HashMap<>();
        for (ClubSeason cs : allSeasons) {
            for (Player p : cs.roster) {
                Player best = peak.get(p.id());
                if (best == null || p.overall() > best.overall()) peak.put(p.id(), p);
            }
        }
        return new PrimeIndex(peak);
    }

    /** The career-best snapshot of this player, or the player unchanged if unseen / single-edition. */
    public Player prime(Player p) {
        return peakById.getOrDefault(p.id(), p);
    }
}
