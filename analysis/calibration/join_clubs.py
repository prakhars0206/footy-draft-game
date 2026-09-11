#!/usr/bin/env python3
"""
Join football-data.co.uk team names onto the FIFA club-season pool.

Phase M2 of the calibration plan. Both sides are keyed on (league, season, normalised club name):

  results side  data/historical_data/{league}/{season}.csv   -> HomeTeam / AwayTeam
  FIFA side     analysis/calibration/data/club_seasons.csv   -> from ClubSeasonExport

`norm_club` is a faithful port of ClubSeason.normClub() — strip accents, drop club-type tokens
(FC/AC/CF/SS...), keep [a-z0-9]. Whatever that doesn't resolve goes in club_aliases.json by hand.

    python3 analysis/calibration/join_clubs.py --league prem   # the spike: Premier League only
    python3 analysis/calibration/join_clubs.py                 # all five leagues
    python3 analysis/calibration/join_clubs.py --write-stubs   # append misses to club_aliases.json

Writes analysis/calibration/data/joined.csv — one row per matched team-season, carrying the FIFA
squad features plus that team's real results. That's the regression's input table.
"""

from __future__ import annotations

import argparse
import csv
import difflib
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "data" / "historical_data"
DATA = Path(__file__).resolve().parent / "data"
CLUB_SEASONS = DATA / "club_seasons.csv"
ALIASES = Path(__file__).resolve().parent / "club_aliases.json"
JOINED = DATA / "joined.csv"

# Our folder name -> the league label FifaDataLoader.canonicalLeague() emits.
LEAGUE_NAME = {
    "prem": "Premier League",
    "laliga": "La Liga",
    "seriea": "Serie A",
    "bundesliga": "Bundesliga",
    "ligue1": "Ligue 1",
}

# Mirrors ClubSeason.normClub()'s token list exactly — keep the two in sync.
_TOKENS = re.compile(
    r"\b(fc|cf|sc|cd|ac|afc|ss|ssc|as|rc|rcd|ud|sd|cp|club|de|del|da|do|the|deportivo|calcio)\b"
)


def norm_club(name: str) -> str:
    """Port of ClubSeason.normClub(). Must stay byte-identical in behaviour to the Java version."""
    s = unicodedata.normalize("NFD", name or "")
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")  # drop combining marks
    s = _TOKENS.sub(" ", s.lower())
    return re.sub(r"[^a-z0-9]", "", s)


def season_label(code: str) -> str:
    """'1718' -> '2017/18', matching FifaDataLoader.seasonFor()."""
    return f"{2000 + int(code[:2])}/{code[2:]}"


def suggest(raw: str, pool: list[str]) -> list[str]:
    """
    Propose FIFA club names for an unmatched result-side name. Containment first (the result name is almost
    always a shortening of the FIFA one — "Bayern Munich" vs "FC Bayern München"), then fuzzy as a backstop.
    Returns every plausible spelling, since a club's name drifts across editions and all variants are needed.
    """
    n = norm_club(raw)
    if not n:
        return []
    hits = [c for c in pool if n in norm_club(c) or norm_club(c) in n]
    if hits:
        return sorted(hits, key=lambda c: (abs(len(norm_club(c)) - len(n)), c))
    # No containment — fall back to edit distance on the normalised forms.
    close = difflib.get_close_matches(n, [norm_club(c) for c in pool], n=3, cutoff=0.72)
    return [c for c in pool if norm_club(c) in close]


def load_aliases() -> dict:
    if not ALIASES.exists():
        return {}
    return json.loads(ALIASES.read_text())


def alias_keys(alias, raw: str) -> list[str]:
    """
    Candidate norm_club keys for one result-side name.

    An alias value may be a single FIFA club name or a LIST of them, because sofifa's spelling drifts
    between editions ("FC Köln" in one, "1. FC Köln" in another) and those normalise differently. Each
    candidate is tried in turn against the season actually being matched.
    """
    if alias is None:
        return [norm_club(raw)]
    if isinstance(alias, str):
        return [norm_club(alias)]
    return [norm_club(a) for a in alias]


def load_fifa(leagues: list[str]) -> dict:
    """(league, season, norm_club) -> the exported squad-feature row."""
    if not CLUB_SEASONS.exists():
        sys.exit(
            f"missing {CLUB_SEASONS}\n"
            "run:  mvn -q compile && java -cp target/classes com.draft.footy.ClubSeasonExport"
        )
    wanted = {LEAGUE_NAME[l] for l in leagues}
    out = {}
    with CLUB_SEASONS.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            if row["league"] in wanted:
                out[(row["league"], row["season"], row["norm_club"])] = row
    return out


