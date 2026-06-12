package com.draft.footy;

import java.util.List;

/** The seven supported formations. Each is just an ordered list of position slots (data-driven). */
public enum Formation {
    F_4_3_3   ("4-3-3",   List.of("LW","ST","RW","CM","CM","CDM","LB","CB","CB","RB","GK")),
    F_4_4_2   ("4-4-2",   List.of("ST","ST","LM","CDM","CM","RM","LB","CB","CB","RB","GK")),
    F_4_2_3_1 ("4-2-3-1", List.of("ST","CAM","LM","RM","CDM","CDM","LB","CB","CB","RB","GK")),
    F_4_5_1   ("4-5-1",   List.of("ST","CAM","LM","RM","CM","CDM","LB","CB","CB","RB","GK")),
    F_3_4_3   ("3-4-3",   List.of("LW","ST","RW","LM","CM","CM","RM","CB","CB","CB","GK")),
    F_3_5_2   ("3-5-2",   List.of("ST","ST","CAM","LM","RM","CM","CDM","CB","CB","CB","GK")),
    F_5_4_1   ("5-4-1",   List.of("ST","LM","CM","CM","RM","LWB","CB","CB","CB","RWB","GK"));

    private final String label;
    private final List<String> slots;
    Formation(String label, List<String> slots) { this.label = label; this.slots = slots; }
    public String label() { return label; }
    public List<String> slots() { return slots; }
}
