package com.draft.footy;

import java.util.List;

/** A single player-season row from the data. positions is the ordered list of slots they can fill (first = primary). */
public record Player(int id, String name, String nation, List<String> positions, int overall) {
    public String primaryPosition() { return positions.isEmpty() ? "CM" : positions.get(0); }
    public Line primaryLine() { return Line.of(primaryPosition()); }
    public boolean canPlay(String position) { return positions.contains(position); }
}
