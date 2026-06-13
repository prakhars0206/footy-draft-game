package com.draft.footy.persistence;

import com.draft.footy.Player;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One formation slot within a {@link DraftRunEntity}. Created empty (just position + index); once drafted it
 * holds the picked player's full snapshot (after any Prime substitution), so simulation is self-contained and
 * doesn't have to re-resolve the player from the pool.
 */
@Entity
@Table(name = "draft_slot")
public class DraftSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private DraftRunEntity run;

    private int slotIndex;
    private String position;

    // Filled snapshot — all null until a player is drafted into this slot.
    private Integer sofifaId;
    private String name;
    private String nation;
    private Integer overall;
    private String dob;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "draft_slot_position", joinColumns = @JoinColumn(name = "slot_id"))
    @OrderColumn(name = "slot_order")
    @Column(name = "position")
    private List<String> playerPositions = new ArrayList<>();

    private String sourceClub;
    private String sourceSeason;

    protected DraftSlotEntity() { } // JPA

    public DraftSlotEntity(int slotIndex, String position) {
        this.slotIndex = slotIndex; this.position = position;
    }

    public boolean isFilled() { return sofifaId != null; }

    /** Record a drafted player (its rating/positions are the active snapshot — season or Prime) and its source. */
    public void fill(Player p, String sourceClub, String sourceSeason) {
        this.sofifaId = p.id(); this.name = p.name(); this.nation = p.nation();
        this.overall = p.overall(); this.dob = p.dob();
        this.playerPositions = new ArrayList<>(p.positions());
        this.sourceClub = sourceClub; this.sourceSeason = sourceSeason;
    }

    public void clear() {
        sofifaId = null; name = null; nation = null; overall = null; dob = null;
        playerPositions = new ArrayList<>(); sourceClub = null; sourceSeason = null;
    }

    /** Rebuild the engine Player from the stored snapshot (filled slots only). */
    public Player toPlayer() {
        return new Player(sofifaId, name, nation, new ArrayList<>(playerPositions), overall, dob);
    }

    void setRun(DraftRunEntity run) { this.run = run; }

    public int getSlotIndex() { return slotIndex; }
    public String getPosition() { return position; }
    public Integer getSofifaId() { return sofifaId; }
    public String getName() { return name; }
    public String getNation() { return nation; }
    public Integer getOverall() { return overall; }
    public List<String> getPlayerPositions() { return playerPositions; }
    public String getSourceClub() { return sourceClub; }
    public String getSourceSeason() { return sourceSeason; }
}
