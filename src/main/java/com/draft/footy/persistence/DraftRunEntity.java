package com.draft.footy.persistence;

import com.draft.footy.*;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A draft run in progress (DESIGN_SPEC §16) — the server-authoritative resource keyed by {@code id}. Holds the
 * run config, the RNG {@code seed} (reproducibility + Scout-band derivation), reroll budget, the current spin,
 * and the 11 formation slots. The client holds only the id and drives it via the {@code /api/runs/{id}/*} endpoints.
 */
@Entity
@Table(name = "draft_run")
public class DraftRunEntity {

    @Id
    private String id;

    private long seed;

    // --- config (chosen at create, DESIGN_SPEC §5A) ---
    private String formation;                    // Formation label, e.g. "4-3-3"
    @Enumerated(EnumType.STRING) private Difficulty difficulty;
    @Enumerated(EnumType.STRING) private ShowRatings showRatings;
    @Enumerated(EnumType.STRING) private DraftMode draftMode;
    @Enumerated(EnumType.STRING) private PlayerRatings playerRatings;
    @Enumerated(EnumType.STRING) private LeagueScope leagueScope;
    private String classicLeague;                // only for CLASSIC scope (nullable)
    private Integer eraFrom;                      // season start-year bounds (nullable)
    private Integer eraTo;

    // --- mutable run state ---
    private int rerollsRemaining;
    private int spinCount;                        // drives deterministic spins (seed + spinCount)
    @Enumerated(EnumType.STRING) private RunStatus status = RunStatus.DRAFTING;

    // current spin awaiting a pick (Squad First): the landed tier + the 2–3 clubs offered (empty when none up)
    private String spinTier;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "draft_run_spin_offer", joinColumns = @JoinColumn(name = "run_id"))
    private List<SpinOffer> spinOffers = new ArrayList<>();

    /** One club-season offered by the current spin (the user picks one to draft from). */
    @Embeddable
    public static class SpinOffer {
        private String club;
        private String season;
        protected SpinOffer() { }
        public SpinOffer(String club, String season) { this.club = club; this.season = season; }
        public String getClub() { return club; }
        public String getSeason() { return season; }
    }

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("slotIndex ASC")
    private List<DraftSlotEntity> slots = new ArrayList<>();

    protected DraftRunEntity() { } // JPA

    public DraftRunEntity(String id, long seed, Formation formation, Difficulty difficulty, ShowRatings showRatings,
                          DraftMode draftMode, PlayerRatings playerRatings, LeagueScope leagueScope,
                          String classicLeague, Integer eraFrom, Integer eraTo) {
        this.id = id; this.seed = seed; this.formation = formation.label();
        this.difficulty = difficulty; this.showRatings = showRatings; this.draftMode = draftMode;
        this.playerRatings = playerRatings; this.leagueScope = leagueScope;
        this.classicLeague = classicLeague; this.eraFrom = eraFrom; this.eraTo = eraTo;
        this.rerollsRemaining = difficulty.rerolls();
        List<String> formSlots = formation.slots();
        for (int i = 0; i < formSlots.size(); i++) addSlot(new DraftSlotEntity(i, formSlots.get(i)));
    }

    public void addSlot(DraftSlotEntity s) { slots.add(s); s.setRun(this); }

    public boolean hasSpin() { return spinOffers != null && !spinOffers.isEmpty(); }
    public void setSpin(String tier, List<ClubSeason> clubs) {
        this.spinTier = tier;
        this.spinOffers = new ArrayList<>();
        for (ClubSeason cs : clubs) this.spinOffers.add(new SpinOffer(cs.club, cs.season));
    }
    public void clearSpin() { this.spinTier = null; this.spinOffers.clear(); }

    public List<DraftSlotEntity> openSlots() {
        return slots.stream().filter(s -> !s.isFilled()).toList();
    }
    public boolean isComplete() { return slots.stream().allMatch(DraftSlotEntity::isFilled); }
    public int nextSpinIndex() { return spinCount++; }

    public String getId() { return id; }
    public long getSeed() { return seed; }
    public Formation formationEnum() {
        for (Formation f : Formation.values()) if (f.label().equals(formation)) return f;
        throw new IllegalStateException("unknown formation: " + formation);
    }
    public String getFormation() { return formation; }
    public Difficulty getDifficulty() { return difficulty; }
    public ShowRatings getShowRatings() { return showRatings; }
    public DraftMode getDraftMode() { return draftMode; }
    public PlayerRatings getPlayerRatings() { return playerRatings; }
    public LeagueScope getLeagueScope() { return leagueScope; }
    public String getClassicLeague() { return classicLeague; }
    public Integer getEraFrom() { return eraFrom; }
    public Integer getEraTo() { return eraTo; }
    public int getRerollsRemaining() { return rerollsRemaining; }
    public void setRerollsRemaining(int n) { this.rerollsRemaining = n; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public String getSpinTier() { return spinTier; }
    public List<SpinOffer> getSpinOffers() { return spinOffers; }
    public List<DraftSlotEntity> getSlots() { return slots; }
}
