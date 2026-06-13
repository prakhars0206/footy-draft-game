package com.draft.footy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data repository over club-seasons. The derived queries map to the game's lookups:
 * Classic mode reads one league's clubs ({@link #findByLeague}); a reference season narrows further
 * ({@link #findByLeagueAndSeason}). World Draft (all top-5 leagues + eras) loads the whole pool via
 * {@link #findAllWithPlayers()}, which fetch-joins rosters so the engine graph is fully initialised
 * without an open transaction during mapping.
 */
public interface ClubSeasonRepository extends JpaRepository<ClubSeasonEntity, Long> {

    List<ClubSeasonEntity> findByLeague(String league);

    List<ClubSeasonEntity> findByLeagueAndSeason(String league, String season);

    @Query("select distinct cs from ClubSeasonEntity cs left join fetch cs.players")
    List<ClubSeasonEntity> findAllWithPlayers();
}
