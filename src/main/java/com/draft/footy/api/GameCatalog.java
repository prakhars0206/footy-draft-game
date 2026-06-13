package com.draft.footy.api;

import com.draft.footy.ClubSeason;
import com.draft.footy.LeagueScope;
import com.draft.footy.Player;
import com.draft.footy.PrimeIndex;
import com.draft.footy.persistence.ClubSeasonRepository;
import com.draft.footy.persistence.EngineMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The loaded game data, shared by every flow (demo + draft). Reads the H2-seeded club-seasons back through the
 * repository once at startup ({@code @PostConstruct} after {@code DataSeeder}, so the cache is ready before the
 * web server serves and no transaction is needed during mapping thanks to the fetch-join query).
 */
@Component
@DependsOn("dataSeeder")
public class GameCatalog {

    private final ClubSeasonRepository repo;

    private List<ClubSeason> clubs;
    private List<Player> pool;
    private PrimeIndex primeIndex;

    public GameCatalog(ClubSeasonRepository repo) { this.repo = repo; }

    @PostConstruct
    void load() {
        clubs = repo.findAllWithPlayers().stream().map(EngineMapper::toEngine).toList();
        pool = new ArrayList<>();
        for (ClubSeason cs : clubs) pool.addAll(cs.roster);
        primeIndex = PrimeIndex.from(clubs);
    }

    public List<ClubSeason> clubs() { return clubs; }
    public List<Player> pool() { return pool; }
    public PrimeIndex primeIndex() { return primeIndex; }

    /**
     * Club-seasons that can be spun, filtered by league scope and era (DESIGN_SPEC §5A). For CLASSIC a single
     * {@code league} is kept; for WORLD all top-5 leagues. {@code eraFrom}/{@code eraTo} bound the season's
     * start year (nullable = open-ended).
     */
    public List<ClubSeason> eligibleClubSeasons(LeagueScope scope, String league, Integer eraFrom, Integer eraTo) {
        List<ClubSeason> out = new ArrayList<>();
        for (ClubSeason cs : clubs) {
            if (scope == LeagueScope.CLASSIC && league != null && !cs.league.equals(league)) continue;
            int year = seasonStartYear(cs.season);
            if (eraFrom != null && year < eraFrom) continue;
            if (eraTo != null && year > eraTo) continue;
            out.add(cs);
        }
        return out;
    }

    /** "2016/17" -> 2016. */
    public static int seasonStartYear(String season) {
        return Integer.parseInt(season.substring(0, 4));
    }
}
