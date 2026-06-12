# footy-draft — backend (Phase 1)

A from-scratch football draft + season-simulation engine, inspired by the 38-0 concept. Spring Boot REST API + a pure-Java simulation core. See `DESIGN_SPEC.md` for the full design.

## What's built (Phase 1)

The **simulation engine** — the core, most-tested, cert-relevant piece — is complete and validated:

- **Data ingestion** (`FifaDataLoader`) — parses the FIFA dataset, filters to the top-5 leagues, groups into club-seasons. Verified on `players_22.csv` (~2,900 top-5 players).
- **Strength model** (`ClubSeason`, `Xi`) — the single *club-season → strength* function (best XI by natural positions; attack/midfield/defence/GK sub-scores).
- **Opponent pyramid** (`OpponentPyramid`) — 19 opponents sampled by strength tier with bounded randomization, deduped, summing to exactly 19.
- **Match engine** (`MatchEngine`) — Poisson scoreline model on a **seeded RNG**, with goal/assist attribution and clean sheets.
- **Season simulator** (`SeasonSimulator`) — full 380-game round-robin with **league-wide** stat tracking (every player on every team), final table, Golden Boot / assists / clean-sheet leaders, streaks, biggest win.
- **Projection** (`Projection`) — Layer-1 "bookies" expected-points + finish odds.
- **REST layer** (`api/`) — `GET /api/season/demo` wraps the engine and returns a season as JSON.

## Get the data

```bash
# one-time: pull the verified FIFA 22 dataset into the project root
curl -L -o players_22.csv \
  https://raw.githubusercontent.com/abineshta/FIFA-22-complete-player-dataset-EDA/main/players_22.csv
```

## Run the engine proof (no Maven needed)

```bash
# from the project root, with players_22.csv present
javac -d out src/main/java/com/draft/footy/*.java
java -cp out com.draft.footy.Demo
```

This prints an example season (table, league-wide Golden Boot race, your record vs projection) and a calibration sweep.

## Run the full Spring Boot app (needs Maven + internet for dependencies)

```bash
mvn spring-boot:run
# then:
curl "http://localhost:8080/api/season/demo?overall=90&formation=4-3-3&seed=42"
```

## Run the tests

```bash
mvn test
```

`EngineTest` encodes the validated invariants: seeded reproducibility, monotonic calibration (stronger → more points), a 90-rated team landing near its ~95-point projection, and structural sanity (20 teams, 38 games).

## Calibration status (validated)

| Overall | Avg points (400 seasons) | Layer-1 projection |
|---|---|---|
| 75 | ~39 | 50 |
| 80 | ~58 | 65 |
| 83 | ~70 | 74 |
| 86 | ~79 | 83 |
| 89 | ~91 | 92 |
| 90 | ~93 | 95 |

The **top end is locked onto the projection** (the spec's anchor). The lower end runs a touch under projection because single-season FIFA-22 opponents skew strong relative to the absolute curve — expected to align once multi-season data adds a fuller spread. **38-0 is ~0%** on single-season data because you can't assemble a true 92+ all-time XI from one edition; multi-season unlocks the rare-but-real perfect season.

Tuning constants live at the top of `MatchEngine` (`BASE_GOALS`, `SCALE`, `HOME_ADV`, `MAX_LAMBDA`).

## Next (per DESIGN_SPEC)

- **Phase 1b:** multi-season ingest (FIFA 15–23 + column-normalization map); JPA + H2 seeding; Dixon-Coles draw correction.
- **Phase 2:** stateful draft flow (`DraftRun` resource: spin / draft / simulate); React scout-dossier frontend; Scout (fuzzy) ratings mode.
- **Phase 3:** AI legends pack + async flavor text.
