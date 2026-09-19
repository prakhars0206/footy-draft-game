package com.draft.footy.api;

import com.draft.footy.Formation;
import org.springframework.web.bind.annotation.*;

/**
 * Stateless demo endpoint (auto-builds an XI of a target overall). The interactive flow lives in
 * {@link DraftRunController} (POST /api/runs ...). Both render results via {@link SeasonViewMapper}.
 */
@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) { this.service = service; }

    @GetMapping("/season/demo")
    public SeasonViewMapper.SeasonView demoSeason(
            @RequestParam(defaultValue = "90") int overall,
            @RequestParam(defaultValue = "4-3-3") String formation,
            @RequestParam(defaultValue = "42") long seed,
            @RequestParam(defaultValue = "false") boolean prime) {

        Formation f = parseFormation(formation);
        return SeasonViewMapper.toView(service.simulateDemo(overall, f, seed, prime));
    }

    private Formation parseFormation(String label) {
        for (Formation f : Formation.values()) if (f.label().equals(label)) return f;
        return Formation.F_4_3_3;
    }
}
