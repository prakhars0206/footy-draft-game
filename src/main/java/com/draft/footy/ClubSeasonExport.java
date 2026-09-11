package com.draft.footy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Analysis tool (not part of the game): dumps every loaded club-season with the squad features the calibration
 * regression needs, so Python can join them onto real match results and learn the engine's constants.
 *
 * <p>Phase M2 of the calibration plan. The features are taken from {@link ClubSeason#optimalXi()} — the same
 * best-fit XI the engine rates opponents by — and era-normalisation is APPLIED (this must be the pool exactly as
 * the game sees it, or the fitted constants won't describe the game). Note the known optimism bias: this is the
 * best XI a club could field, not the one that actually played, so the features systematically overstate. The
 * regression absorbs that into its slope.
 *
 * <pre>
 *   mvn -q compile &amp;&amp; java -cp target/classes com.draft.footy.ClubSeasonExport
 * </pre>
 *
 * Writes {@code analysis/calibration/data/club_seasons.csv} (gitignored — regenerate, don't commit).
 */
public final class ClubSeasonExport {

    private ClubSeasonExport() { }

    private static final Path OUT = Path.of("analysis/calibration/data/club_seasons.csv");

    public static void main(String[] args) throws IOException {
        List<ClubSeason> clubs = TierReport.loadPool();
        clubs.sort(Comparator.comparing((ClubSeason c) -> c.season).thenComparing(c -> c.club));

        StringBuilder csv = new StringBuilder(
            "club,norm_club,season,edition,league,roster_size,formation,natural_fits,"
          + "overall,attack_rating,defence_rating,mean_att,mean_mid,mean_def,mean_gk,depth_12_16\n");

        for (ClubSeason cs : clubs) {
            Xi xi = cs.optimalXi();
            Map<Line, Double> means = lineMeans(xi);

            csv.append(q(cs.club)).append(',')
               .append(q(ClubSeason.normClub(cs.club))).append(',')
               .append(cs.season).append(',')
               .append(editionOf(cs.season)).append(',')
               .append(q(cs.league)).append(',')
               .append(cs.roster.size()).append(',')
               .append(xi.formation.label()).append(',')
               .append(naturalFits(xi)).append(',')
               .append(xi.overall()).append(',')
               .append(f(xi.attackRating())).append(',')
               .append(f(xi.defenceRating())).append(',')
               .append(f(means.get(Line.ATT))).append(',')
               .append(f(means.get(Line.MID))).append(',')
               .append(f(means.get(Line.DEF))).append(',')
               .append(f(means.get(Line.GK))).append(',')
               .append(f(depth(cs))).append('\n');
        }

        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, csv.toString());

        long editions = clubs.stream().map(c -> c.season).distinct().count();
        System.out.printf("Wrote %d club-seasons across %d editions -> %s%n", clubs.size(), editions, OUT);
        System.out.println("Per league:");
        clubs.stream().collect(java.util.stream.Collectors.groupingBy(c -> c.league,
                java.util.TreeMap::new, java.util.stream.Collectors.counting()))
            .forEach((lg, n) -> System.out.printf("  %-16s %4d%n", lg, n));
    }

    /** Unrounded per-line mean overall — the regression wants the raw numbers, not {@code Xi}'s rounded ints. */
    private static Map<Line, Double> lineMeans(Xi xi) {
        Map<Line, Double> out = new EnumMap<>(Line.class);
        for (Line l : Line.values())
            out.put(l, xi.slots.stream().filter(s -> s.line() == l)
                .mapToInt(s -> s.player().overall()).average().orElse(0));
        return out;
    }

    /** Squad depth: mean overall of the 12th–16th best players, i.e. what's on the bench behind the XI. */
    private static double depth(ClubSeason cs) {
        List<Integer> sorted = cs.roster.stream().map(Player::overall)
            .sorted(Comparator.reverseOrder()).toList();
        if (sorted.size() <= 11) return 0;
        return sorted.subList(11, Math.min(16, sorted.size())).stream()
            .mapToInt(Integer::intValue).average().orElse(0);
    }

    /** How many of the XI are in a natural position — a data-quality flag for the join. */
    private static int naturalFits(Xi xi) {
        int n = 0;
        for (Xi.Slot s : xi.slots) if (s.player().canPlay(s.position())) n++;
        return n;
    }

    /** "2008/09" -> 9 (the FIFA edition). */
    private static int editionOf(String season) {
        return Integer.parseInt(season.substring(0, 4)) - 1999;
    }

    private static String f(double d) { return String.format("%.4f", d); }

    /** Minimal CSV quoting — club names contain commas ("Borussia Monchengladbach" doesn't, but EA's don't all behave). */
    private static String q(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
