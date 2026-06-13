# CLAUDE.md — footy-draft

Context for AI sessions working on this repo. **`DESIGN_SPEC.md` is the source of truth** for design; this file is the quick-start + invariants + tuning map.

## What this is

A from-scratch football draft + season-simulation game (personal/educational; inspired by the 38-0 concept, original code/UI). Spin → land on a real club-season → draft players into a formation → simulate a 38-game season chasing an unbeaten run. Spring Boot REST backend + (Phase 2) React frontend.

## Build / run

```bash
# data: the multi-edition combined export (FIFA 15–23, sofifa schema) lives at data/male_players_all.csv
# (stefanoleone992 "male_players_all"; Kaggle-only, ~91MB, git-ignored). The schema-flexible loader also
# still reads a legacy single-season file, e.g.:
#   curl -L -o players_22.csv \
#     https://raw.githubusercontent.com/abineshta/FIFA-22-complete-player-dataset-EDA/main/players_22.csv

mvn spring-boot:run                                   # API on :8080 (loads data/male_players_all.csv)
curl "http://localhost:8080/api/season/demo?overall=90&formation=4-3-3&seed=42"
curl "http://localhost:8080/api/season/demo?overall=90&formation=4-3-3&seed=42&prime=true"  # Prime Mode (career-best)
mvn test                                              # engine invariants

# engine-only proof, no Maven (defaults to data/male_players_all.csv):
javac -d out src/main/java/com/draft/footy/*.java && java -cp out com.draft.footy.Demo
```

## Architecture

- `com.draft.footy` — pure-Java engine, **no Spring imports** (keeps it unit-testable and portable):
    - `FifaDataLoader` — CSV → top-5-league `ClubSeason`s. **Schema-flexible + multi-edition**: column-alias resolver (`player_id`|`sofifa_id`, `club_name`|`club`, …); top-5 filter prefers the stable numeric `league_id` (13/53/31/19/16) and falls back to a name-alias set; reads a per-row `fifa_version` so the combined export yields one `ClubSeason` per (club, edition). `loadAllSeasons(path)` for the multi-edition file; `loadTop5(path, edition)` is the single-season wrapper.
    - `PrimeIndex` — career-best ("Prime Mode") lookup: groups by `player_id`, keeps the peak-`overall` row (rating + positions from the **same** edition). Applied to the **user XI only** (invariant 6); a no-op on single-edition data.
    - `ClubSeason` / `Xi` — `optimalXi()` fields a club's best-fit XI: tries all 7 formations and picks the one with the most **natural-position** fits (overall as tiebreak), cached. `buildXi(formation)` fills a single formation, with fallbacks that keep keepers out of outfield slots and vice versa. `Xi` carries attack/mid/def/gk sub-scores.
    - `OpponentPyramid` — 19 opponents sampled by **`optimalStrength()`** tier (no fixed formation), bounded randomization, deduped, sums to 19. Each opponent fields its own best-fit shape.
    - `MatchEngine` — seeded-RNG Poisson scoreline; scorer/assist chosen by **position weight (`ScoringWeights`) × (overall/100)²**.
    - `ScoringWeights` — per-position goal/assist propensity. **Attribution only — does NOT affect scorelines or points**, so tune freely against the leaderboards.
    - `SeasonSimulator` — 380-game round-robin, **league-wide** stat ledger keyed per (team, player), table, awards.
    - `Projection` — Layer-1 "bookies" expected points + finish odds.
- `com.draft.footy.persistence` — JPA layer (cert practice): relational `ClubSeasonEntity` ↔ `PlayerEntity` (positions in an ordered join table), `ClubSeasonRepository` (derived `findByLeague`/`findByLeagueAndSeason` + a `findAllWithPlayers` fetch-join), `EngineMapper` (entity ⇄ engine record, both ways), `DataSeeder` (`@PostConstruct`: CSV → entities → H2). Keeps JPA out of the pure engine.
- `com.draft.footy.api` — Spring layer (`@RestController` / `@Service`) wrapping the engine. `SimulationService` `@DependsOn("dataSeeder")` and loads the engine pool from the repo in `@PostConstruct` (race-free: both run during context refresh, before the web server serves). `GET /api/season/demo` returns a season as JSON, including each team's lineup; `&prime=true` applies career-best ratings to the user XI.

## Invariants — do not break these

1. **Single strength function**: club-season → `optimalStrength()` (overall of its best-fit XI). Used for the user XI, Classic opponents, and World-draft opponents alike. No bespoke per-opponent ratings.
2. **Seeded RNG threaded everywhere** → a season is fully reproducible (tests + future share/replay depend on it). Same seed ⇒ identical result.
3. **League-wide stats**, keyed **per (team, player)**, never globally by player id (the same real player can be on two teams). Attribution is weighted by position × rating², which only redistributes goals/assists — it never changes points.
4. **Natural positions** for placement; fallbacks never field a keeper outfield or an outfielder in goal.
5. **Dual-layer**: Layer 1 (projection, no opponents) vs Layer 2 (actual sim). The gap is the drama (over/underperformed).
6. Opponents are rated/attributed as **their sampled season**, independent of the user's Prime/Career toggle.

