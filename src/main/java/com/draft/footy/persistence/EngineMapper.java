package com.draft.footy.persistence;

import com.draft.footy.ClubSeason;
import com.draft.footy.Player;

import java.util.ArrayList;
import java.util.List;

/** Converts between the persisted entities and the pure-engine records, keeping JPA out of the engine. */
public final class EngineMapper {

    private EngineMapper() { }

    /** Engine club-season -> a new detached entity graph (club-season + its players), ready to persist. */
    public static ClubSeasonEntity toEntity(ClubSeason cs) {
        ClubSeasonEntity e = new ClubSeasonEntity(cs.club, cs.season, cs.league);
        for (Player p : cs.roster)
            e.addPlayer(new PlayerEntity(p.id(), p.name(), p.nation(), p.positions(), p.overall(), p.dob()));
        return e;
    }

    /** Persisted entity -> engine club-season (read inside an open transaction so the roster is initialised). */
    public static ClubSeason toEngine(ClubSeasonEntity e) {
        List<Player> roster = new ArrayList<>();
        for (PlayerEntity p : e.getPlayers())
            roster.add(new Player(p.getSofifaId(), p.getName(), p.getNation(),
                new ArrayList<>(p.getPositions()), p.getOverall(), p.getDob()));
        return new ClubSeason(e.getClub(), e.getSeason(), e.getLeague(), roster);
    }
}
