# CLAUDE.md — footy-draft

Context for AI sessions working on this repo. **`DESIGN_SPEC.md` is the source of truth** for design; this file is the quick-start + invariants + tuning map.

## What this is

A from-scratch football draft + season-simulation game (personal/educational; inspired by the 38-0 concept, original code/UI). Spin → land on a real club-season → draft players into a formation → simulate a 38-game season chasing an unbeaten run. Spring Boot REST backend + (Phase 2) React frontend.

## Build / run

```bash
# data (one-time): pull the FIFA 22 dataset into the project root
curl -L -o players_22.csv \
  https://raw.githubusercontent.com/abineshta/FIFA-22-complete-player-dataset-EDA/main/players_22.csv

mvn spring-boot:run                                   # API on :8080
curl "http://localhost:8080/api/season/demo?overall=90&formation=4-3-3&seed=42"
mvn test                                              # engine invariants

# engine-only proof, no Maven:
javac -d out src/main/java/com/draft/footy/*.java && java -cp out com.draft.footy.Demo
```

## Architecture

- `com.draft.footy` — pure-Java engine, **no Spring imports** (keeps it unit-testable and portable):
    - `FifaDataLoader` — CSV → top-5-league `ClubSeason`s.
    - `ClubSeason` / `Xi` — `optimalXi()` fields a club's best-fit XI: tries all 7 formations and picks the one with the most **natural-position** fits (overall as tiebreak), cached. `buildXi(formation)` fills a single formation, with fallbacks that keep keepers out of outfield slots and vice versa. `Xi` carries attack/mid/def/gk sub-scores.
    - `OpponentPyramid` — 19 opponents sampled by **`optimalStrength()`** tier (no fixed formation), bounded randomization, deduped, sums to 19. Each opponent fields its own best-fit shape.
    - `MatchEngine` — seeded-RNG Poisson scoreline; scorer/assist chosen by **position weight (`ScoringWeights`) × (overall/100)²**.
    - `ScoringWeights` — per-position goal/assist propensity. **Attribution only — does NOT affect scorelines or points**, so tune freely against the leaderboards.
    - `SeasonSimulator` — 380-game round-robin, **league-wide** stat ledger keyed per (team, player), table, awards.
    - `Projection` — Layer-1 "bookies" expected points + finish odds.
- `com.draft.footy.api` — Spring layer (`@RestController` / `@Service`) wrapping the engine. `GET /api/season/demo` returns a season as JSON, including each team's lineup.

## Invariants — do not break these

1. **Single strength function**: club-season → `optimalStrength()` (overall of its best-fit XI). Used for the user XI, Classic opponents, and World-draft opponents alike. No bespoke per-opponent ratings.
2. **Seeded RNG threaded everywhere** → a season is fully reproducible (tests + future share/replay depend on it). Same seed ⇒ identical result.
3. **League-wide stats**, keyed **per (team, player)**, never globally by player id (the same real player can be on two teams). Attribution is weighted by position × rating², which only redistributes goals/assists — it never changes points.
4. **Natural positions** for placement; fallbacks never field a keeper outfield or an outfielder in goal.
5. **Dual-layer**: Layer 1 (projection, no opponents) vs Layer 2 (actual sim). The gap is the drama (over/underperformed).
6. Opponents are rated/attributed as **their sampled season**, independent of the user's Prime/Career toggle.

## Tuning map — two separate concerns, two files

**Match volume & outcomes (COUPLED to points!) → `MatchEngine` constants.**
Model: `lambda = min(BASE_GOALS * exp((attack - defence + home) / SCALE), MAX_LAMBDA)`.
- `BASE_GOALS` (1.32) — global goal-volume multiplier; lower = fewer goals everywhere, uniformly.
- `SCALE` (15.5) — larger = strength gaps matter less (fewer blowouts); also a strong **points** lever — raising it lowers top-team points.
- `HOME_ADV` (4.5) — shifts goals/edge from away to home.
- `MAX_LAMBDA` (4.7) — caps per-team expected goals; lowering trims blowouts with little points impact (safest lever).
- **After ANY change here, re-run the calibration sweep** (`java -cp out com.draft.footy.Demo`): the 89/90 rows must stay near ~91/~94 and the curve must stay monotonic.

**Who on a team scores (NO points impact) → `ScoringWeights`.**
Per-position goal/assist weights. Validate by eyeballing the leaderboards: top scorer ~28–34, creators (wingers/CAMs) lead assists, no CDM/CB topping either.

## Calibration (validated)

89 → ~91 pts (projected 92), 90 → ~94 (projected 95) — top end locked onto the projection. Lower rows run under projection (the opponent pyramid is the same tough league regardless of the user). **38-0 ~0% on single-season FIFA-22** — you can't build a true 92+ all-time XI from one edition; multi-season unlocks the rare-but-real perfect season.

## Status & roadmap

- **Phase 1 (done):** engine + Spring REST wrapper + tests; runs end-to-end (Spring verified locally). Refinements landed: per-opponent best-fit formation (`optimalXi`), and position- + rating-weighted attribution (`ScoringWeights`).
- **Phase 1b:** multi-season ingest (FIFA 15–23 + column-normalization map for `club` vs `club_name`); JPA + H2 seeding (cert practice, not perf); Dixon-Coles draw correction.
- **Phase 2:** stateful `DraftRun` resource (`POST /api/runs`, `/spin`, `/draft`, `/simulate`); React scout-dossier frontend; **Scout** fuzzy-ratings mode (strip true_rating in the DTO; derive the asymmetric band deterministically from playerId+runSeed).
- **Phase 3:** AI legends pack (icons retired pre-2014, absent from FIFA data); async LLM flavor text (never block the results endpoint).

## Conventions

- Minimal dependencies; records for data carriers; engine free of framework code.
- Tests assert determinism + monotonic calibration + sane ranges (see `EngineTest`).
- The user's two headline additions vs the original: all top-5 leagues, and Scout fuzzy-ratings mode.