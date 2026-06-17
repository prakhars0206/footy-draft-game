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

    /**
     * Tolerant int parse — reads the leading integer and ignores any trailing noise: float-formatted columns
     * ("24.0"→24) and sofifa's form-change suffix on overalls ("87-1"→87, "85+1"→85, i.e. rating 87 down one
     * since the last update). Throws if there are no leading digits.
     */
    private static int parseIntLoose(String s) {
        s = s.trim();
        int i = 0, n = s.length();
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++; // optional leading sign
        int start = i;
        while (i < n && Character.isDigit(s.charAt(i))) i++;
        if (i == start) throw new NumberFormatException("no leading integer in: " + s);
        return Integer.parseInt(s.substring(0, i));
    }

    private static int max(int... xs) {
        int m = Integer.MIN_VALUE;
        for (int x : xs) m = Math.max(m, x);
        return m;
    }

    /**
     * Overall offset that puts older editions on the modern (FIFA 17+) rating scale. EA inflated ratings once,
     * across the FIFA 15→16→17 transition (same-player median drift was flat before and after) — but it was NOT
     * uniform: the mid-tier rose ~+2 while elite players stayed pinned to the ceiling (the 94-rated never moved,
     * and the player-overall distributions are identical at the top). So we taper — lift the mid, leave the top
     * alone — which aligns club strength/tiers across eras without inventing 96-rated players. FIFA ≤15 gets the
     * full step (+2 below 83, +1 at 83–88, 0 from 89), FIFA 16 the half step, FIFA 17+ nothing.
     */
    /** The active offset. Production uses {@link #flatOffset}; analysis tooling (TierReport) can swap it. */
    static java.util.function.IntBinaryOperator eraOffsetFn = FifaDataLoader::flatOffset;

    private static int eraOffset(int edition, int overall) {
        return eraOffsetFn.applyAsInt(edition, overall);
    }

    /**
     * Committed scheme: a FLAT lift onto the modern scale — FIFA ≤15 +2, FIFA 16 +1, FIFA 17+ none. Because every
     * card in an edition moves by the same amount, within-edition ranking order is preserved (we don't bump some
     * cards and not others). The load-site cap at {@link #ERA_CAP} then keeps it sane at the very top: only a raw
     * 94 in a +2 edition would exceed the cap (96 → 95), so prime Messi (95) still sits above everyone (≤94).
     */
    static int flatOffset(int edition, int overall) {
        return edition <= 15 ? 2 : edition == 16 ? 1 : 0;
    }

    /** Earlier alternative, kept for the TierReport comparison: a ceiling-preserving taper (elite untouched). */
    static int taperOffset(int edition, int overall) {
        if (edition >= 17 || overall >= 89) return 0;
        int step = edition == 16 ? 1 : 2;
        return overall >= 87 ? Math.min(step, 1) : step;
    }

    /** Era-normalised ceiling: a +2 edition's raw-94 (prime Messi) lands at 95, one above the modern 94. */
    private static final int ERA_CAP = 95;

    /** Clean a display name: trim and drop a trailing "-" export artifact (the fc_25_sofifa dump suffixes " -"). */
    private static String cleanName(String s) {
        s = s.trim();
        while (s.endsWith("-")) s = s.substring(0, s.length() - 1).trim();
        return s;
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

    /**
     * Load one edition, auto-detecting the schema: the EA "ratings export" (an {@code OVR} column) goes through
     * {@link #loadModern}, otherwise it's a sofifa-schema file (e.g. fc_25_sofifa.csv — {@code player_id} +
     * {@code overall_rating} + {@code club_league_id}) loaded at the given fixed edition.
     */
    public static List<ClubSeason> loadEdition(Path csv, int edition) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(csv)) {
            for (String c : splitCsv(r.readLine())) if (c.trim().equals("OVR")) return loadModern(csv, edition);
        }
        return load(csv, edition);
    }

    private static List<ClubSeason> load(Path csv, int defaultEdition) throws IOException {
        // Key = club + season so the same club in different editions is a distinct ClubSeason (the spin pool).
        Map<String, List<Player>> byKey = new LinkedHashMap<>();
        Map<String, String[]> keyMeta = new LinkedHashMap<>(); // key -> {club, season, league}

        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String[] header = splitCsv(r.readLine());
            // Column aliases span three sofifa variants: old combined export (short_name/player_positions/overall/
            // league_name/league_level), legacy single-season, and the newer fc_25_sofifa dump (name/positions/
            // overall_rating/club_league_name/club_league_id, no league_level).
            int iId      = idx(header, true,  "player_id", "sofifa_id");
            int iName    = idx(header, true,  "short_name", "name");
            int iPos     = idx(header, true,  "player_positions", "positions");
            int iOverall = idx(header, true,  "overall", "overall_rating");
            int iClub    = idx(header, true,  "club_name", "club");
            int iNation  = idx(header, true,  "nationality_name", "nationality", "country_name");
            int iLeagueN = idx(header, true,  "league_name", "club_league_name");
            int iLevel   = idx(header, false, "league_level");                  // absent in the fc_25_sofifa dump
            int iLeagueId = idx(header, false, "league_id", "club_league_id");
            int iVersion  = idx(header, false, "fifa_version");   // combined file only ("version" dates are NOT this)
            int iDob      = idx(header, false, "dob");

            int maxNeeded = max(iId, iName, iPos, iOverall, iClub, iNation, iLeagueN, iLevel, iLeagueId, iVersion);

            String line;
            while ((line = r.readLine()) != null) {
                String[] f = splitCsv(line);
                if (f.length <= maxNeeded) continue;
                if (iLevel >= 0 && !"1".equals(f[iLevel].trim())) continue;    // skip lower tiers when level is given
                if (!isTop5(f, iLeagueId, iLeagueN)) continue;
                try {
                    int edition = iVersion >= 0 ? parseIntLoose(f[iVersion]) : defaultEdition;
                    if (edition < 0) continue; // no version column and no default — cannot label the season
                    String season = seasonFor(edition);

                    // "SW" (sweeper) was a real position in FIFA 07–11 but no formation fields one — treat it as
                    // a centre-back (which it effectively is) so those players are placeable and line up as DEF.
                    List<String> positions = Arrays.stream(f[iPos].split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(s -> s.equals("SW") ? "CB" : s).distinct().collect(Collectors.toList());
                    String nation = iNation >= 0 && iNation < f.length ? f[iNation] : "";
                    String dob = iDob >= 0 && iDob < f.length ? f[iDob].trim() : "";
                    int raw = parseIntLoose(f[iOverall]);
                    int overall = Math.min(ERA_CAP, raw + eraOffset(edition, raw));
                    Player p = new Player(parseIntLoose(f[iId]), cleanName(f[iName]), nation,
                        positions, overall, dob);

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
                    int rawOvr = parseIntLoose(f[iOvr]);
                    int overall = Math.min(ERA_CAP, rawOvr + eraOffset(edition, rawOvr)); // no-op for FC 25/26
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
        for (var e : byKey.entrySet()) {
            // Dedup by player id within a squad — some exports list a player twice, which would otherwise field
            // them in two XI slots. Same id at the same club = same player; keep the higher-rated row.
            Map<Integer, Player> uniq = new LinkedHashMap<>();
            for (Player p : e.getValue()) uniq.merge(p.id(), p, (a, b) -> b.overall() > a.overall() ? b : a);
            if (uniq.size() >= 11) {
                String[] m = keyMeta.get(e.getKey());
                out.add(new ClubSeason(m[0], m[1], m[2], new ArrayList<>(uniq.values())));
            }
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
