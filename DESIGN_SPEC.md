# Football Draft Game — Design Spec

*A from-scratch, personal/educational reimplementation inspired by the 38-0 concept. Original code is not used; everything here is written fresh.*

---

## 1. Goals

- Rebuild the core 38-0 experience from scratch, with original code and a distinct UI.
- Add two headline features: **top-5 European leagues** (not just the Premier League) and a **fuzzy/"scout" ratings mode** (a middle ground between fully visible and fully hidden ratings).
- Use the build as **Spring Boot / Java practice**, plus a couple of **LLM-in-the-loop integration points**.
- Personal use only, not distributed.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Backend | **Java + Spring Boot REST API** | Owns player data, draft/spin logic, and the simulation engine: controllers, service layer, DTOs, DI, JPA if we persist runs. |
| Sim engine | Plain Java service class | Self-contained, unit-testable business logic — the most interesting and most testable piece. |
| Frontend | **React (separate SPA)** — *decided* | Consumes the API over HTTP. Chosen over Thymeleaf+HTMX deliberately: the decoupled REST API + SPA split is the more transferable enterprise architecture. |
| Data | FIFA/EA ratings (sofifa-lineage) + AI-generated legends | See §4. |
| AI hooks | Claude API | Legend-rating generation + dynamic flavor text. |

Note: a Spring Boot backend is *more* than a browser game strictly needs — that "overkill" is intentional, to practice the layered enterprise architecture used professionally.

---

## 3. Core Game Loop

1. **Spin** — lands on a real club + specific season.
2. **Draft** — pick one player from that squad, slot into the formation.
3. **Build XI** — repeat until all 11 positions are filled (limited re-rolls: e.g. one club swap, one era swap).
4. **Simulate** — run a full season; see the result.

---

## 4. Data Sourcing

Three tiers of coverage:

- **2015–2025 — clean bundle.** sofifa-lineage FIFA 15–25 career-mode dataset. Free, sourced, has rating + positions + club + **league** (all top-5 leagues native). This is v1.
- **2007–2014 — scrapable.** Older FIFA editions exist on sofifa (back to ~FC 07) but aren't bundled; would need scraping. Optional extension.
- **1992–2006 — the honest gap.** No clean rating database exists this far back. The original almost certainly **generated** these (hand-curated / AI). We'll do the same: use the Claude API to assign 0–99 ratings to notable legend club-seasons, output as structured JSON. Caveat: vibes-calibrated, not ground truth — fine for a draft game, and what the original did.

Sourcing strategy: **FIFA 15–25 for v1 → optionally scrape back to FC 07 → AI-generated "legends pack" as phase 2.**

### Verified source (de-risked)

The **stefanoleone992 "FIFA complete player dataset"** family (sofifa-scraped) is the anchor. Verified working:

- **Confirmed file:** `players_22.csv` — reachable at
  `https://raw.githubusercontent.com/abineshta/FIFA-22-complete-player-dataset-EDA/main/players_22.csv`
- **Columns we use:** `short_name`, `player_positions` (e.g. "ST, LW"), `overall`, `club_name`, `league_name`, `league_level`, `club_position`, `nationality_name`.
- **Coverage:** 19,240 players in FIFA 22; top-5 leagues all present (EPL 652, La Liga 633, Ligue 1 577, Serie A 563, Bundesliga 551 ≈ 2,900 top-5 players/season). Filter on `league_level == 1` + the five `league_name` values.
- **Each FIFA edition ≈ one season** (FIFA 22 ≈ 2021-22), so pulling `players_15` … `players_23` gives ~9 seasons → the "spin a club + season" mechanic falls out naturally.
- **Schema wrinkle:** FIFA 15–20 use older column names (`club`, `nationality` vs `club_name`, `nationality_name`). Ingest layer needs a small **column-normalization map** to merge old + new variants. Minor ETL, not a blocker.
- **v1 can ship on `players_22.csv` alone** and be fully playable; multi-season is enrichment.
- **No FUT ICONs/legends in this data (verified).** Searched the file directly — Beckham, Henry, Zidane, Maradona, Pelé, Cantona etc. are absent; it's the career-mode active roster per edition. *But* multi-edition coverage means anyone active 2014–2023 is captured (late-career Gerrard, Lampard, Xavi, Pirlo, peak Ronaldo/Messi). Only retired-pre-2014 icons need the AI-generated legends pack.

