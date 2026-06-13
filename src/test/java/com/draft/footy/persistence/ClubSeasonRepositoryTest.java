package com.draft.footy.persistence;

import com.draft.footy.ClubSeason;
import com.draft.footy.Player;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cert-practice coverage for the JPA layer: the relational ClubSeason↔Player mapping round-trips through H2,
 * positions keep their order (primary = first), the same sofifaId can live on two club-seasons, and the
 * derived queries resolve. Self-contained config so it discovers the persistence package directly.
 */
@DataJpaTest
@EntityScan(basePackageClasses = ClubSeasonEntity.class)
@EnableJpaRepositories(basePackageClasses = ClubSeasonRepository.class)
class ClubSeasonRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig { }

    @Autowired ClubSeasonRepository repo;

    private ClubSeason sampleSeason(String club, String season, int salahOverall) {
        return new ClubSeason(club, season, "Premier League", List.of(
            new Player(209331, "M. Salah", "Egypt", List.of("RW", "ST"), salahOverall, "1992-06-15"),
            new Player(155862, "Sergio Ramos", "Spain", List.of("CB"), 88, "1986-03-30")));
    }

    @Test
    void roundTripsRelationalGraphAndPreservesPositionOrder() {
        repo.save(EngineMapper.toEntity(sampleSeason("Liverpool", "2021/22", 90)));

        ClubSeasonEntity e = repo.findByLeagueAndSeason("Premier League", "2021/22").get(0);
        assertEquals("Liverpool", e.getClub());
        assertEquals(2, e.getPlayers().size());

        ClubSeason back = EngineMapper.toEngine(e);
        Player salah = back.roster.stream().filter(p -> p.id() == 209331).findFirst().orElseThrow();
        assertEquals(90, salah.overall());
        assertEquals(List.of("RW", "ST"), salah.positions(), "position order (primary first) must survive the join table");
        assertEquals("RW", salah.primaryPosition());
    }

    @Test
    void sameSofifaIdLivesOnTwoClubSeasons() {
        // Salah appears in two different editions — two PlayerEntity rows, same sofifaId (invariant 3).
        repo.save(EngineMapper.toEntity(sampleSeason("Liverpool", "2020/21", 90)));
        repo.save(EngineMapper.toEntity(sampleSeason("Liverpool", "2021/22", 89)));

        long salahRows = repo.findAllWithPlayers().stream()
            .flatMap(cs -> cs.getPlayers().stream())
            .filter(p -> p.getSofifaId() == 209331)
            .count();
        assertEquals(2, salahRows, "the same real player on two club-seasons must be two persisted rows");
    }

    @Test
    void derivedLeagueQueryFiltersByLeague() {
        repo.save(EngineMapper.toEntity(sampleSeason("Liverpool", "2021/22", 90)));
        assertEquals(1, repo.findByLeague("Premier League").size());
        assertTrue(repo.findByLeague("Serie A").isEmpty());
    }
}
