# Footy-Draft — A From-Zero Guide to the Whole Codebase

This document explains the entire project assuming **no programming knowledge at all**. It starts with
"what even is a program" and builds up, step by step, until you understand every part of *this* app and how
the pieces fit together. Read it top to bottom the first time; afterwards use the table of contents to jump.

> The other docs assume you already code. This one doesn't. `CLAUDE.md` is the quick-reference for someone
> who knows the codebase; `DESIGN_SPEC.md` is the design source-of-truth; **this file is the teaching guide.**

## Contents

0. [The absolute basics](#part-0--the-absolute-basics)
1. [What this app actually is](#part-1--what-this-app-actually-is)
2. [The big picture: four layers](#part-2--the-big-picture-four-layers)
3. [Where the players come from (the data)](#part-3--where-the-players-come-from-the-data)
4. [The core building blocks](#part-4--the-core-building-blocks)
5. [How one match is decided (the gentle math)](#part-5--how-one-match-is-decided-the-gentle-math)
6. [How a whole season is played](#part-6--how-a-whole-season-is-played)
7. [The forecast: playing the season 1,000 times](#part-7--the-forecast-playing-the-season-1000-times)
8. [The draft: the step-by-step game flow](#part-8--the-draft-the-step-by-step-game-flow)
9. [Two special features: Scout mode and Prime mode](#part-9--two-special-features-scout-mode-and-prime-mode)
10. [The website (the frontend)](#part-10--the-website-the-frontend)
11. [Following one click all the way through](#part-11--following-one-click-all-the-way-through)
12. [The "rules that must never break" (invariants)](#part-12--the-rules-that-must-never-break-invariants)
13. [How to run it and poke at it](#part-13--how-to-run-it-and-poke-at-it)
14. [Glossary](#part-14--glossary)

---

## Part 0 — The absolute basics

### What is a program?

A **program** is a list of instructions a computer follows, written in a language humans can read. You write
text in a file; a tool translates that text into something the machine runs. That's it. Everything below is
just *organising* those instructions so humans can understand and change them.

### Two languages live here, and why

This project uses **two** programming languages because it has two very different jobs:

- **Java** — runs on a **server** (a computer that does the thinking). It loads the football data, does all the
  simulation maths, and remembers your game. Java is good at heavy, reliable number-crunching.
- **TypeScript** (a stricter version of JavaScript) — runs in your **web browser**. It draws the screens,
  buttons, pitch, and charts you actually see and click. Browsers only speak JavaScript, so anything visual
  lives here.

You'll also see **CSV files** — these aren't a programming language, just data: plain text tables (like a
spreadsheet saved as text), one football player per line.

### Frontend vs backend vs API — the restaurant analogy

Think of a restaurant:

- The **frontend** is the dining room — the menu, the table, the plated food. It's everything the customer
  sees and interacts with. Here, that's the website running in your browser.
- The **backend** is the kitchen — where the actual cooking (the real work) happens, out of sight. Here, that's
  the Java server.
- The **API** is the waiter — it carries your order to the kitchen and brings food back. The frontend never
  walks into the kitchen; it just hands requests to the waiter and receives results. Here, the API is a set of
  web addresses (URLs) the browser sends messages to, like `POST /api/runs` ("please start a new game").

"REST API" just means a tidy, conventional style of waiter: specific URLs, standard verbs (`GET` = fetch
something, `POST` = do something / create something). You'll see those verbs throughout.

### What does "running the app" mean?

Two things start up:

1. The **Java server** boots (command: `mvn spring-boot:run`). It reads all the football CSV files into memory,
   then waits, listening on "port 8080" — think of a port as a numbered door on the computer where the waiter
   takes orders.
2. The **frontend** boots (command: `npm run dev`). It serves the website on "port 5173" in your browser, and
   forwards any kitchen orders to the server on 8080.

So: browser (5173) ⟶ asks ⟶ server (8080) ⟶ thinks ⟶ replies ⟶ browser draws the answer.

### The handful of words you need

You don't need to know much vocabulary. Here are the only words that matter, in plain terms:

- **Value / data**: a piece of information. The number `90`. The text `"Lionel Messi"`. A yes/no.
- **Variable**: a labelled box holding a value, so you can refer to it by name. `overall = 90`.
- **Function** (a.k.a. method): a named recipe that takes some inputs and produces an output. `add(2, 3)` gives
  `5`. Functions are how work gets organised into reusable steps.
- **List**: an ordered collection. `[Messi, Ronaldo, Mbappé]`. (In code: `List`.)
- **Map** (a.k.a. dictionary): a lookup table of key → value. `{"Messi" → 30 goals}`. Ask it a key, it gives
  you the value.
- **Type**: what *kind* of value something is — a number, text, a Player, a list of Players. The languages
  here check types so you can't accidentally put text where a number belongs.
- **Class / object / record**: a way to bundle related data (and sometimes functions) into one named thing.
  A `Player` bundles together an id, a name, a rating, and a position. A **record** is just a simple, read-only
  bundle of data — most of the data in this app are records.

That's genuinely enough to follow the whole codebase. We'll introduce anything else as we hit it.

---

## Part 1 — What this app actually is

It's a **football (soccer) draft-and-season game**, played solo, inspired by the "can you go a whole season
unbeaten?" challenge. The loop:

1. You **spin** — like a slot machine, it lands on a *tier* of quality (from "Minnow" up to a rare "Iconic"),
   then offers you 2–3 real club-squads from football history of that quality (e.g. *Liverpool 2019/20*).
2. You **pick one player** from the offered squad and slot them into your formation (e.g. put their striker at
   your ST position).
3. Repeat **11 times** until you've built a full team of 11, cherry-picked from across history.
4. The game builds you a **19-opponent league**, then **simulates a 38-game season**.
5. You watch it play out matchday by matchday, then get a **debrief**: where you finished, your stats, and how
   your season compared to what was expected.

The twist is realism: the opponents and players are real historical FIFA-game squads (FIFA 07 through EA FC 26),
and the match engine is built on actual football-statistics maths, so results *feel* like real football.

---

## Part 2 — The big picture: four layers

The code is organised as four **layers**, like an onion. The most important rule: **inner layers know nothing
about outer layers.** The maths engine has no idea a website exists. This keeps each layer simple and testable.

```
┌─ FRONTEND  — the website in your browser (TypeScript/React)        │ what you see
│  ┌─ API     — the waiter: web addresses wrapping the engine (Java) │ how the browser talks to the server
│  │  ┌─ PERSISTENCE — saving/loading data in a database (Java)      │ memory
│  │  │  ┌─ ENGINE — the pure football maths (Java)                  │ the brain
│  │  │  └────────────────────────────────────────────────────────  │
│  │  └───────────────────────────────────────────────────────────  │
│  └──────────────────────────────────────────────────────────────  │
└─────────────────────────────────────────────────────────────────  │
```

Where each lives on disk:

| Layer | Folder | Language | Job |
|---|---|---|---|
| Engine | `src/main/java/com/draft/footy/` | Java | All the football logic and maths. No website/database code. |
| Persistence | `src/main/java/com/draft/footy/persistence/` | Java | Stores data in a database (H2) so the server can remember your game. |
| API | `src/main/java/com/draft/footy/api/` | Java | The web addresses (`/api/...`) the browser calls. |
| Frontend | `frontend/src/` | TypeScript | The screens, buttons, pitch, charts. |

> "`com.draft.footy`" is just a **package** — a folder-like namespace that groups related Java files. Read it
> like a web address backwards: it's this project's home for footy code.

### The single golden thread: the "seed"

One idea ties the whole app together: a **seed**. A seed is just a starting number for the randomness. Computers
can't make true randomness; they make *predictable* randomness from a seed — same seed in, same "random"
sequence out, every time. (Like shuffling a deck the exact same way if you start from the exact same order.)

Because every random choice in a game flows from one seed, **a whole season is perfectly repeatable**. Give the
engine seed `42` twice and you get the identical season down to every goal. This is why the game can show you a
forecast *before* the season and have it match the season *after* — they're the same maths from the same seed.
Keep this in mind; it explains a lot of design choices.

---

## Part 3 — Where the players come from (the data)

Every player and squad is real data from the FIFA video-game series. That data lives in **CSV files** (text
tables) in the `data/` folder. There's a catch: these files come from many sources across ~20 years, and they
disagree about *everything* — column names, club names, even how harsh the ratings are. The job of turning this
mess into clean, consistent data is done by one file:

**`FifaDataLoader.java`** — the "data janitor." In plain terms, it:

- **Reads each CSV line into a `Player`.** (A line of text → a tidy player record.)
- **Handles different file formats.** Old files call a column `sofifa_id`; newer ones call it `player_id`.
  Some call the league `La Liga`, others `LALIGA EA SPORTS`. The loader knows all the aliases and treats them
  as the same thing (see `idx(...)` and the alias lists near the top of the file).
- **Keeps only the top-5 leagues** (England, Spain, Italy, Germany, France). It's careful: one data dump had its
  league labels *scrambled*, so the loader insists that *both* the league's ID number *and* its name agree
  before trusting it (`isTop5(...)`).
- **Fixes the rating drift between eras** (`wideTaperOffset(...)`). The FIFA games quietly inflated ratings
  around 2015–2017, so an "84" from 2014 isn't the same as an "84" from 2020. The loader gently nudges older
  ratings up onto the modern scale (a little for the mid-tier, nothing for the 90+ legends so the ceiling stays
  honest). This is why a 2008 squad can fairly share a league with a 2023 one.
- **Maps awkward positions** (`normPos(...)`). Some old data lists a player only as "CF" (centre-forward), but no
  formation here has a "CF" slot — so it's mapped to "ST" (striker), letting that player actually be used.
- **Groups players into squads** (`group(...)`): every player belonging to *Barcelona in 2011* becomes one
  `ClubSeason`, as long as the squad has at least 11 players.

The end result: ~1,950 club-squads spanning 20 FIFA editions, all on one fair scale.

> **Prime Mode** (`PrimeIndex.java`): because a player appears in many editions, the game can find each player's
> *career-best* version (their highest-rated season) and use that. So "Prime Messi" is peak Messi, not whichever
> year you happened to spin. This only ever applies to *your* drafted team, never the opponents.

---

## Part 4 — The core building blocks

These are the small data records the whole engine is made of. They're tiny — read them first in the real code;
each is only a few lines.

### `Player.java`

One footballer. Holds: an `id` (a unique number), a `name`, a `nation`, a list of `positions` they can play
(first one is their best), an `overall` rating (0–99), and a date of birth. Two helpers: `primaryPosition()`
(their best position) and `canPlay("ST")` (can they play striker? — just checks if "ST" is in their list).

That's the entire player model: **one rating, and a list of positions.** Simple on purpose.

### `Line.java`

Football positions group into four **lines**: **ATT** (attack), **MID** (midfield), **DEF** (defence),
**GK** (goalkeeper). This file maps every position to its line (ST → ATT, CB → DEF, etc.) and stores how likely
each line is to score or assist a goal (attackers score a lot, defenders rarely, keepers basically never). These
likelihoods are used later to decide *who* gets credited with goals.

### `Formation.java`

The 7 supported formations (4-3-3, 4-4-2, etc.). Each is simply an **ordered list of 11 position slots**. For
example 4-3-3 is `[LW, ST, RW, CM, CM, CDM, LB, CB, CB, RB, GK]`. The whole formation system is just this list —
nothing fancier.

### `Xi.java`

An **"XI"** is football slang for a starting eleven (11 players). This record represents one concrete team: a
`name`, the `Formation` it's arranged in, and the 11 filled slots (each slot = a position + the player in it).

Crucially, an `Xi` knows how to summarise its own strength into numbers the match engine can use:

- `attackRating()` = mostly the attackers' average rating, plus some of the midfield's.
- `defenceRating()` = mostly the defenders' average, plus the keeper and a bit of midfield.

So a whole team of 11 is boiled down to two numbers — an attack score and a defence score. That's all the match
engine needs.

> *(A small recent cleanup: the formation used to be hidden inside the team's text name and parsed back out,
> which was fragile. Now `Xi` carries the `Formation` as a proper field. If you read older notes mentioning
> "formation in the name," that's history.)*

### `ClubSeason.java`

All the players for one real club in one real season (e.g. all of *Real Madrid 2017/18*), plus the cleverest
small piece of logic in the app: **picking that club's best possible XI.**

This isn't as easy as "take the 11 highest-rated players," because they have to fit into real positions — you
can't field four left-backs. The method `buildXi(formation)` solves this with a classic algorithm called
**maximum bipartite matching** (`augment(...)`). In plain words: it tries to seat each player (best players
first) into a position they can naturally play, and if a seat is taken, it *re-shuffles* the earlier player to
another open seat rather than benching the better newcomer. The result is the genuinely strongest legal lineup.

`optimalStrength()` then returns that best XI's overall rating — and this single number is used *everywhere* to
measure how good a squad is. (That's an important rule — see Part 12.)

---

## Part 5 — How one match is decided (the gentle math)

This is `MatchEngine.java`. Don't worry — the maths is explained in words; you don't need the formulas.

A football match boils down to: **how many goals does each team score?** The engine answers in two steps.

### Step 1: How many goals to *expect*

For each team it computes an "expected goals" number (statisticians call this **lambda**). The idea:

> Take the attacking team's attack rating, subtract the defending team's defence rating, add a bonus if they're
> playing at home, and turn that gap into an expected number of goals.

A big gap (strong attack vs weak defence) ⟶ more expected goals. There's a cap so even a mismatch doesn't
produce absurd 12-goal expectations. Several **tuning knobs** control the feel:

- `BASE_GOALS` — the baseline goals-per-team in an even match.
- `SCALE` — how much a strength gap matters (bigger = closer games, fewer blowouts).
- `HOME_ADV` — the home-advantage bonus.
- `MAX_LAMBDA` — the cap on expected goals.

These are *calibrated* (tuned by experiment) so the simulated league looks like real football: champions get
~88–89 points, about a quarter of games are draws, ~2.6 goals per game.

### Step 2: Turn "expected goals" into an actual scoreline

Expected goals is an average (e.g. "1.7"), but a real match has a whole-number score (2–1, 0–0…). The engine
draws an actual score using a well-known football model called **Dixon-Coles** (`sampleScore(...)`).

You don't need the formula. The intuition: a naive method would produce too few draws (real football has lots of
1–1s and 0–0s because once a goal goes in, teams change behaviour — they don't score independently). Dixon-Coles
adds a small correction that nudges extra probability onto the low-scoring draw results, matching reality. The
knob `RHO` controls how strong that nudge is.

### Step 3: Who scored?

Now the engine knows the score is, say, 2–1. *Which players* get the goals? `pick(...)` chooses, weighted by
two things multiplied together:

- **Position** (`ScoringWeights.java`): strikers are far likelier to score than defenders.
- **Rating** (`ratingFactor(...)`): a 90-rated player grabs a much bigger share than a 75-rated one.

**Very important idea:** this "who scored" step is *purely cosmetic*. It only decides whose name appears on the
goal — it can **never** change the score or the points. So you can freely tweak the scoring weights to make the
leaderboards look right without affecting who wins. (This separation is deliberate and is one of the project's
core rules.)

---

## Part 6 — How a whole season is played

This is `SeasonSimulator.java`. A season = 20 teams (you + 19 opponents), everyone plays everyone twice (home and
away) = 38 games each, 380 total.

- **The fixture calendar** (`schedule(...)`): it generates a proper round-robin schedule (the "circle method")
  so every team plays exactly once per matchday, and every pairing happens exactly twice (once home, once away).
- **Season form** (`FORM_SIGMA`): at the start, each team secretly draws a "form" value — a good or bad campaign
  for the whole year. This is the **drama dial**: it's why a great team can occasionally have an off-year, or an
  underdog can overachieve. On average it cancels out (it's centred on zero), so the long-run expectations don't
  change — but any *single* season can swing.
- **Playing it out**: it runs every fixture through the `MatchEngine`, records points (win = 3, draw = 1), and
  tallies each player's goals/assists/clean-sheets.
- **Bookkeeping detail that matters**: stats are tracked **per (team, player)**, not per player globally. Why?
  The same real player (say, 2019 Mané) could appear on two different teams in the same league (your team *and*
  an opponent that drafted his club). They must be counted as two separate competitors.
- **After each matchday** it saves a snapshot of the league table — that's what lets the website replay the
  season step by step.
- **Awards** at the end: top scorer (Golden Boot), top assister, best keeper (Golden Glove — filtered to actual
  goalkeepers), and Player of the Season (best all-round contribution, weighted by how high their team finished,
  mimicking how real awards favour winners).

The output is one big `SeasonResult` record: the final table, every matchday snapshot, all the stats, and the
awards.

---

## Part 7 — The forecast: playing the season 1,000 times

This is `MonteCarlo.java`, named after the famous "Monte Carlo" technique: *if you can't calculate the odds
directly, just simulate it thousands of times and count what happens.* It's how real bookmakers think.

The problem it solves: before the season, the game wants to tell you "you're expected to get about 82 points,
with a 30% chance of the title." How? It **re-plays your exact season ~1,000 times**, each time with a different
random form draw, using a stripped-down fast version of the match engine (`fastScore` — it skips the cosmetic
"who scored" step, so it's lightning quick). Then it reads the answer straight off the results:

- Average points, and the spread (your worst, typical, and best outcomes — the "p5/median/p95" you see on the
  chart).
- Odds of winning the league, finishing top-4, getting relegated, going unbeaten, even a perfect 38-win season.

A neat bonus: because each of the 1,000 simulations ranks *all* 20 teams, one run produces a full forecast for
**every** team for free — so the website can show any opponent's projected finish too (`TeamCloud`).

The key payoff: the forecast and the actual season use the **same engine, same opponents, same seed**, so the
"before" prediction and the "after" result are always consistent. After the season, `percentile(...)` answers
"your actual points beat what % of your possible seasons?" — that's the over/under-performance verdict in the
debrief.

> The wavy chart you see on the results screen — the gold fan with your final score pinned on it — is exactly
> this forecast (`ForecastCone.tsx`). The fan is the range of likely outcomes; the pin is where your real season
> landed.

**Building the opponents** is `OpponentPyramid.java`: it assembles 19 opponents in a realistic spread of quality
(a couple of giants, several contenders, a midtable mass, some strugglers — the "pyramid"), sampled from history
and de-duplicated so you never face two versions of the same club.

---

## Part 8 — The draft: the step-by-step game flow

Everything so far was the *engine*. Now the *game* — the back-and-forth of actually drafting a team. This is a
**state machine**: a thing that moves through defined stages (DRAFTING → SIMULATED), where each action is only
legal in the right stage. It lives in `DraftRunService.java`, exposed to the browser through
`DraftRunController.java`. "Server-authoritative" means the server is the referee — the browser can't cheat,
because the server validates every move and holds the real state.

The stages, as web requests the browser sends:

1. **`POST /api/runs`** — *start a game.* You send your settings (formation, difficulty, whether ratings are
   shown, a seed). The server creates a `DraftRun`, saves it, and hands back an id.
2. **`POST /api/runs/{id}/spin`** — *spin the slot machine.* The server first randomly lands on a quality
   **tier** (weighted so good squads are common but "Iconic" is a rare jackpot — see `DraftTiers.java`), then
   offers 2–3 distinct real club-squads of that tier (excluding clubs you've already drafted from). It's all
   driven by your seed, so it's repeatable.
3. **`POST /api/runs/{id}/draft`** — *pick a player.* You name a player from the offered squads and a position
   to put them in. The server checks: is this player actually in the current offer? Can they play that position?
   Is the slot open? If yes, they're placed.
4. Repeat spin/draft until all 11 slots are filled.
5. **`GET /api/runs/{id}/preview`** — *see your league + forecast* before committing.
6. **`POST /api/runs/{id}/simulate`** — *play the season.* Returns the full matchday-by-matchday replay plus the
   debrief.

> A recent cleanup added one shared helper, `opponentsFor(run, rng)`, used by both *preview* and *simulate*, so
> the league you're shown beforehand is guaranteed identical to the one you actually play. Previously this was
> duplicated in two places and could silently drift apart.

The whole game's state (your settings, your seed, your 11 slots) is stored in a database via the **persistence**
layer (`DraftRunEntity` etc.). Right now that database lives only in memory, so games vanish when the server
restarts — making them permanent is a known to-do.

---

## Part 9 — Two special features: Scout mode and Prime mode

These are the project's two signature additions over the original concept.

### Scout mode (`ScoutRatings.java`)

A difficulty/immersion option for how much you know about players:

- **ON** — you see exact ratings (e.g. "88").
- **SCOUT** — you see only a *fuzzy range* (e.g. "84–91"), like a scout's rough read.
- **OFF** — ratings hidden entirely.

The clever part of SCOUT: the range is generated from the player's id + your game's seed, so it's *stable* (you
see the same range every time you look) without the server having to store it. And it's deliberately **lopsided**
— the true rating isn't in the middle of the range — so you can't average several looks to reverse-engineer the
real number. The true rating literally never leaves the server in SCOUT/OFF mode.

### Prime mode

Covered in Part 3: use each of *your* drafted players' career-best season. Opponents always stay as their real
sampled season — fairness rule.

---

## Part 10 — The website (the frontend)

This is `frontend/`, written in **React** (a popular toolkit for building web interfaces) with **TypeScript**.
A few concepts:

- A **component** is a reusable piece of screen — a button, a chart, a pitch diagram — defined once and used
  wherever needed. Components live in `frontend/src/components/`.
- A **screen** is a full page made of components. They live in `frontend/src/screens/`.
- **State** is the data the screen currently holds (which view you're on, your current game, the season result).
  When state changes, React automatically redraws the affected parts.

`App.tsx` is the conductor. It's a simple **view machine**: it tracks which screen you're on
(`setup → draft → playback → results`, plus `explore`) and swaps between them with a page-turn animation. It
holds the shared data (your run, the replay, the season) and passes it down to each screen.

The screens:

- **`SetupScreen`** — choose your settings and start, or browse the archive.
- **`DraftScreen`** — spin, see the reveal, place players on the pitch, scout opponents.
- **`PlaybackScreen`** — watch the season unfold matchday by matchday, with a live-updating table. Occasionally
  it pauses and asks you to *predict* a result in your own fixtures (the "pundit" mini-game in `predictions.ts`)
  — fun fact: the result is already decided by the sim, so you're really reading your own team.
- **`ResultsScreen`** — the debrief: final table, your stats, the forecast chart with your season pinned on it,
  awards, and clickable teams to inspect any club's squad and its forecast.
- **`ExploreScreen`** — "the Almanac": browse every top-5 squad from FIFA 07 to FC 26, read-only.

`api.ts` is the browser's phone line to the server — typed functions like `createRun(...)`, `spin(...)`,
`simulate(...)` that send the web requests from Part 8 and hand back the replies. The TypeScript "types" in this
file mirror the Java records on the server, so the two sides agree on the shape of every message.

The look is an editorial "Season Almanac" yearbook theme (serif headlines, a warm near-black background, one
gold accent). The colour and layout tokens are defined in `frontend/src/index.css`.

---

## Part 11 — Following one click all the way through

Let's trace **"I clicked Simulate"** end to end, to see the layers cooperate:

1. **Browser** (`ResultsScreen`/`DraftScreen` → `api.ts`): you click the button. `api.ts` sends
   `POST /api/runs/{your-id}/simulate` to the server on port 8080.
2. **API layer** (`DraftRunController.java`): the waiter receives the request, finds your game by id, and asks
   the service to do the work.
3. **Service** (`DraftRunService.simulate(...)`): the referee checks all 11 slots are filled, then:
   - builds *your* team into an `Xi` (Part 4),
   - builds the 19 opponents via `opponentsFor(...)` → `OpponentPyramid` (Part 7),
   - runs `SeasonSimulator.simulate(...)` (Part 6) to play the 38 games,
   - runs `MonteCarlo.run(...)` (Part 7) to compute the forecast for comparison.
4. **Engine** (`MatchEngine`, `SeasonSimulator`, `MonteCarlo`): the pure maths happens here — no idea a website
   exists. It returns a big `SeasonResult` + a `MonteCarlo.Outcome`.
5. **Translation** (`SeasonViewMapper.java`): the raw engine result is converted into a tidy, browser-friendly
   shape (`SeasonReplayView`) — only the fields the screen needs, named clearly.
6. **Back to the browser**: the reply travels back. `api.ts` receives it; `App.tsx` stores it and switches to
   the `playback` screen; React draws the matchdays and, at the end, the debrief and the forecast chart.

Every layer did exactly its one job and trusted the next. That's the whole architecture in one journey.

---

## Part 12 — The "rules that must never break" (invariants)

These are promises the code makes to itself. Breaking one quietly corrupts the game, so they're guarded by
automated tests (`EngineTest.java`). In plain terms:

1. **One way to measure squad strength.** Every squad's quality is `optimalStrength()` (its best XI's rating) —
   used for your team, opponents, and tiers alike. No special-case ratings anywhere.
2. **Everything flows from the seed.** Same seed ⟶ identical season, always. This is what makes forecasts match
   results and lets games be replayed.
3. **Stats are per (team, player), never global.** Because one real player can be on two teams at once.
4. **Players only go in positions they can play.** Fallbacks never put a keeper outfield or an outfielder in
   goal.
5. **Two separate layers: prediction vs reality.** The forecast (no specific opponents) vs the actual simulated
   season. The gap between them *is* the drama (you over- or under-performed).
6. **Opponents are always their real season**, even when *you're* in Prime mode. Fairness.

### Automated tests, briefly

A **test** is a small program that checks the real program behaves correctly, and fails loudly if not.
`EngineTest.java` is worth reading even as a beginner — it reads almost like English ("stronger teams earn more
points", "a 90-rated team lands near its expected ~89 points", "the same seed gives an identical season"). It's
effectively a checklist of what the engine promises. You run all tests with `mvn test`.

---

## Part 13 — How to run it and poke at it

You need **Java** and **Maven** (the Java build tool, the `mvn` command) for the server, and **Node.js**
(the `npm` command) for the website.

```bash
# 1) Start the server (loads all the football data, listens on port 8080)
mvn spring-boot:run

# 2) In another terminal, start the website (opens on port 5173, forwards to 8080)
cd frontend && npm install && npm run dev

# 3) Run the automated tests to confirm the engine behaves
mvn test

# 4) See the engine think with no website/server at all — prints a calibration report:
mvn -q compile && java -cp target/classes com.draft.footy.Demo data/male_players_all.csv
```

Good first experiments (low risk, high learning):

- Open `EngineTest.java` and just read the test names — it's the engine's behaviour in plain words.
- Open `Player.java`, `Line.java`, `Formation.java` — three tiny files that define the whole vocabulary.
- Change a tuning knob in `MatchEngine.java` (e.g. `HOME_ADV`), run the `Demo` command, and watch the numbers
  move. Then put it back. This is the fastest way to *feel* how the engine works.

A suggested reading order for the code, easiest to hardest:
`Player` → `Line` → `Formation` → `Xi` → `ClubSeason.buildXi` → `MatchEngine` → `SeasonSimulator` →
`MonteCarlo` → `EngineTest` → then the `api/` files → then `frontend/`.

---

## Part 14 — Glossary

- **API** — the set of web addresses the browser uses to talk to the server (the "waiter").
- **Backend / server** — the Java program doing the thinking (the "kitchen").
- **Calibration** — tuning the engine's knobs so the simulated league matches real football statistics.
- **Class / record / object** — a named bundle of related data (and sometimes functions).
- **ClubSeason** — all the players of one real club in one real season.
- **CSV** — a plain-text data table; the format the football data is stored in.
- **Dixon-Coles** — the football-statistics model used to turn "expected goals" into a realistic scoreline.
- **Frontend** — the website you see and click (the "dining room").
- **Function / method** — a named recipe: inputs in, output out.
- **H2** — the small in-memory database the server uses to remember a game.
- **Java / TypeScript** — the two programming languages: Java on the server, TypeScript in the browser.
- **lambda** — statistician's word for "expected number of goals" for a team in a match.
- **List / Map** — an ordered collection / a key→value lookup table.
- **Monte Carlo** — estimating odds by simulating something thousands of times and counting outcomes.
- **Overall** — a player's single 0–99 quality rating.
- **Package** — a folder-like namespace grouping related Java files (e.g. `com.draft.footy`).
- **REST** — a tidy, conventional style of API using web verbs like GET and POST.
- **Seed** — the starting number for the randomness; same seed ⟶ identical results.
- **State machine** — a system that moves through defined stages, where actions are only valid in the right one.
- **Test** — a small program that checks the real program behaves correctly.
- **Tier** — a quality band of squad (Minnow up to Iconic) that a spin can land on.
- **Xi** — football slang for a starting eleven; here, one concrete team in a formation.
```