> **Prime Mode note:** "Prime Mode" (career-best rating, §5A) groups all rows by the stable `sofifa_id` and takes each player's **max `overall` across editions** — so it needs the multi-season ingest (which we're doing). On a single-season fallback it would be a no-op (one entry per player). The same player legitimately appears in multiple club-seasons (e.g. Shay Given in a Sunderland season *and* Stoke 2016/17), which is exactly the structure this reads from. Caveat: a player whose true peak predates FIFA 15 has their "career-best" capped at their best within 2014–2023 — only relevant for the legends pack.

> Stretch / ML path (optional, ties to GNN/ML interests): derive *own* ratings from real performance stats (FBRef-style) via regression or a small model, instead of using FIFA's numbers. Real scope creep — keep as a stretch goal.

---

## 5. Game Modes — Option C (Hybrid)

**Classic (single league).** Pick one league, draft only its clubs/eras, simulate that league's season. Faithful to the original; respects real season lengths (e.g. Bundesliga = 34 games).

**World Draft (global).** Spin lands on any club from any top-5 league and any era; build a cross-league, cross-era XI. Two sub-options for what you play against:
- **(a) Randomized-but-balanced opponent league** ← *flagship, build first.* See §7.
- **(b) Global team in one real league.** Take your global XI and test it specifically in the Prem / La Liga / Bundesliga / etc. ("Could this all-star team go unbeaten in the Premier League?")

Build priority: **flagship (a) first**, then (b).

---

## 5A. Run Configuration (Setup Screen)

*Captured from the original's setup screen + our additions. All chosen before a run starts.*

**Formation** — all seven, each with a one-line flavor description and a live pitch preview:
`4-3-3`, `4-4-2`, `4-2-3-1`, `4-5-1`, `3-4-3`, `3-5-2`, `5-4-1`.
Built data-driven (each formation = an ordered list of position slots), so the draft board, position-fit, and pitch view all read from the same definition.

**Difficulty** — bundles rerolls + rating visibility:
- *Easy* — 3 rerolls
- *Normal* — 1 reroll
- *Hard* — 0 rerolls · ratings hidden

**Show Ratings** — *independent toggle; this is where our headline feature lives:*
- *On* — overalls visible
- *Scout (range)* ← **our addition.** Asymmetric fuzzy range per player (see §8)
- *Off* — blind mode

**Draft Mode** — *both modes exist in the original; support both:*
- *Squad First* — spin a club-season, pick any player from it, then choose which open slot they fill
- *Position First* — pick an open slot, then spin for a club-season to fill it

