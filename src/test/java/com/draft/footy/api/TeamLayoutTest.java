package com.draft.footy.api;

import com.draft.footy.Formation;
import com.draft.footy.Player;
import com.draft.footy.Xi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure unit test: the formation-order helper re-pairs an XI's slots onto its formation's slot order. */
class TeamLayoutTest {

    private static Player player(String pos) {
        return new Player(pos.hashCode(), pos, "X", List.of(pos), 80, "2000-01-01");
    }

    @Test
    void ordersEveryFormationBackIntoFormationSlotOrder() {
        for (Formation f : Formation.values()) {
            Xi xi = new Xi("Test FC 2020/21 (" + f.label() + ")");
            List<String> shuffled = new ArrayList<>(f.slots());
            Collections.reverse(shuffled); // build the XI in a non-formation order
            for (String pos : shuffled) xi.add(pos, player(pos));

            var ord = TeamLayout.inFormationOrder(xi);
            assertEquals(f.label(), ord.formation(), "formation parsed from the label");
            assertEquals(f.slots(), ord.slots().stream().map(Xi.Slot::position).toList(),
                f.label() + " should be re-ordered to its formation slot order");
        }
    }
}
