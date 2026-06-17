package com.draft.footy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Analysis tool: deep-dive on the "flat +2 (FIFA ≤15) / +1 (FIFA 16) / 0 (17+), then CAP at 94" scheme.
 * Reports the tier distribution, the ceiling-clustering it causes, and every player ≥91 raw (so you can see
 * exactly who the cap touches). Writes analysis/flat-capped-scheme.md.
 *
 *   mvn -q compile && java -cp target/classes com.draft.footy.CappedSchemeReport
 */
public final class CappedSchemeReport {

    private static final int CAP = 95; // committed scheme caps at 95 (prime Messi → 95, above the modern 94)
    private static final String[] TIERS = {"Iconic", "Elite", "Pedigree", "Steady", "Minnow"};

    private static int off(int ed) { return ed <= 15 ? 2 : ed == 16 ? 1 : 0; }
    private static int preCap(int ed, int raw) { return Math.min(99, raw + off(ed)); }
    private static int finalOvr(int ed, int raw) { return Math.min(CAP, preCap(ed, raw)); }
    private static int editionOf(String s) { return Integer.parseInt(s.substring(0, 4)) - 1999; }
    private static int tierIndex(int s) { return s >= 87 ? 0 : s >= 83 ? 1 : s >= 78 ? 2 : s >= 72 ? 3 : 4; }
    private static String pct(int n, int t) { return t == 0 ? "0%" : String.format("%.1f%%", 100.0 * n / t); }

