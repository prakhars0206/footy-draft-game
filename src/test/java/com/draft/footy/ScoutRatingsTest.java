package com.draft.footy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Scout band invariants (DESIGN_SPEC §8/§16): deterministic, true value inside but off-centre, width varies. */
class ScoutRatingsTest {

    @Test
    void deterministicForSameIdAndSeed() {
        var a = ScoutRatings.band(209331, 42L, 90);
        var b = ScoutRatings.band(209331, 42L, 90);
        assertEquals(a, b, "same (id, seed) must yield the same band");
    }

    @Test
    void trueValueAlwaysInsideBand() {
        for (int ovr = 50; ovr <= 99; ovr++) {
            var band = ScoutRatings.band(ovr * 7 + 1, 1234L, ovr);
            assertTrue(band.low() <= ovr && ovr <= band.high(),
                "true " + ovr + " must lie in [" + band.low() + "," + band.high() + "]");
            assertTrue(band.low() >= 1 && band.high() <= 99, "band must stay in plausible bounds");
        }
    }

    @Test
    void bandIsAsymmetricAndWidthVaries() {
        boolean offCentreSeen = false;
        var widths = new java.util.HashSet<Integer>();
        for (int id = 1; id <= 400; id++) {
            var band = ScoutRatings.band(id, 99L, 85);
            int below = 85 - band.low(), above = band.high() - 85;
            if (below != above) offCentreSeen = true;            // not centred on the true value
            widths.add(band.high() - band.low());
        }
        assertTrue(offCentreSeen, "ranges should not always be centred on the true rating");
        assertTrue(widths.size() > 3, "band width should vary across players");
    }

    @Test
    void differentSeedsGiveDifferentIntel() {
        int differ = 0;
        for (int id = 1; id <= 50; id++)
            if (!ScoutRatings.band(id, 1L, 88).equals(ScoutRatings.band(id, 2L, 88))) differ++;
        assertTrue(differ > 25, "a different run seed should reshuffle most bands, was " + differ + "/50");
    }
}
