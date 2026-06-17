package com.draft.footy.persistence;

import com.draft.footy.ClubSeason;
import com.draft.footy.FifaDataLoader;
import com.draft.footy.Player;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    @Value("${footy.data.csv:data/male_players_all.csv,data/fc_24.csv,data/fc_25_sofifa.csv:25,data/EAFC26-Men.csv:26,data/Scraped_data_new/fifa_07.csv:7,data/Scraped_data_new/fifa_08.csv:8,data/Scraped_data_new/fifa_09.csv:9,data/Scraped_data_new/fifa_10.csv:10,data/Scraped_data_new/fifa_11.csv:11,data/Scraped_data_new/fifa_12.csv:12,data/Scraped_data_new/fifa_13.csv:13,data/Scraped_data_new/fifa_14.csv:14}")
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
            List<ClubSeason> part = edition >= 0 ? FifaDataLoader.loadEdition(path, edition)
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
        clubs = bridgePlayerIds(clubs);
        clubs = unifyPlayerNames(clubs);
        clubs = canonicalizeClubNames(clubs);
        repo.saveAll(clubs.stream().map(EngineMapper::toEntity).toList());

        long seasons = clubs.stream().map(c -> c.season).distinct().count();
        log.info("Seeded H2: {} top-5 club-seasons across {} editions ({} players).",
            clubs.size(), seasons, clubs.stream().mapToInt(c -> c.roster.size()).sum());
    }

    /**
     * Bridge id-less players to the real sofifa id so Prime Mode (career-best) is consistent across editions.
     * EA FC 26's ID column already IS the sofifa player_id; only FC 25 has no id column (we assign negative
     * synthetic ids in the loader). FC 25/26 share EA's full-name format, so we resolve each id-less player by
     * (name, nation) against the players that DO carry a real id (FC 26 + the sofifa pool). Keys whose (name,
     * nation) maps to two different real ids are treated as ambiguous and skipped, so we never mis-link
     * namesakes; unmatched players keep their synthetic id (harmless no-op — same as before).
     */
    private static List<ClubSeason> bridgePlayerIds(List<ClubSeason> clubs) {
        Map<String, Integer> byName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (ClubSeason cs : clubs)
            for (Player p : cs.roster)
                if (p.id() >= 0) {
                    String k = nameKey(p.name(), p.nation());
                    Integer prev = byName.putIfAbsent(k, p.id());
                    if (prev != null && prev != p.id()) ambiguous.add(k);
                }
        return clubs.stream().map(cs -> {
            if (cs.roster.stream().noneMatch(p -> p.id() < 0)) return cs;
            List<Player> fixed = cs.roster.stream().map(p -> {
                if (p.id() >= 0) return p;
                String k = nameKey(p.name(), p.nation());
                Integer real = ambiguous.contains(k) ? null : byName.get(k);
                return real == null ? p : new Player(real, p.name(), p.nation(), p.positions(), p.overall(), p.dob());
            }).toList();
            return new ClubSeason(cs.club, cs.season, cs.league, fixed);
        }).toList();
    }

    /**
     * Unify a player's display name across editions to the shortest variant they actually carry. The sofifa
     * FC 25 dump labels players with verbose full names ("Jude Victor William Bellingham"), whereas the other
     * editions use the clean short form ("J. Bellingham"). Keyed by the (now-bridged) player id, pick the
     * shortest real name seen for that id — so every edition shows the same tidy label. Safe: it only ever
     * reuses a name the same player already had; a player seen only with a long name keeps it.
     */
    private static List<ClubSeason> unifyPlayerNames(List<ClubSeason> clubs) {
        Map<Integer, String> shortest = new HashMap<>();
        for (ClubSeason cs : clubs)
            for (Player p : cs.roster)
                shortest.merge(p.id(), p.name(), (a, b) -> b.length() < a.length() ? b : a);
        return clubs.stream().map(cs -> {
            List<Player> roster = cs.roster.stream().map(p -> {
                String name = shortest.get(p.id());
                return name.equals(p.name()) ? p : new Player(p.id(), name, p.nation(), p.positions(), p.overall(), p.dob());
            }).toList();
            return new ClubSeason(cs.club, cs.season, cs.league, roster);
        }).toList();
    }

    /** Accent-stripped, alphanumeric (name, nation) identity key for cross-edition matching. */
    private static String nameKey(String name, String nation) {
        return java.text.Normalizer.normalize(name + "|" + nation, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase().replaceAll("[^a-z0-9|]", "");
    }

    /**
     * Unify a club's display name across editions. The source data labels the same club inconsistently per edition
     * ("Real Madrid" vs "Real Madrid CF", "AC Milan" vs "Milan", "Atlético Madrid" vs "Atlético de Madrid"), which
     * makes one club show up as two in the browser/search. Group by the engine dedup key ({@link ClubSeason#clubKey})
     * — which already treats those as the same club — and relabel every season to that club's most-common name
     * (ties → the longer, more complete form). Safe: keys never span leagues, so this only re-labels, never merges
     * distinct clubs.
     */
    private static List<ClubSeason> canonicalizeClubNames(List<ClubSeason> clubs) {
        Map<String, Map<String, Long>> freq = new HashMap<>();
        for (ClubSeason cs : clubs)
            freq.computeIfAbsent(cs.clubKey(), k -> new HashMap<>()).merge(cs.club, 1L, Long::sum);
        Map<String, String> canonical = new HashMap<>();
        for (var e : freq.entrySet())
            canonical.put(e.getKey(), e.getValue().entrySet().stream()
                .max(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .thenComparingInt(x -> x.getKey().length()))
                .orElseThrow().getKey());
        return clubs.stream()
            .map(cs -> {
                String name = canonical.get(cs.clubKey());
                return name.equals(cs.club) ? cs : new ClubSeason(name, cs.season, cs.league, cs.roster);
            })
            .toList();
    }
}