    public static void main(String[] args) throws IOException {
        List<ClubSeason> raw = TierReport.loadRaw();
        List<Integer> editions = raw.stream().map(c -> editionOf(c.season)).distinct().sorted().toList();

        StringBuilder md = new StringBuilder();
        md.append("# Scheme deep-dive — flat +2 / +1 (FIFA 16), capped at 95  (considered, NOT committed)\n\n");
        md.append("The committed scheme is the **wide taper** (see tier-distribution.md). This file deep-dives the\n");
        md.append("flat+cap alternative that was considered. Offset: **FIFA ≤15 +2 / FIFA 16 +1 / 17+ 0**, **clamped at 95**.\n");
        md.append("Tier = club `optimalStrength`: Iconic ≥87 · Elite 83–86 · Pedigree 78–82 · Steady 72–77 · Minnow ≤71.\n\n");

        // ---- A. tier distribution ----
        md.append("## A. Tier distribution per edition (this scheme)\n\n");
        md.append("| Edition | n | Iconic | Elite | Pedigree | Steady | Minnow | mean | maxClub | maxOVR |\n");
        md.append("|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|\n");
        int[][] oldMod = new int[2][TIERS.length];
        int[] oldModN = new int[2];
        Map<Integer, List<Integer>> strByEd = new LinkedHashMap<>();
        Map<Integer, Integer> maxOvrByEd = new LinkedHashMap<>();
        for (ClubSeason cs : raw) {
            int ed = editionOf(cs.season);
            List<Player> adj = new ArrayList<>(cs.roster.size());
            int mo = 0;
            for (Player p : cs.roster) {
                int o = finalOvr(ed, p.overall());
                mo = Math.max(mo, o);
                adj.add(new Player(p.id(), p.name(), p.nation(), p.positions(), o, p.dob()));
            }
            strByEd.computeIfAbsent(ed, k -> new ArrayList<>()).add(new ClubSeason(cs.club, cs.season, cs.league, adj).optimalStrength());
            maxOvrByEd.merge(ed, mo, Math::max);
        }
        for (int ed : editions) {
            List<Integer> ss = strByEd.get(ed);
            int[] tc = new int[TIERS.length];
            int sum = 0, mx = 0;
            for (int s : ss) { tc[tierIndex(s)]++; sum += s; mx = Math.max(mx, s); }
            int grp = ed <= 15 ? 0 : 1;
            for (int i = 0; i < TIERS.length; i++) oldMod[grp][i] += tc[i];
            oldModN[grp] += ss.size();
            md.append(String.format("| %s | %d | %s | %s | %s | %s | %s | %.1f | %d | %d |%n",
                FifaDataLoader.seasonFor(ed), ss.size(), pct(tc[0], ss.size()), pct(tc[1], ss.size()),
                pct(tc[2], ss.size()), pct(tc[3], ss.size()), pct(tc[4], ss.size()),
                (double) sum / ss.size(), mx, maxOvrByEd.get(ed)));
        }
        appendAgg(md, "OLD 07–15", oldMod[0], oldModN[0]);
        appendAgg(md, "MODERN 16–26", oldMod[1], oldModN[1]);
        md.append("\n");

        // ---- B. ceiling clustering ----
        md.append("## B. What the 94-cap does to the top end\n\n");
        md.append("`capped` = players whose offset rating would exceed 94 (so they're pulled down to 94). ");
        md.append("`raw→94` = how many *distinct* raw ratings collapse onto 94 (top-end compression). ");
        md.append("`#at94` = players sitting at 94 after the scheme — compare old vs modern for over-density.\n\n");
        md.append("| Edition | offset | rawMax | capped | raw→94 | #at 94 |\n|---|--:|--:|--:|--:|--:|\n");
        Map<Integer, Integer> at94 = new TreeMap<>();
        for (int ed : editions) {
            // gather this edition's players (dedup by id)
            Map<Integer, Integer> byId = new LinkedHashMap<>(); // id -> raw
            for (ClubSeason cs : raw) if (editionOf(cs.season) == ed) for (Player p : cs.roster) byId.putIfAbsent(p.id(), p.overall());
            int capped = 0, n94 = 0, rawMax = 0;
            TreeSet<Integer> rawsTo94 = new TreeSet<>();
            for (int rawO : byId.values()) {
                rawMax = Math.max(rawMax, rawO);
                if (preCap(ed, rawO) > CAP) capped++;
                if (finalOvr(ed, rawO) == CAP) { n94++; rawsTo94.add(rawO); }
            }
            at94.put(ed, n94);
            md.append(String.format("| %s | +%d | %d | %d | %d | %d |%n",
                FifaDataLoader.seasonFor(ed), off(ed), rawMax, capped, rawsTo94.size(), n94));
        }
        md.append("\n");

        // ---- C. every player >= 91 raw, per edition ----
        md.append("## C. Every player rated ≥91 (raw), per edition\n\n");
        md.append("`raw → final` after +offset and the 94 cap. **CAPPED** marks players the cap actually pulled down.\n\n");
        for (int ed : editions) {
            List<String[]> rows = new ArrayList<>(); // name, club, raw, final, capped
            Map<Integer, Boolean> seen = new LinkedHashMap<>();
            for (ClubSeason cs : raw) {
                if (editionOf(cs.season) != ed) continue;
                for (Player p : cs.roster) {
                    if (p.overall() >= 91 && seen.putIfAbsent(p.id(), true) == null) {
                        int fin = finalOvr(ed, p.overall());
                        boolean capped = preCap(ed, p.overall()) > CAP;
                        rows.add(new String[]{p.name(), cs.club, String.valueOf(p.overall()), String.valueOf(fin), capped ? "CAPPED" : ""});
                    }
                }
            }
            if (rows.isEmpty()) continue;
            rows.sort((a, b) -> Integer.compare(Integer.parseInt(b[2]), Integer.parseInt(a[2])));
            md.append("### ").append(FifaDataLoader.seasonFor(ed)).append("  (offset +").append(off(ed)).append(")\n\n");
            md.append("| Player | Club | raw | → final | |\n|---|---|--:|--:|---|\n");
            for (String[] r : rows)
                md.append(String.format("| %s | %s | %s | %s | %s |%n", r[0], TierReport_short(r[1]), r[2], r[3], r[4]));
            md.append("\n");
        }

        Path dir = Path.of("analysis");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("flat-capped-scheme.md"), md.toString());
        System.out.println("Wrote analysis/flat-capped-scheme.md");
    }

    private static void appendAgg(StringBuilder md, String label, int[] tc, int n) {
        md.append("| **").append(label).append("** | ").append(n).append(" |");
        for (int v : tc) md.append(" **").append(pct(v, n)).append("** |");
        md.append(" — | — | — |\n");
    }

    private static String TierReport_short(String club) { return club.replace(",", ";"); }
}
