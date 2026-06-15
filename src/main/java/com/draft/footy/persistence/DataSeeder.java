package com.draft.footy.persistence;

import com.draft.footy.ClubSeason;
import com.draft.footy.FifaDataLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    // Comma-separated data sources. A bare path = sofifa multi-edition export (e.g. FIFA 15–FC 24). A `path:NN`
    // entry = the newer EA-FC "ratings export" schema for edition NN (e.g. fc_25.csv:25). Missing files are skipped.
    @Value("${footy.data.csv:data/fc_24.csv,data/fc_25.csv:25,data/EAFC26-Men.csv:26,data/male_players_all.csv}")
    private String csvPaths;

    private final ClubSeasonRepository repo;

    public DataSeeder(ClubSeasonRepository repo) { this.repo = repo; }

    @PostConstruct
    void seed() throws Exception {
        if (repo.count() > 0) return; // idempotent

        List<ClubSeason> clubs = new ArrayList<>();
        boolean loadedSofifa = false;   // a bare path is a many-edition export; loading two would duplicate editions
        for (String spec : csvPaths.split(",")) {
            spec = spec.trim();
            if (spec.isEmpty()) continue;
            int colon = spec.lastIndexOf(':');
            int edition = -1;
            Path path = Path.of(spec);
            if (colon > 1) {                                  // "path:NN" → modern single-edition file
                try { edition = Integer.parseInt(spec.substring(colon + 1).trim()); path = Path.of(spec.substring(0, colon).trim()); }
                catch (NumberFormatException ignore) { }
            }
            if (edition < 0 && loadedSofifa) continue;        // already have a multi-edition export; skip overlaps
            if (!Files.exists(path)) { log.warn("data source not found, skipping: {}", path); continue; }
            List<ClubSeason> part = edition >= 0 ? FifaDataLoader.loadModern(path, edition)
                                                 : FifaDataLoader.loadAllSeasons(path);
            log.info("Loaded {} club-seasons from {}{}", part.size(), path, edition >= 0 ? " (edition " + edition + ")" : "");
            clubs.addAll(part);
            if (edition < 0) loadedSofifa = true;
        }
        repo.saveAll(clubs.stream().map(EngineMapper::toEntity).toList());

        long seasons = clubs.stream().map(c -> c.season).distinct().count();
        log.info("Seeded H2: {} top-5 club-seasons across {} editions ({} players).",
            clubs.size(), seasons, clubs.stream().mapToInt(c -> c.roster.size()).sum());
    }
}
