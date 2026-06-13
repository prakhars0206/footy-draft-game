package com.draft.footy.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Relational mirror of the engine's {@code Player}. The surrogate {@code id} is the PK; {@code sofifaId} is the
 * stable cross-edition player key (the same real player on two club-seasons is two rows, distinct PKs — matching
 * the engine's per-(team, player) stat keying, invariant 3). Positions keep their order (primary = first).
 */
@Entity
@Table(name = "player", indexes = { @Index(name = "ix_player_sofifa", columnList = "sofifaId") })
public class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int sofifaId;
    private String name;
    private String nation;
    private int overall;
    private String dob;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_position", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "slot_order")
    @Column(name = "position")
    private List<String> positions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_season_id")
    private ClubSeasonEntity clubSeason;

    protected PlayerEntity() { } // JPA

    public PlayerEntity(int sofifaId, String name, String nation, List<String> positions, int overall, String dob) {
        this.sofifaId = sofifaId; this.name = name; this.nation = nation;
        this.positions = new ArrayList<>(positions); this.overall = overall; this.dob = dob;
    }

    void setClubSeason(ClubSeasonEntity cs) { this.clubSeason = cs; }

    public int getSofifaId() { return sofifaId; }
    public String getName() { return name; }
    public String getNation() { return nation; }
    public int getOverall() { return overall; }
    public String getDob() { return dob; }
    public List<String> getPositions() { return positions; }
}
