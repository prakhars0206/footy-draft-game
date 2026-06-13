package com.draft.footy.persistence;

import com.draft.footy.ClubSeason;
import com.draft.footy.FifaDataLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Seeds the embedded H2 database from the FIFA CSV via Spring Data JPA (DESIGN_SPEC §16 — the point is cert
 * practice, not performance; the dataset fits in memory comfortably). Seeds in {@code @PostConstruct} during
 * context refresh, before the web server serves, so {@code SimulationService} (which {@code @DependsOn} this
 * bean) reads a fully-populated repository.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Value("${footy.data.csv:data/male_players_all.csv}")
    private String csvPath;

    private final ClubSeasonRepository repo;

    public DataSeeder(ClubSeasonRepository repo) { this.repo = repo; }

    @PostConstruct
    void seed() throws Exception {
        if (repo.count() > 0) return; // idempotent

        List<ClubSeason> clubs = FifaDataLoader.loadAllSeasons(Path.of(csvPath));
        repo.saveAll(clubs.stream().map(EngineMapper::toEntity).toList());

        long seasons = clubs.stream().map(c -> c.season).distinct().count();
        log.info("Seeded H2: {} top-5 club-seasons across {} editions ({} players).",
            clubs.size(), seasons, clubs.stream().mapToInt(c -> c.roster.size()).sum());
    }
}
