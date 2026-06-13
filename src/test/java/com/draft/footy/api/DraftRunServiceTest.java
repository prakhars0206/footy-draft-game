package com.draft.footy.api;

import com.draft.footy.*;
import com.draft.footy.persistence.DraftRunEntity;
import com.draft.footy.persistence.DraftSlotEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end draft state machine over the real seeded pool (DESIGN_SPEC §3/§5B/§16). Skipped when the combined
 * dataset isn't present (CI without the CSV), mirroring the engine tests' file assumption.
 */
@SpringBootTest
@EnabledIf("dataPresent")
class DraftRunServiceTest {

    static boolean dataPresent() { return Files.exists(Path.of("data/male_players_all.csv")); }

    @Autowired DraftRunService service;
    @Autowired GameCatalog catalog;
    @Autowired DraftRunController controller;

    private DraftRunService.CreateRunCommand cmd(long seed, ShowRatings show, PlayerRatings pr) {
        return new DraftRunService.CreateRunCommand(seed, Formation.F_4_3_3, Difficulty.NORMAL, show,
            DraftMode.SQUAD_FIRST, pr, LeagueScope.WORLD, null, null, null);
    }

    private List<String> openPositions(DraftRunEntity run) {
        return run.openSlots().stream().map(DraftSlotEntity::getPosition).distinct().toList();
    }

    /** Spin once and draft the first squad player who can fill an open slot. Returns the drafted sofifaId. */
    private int draftOne(String runId) {
        var spin = service.spin(runId);
        DraftRunEntity run = spin.run();
        List<String> open = openPositions(run);
        Set<Integer> taken = run.getSlots().stream().filter(DraftSlotEntity::isFilled)
            .map(DraftSlotEntity::getSofifaId).collect(Collectors.toSet());
        for (Player base : spin.squad().roster) {
            if (taken.contains(base.id())) continue;
            Player active = service.active(run, base);
            for (String pos : open) {
                if (active.canPlay(pos)) { service.draft(runId, pos, base.id()); return base.id(); }
            }
        }
        throw new IllegalStateException("no draftable player in spun squad (safeguard should prevent this)");
    }

    private void completeDraft(String runId) {
        for (int i = 0; i < 60 && !service.get(runId).isComplete(); i++) draftOne(runId);
        assertTrue(service.get(runId).isComplete(), "draft should fill all 11 slots");
    }

    @Test
    void fullLoopCreateDraftSimulate() {
        var run = service.create(cmd(101L, ShowRatings.ON, PlayerRatings.CAREER));
        completeDraft(run.getId());

        var res = service.simulate(run.getId());
        assertEquals(20, res.table().size(), "league should have 20 teams");
        assertEquals(38, res.userStanding().won + res.userStanding().drawn + res.userStanding().lost,
            "user should play 38 games");
        assertEquals(RunStatus.SIMULATED, service.get(run.getId()).getStatus());
    }

    @Test
    void naturalPositionOnly() {
        var run = service.create(cmd(7L, ShowRatings.ON, PlayerRatings.CAREER));
        var spin = service.spin(run.getId());
        Player outfielder = spin.squad().roster.stream().filter(p -> !p.canPlay("GK")).findFirst().orElseThrow();
        assertThrows(ResponseStatusException.class,
            () -> service.draft(run.getId(), "GK", outfielder.id()),
            "an outfielder must not be draftable into GK");
    }

    @Test
    void rerollBudgetDecrementsAndExhausts() {
        var run = service.create(cmd(9L, ShowRatings.ON, PlayerRatings.CAREER)); // NORMAL = 1 reroll
        service.spin(run.getId());                                  // first spin of the turn — free
        assertEquals(1, service.get(run.getId()).getRerollsRemaining());
        service.spin(run.getId());                                  // re-spin — costs the reroll
        assertEquals(0, service.get(run.getId()).getRerollsRemaining());
        assertThrows(ResponseStatusException.class, () -> service.spin(run.getId()), "no rerolls left");
    }

    @Test
    void primeSubstitutionAppliesCareerBest() {
        var run = service.create(cmd(3L, ShowRatings.ON, PlayerRatings.PRIME));
        var spin = service.spin(run.getId());
        List<String> open = openPositions(run);
        for (Player base : spin.squad().roster) {
            Player active = service.active(run, base);
            String pos = open.stream().filter(active::canPlay).findFirst().orElse(null);
            if (pos == null) continue;
            service.draft(run.getId(), pos, base.id());
            var filled = service.get(run.getId()).getSlots().stream()
                .filter(s -> s.isFilled() && s.getSofifaId() == base.id()).findFirst().orElseThrow();
            assertEquals(catalog.primeIndex().prime(base).overall(), filled.getOverall(),
                "Prime mode stores the career-best overall");
            return;
        }
        fail("no eligible squad player to draft");
    }

