package com.draft.footy;

import java.util.List;

/** A single player-season row from the data. positions is the ordered list of slots they can fill (first = primary). */
record Player(int id, String name, String nation, List<String> positions, int overall) {
    String primaryPosition() { return positions.isEmpty() ? "CM" : positions.get(0); }
    Line primaryLine() { return Line.of(primaryPosition()); }
    boolean canPlay(String position) { return positions.contains(position); }
}
