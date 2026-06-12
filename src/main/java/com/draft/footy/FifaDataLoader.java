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

/** Loads the stefanoleone992 FIFA dataset (sofifa schema) into ClubSeason objects, filtered to the top-5 leagues. */
public final class FifaDataLoader {

    private static final Set<String> TOP5 = Set.of(
        "English Premier League", "Spain Primera Division",
        "Italian Serie A", "German 1. Bundesliga", "French Ligue 1");

    /** FIFA edition -> season label (FIFA 22 ~ 2021/22). */
    public static String seasonFor(int fifaEdition) {
        int start = 2000 + fifaEdition - 1;            // 22 -> 2021
        return start + "/" + String.format("%02d", (start + 1) % 100);
    }

    public static List<ClubSeason> loadTop5(Path csv, int fifaEdition) throws IOException {
        String season = seasonFor(fifaEdition);
        Map<String, List<Player>> byClub = new LinkedHashMap<>();
        Map<String, String> clubLeague = new LinkedHashMap<>();

        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String headerLine = r.readLine();
            String[] header = splitCsv(headerLine);
            int iId = idx(header, "sofifa_id"), iName = idx(header, "short_name"),
                iPos = idx(header, "player_positions"), iOverall = idx(header, "overall"),
                iClub = idx(header, "club_name"), iLeague = idx(header, "league_name"),
                iLevel = idx(header, "league_level"), iNation = idx(header, "nationality_name");

            String line;
            while ((line = r.readLine()) != null) {
                String[] f = splitCsv(line);
                if (f.length <= Math.max(iLeague, iLevel)) continue;
                String league = f[iLeague];
                if (!TOP5.contains(league) || !"1".equals(f[iLevel])) continue;
                try {
                    List<String> positions = Arrays.stream(f[iPos].split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    Player p = new Player(Integer.parseInt(f[iId]), f[iName], f[iNation],
                        positions, Integer.parseInt(f[iOverall]));
                    String club = f[iClub];
                    byClub.computeIfAbsent(club, k -> new ArrayList<>()).add(p);
                    clubLeague.put(club, league);
                } catch (NumberFormatException ignore) { }
            }
        }

        List<ClubSeason> out = new ArrayList<>();
        for (var e : byClub.entrySet()) {
            if (e.getValue().size() >= 11) // need a fieldable XI
                out.add(new ClubSeason(e.getKey(), season, clubLeague.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    private static int idx(String[] header, String col) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(col)) return i;
        throw new IllegalStateException("missing column: " + col);
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