def load_results(league: str) -> dict:
    """season_label -> {raw team name -> {played, gf, ga, home_g, away_g, ...}} plus the raw fixtures."""
    seasons = {}
    for path in sorted((RESULTS / league).glob("*.csv")):
        label = season_label(path.stem)
        teams = defaultdict(lambda: dict(played=0, gf=0, ga=0, wins=0, draws=0, losses=0))
        fixtures = []
        with path.open(newline="", encoding="utf-8-sig") as fh:
            for r in csv.DictReader(fh):
                h, a = (r.get("HomeTeam") or "").strip(), (r.get("AwayTeam") or "").strip()
                if not h or not a or not (r.get("FTHG") or "").strip():
                    continue
                try:
                    hg, ag = int(r["FTHG"]), int(r["FTAG"])
                except ValueError:
                    continue
                fixtures.append((h, a, hg, ag))
                for team, gf, ga in ((h, hg, ag), (a, ag, hg)):
                    t = teams[team]
                    t["played"] += 1
                    t["gf"] += gf
                    t["ga"] += ga
                    t["wins" if gf > ga else "draws" if gf == ga else "losses"] += 1
        if teams:
            seasons[label] = {"teams": dict(teams), "fixtures": fixtures}
    return seasons


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--league", action="append", choices=sorted(LEAGUE_NAME),
                    help="restrict to one league (repeatable); default all five")
    ap.add_argument("--write-stubs", action="store_true",
                    help="append unmatched names to club_aliases.json as empty stubs to fill in")
    ap.add_argument("--suggest", action="store_true",
                    help="propose alias candidates for every unmatched name, for review")
    args = ap.parse_args()

    leagues = args.league or sorted(LEAGUE_NAME)
    aliases = load_aliases()
    fifa = load_fifa(leagues)

    joined_rows = []
    unmatched = defaultdict(set)   # league -> raw result-side names with no FIFA counterpart
    orphan_fifa = defaultdict(set) # league -> FIFA club-seasons no result row matched
    matched = 0
    total = 0

    for league in leagues:
        lname = LEAGUE_NAME[league]
        la = aliases.get(league, {})
        seasons = load_results(league)

        for label, payload in seasons.items():
            seen_keys = set()
            for raw, stats in payload["teams"].items():
                total += 1
                row = key = None
                for nc in alias_keys(la.get(raw), raw):   # hand alias first, else the raw name
                    candidate = (lname, label, nc)
                    if candidate in fifa:
                        key, row = candidate, fifa[candidate]
                        break
                if row is None:
                    unmatched[league].add(raw)
                    continue
                matched += 1
                seen_keys.add(key)
                joined_rows.append({
                    "league": lname, "season": label,
                    "club": row["club"], "result_name": raw,
                    "edition": row["edition"],
                    "overall": row["overall"],
                    "attack_rating": row["attack_rating"],
                    "defence_rating": row["defence_rating"],
                    "mean_att": row["mean_att"], "mean_mid": row["mean_mid"],
                    "mean_def": row["mean_def"], "mean_gk": row["mean_gk"],
                    "depth_12_16": row["depth_12_16"],
                    "played": stats["played"], "gf": stats["gf"], "ga": stats["ga"],
                    "wins": stats["wins"], "draws": stats["draws"], "losses": stats["losses"],
                    "points": stats["wins"] * 3 + stats["draws"],
                })
            for k in fifa:
                if k[0] == lname and k[1] == label and k not in seen_keys:
                    orphan_fifa[league].add(f"{label} {k[2]}")

    # ---- report ----
    pct = 100.0 * matched / total if total else 0
    print(f"matched {matched}/{total} team-seasons  ({pct:.1f}%)\n")

    for league in leagues:
        miss, orph = sorted(unmatched[league]), sorted(orphan_fifa[league])
        status = "ok" if not miss else f"{len(miss)} unmatched"
        print(f"  {league:<11} {status}")
        for m in miss[:40]:
            print(f"      no FIFA match: {m!r}  (norm: {norm_club(m)!r})")
        if len(miss) > 40:
            print(f"      … and {len(miss) - 40} more")
        if orph and miss:
            print(f"      FIFA side unclaimed: {', '.join(orph[:8])}{' …' if len(orph) > 8 else ''}")

    if args.suggest and any(unmatched.values()):
        print("\n--- alias suggestions (REVIEW before pasting into club_aliases.json) ---")
        for league in leagues:
            if not unmatched[league]:
                continue
            lname = LEAGUE_NAME[league]
            # Every FIFA spelling in this league, so a club with edition-drift yields all its variants.
            pool = sorted({r["club"] for k, r in fifa.items() if k[0] == lname})
            print(f'\n  "{league}": {{')
            for raw in sorted(unmatched[league]):
                cands = suggest(raw, pool)
                if len(cands) == 1:
                    print(f'    {json.dumps(raw)}: {json.dumps(cands[0], ensure_ascii=False)},')
                elif cands:
                    print(f'    {json.dumps(raw)}: {json.dumps(cands, ensure_ascii=False)},')
                else:
                    print(f'    {json.dumps(raw)}: "",   // no candidate — check by hand')
            print("  },")

    if args.write_stubs and any(unmatched.values()):
        for league, names in unmatched.items():
            aliases.setdefault(league, {})
            for n in names:
                aliases[league].setdefault(n, "")
        ALIASES.write_text(json.dumps(aliases, indent=2, sort_keys=True, ensure_ascii=False) + "\n")
        print(f"\nwrote stubs to {ALIASES} — fill in the empty strings with the FIFA club name")

    DATA.mkdir(parents=True, exist_ok=True)
    if joined_rows:
        with JOINED.open("w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(joined_rows[0]))
            w.writeheader()
            w.writerows(joined_rows)
        print(f"\nwrote {len(joined_rows)} joined team-seasons -> {JOINED}")

    return 0 if pct >= 95 else 1


if __name__ == "__main__":
    sys.exit(main())
