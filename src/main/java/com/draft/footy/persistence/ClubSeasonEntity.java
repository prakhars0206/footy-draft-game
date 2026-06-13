package com.draft.footy.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Relational mirror of the engine's {@code ClubSeason} — one club in one FIFA edition, owning its roster.
 * Lives in the persistence layer so the pure engine ({@code com.draft.footy}) stays framework-free.
 */
@Entity
@Table(name = "club_season",
       indexes = { @Index(name = "ix_club_season_league", columnList = "league"),
                   @Index(name = "ix_club_season_league_season", columnList = "league,season") })
public class ClubSeasonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String club;
    private String season;
    private String league;

    @OneToMany(mappedBy = "clubSeason", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerEntity> players = new ArrayList<>();

    protected ClubSeasonEntity() { } // JPA

    public ClubSeasonEntity(String club, String season, String league) {
        this.club = club; this.season = season; this.league = league;
    }

    public void addPlayer(PlayerEntity p) { players.add(p); p.setClubSeason(this); }

    public Long getId() { return id; }
    public String getClub() { return club; }
    public String getSeason() { return season; }
    public String getLeague() { return league; }
    public List<PlayerEntity> getPlayers() { return players; }
}
