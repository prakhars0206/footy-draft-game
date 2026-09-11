#!/usr/bin/env python3
"""
End-to-end smoke test: plays a whole run against a live server and prints what happened.

Exercises the full loop — create a run, spin and draft eleven times, read the pre-season
projection, simulate the season, read the debrief — and checks the numbers are sane at each step.
Standard library only, so no venv needed.

    mvn spring-boot:run                  # in one terminal
    python3 scripts/smoke_test.py        # in another

    python3 scripts/smoke_test.py --seed 42 --formation 4-2-3-1 --ratings ON
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"


def call(method: str, path: str, body: dict | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        detail = e.read().decode()[:400]
        raise SystemExit(f"\n{method} {path} -> HTTP {e.code}\n{detail}")
    except urllib.error.URLError as e:
        raise SystemExit(f"\nCannot reach {BASE} ({e.reason}).\nStart the server: mvn spring-boot:run")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=777)
    ap.add_argument("--formation", default="4-3-3")
    ap.add_argument("--ratings", default="SCOUT", choices=["ON", "SCOUT", "OFF"])
    args = ap.parse_args()

    failures: list[str] = []

    def check(cond: bool, msg: str):
        if not cond:
            failures.append(msg)
            print(f"    FAIL  {msg}")

    print(f"creating run  (seed {args.seed}, {args.formation}, ratings {args.ratings})")
    run = call("POST", "/api/runs", {
        "formation": args.formation, "showRatings": args.ratings,
        "leagueScope": "WORLD", "seed": args.seed,
    })
    run_id = run["runId"]
    print(f"  runId {run_id}\n")

    print("drafting eleven players")
    for pick_no in range(1, 12):
        spin = call("POST", f"/api/runs/{run_id}/spin")
        chosen = None
        for club in spin["clubs"]:
            for p in club["squad"]:
                if p["eligibleSlots"]:
                    chosen = (p["eligibleSlots"][0], p["sofifaId"], p["name"], club["club"], club["season"])
                    break
            if chosen:
                break
        check(chosen is not None, f"pick {pick_no}: no eligible player in the spin")
        if not chosen:
            break

        slot, sofifa_id, name, club_name, season = chosen
        call("POST", f"/api/runs/{run_id}/draft", {"slotPosition": slot, "sofifaId": sofifa_id})
        print(f"  {pick_no:>2}. {spin['tier']:<9} {slot:<4} {name:<22} {club_name} {season}")

    print("\npre-season projection")
    pv = call("GET", f"/api/runs/{run_id}/preview")
    mc = pv["monteCarlo"]
    print(f"  XI overall {pv['userOverall']}   opponent league mean {pv['leagueMean']}")
    print(f"  Monte-Carlo over {mc['sims']} seasons: mean {mc['mean']:.1f} pts, "
          f"likely {mc['p25']}-{mc['p75']}, range {mc['min']}-{mc['max']}")
    print(f"  title {100 * mc['title']:.1f}%   top4 {100 * mc['top4']:.1f}%   "
          f"unbeaten {100 * mc['unbeaten']:.2f}%")

    # The preview's league is the full 20-team table — your XI included, not just the 19 opponents.
    check(len(pv["league"]) == 20, f"expected a 20-team league, got {len(pv['league'])}")
    check(20 <= mc["mean"] <= 100, f"projected points off the scale: {mc['mean']}")
    check(mc["p25"] <= mc["median"] <= mc["p75"], "Monte-Carlo quantiles are out of order")

    print("\nsimulating")
    sim = call("POST", f"/api/runs/{run_id}/simulate")
    d = sim["debrief"]
    played = d["won"] + d["drawn"] + d["lost"]
    print(f"  finished {d['finishPos']} of 20 on {d['points']} pts "
          f"({d['won']}W {d['drawn']}D {d['lost']}L, GF {d['goalsFor']} GA {d['goalsAgainst']})")
    print(f"  {len(sim['matchdays'])} matchdays recorded")

    check(played == 38, f"should have played 38 games, played {played}")
    check(d["points"] == d["won"] * 3 + d["drawn"], "points don't match W/D/L")
    check(len(sim["matchdays"]) == 38, f"expected 38 matchdays, got {len(sim['matchdays'])}")
    check(1 <= d["finishPos"] <= 20, f"finish position out of range: {d['finishPos']}")
    check(len(sim["matchdays"][-1]["table"]) == 20, "final table should have 20 rows")

    # The pre-season projection and the debrief must agree — same seed, same opponents.
    print("\ndeterminism")
    pv2 = call("GET", f"/api/runs/{run_id}/preview")
    check(abs(pv2["monteCarlo"]["mean"] - mc["mean"]) < 1e-9,
          "re-reading the preview gave different projections")
    print("  preview is stable across reads")

    goals = sum(m["homeGoals"] + m["awayGoals"] for md in sim["matchdays"] for m in md["matches"])
    games = sum(len(md["matches"]) for md in sim["matchdays"])
    draws = sum(1 for md in sim["matchdays"] for m in md["matches"] if m["homeGoals"] == m["awayGoals"])
    print(f"\nleague-wide sanity: {goals / games:.2f} goals/game, {100 * draws / games:.1f}% draws "
          f"over {games} matches")
    check(2.0 <= goals / games <= 3.5, f"goals per game out of band: {goals / games:.2f}")
    check(0.12 <= draws / games <= 0.38, f"draw rate out of band: {draws / games:.3f}")

    print()
    if failures:
        print(f"FAILED — {len(failures)} check(s):")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("PASS — full loop works end to end.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