**Player Ratings** — how a drafted player is rated:
- *Career Seasons* — rated as they were in that exact spun season (uses that edition's `overall`)
- *Prime Mode* — every player at their career-best rating (max `overall` across editions; needs multi-season data)

> **Player Ratings × Show Ratings are independent axes.** Player Ratings picks *which number* (season vs career-best); Show Ratings picks *how it's displayed* (exact / range / hidden). All six combinations are valid; in Scout mode the range is computed around whichever rating is active. Keep them decoupled in code.

**Era filter** — preset chips (`All-time`, `2000s+`, `2010s+`, `Modern 2016+`) **and** a dual-handle year slider. Only club-seasons inside the range can be spun — narrows the pool to a familiar era.

**League scope** — *our addition, layered on top of the above:*
- *Classic* — single league (pick PL / La Liga / Serie A / Bundesliga / Ligue 1)
- *World Draft* — all top-5 leagues + eras → then opponent mode (randomized-balanced §7, or specific real league §5b)

---

## 5B. Draft Screen Features

- **Spin** the CLUB × SEASON wheel ("tap anywhere to spin"); animates through a brief **"Spinning…"** state before landing. Header shows "N positions left to fill". Season format `1998/99`, `2016/17`. Club shown with a colour dot.
- **Reroll counter** (e.g. `1/11`) with a reset, governed by difficulty. A spun squad also has its own **`Reroll (N)`** button to discard it and spin again.
- **Pitch view** — empty slots as dashed position markers (ST/LM/CB/GK…), filled slots show the drafted player (initials badge, coloured by line).
- **Move a player** — reposition an already-drafted player to open a different slot.
- **Live team strength** — running **Overall** plus sub-scores (**Attack / Midfield / Defence / GK**) with bars, recalculated as each player is drafted. Lines not yet drafted show `—`.
- **Continue Draft** — in-progress drafts persist so a run can be resumed (landing screen offers "Start New Run" vs "Continue Draft").

### Squad-Spun Placement (Squad First mode)

After a spin, the squad's players are listed (sorted by overall desc), each row showing: a **rating badge coloured by primary line**, name, nationality, and **all of the player's positions as colour-coded chips**. Picking a player opens a **Place [name]** panel showing every formation slot in one of three states:

| State | Meaning | Example |
|---|---|---|
| **Available** (green button) | open slot the player's positions cover | Keane → `CM`, `CDM` |
| **N/A** (greyed) | open slot, but *not* one of the player's positions | Keane → `ST · N/A`, `CB · N/A` |
| **Filled** (greyed, named) | slot already taken | `GK · Given` |

**Placement is restricted to natural positions** — you cannot place a player into a slot outside their listed positions. Strategic tension comes from random squads not always covering your open slots (→ rerolls + Move a player), *not* from out-of-position penalties.

> **Decided:** natural positions only — **no out-of-position placement at all** (exact replica of the original). No fit penalties anywhere; everyone always plays a listed position. *(This supersedes the earlier "position-fit penalty" framing.)*
>
> *Edge-case to handle:* if a spun squad has **zero** players eligible for any remaining open slot (rare — real squads cover all lines, and every squad has a GK), grant a **free reroll** rather than letting the draft dead-end. Cheap safeguard; keeps the natural-only rule intact.

### Position colour language (UI)

Consistent across badges and chips: **attack = red** (ST/LW/RW), **midfield = green** (CM/CDM/CAM/LM/RM), **defence = blue** (CB/LB/RB/RWB), **GK = amber**. The rating badge takes the player's *primary* line's colour. Pairs naturally with the scout-dossier theme (§9), and in **Scout mode** the badge simply shows a range (e.g. `88–96`) instead of the exact number while keeping the colour.

---

## 6. The Simulation — Two Layers

The drama comes from the gap between these two layers (e.g. "PROJECTED 4th / FINISHED 3rd / OVERPERFORMED").

### Layer 1 — Pre-season projection ("the bookies")
Pure function of team strength, no opponents. Map Overall rating → expected points + finish-probability distribution, calibrated on real historical seasons. (Original: "based on 20 top-flight seasons.") Example target curve: Overall 75 → ~50 pts, 83 → ~70 pts, 90 → ~95 pts. Produces "projected finish" + win-league / top-4 / top-6 / top-10 / relegation probabilities.

### Layer 2 — Actual match sim (named opponents)
Generates a real 38-game season:

1. **Team strength** from the XI → split into attack / midfield / defense / GK sub-scores. Placement is natural-position-only (§5B), so every player is in a position they can play — sub-scores aggregate raw `overall` per line directly, no fit penalty anywhere.
2. **Per fixture** → each side's expected goals = f(your attack vs their defense, + home advantage); draw actual goals from a **Poisson** distribution → scoreline.
3. **Attribute goals/assists** → each goal assigned to a player weighted by attacking contribution (ST/winger/CAM high, CB low), with a random minute. Same for assists. Clean sheet → GK + defenders when opponent scores 0. **This runs for *both* teams in *every* match — see §6.5.**
4. **Aggregate** → W/D/L, points, GF/GA, streaks, biggest win, highest-scoring game, season awards (Golden Boot / Playmaker / Golden Glove / Player of the Season), full final table.

### 6.5 League-wide stat tracking (not just your XI)

Attribution is **league-wide**: every player on every team accumulates stats across all **380 matches**, including the 342 your team isn't in — not only your own 38.

- Each opponent club-season is a **real roster** in the data. Its **best XI** (the same 11 averaged for strength via the one-strength-function, §7) doubles as the stat-bearing XI: that club's goals are distributed across those 11 by attacking weight.
- So Golden Boot / Playmaker / Golden Glove / Player of the Season are computed over the **whole-league ledger** — an opponent's striker can win the Golden Boot ahead of yours. This is the point: a real scoring race, a living league, sharper "did I dominate or just win?" tension.
- **Opponents are rated/attributed as their sampled season** (2016 Leicester scores like 2016 Leicester), independent of the user's Prime/Career toggle, which governs only the user's own draft.
- Cost: ~380 matches × a handful of goals × 11-player attribution, over ~220 tracked players — trivial.

Opponents carry their own strength ratings; you play all 19 home + away = 38.

> **Seed the RNG.** Thread a single seeded `Random` through the whole sim (spins, Poisson draws, goal attribution). This makes runs **reproducible** — essential for deterministic JUnit tests (draft a fixed XI → assert an exact record) and a free path to a future "share this run / replay seed" feature.

---

## 7. Opponent League Generation (Flagship Mode)

Build a synthetic 19-team opponent pool (your XI is the 20th team) with a **realistic strength pyramid**, sampling real historical club-seasons from any league/era, constrained by tier.

Base tier shape (sums to 19):

| Tier | Strength | Base count | Randomized range |
|---|---|---|---|
| Juggernaut | 90+ | 1 | 0–2 |
| Title contender | 85–89 | 3 | 2–4 |
| European chaser | 80–84 | 5 | 4–6 |
| Mid-table | 75–79 | 6 | flex (balancer) |
| Relegation scrapper | 68–74 | 4 | 3–5 |

**Randomization rules:**
- Randomize the *shape* each run, but always total exactly 19.
- One tier (mid-table) acts as the **balancer** to absorb the remainder.
- Keep the **average league strength near a target mean** so difficulty stays in a fun band while the shape varies. → This target mean is the future **difficulty knob** (easy / normal / hard).
- Guardrails: never so many juggernauts that 38-0 is impossible; never so few that it's trivial.

Result: each run feels different (e.g. "two juggernauts but a soft bottom half" vs "no juggernaut but a deep, tough mid-table"), but a perfect season always means something. Your drafted XI slots in as team 20 and plays everyone twice — same engine as §6, no new code.

For the **final league table**, fully simulate the round-robin for the other clubs too (≈380 games, cheap) so the table is honest.

### One strength function, used everywhere

Define a single function: **club-season → strength = mean `overall` of its best XI** (per line, like the player's own team). This one function powers everything:
- **your XI's** Overall + sub-scores,
- **Classic-mode opponents** — the real clubs of the chosen league in a reference season, strengths read straight from the data,
- **World-mode randomized opponents** — each sampled historical club-season's strength, used to slot it into a tier.

So Classic and World aren't different opponent systems — both pull club strengths from the same data-derived function. No bespoke opponent ratings to maintain.

---

## 8. Ratings Modes (the "Show Ratings" toggle)

Three options on the Show Ratings control (§5A):

- **On** — full overalls shown (classic "easy").
- **Off** — no ratings (classic "blind / hard").
- **Scout (range)** ← *our new middle ground.* Show a **range** instead of the exact number, as incomplete scout intel.

Scout design (**asymmetric range**): the true rating sits *somewhere inside* the band but **not necessarily centered**, so players can't just average to reverse it. E.g. true 84 → shows "72–88". Band width itself varies per player, so elite guys are sometimes obvious, sometimes not. Rewards football knowledge without fully removing guidance. Ties thematically to the scout-dossier UI (§9).

*Interaction note:* Hard difficulty forces ratings hidden; otherwise Show Ratings is independent of difficulty.

---

## 9. UI / Theme

Distinct identity (not a recolor of the original's slot machine). Concept: **scouting / intelligence dossier** — you're a scout assembling a squad from incomplete intel, which ties directly into the fuzzy-ratings mode. Ideas: card-pack reveal instead of a spin wheel, a pitch view for placement, own palette, animated reveals.

---

## 10. AI Integration Points

Two clean LLM-in-the-loop hooks, both via the Claude API:

1. **Legend ratings** — generate 0–99 ratings + positions for pre-2007 club-seasons as structured JSON (an LLM-in-the-loop ETL step).
2. **Dynamic flavor text** — generate the per-run narrative ("Nobody saw that coming," "Cech had safe hands all year") at runtime instead of templating, conditioned on the actual results.

---

## 11. Results Screen (target output)

Mirrors the richness of the original: full match log with scorelines + goalscorers/minutes; projected-vs-actual finish with over/underperformed badge; W-D-L / points / GF-GA; **season awards computed league-wide** (Golden Boot etc. can be won by an opponent — §6.5); a **league-wide top-scorers / top-assisters table** alongside your own per-player G/A/CS; clean sheets, longest win streak, biggest win, highest-scoring game; collapsible final league table; share + new run.

---

## 12. Build Plan

**Phase 1 (now):** Spring Boot backend, starting with the **simulation engine** as a self-contained, unit-tested service.
- Ingest FIFA 15–25 dataset.
- Team-strength + position-fit model.
- Poisson match sim + goal/assist/clean-sheet attribution.
- Opponent-league generator (§7) with bounded randomization.
- Pre-season projection layer (§6, Layer 1).
- REST endpoints: spin, draft, simulate.

**Phase 2:** React scout-dossier frontend; fuzzy ratings mode; "global team in one real league" mode (§5b).

**Phase 3:** Legends pack (AI-generated); dynamic flavor text; difficulty knob; persistence/leaderboard.

---

## 13. Learning Objectives

- **Spring Boot / Java:** REST controllers, service layer, DTOs, dependency injection, (optional) JPA persistence, unit + integration testing of the sim engine.
- **AI integration:** structured-output prompting, LLM-in-the-loop ETL, runtime LLM calls.

---

## 14. Open Decisions for Build Time

Mostly resolved now (defaults in brackets):

- **Formation:** ~~one or many?~~ **Resolved — all seven** (`4-3-3`, `4-4-2`, `4-2-3-1`, `4-5-1`, `3-4-3`, `3-5-2`, `5-4-1`), data-driven so each is just a slot list. Pitch view / draft board / position-fit all read from it.
- **Draft flow:** ~~one loop?~~ **Resolved — support both** *Squad First* and *Position First* (both are in the original; it's a cheap toggle that adds replay variety).
- **Out-of-position placement:** **Resolved — natural positions only** (exact replica; no fit penalty). Free reroll on the rare zero-eligible-player dead-end.
- **Rerolls:** governed by difficulty — Easy 3 / Normal 1 / Hard 0.
- **Data:** ✅ verified — anchor on `players_22.csv` (URL in §4); add other years with the column-normalization map.
- **Still genuinely open:** the exact rating→points calibration curve (Layer 1) and the Poisson tuning (Layer 2) — these get tuned empirically once the engine runs.

---

## 15. Carrying Context Into a New Chat / Project

Memory across chats is **partial and delayed**, and if the build happens inside a **Project**, a fresh instance generally **won't** be able to search this (non-project) conversation. So don't rely on memory:

- **Upload this `DESIGN_SPEC.md` into the new project** — it's the portable source of truth and makes context-carryover reliable regardless of memory.
- Optionally re-attach the original 38-0 screenshots if UI/results parity matters in that session.

---

## 16. Engineering Considerations (from review)

Validated technical points to build in from the start:

- **Scout-mode DTO must strip `true_rating`.** Backend computes the range; the payload sent to React contains only the displayed range/string, never the true number (else DevTools reveals it). Derive the range **deterministically from `(playerId, runSeed)`** so it's stable across requests without server storage — ties into the seeded RNG. *(Low real stakes for a personal build, but the correct API pattern.)*
- **Seed DB via Spring Data JPA, not in-memory.** Parse the CSVs once → seed an embedded **H2** (or local Postgres) → query via repositories (`findByLeagueNameAndSeason`, etc.). *The reason is to exercise the persistence layer, not performance* — the dataset (~29k rows filtered to top-5/level-1) is small and would fit in memory fine; JPA is the point.
- **Draft state = a persisted `DraftRun` resource.** Server-authoritative, keyed by `runId`; client holds only the id and calls `/api/runs/{id}/spin` etc. This is proper REST (a run-in-progress *is* a resource), and it's what powers **Continue Draft** (§5B) and seed-replay. Avoids trusting client-held reroll counts.
- **Poisson under-predicts draws → Dixon-Coles.** Pure independent Poisson produces too few 0-0/1-1 draws (scorelines are correlated). Ship raw Poisson for v1; add the low-score (Dixon-Coles) correction during tuning.
- **The 38-0 calibration target.** Rarity *is* the product — tune for "rare but real for a 90+ draft" (a few % per attempt), not "achievable." Levers in order: (1) steep strength→λ curve at the top end; (2) a modest variance-reducer only for severe strength mismatches; (3) calibrate Layer-2 per-game win probabilities so the simulated average lands near the Layer-1 projection (a 90-rated team ≈ 95 pts). This anchors the engine and prevents "everyone finishes on 110 or 60 points" drift.
- **Async flavor text.** Return numerical results immediately; fire the Claude flavor-text call asynchronously and stream into a "scouting report compiling…" skeleton. Never block the results endpoint on the LLM.
- **Prime Mode merge rule.** Validate `sofifa_id` stability across editions on ingest (assert id↔name consistency; fall back to name+DOB on misses). For position drift across years, use the **peak-year snapshot** — take the rating *and* its positions from the same best-`overall` row, so "prime" is one coherent season, not a merge of best-rating + unioned-positions.

---

*Status: design locked; frontend = Spring Boot REST API + separate React SPA. Next step — scaffold the backend, simulation engine first. Critical path: verify the data CSV before Phase 1.*

---

## 17. Implementation refinements (post-v1, in code)

Two refinements landed during the Phase-1 build that sharpen §6 and §7:

- **Per-opponent best-fit formation.** Opponents are no longer forced into the user's formation. `ClubSeason.optimalXi()` tries all seven formations and fields the one with the most natural-position fits (overall as the tiebreak), cached; `optimalStrength()` returns that XI's overall and is the single strength function used for tiering. This decouples opponent generation from the user's chosen shape, and the final table shows each opponent's actual formation (e.g. "Valencia 2021/22 (4-4-2)").

- **Position- and rating-weighted attribution.** `ScoringWeights` gives every position a goal/assist propensity (ST/CF finish; LW/RW/CAM create; CDM/CB low on both), multiplied by `(overall/100)²` so higher-rated players score more within their line. This is attribution-only — it changes *who* scores, never *how many*, so it has no effect on scorelines or points and can be tuned freely against the leaderboards. Goal-volume and match-outcome knobs remain the `MatchEngine` constants (`BASE_GOALS`, `SCALE`, `HOME_ADV`, `MAX_LAMBDA`), which *are* coupled to the points calibration.

- **Placement fallbacks** never field a keeper outfield or an outfielder in goal in the rare zero-eligible case.