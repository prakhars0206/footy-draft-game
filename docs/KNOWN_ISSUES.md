# Known issues

Things found but not yet fixed, with enough detail to pick up cold.

---

## 1. FIFA 07–10 players have only one position each

**Found:** 12 Sep 2026, by browsing Manchester United 2007/08 in the Almanac and noticing Ronaldo wasn't in the XI.

### What's wrong

The four manually-scraped editions record a single position per player. Every other edition records two or more:

| Editions | Positions per player | Single-position |
|---|---|---|
| **FIFA 07–10** | **1.00** | **100%** |
| FIFA 11 | 2.49 | 27% |
| FIFA 12–14 | 1.67–2.22 | 32–50% |
| FIFA 15–26 | ~1.66 | ~50% |

It's in the source data, not the loader:

```
fifa_08.csv   "20801","Cristiano Ronaldo","RW","91","Manchester United",…
fifa_11.csv   "20801","Cristiano Ronaldo","RW,CAM,LW,ST","89","Real Madrid",…
```

### Consequences

**Squads get mis-fielded.** 25 of 1,952 club-seasons (1.3%) bench their highest-rated player. Man United 2007/08 is the worst case: six formations tie at 11 natural fits, and the tiebreak picks 3-5-2, which has no RW slot — so a 91-rated Ronaldo sits out.

Worth noting this is *not* a tiebreak bug. 3-5-2 wins on every measure the engine has:

| Formation | Mean overall | Engine strength | Ronaldo |
|---|---|---|---|
| 4-3-3 | 85 | 85.10 | yes |
| 3-4-3 | 85 | 85.51 | yes |
| **3-5-2** | **86** | **86.40** | no |

Two reasons: 4-3-3 forces Evra (80) and Neville (82) in at full-back where 3-5-2 uses three centre-backs, and because line strength is a *mean*, a front three of 91/90/85 averages 88.7 while a front two of 90/89 averages 89.5. Adding Ronaldo mathematically lowers the attack rating.

**Players become undraftable.** `canPlay(slot)` gates drafting, so an RW-only player cannot be drafted into 3-5-2, 4-4-2, 4-2-3-1 or 4-5-1 — four of seven formations. This applies to every player in those four editions, roughly 20% of the pool, and is the more serious effect.

### Candidate fix, and the objection to it

Backfill from the same `player_id` in later editions. 2,156 of the 5,215 players in FIFA 07–10 (41.3%) appear later with multiple positions; the rest retired before FIFA 11 and would stay rigid.

**The objection, and it's a fair one: Ronaldo in 2007/08 really did play mainly right wing.** The LW/ST/CAM in his FIFA 11 record reflect what he became at Real Madrid, not what he was under Ferguson. Backfilling from later editions risks granting players positions they grew into years afterwards — which is a different distortion, not obviously smaller than the one it fixes.

Options if picked up:

- **Narrow backfill** — nearest edition only, capped at a 2–3 year gap, exact `player_id` match. Ronaldo would take FIFA 11's positions onto 2010, and arguably 2009, but not 2007.
- **Primary-position-only backfill** — add only positions in the same line (a RW gains LW/RM but never ST), which keeps the player recognisably the same footballer.
- **Leave the data alone**, and instead let the XI builder treat adjacent positions as near-natural (RW ↔ RM ↔ LW) with a small penalty. Changes behaviour across all editions rather than just the broken four.
- **Do nothing**, document it, and accept that the earliest editions field more rigidly.

### Before doing anything

Any of the first three changes ~400 club-seasons' best XIs, hence their strengths, hence their tiers — **and hence the features the calibration was fitted on. A refit would be required** (`TECHNICAL.md` invariant 8).

### Reproduce

```bash
mvn -q compile
java -cp target/classes com.draft.footy.ClubSeasonExport   # then inspect 2007/08 rows
```

---

## 2. Opponents ignore the run's era and scope filters

The opponent league is built from the full club pool, so a "Modern '16+" run still draws opponents from any era. Deliberate for now — draft clubs never block league clubs — and both `preview()` and `simulate()` go through one seam, `DraftRunService.opponentsFor(run, rng)`, so changing it is a one-line edit that keeps projection and result consistent.

---

## 3. Runs do not survive a restart

H2 is `mem:` with `create-drop`. Switching to `jdbc:h2:file:` is not a free swap — Hibernate's schema management needs handling too.
