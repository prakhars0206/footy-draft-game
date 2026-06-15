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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds the embedded H2 database from the FIFA CSV via Spring Data JPA (DESIGN_SPEC §16 — the point is cert
 * practice, not performance; the dataset fits in memory comfortably). Seeds in {@code @PostConstruct} during
 * context refresh, before the web server serves, so {@code SimulationService} (which {@code @DependsOn} this
 * bean) reads a fully-populated repository.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Comma-separated data sources, earlier wins overlapping seasons. A bare path = sofifa multi-edition export;
    // `path:NN` = the newer EA-FC "ratings export" schema for edition NN. male_players_all (clean 15–23) leads;
    // fc_24 (a messier export, only used for its unique edition 24) follows; then the modern FC 25/26 files.
    @Value("${footy.data.csv:data/male_players_all.csv,data/fc_24.csv,data/fc_25.csv:25,data/EAFC26-Men.csv:26}")
    private String csvPaths;

    private final ClubSeasonRepository repo;

    public DataSeeder(ClubSeasonRepository repo) { this.repo = repo; }

    @PostConstruct
    void seed() throws Exception {
        if (repo.count() > 0) return; // idempotent

        List<ClubSeason> clubs = new ArrayList<>();
        Set<String> seenSeasons = new HashSet<>();   // earlier files win a season; overlaps from later files are dropped
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
            if (!Files.exists(path)) { log.warn("data source not found, skipping: {}", path); continue; }
            List<ClubSeason> part = edition >= 0 ? FifaDataLoader.loadModern(path, edition)
                                                 : FifaDataLoader.loadAllSeasons(path);
            // Dedup by season: a multi-edition export (fc_24) overlaps an earlier one (male_players_all 15–23),
            // so we keep only the seasons not yet provided — letting the cleaner/earlier file own them.
            List<ClubSeason> fresh = part.stream().filter(c -> !seenSeasons.contains(c.season)).toList();
            int dup = part.size() - fresh.size();
            log.info("Loaded {} club-seasons from {}{}{}", fresh.size(), path,
                edition >= 0 ? " (edition " + edition + ")" : "",
                dup > 0 ? " (" + dup + " dup-season skipped)" : "");
            clubs.addAll(fresh);
            fresh.forEach(c -> seenSeasons.add(c.season));
        }
        repo.saveAll(clubs.stream().map(EngineMapper::toEntity).toList());

        long seasons = clubs.stream().map(c -> c.season).distinct().count();
        log.info("Seeded H2: {} top-5 club-seasons across {} editions ({} players).",
            clubs.size(), seasons, clubs.stream().mapToInt(c -> c.roster.size()).sum());
    }
}