## Tuning map — two separate concerns, two files

**Match volume & outcomes (COUPLED to points!) → `MatchEngine` constants.**
Model: `lambda = min(BASE_GOALS * exp((attack - defence + home) / SCALE), MAX_LAMBDA)`; the scoreline is then drawn from the **Dixon-Coles** joint distribution (`sampleScore`) rather than two independent Poissons.
- `BASE_GOALS` (1.25) — global goal-volume multiplier; lower = fewer goals everywhere, uniformly.
- `SCALE` (14.5) — larger = strength gaps matter less (fewer blowouts); also a strong **points** lever — raising it lowers top-team points. *Inversely affects the draw rate* (more decisive games = fewer draws).
- `HOME_ADV` (4.5) — shifts goals/edge from away to home.
- `MAX_LAMBDA` (4.5) — caps per-team expected goals; lowering trims blowouts with little points impact (safest lever).
- `RHO` (-0.11) — Dixon-Coles low-score correction; more negative ⇒ more 0-0/1-1 draws. Lifts the draw rate fairly independently of the points spread, so it's the lever for draws after `SCALE` sets the spread. (Slightly coupled to points: more draws cost favourites.)
- **After ANY change here, re-run the calibration sweep** (`java -cp out com.draft.footy.Demo players_22.csv` — the locked single-season reference): the 89/90 rows must stay near ~92/~94, the curve must stay monotonic, and the league draw rate near ~24%.

**Who on a team scores (NO points impact) → `ScoringWeights`.**
Per-position goal/assist weights. Validate by eyeballing the leaderboards: top scorer ~28–34, creators (wingers/CAMs) lead assists, no CDM/CB topping either.

## Calibration (validated)

On the locked **players_22** reference: 89 → ~92 pts (projected 92), 90 → ~94 (projected 95) — top end locked onto the projection; monotonic; **league draw rate ~24%** (Dixon-Coles lifts it from ~17% pure-Poisson into a realistic band). Lower rows run under projection (the opponent pyramid is the same tough league regardless of the user). On the **multi-season** pool the deeper era spread runs a touch under (90 → ~92, draws ~18%). **38-0 ~0% on single-season FIFA-22** — you can't build a true 92+ all-time XI from one edition; multi-season unlocks the rare-but-real perfect season.

## Status & roadmap

- **Phase 1 (done):** engine + Spring REST wrapper + tests; runs end-to-end (Spring verified locally). Refinements landed: per-opponent best-fit formation (`optimalXi`), and position- + rating-weighted attribution (`ScoringWeights`).
- **Phase 1b (done):**
    - ✅ **Multi-season ingest** — combined FIFA 15–23 export (`data/male_players_all.csv`) → 880 club-seasons across 9 editions via a schema-flexible, `league_id`-keyed loader; **Prime Mode** career-best snapshots (`PrimeIndex`), exposed on the demo endpoint via `&prime=true`. *(In practice the normalization that mattered was `player_id`↔`sofifa_id` + per-edition league-name drift, solved by the stable numeric `league_id` — not `club`↔`club_name`.)*
    - ✅ **JPA + H2 seeding** (cert practice, not perf) — relational `ClubSeasonEntity`↔`PlayerEntity` + ordered positions join table; `DataSeeder` seeds H2 from the CSV at startup, `SimulationService` reads it back via the repository. H2 console at `/h2-console` (`jdbc:h2:mem:footy`). See `com.draft.footy.persistence`.
    - ✅ **Dixon-Coles draw correction** — `MatchEngine.sampleScore` draws correlated scorelines from the DC joint distribution (`RHO`), lifting the draw rate from ~17% (pure Poisson) to ~24%. Recalibrated (`SCALE` 16→14.5, `RHO` -0.11) so players_22 holds 89→~92 / 90→~94. See the tuning map above.
    - ℹ️ **Calibration:** locked on the single-season **players_22** reference (`Demo players_22.csv`). The deeper multi-era pool runs a touch under (90 → ~92, draws ~18%) — expected, the opponent pyramid is the same tough league regardless of the user. `EngineTest` calibration assertions pin to `players_22.csv`.
- **Phase 2:** stateful `DraftRun` resource (`POST /api/runs`, `/spin`, `/draft`, `/simulate`); React scout-dossier frontend; **Scout** fuzzy-ratings mode (strip true_rating in the DTO; derive the asymmetric band deterministically from playerId+runSeed).
- **Phase 3:** AI legends pack (icons retired pre-2014, absent from FIFA data); async LLM flavor text (never block the results endpoint).

## Conventions

- Minimal dependencies; records for data carriers; engine free of framework code.
- Tests assert determinism + monotonic calibration + sane ranges (see `EngineTest`).
- The user's two headline additions vs the original: all top-5 leagues, and Scout fuzzy-ratings mode.