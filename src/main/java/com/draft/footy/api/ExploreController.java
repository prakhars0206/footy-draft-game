package com.draft.footy.api;

import com.draft.footy.ClubSeason;
import com.draft.footy.Player;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * "The Almanac" — a read-only browse over the loaded pool (every top-5 club-season across FIFA 15 → EA FC 26):
 * the strongest squads, filter/search, and any club's full squad with true ratings. Pure read; no run state.
 */
@RestController
@RequestMapping("/api/explore")
public class ExploreController {

    private final GameCatalog catalog;
    public ExploreController(GameCatalog catalog) { this.catalog = catalog; }

    public record MetaView(List<String> leagues, List<Integer> years) { }
    public record ClubSummary(String club, String season, String league, int strength, String tier,
                              String topPlayer, int topOverall) { }
    public record AlmanacSlot(String position, String line, String name, int overall) { }
    public record AlmanacPlayer(int sofifaId, String name, String nation, List<String> positions, String line, int overall) { }
    public record SquadView(String club, String season, String league, int strength, String tier,
                            String formation, List<AlmanacSlot> xi, List<AlmanacPlayer> roster) { }

    /** Filter options: the leagues present and the span of season start-years. */
    @GetMapping("/meta")
    public MetaView meta() {
        List<String> leagues = catalog.clubs().stream().map(c -> c.league).distinct().sorted().toList();
        List<Integer> years = catalog.clubs().stream().map(c -> startYear(c.season)).distinct()
            .sorted(Comparator.reverseOrder()).toList();
        return new MetaView(leagues, years);
    }

    /** Browse club-seasons — filtered by league/era/name, sorted (default: strongest first = the iconic squads). */
    @GetMapping("/clubs")
    public List<ClubSummary> clubs(@RequestParam(required = false) String league,
                                   @RequestParam(required = false) Integer from,
                                   @RequestParam(required = false) Integer to,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "strength") String sort,
                                   @RequestParam(defaultValue = "5000") int limit) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        Comparator<ClubSeason> cmp = switch (sort) {
            case "club" -> Comparator.comparing((ClubSeason c) -> c.club).thenComparing(c -> c.season);
            case "season" -> Comparator.comparing((ClubSeason c) -> c.season, Comparator.reverseOrder())
                .thenComparingInt(c -> -c.optimalStrength());
            default -> Comparator.comparingInt((ClubSeason c) -> -c.optimalStrength()); // strongest first
        };
        return catalog.clubs().stream()
            .filter(c -> league == null || league.isBlank() || c.league.equalsIgnoreCase(league))
            .filter(c -> needle.isEmpty() || c.club.toLowerCase().contains(needle))
            .filter(c -> { int y = startYear(c.season); return (from == null || y >= from) && (to == null || y <= to); })
            .sorted(cmp)
            .limit(Math.max(1, Math.min(5000, limit)))
            .map(this::summary)
            .toList();
    }

    /** A single club-season's full squad: its best XI in formation + the whole roster, ratings shown. */
    @GetMapping("/squad")
    public SquadView squad(@RequestParam String club, @RequestParam String season) {
        ClubSeason cs = catalog.clubs().stream()
            .filter(c -> c.club.equals(club) && c.season.equals(season)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such club-season"));
        var ord = TeamLayout.inFormationOrder(cs.optimalXi());
        List<AlmanacSlot> xi = ord.slots().stream()
            .map(s -> new AlmanacSlot(s.position(), s.line().name(), s.player().name(), s.player().overall())).toList();
        List<AlmanacPlayer> roster = cs.roster.stream()
            .sorted(Comparator.comparingInt((Player p) -> -p.overall()))
            .map(p -> new AlmanacPlayer(p.id(), p.name(), p.nation(), p.positions(), p.primaryLine().name(), p.overall()))
            .toList();
        return new SquadView(cs.club, cs.season, cs.league, cs.optimalStrength(),
            DraftTiers.label(cs.optimalStrength()), ord.formation(), xi, roster);
    }

    private ClubSummary summary(ClubSeason c) {
        Player top = c.roster.stream().max(Comparator.comparingInt(Player::overall)).orElse(null);
        return new ClubSummary(c.club, c.season, c.league, c.optimalStrength(), DraftTiers.label(c.optimalStrength()),
            top != null ? top.name() : "", top != null ? top.overall() : 0);
    }

    private static int startYear(String season) {
        try { return Integer.parseInt(season.substring(0, 4)); } catch (RuntimeException e) { return 0; }
    }
}
