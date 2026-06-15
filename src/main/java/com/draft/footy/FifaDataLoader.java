package com.draft.footy;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the stefanoleone992 FIFA dataset (sofifa schema) into ClubSeason objects, filtered to the top-5 leagues.
 *
 * Schema-flexible by design: handles both the legacy single-season files (e.g. players_22.csv — {@code sofifa_id},
 * long league names, one edition per file) and the combined multi-edition export (male_players_all.csv —
 * {@code player_id}, short league names, a per-row {@code fifa_version} spanning FIFA 15–23). Column names are
 * resolved through alias lists and the top-5 filter prefers the stable numeric {@code league_id}, falling back to
 * a name-alias set when that column is absent.
 */
public final class FifaDataLoader {

    /** One top-5 league: its stable sofifa league_id, a canonical display name, and every EA name it has carried
     *  across editions (incl. the sponsored EA Sports FC names — "LALIGA EA SPORTS", "Serie A Enilive", …). */
    private record League(int id, String primary, Set<String> names) {}

    private static final List<League> TOP5 = List.of(
        new League(13, "Premier League", Set.of("Premier League", "English Premier League")),
        new League(53, "La Liga", Set.of("La Liga", "LaLiga Santander", "Spain Primera Division", "Spanish Primera Division",
            "Primera Division", "Primera División", "LALIGA EA SPORTS")),
        new League(31, "Serie A", Set.of("Serie A", "Italian Serie A", "Serie A Enilive", "Serie A TIM")),
        new League(19, "Bundesliga", Set.of("Bundesliga", "German 1. Bundesliga")),
        new League(16, "Ligue 1", Set.of("Ligue 1", "French Ligue 1", "Ligue 1 Conforama", "Ligue 1 Uber Eats",
            "Ligue 1 McDonald's")));

    /** The sponsored/aliased name back to a clean league label. */
    private static String canonicalLeague(String name) {
        for (League l : TOP5) if (l.names().contains(name)) return l.primary();
        return name;
    }

    /**
     * The EA Sports FC "ratings export" files (fc_25 / EAFC26) abbreviate or re-licence club names — "Man Utd",
     * "Spurs", "Paris SG" — and EA FC 26 renames clubs that lost their licence ("Latium" = Lazio, "Milano FC" =
     * AC Milan, identified by squad). This maps those back to the sofifa name so a club is one identity across
     * all editions (so it's searchable and doesn't appear twice). Newly-promoted sides with no prior top-flight
     * season (Como, Pisa, Paris FC, FC St. Pauli, Holstein Kiel) keep their own name.
     */
    private static final Map<String, String> CLUB_ALIASES = Map.ofEntries(
        Map.entry("Man Utd", "Manchester United"), Map.entry("Newcastle Utd", "Newcastle United"),
        Map.entry("Nott'm Forest", "Nottingham Forest"), Map.entry("Spurs", "Tottenham Hotspur"),
        Map.entry("West Ham", "West Ham United"), Map.entry("Wolves", "Wolverhampton Wanderers"),
        Map.entry("Brighton", "Brighton & Hove Albion"), Map.entry("Ipswich", "Ipswich Town"),
        Map.entry("AS Roma", "Roma"), Map.entry("SSC Napoli", "Napoli"), Map.entry("Venezia", "Venezia FC"),
        Map.entry("Milano FC", "AC Milan"), Map.entry("Lombardia FC", "Inter"),
        Map.entry("Bergamo Calcio", "Atalanta"), Map.entry("Latium", "Lazio"),
        Map.entry("Paris SG", "Paris Saint-Germain"), Map.entry("OL", "Olympique Lyonnais"),
        Map.entry("OM", "Olympique de Marseille"), Map.entry("RC Lens", "Lens"),
        Map.entry("AJ Auxerre", "Auxerre"), Map.entry("Havre AC", "Le Havre"), Map.entry("Toulouse FC", "Toulouse"),
        Map.entry("Celta", "Celta de Vigo"), Map.entry("RC Celta", "Celta de Vigo"),
        Map.entry("D. Alavés", "Deportivo Alavés"), Map.entry("Levante UD", "Levante Unión Deportiva"),
        Map.entry("R. Valladolid CF", "Real Valladolid"), Map.entry("R. Oviedo", "Real Oviedo"),
        Map.entry("RCD Espanyol", "Espanyol"), Map.entry("UD Las Palmas", "Las Palmas"),
        Map.entry("Frankfurt", "Eintracht Frankfurt"), Map.entry("Leverkusen", "Bayer 04 Leverkusen"),
        Map.entry("M'gladbach", "Borussia Mönchengladbach"), Map.entry("Union Berlin", "1. FC Union Berlin"));

    private static String canonicalClub(String name) {
        String n = name.trim();
        return CLUB_ALIASES.getOrDefault(n, n);
    }