    @Test
    void sameSeedAndPicksReplaysIdentically() {
        var a = service.create(cmd(2024L, ShowRatings.ON, PlayerRatings.CAREER));
        var b = service.create(cmd(2024L, ShowRatings.ON, PlayerRatings.CAREER));
        completeDraft(a.getId());
        completeDraft(b.getId());
        var ra = service.simulate(a.getId());
        var rb = service.simulate(b.getId());
        assertEquals(ra.userStanding().points(), rb.userStanding().points(), "same seed + picks ⇒ same points");
        assertEquals(ra.userPosition(), rb.userPosition(), "same seed + picks ⇒ same finish");
    }

    @Test
    void previewReturnsLeagueAndLeagueAwareProjection() {
        var run = service.create(cmd(55L, ShowRatings.ON, PlayerRatings.CAREER));
        completeDraft(run.getId());
        var p = service.preview(run.getId());
        assertEquals(19, p.league().size(), "preview should field the 19 opponents");
        assertTrue(p.projection().expectedPoints() > 0 && p.projection().expectedPoints() <= 104);
        assertTrue(p.leagueMean() > 60 && p.leagueMean() < 95, "league mean in a sane band, was " + p.leagueMean());
        // Each opponent carries a full XI for inspection.
        assertTrue(p.league().stream().allMatch(t -> t.slots.size() == 11));
    }

    @Test
    void draftDeclassifiesTheSpunSquadWithTrueId() {
        var run = service.create(cmd(7L, ShowRatings.SCOUT, PlayerRatings.CAREER));
        var spin = service.spin(run.getId());
        List<String> open = openPositions(run);
        for (Player base : spin.squad().roster) {
            Player active = service.active(run, base);
            String pos = open.stream().filter(active::canPlay).findFirst().orElse(null);
            if (pos == null) continue;
            var res = service.draft(run.getId(), pos, base.id());
            assertEquals(base.id(), res.draftedId());
            assertEquals(spin.squad().roster.size(), res.squad().roster.size(), "the whole squad is declassified");
            return;
        }
        fail("no eligible squad player to draft");
    }

    @Test
    void seasonViewCarriesEveryTeamWithStatsAndProjectedPos() {
        var run = service.create(cmd(99L, ShowRatings.ON, PlayerRatings.CAREER));
        completeDraft(run.getId());
        var view = controller.simulate(run.getId());
        assertEquals(20, view.table().size());
        for (var row : view.table()) {
            assertEquals(11, row.players().size(), "each team carries its full XI");
            assertNotNull(row.formation());
            assertTrue(row.projectedPos() >= 1 && row.projectedPos() <= 20, "projected pos in range");
        }
        int totalGoals = view.table().stream().flatMap(t -> t.players().stream()).mapToInt(p -> p.goals()).sum();
        assertTrue(totalGoals > 100, "per-player goal stats attached league-wide, was " + totalGoals);
    }

    @Test
    void preSeasonAndDebriefProjectionsAgree() {
        var run = service.create(cmd(2025L, ShowRatings.ON, PlayerRatings.CAREER));
        completeDraft(run.getId());
        int presim = service.preview(run.getId()).projection().expectedPoints();
        var view = controller.simulate(run.getId());
        assertEquals(presim, view.projection().expectedPoints(), "debrief projection must match the pre-sim one");
        var you = view.table().stream().filter(SeasonViewMapper.TeamRow::you).findFirst().orElseThrow();
        assertEquals(presim, you.projectedPoints(), "your row's projected points must match the projection");
    }

    @Test
    void scoutModeNeverExposesTrueOverall() {
        var state = controller.create(new DraftRunController.CreateRunRequest(
            "4-3-3", Difficulty.NORMAL, ShowRatings.SCOUT, DraftMode.SQUAD_FIRST, PlayerRatings.CAREER,
            LeagueScope.WORLD, null, null, null, 11L));
        var spin = controller.spin(state.runId());
        assertFalse(spin.squad().isEmpty());
        for (var pv : spin.squad()) {
            assertNull(pv.rating().overall(), "SCOUT payload must omit the true overall");
            assertNotNull(pv.rating().low());
            assertNotNull(pv.rating().high());
            assertTrue(pv.rating().low() <= pv.rating().high());
        }
        // Strength is shown in every mode now (drafted players are revealed), but empty before any pick.
        assertNull(state.strength().overall(), "no team strength before drafting anyone");
    }

    @Test
    void draftedPlayersAreRevealedOnThePitch() {
        var run = service.create(cmd(31L, ShowRatings.SCOUT, PlayerRatings.CAREER));
        draftOne(run.getId());
        var state = controller.state(run.getId());
        var filled = state.slots().stream().filter(DraftRunController.SlotView::filled).findFirst().orElseThrow();
        assertNotNull(filled.rating().overall(), "a drafted player's true rating is revealed even in Scout mode");
    }
}