    /** Tolerant int parse — handles the float-formatted columns ("24.0", "13.0") some EA FC exports use. */
    private static int parseIntLoose(String s) {
        s = s.trim();
        int dot = s.indexOf('.');
        return Integer.parseInt(dot >= 0 ? s.substring(0, dot) : s);
    }

    private static final Set<Integer> TOP5_IDS =
        TOP5.stream().map(League::id).collect(Collectors.toSet());
    private static final Set<String> TOP5_NAMES =
        TOP5.stream().flatMap(l -> l.names().stream()).collect(Collectors.toSet());

    /**
     * The <em>exact</em> top-flight league names the EA-FC ratings exports use (fc_25 / fc_26). The modern files
     * have no league_id to disambiguate, so we must NOT reuse the broad sofifa alias set — e.g. "Primera División"
     * there is the <strong>Argentine</strong> league, not Spain's (which is "LALIGA EA SPORTS"). Strict membership
     * keeps non-European top flights (Liga Profesional, MLS, Libertadores…) out.
     */
    private static final Set<String> MODERN_TOP5_NAMES = Set.of(
        "Premier League", "LALIGA EA SPORTS", "LaLiga Santander", "Serie A Enilive", "Serie A TIM",
        "Bundesliga", "Ligue 1 McDonald's", "Ligue 1 Uber Eats");

    /** FIFA edition -> season label (FIFA 22 ~ 2021/22). */
    public static String seasonFor(int fifaEdition) {
        int start = 2000 + fifaEdition - 1;            // 22 -> 2021
        return start + "/" + String.format("%02d", (start + 1) % 100);
    }

    /**
     * Loads every edition present in the file. Uses the per-row {@code fifa_version} column when available
     * (the combined export) and falls back to {@code defaultEdition} for legacy single-season files.
     */
    public static List<ClubSeason> loadAllSeasons(Path csv) throws IOException {
        return load(csv, -1);
    }

    /** Back-compat entry point for the legacy single-season files (one edition per file). */
    public static List<ClubSeason> loadTop5(Path csv, int fifaEdition) throws IOException {
        return load(csv, fifaEdition);
    }

    private static List<ClubSeason> load(Path csv, int defaultEdition) throws IOException {
        // Key = club + season so the same club in different editions is a distinct ClubSeason (the spin pool).
        Map<String, List<Player>> byKey = new LinkedHashMap<>();
        Map<String, String[]> keyMeta = new LinkedHashMap<>(); // key -> {club, season, league}

        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String[] header = splitCsv(r.readLine());
            int iId      = idx(header, true,  "player_id", "sofifa_id");
            int iName    = idx(header, true,  "short_name");
            int iPos     = idx(header, true,  "player_positions");
            int iOverall = idx(header, true,  "overall");
            int iClub    = idx(header, true,  "club_name", "club");
            int iNation  = idx(header, true,  "nationality_name", "nationality");
            int iLeagueN = idx(header, true,  "league_name");
            int iLevel   = idx(header, true,  "league_level");
            int iLeagueId = idx(header, false, "league_id");      // combined file only
            int iVersion  = idx(header, false, "fifa_version");   // combined file only
            int iDob      = idx(header, false, "dob");

            int maxNeeded = Math.max(Math.max(iLeagueN, iLevel), Math.max(iClub, iOverall));

            String line;
            while ((line = r.readLine()) != null) {
                String[] f = splitCsv(line);
                if (f.length <= maxNeeded) continue;
                if (!"1".equals(f[iLevel].trim())) continue;
                if (!isTop5(f, iLeagueId, iLeagueN)) continue;
                try {
                    int edition = iVersion >= 0 ? parseIntLoose(f[iVersion]) : defaultEdition;
                    if (edition < 0) continue; // no version column and no default — cannot label the season
                    String season = seasonFor(edition);

                    List<String> positions = Arrays.stream(f[iPos].split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    String dob = iDob >= 0 && iDob < f.length ? f[iDob].trim() : "";
                    Player p = new Player(Integer.parseInt(f[iId].trim()), f[iName], f[iNation],
                        positions, Integer.parseInt(f[iOverall].trim()), dob);

                    String club = f[iClub];
                    String key = club + "|" + season;
                    byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                    keyMeta.putIfAbsent(key, new String[]{club, season, canonicalLeague(f[iLeagueN].trim())});
                } catch (NumberFormatException ignore) { }
            }
        }

        return group(byKey, keyMeta);
    }

    /**
     * Loads a single EA Sports FC edition in the newer "ratings export" schema (fc_25 / EAFC26-Men style):
     * {@code Name, OVR, Position, Alternative positions, Nation, League, Team} — no {@code league_id},
     * {@code league_level} or {@code fifa_version}. Top flight is identified by the (sponsored) league NAME, so
     * the second tiers ("LALIGA HYPERMOTION", "Bundesliga 2") are naturally excluded. The season is {@code edition}.
     */
    public static List<ClubSeason> loadModern(Path csv, int edition) throws IOException {
        Map<String, List<Player>> byKey = new LinkedHashMap<>();
        Map<String, String[]> keyMeta = new LinkedHashMap<>();
        String season = seasonFor(edition);

        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String[] header = splitCsv(r.readLine());
            int iName = idx(header, true,  "Name", "long_name", "short_name");
            int iOvr  = idx(header, true,  "OVR", "overall");
            int iPos  = idx(header, true,  "Position", "player_positions");
            int iAlt  = idx(header, false, "Alternative positions");
            int iNat  = idx(header, false, "Nation", "nationality_name");
            int iLg   = idx(header, true,  "League", "league_name");
            int iTeam = idx(header, true,  "Team", "club_name", "club");
            int iId   = idx(header, false, "ID", "player_id", "sofifa_id");
            int maxNeeded = Math.max(Math.max(iName, iOvr), Math.max(iLg, iTeam));

            int synthId = -1; // stable-ish ids for files without one (FC25 has no player id)
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = splitCsv(line);
                if (f.length <= maxNeeded) continue;
                if (!MODERN_TOP5_NAMES.contains(f[iLg].trim())) continue; // strict: top-5 European top flights only
                try {
                    int overall = parseIntLoose(f[iOvr]);
                    List<String> positions = modernPositions(f[iPos], iAlt >= 0 && iAlt < f.length ? f[iAlt] : "");
                    if (positions.isEmpty()) continue;
                    int id = (iId >= 0 && iId < f.length && !f[iId].isBlank()) ? parseIntLoose(f[iId]) : synthId--;
                    String nation = iNat >= 0 && iNat < f.length ? f[iNat].trim() : "";
                    Player p = new Player(id, f[iName].trim(), nation, positions, overall, "");

                    String club = canonicalClub(f[iTeam]);
                    String key = club + "|" + season;
                    byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                    keyMeta.putIfAbsent(key, new String[]{club, season, canonicalLeague(f[iLg].trim())});
                } catch (NumberFormatException ignore) { }
            }
        }
        return group(byKey, keyMeta);
    }

    /** Combine the primary position with any alternates ("LW" or "['RW', 'LM']") into a clean, de-duped list. */
    private static List<String> modernPositions(String primary, String alt) {
        List<String> out = new ArrayList<>();
        String p = primary.trim();
        if (!p.isEmpty()) out.add(p);
        for (String a : alt.replaceAll("[\\[\\]'\"]", " ").split(",")) {
            a = a.trim();
            if (!a.isEmpty() && !out.contains(a)) out.add(a);
        }
        return out;
    }

    /** Group accumulated players into club-seasons, keeping only those that can field an XI (>=11). */
    private static List<ClubSeason> group(Map<String, List<Player>> byKey, Map<String, String[]> keyMeta) {
        List<ClubSeason> out = new ArrayList<>();
        for (var e : byKey.entrySet())
            if (e.getValue().size() >= 11) {
                String[] m = keyMeta.get(e.getKey());
                out.add(new ClubSeason(m[0], m[1], m[2], e.getValue()));
            }
        return out;
    }

    /**
     * Top-5 membership. Some exports (notably the fc_24 dump) have a <em>scrambled</em> league_id↔league_name
     * mapping — e.g. league_id 13 (Premier League) stamped on 1,700+ Championship rows, and vice-versa — so
     * neither column is trustworthy alone (id-only leaks 2nd tiers; name-only over-includes). We therefore
     * require <strong>both</strong> the numeric id and the name to independently say top-5. When the id column
     * is absent (legacy single-season files) we fall back to the name alone.
     */
    private static boolean isTop5(String[] f, int iLeagueId, int iLeagueN) {
        boolean nameOk = iLeagueN >= 0 && iLeagueN < f.length && TOP5_NAMES.contains(f[iLeagueN].trim());
        if (iLeagueId >= 0 && iLeagueId < f.length) {
            try { return nameOk && TOP5_IDS.contains(parseIntLoose(f[iLeagueId])); }
            catch (NumberFormatException ignore) { return nameOk; } // id unparseable → trust the name
        }
        return nameOk;
    }

    /** First-present column index across alias names. Throws when required and none match; -1 when optional. */
    private static int idx(String[] header, boolean required, String... aliases) {
        for (String alias : aliases)
            for (int i = 0; i < header.length; i++)
                if (header[i].equals(alias)) return i;
        if (required) throw new IllegalStateException("missing column, tried: " + Arrays.toString(aliases));
        return -1;
    }

    /** Minimal CSV splitter handling quoted fields. */
    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
