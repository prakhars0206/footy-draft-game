#!/usr/bin/env python3
"""
Fetch historical top-5-league results from football-data.co.uk.

Phase M1 of the calibration plan: the real match results the Dixon-Coles MLE is fit to.
Covers FIFA 07 -> EA FC 26, i.e. seasons 2006/07 -> 2025/26, matching the FIFA pool span.

URL scheme:  https://www.football-data.co.uk/mmz4281/{SEASON}/{LEAGUE}.csv
             SEASON = "0607" .. "2526"       LEAGUE = E0 SP1 I1 D1 F1

Writes to   data/historical_data/{league}/{season}.csv   (gitignored, like the FIFA CSVs).
Resumable: files already on disk are skipped, so a failed run can just be re-run.

    python3 analysis/calibration/fetch_results.py            # fetch everything missing
    python3 analysis/calibration/fetch_results.py --force    # re-download even if present
    python3 analysis/calibration/fetch_results.py --verify   # don't fetch, just audit what's there
"""

from __future__ import annotations

import argparse
import csv
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "https://www.football-data.co.uk/mmz4281"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "historical_data"

# football-data division code -> our folder name. Folder names match what's already on disk.
LEAGUES = {
    "E0": "prem",
    "SP1": "laliga",
    "I1": "seriea",
    "D1": "bundesliga",
    "F1": "ligue1",
}

# Expected matches per season. Bundesliga is 18 teams (306); the rest are 20 (380).
# Ligue 1 dropped to 18 teams from 2023/24, so it's 306 from "2324" onward.
EXPECTED = {"bundesliga": 306, "prem": 380, "laliga": 380, "seriea": 380, "ligue1": 380}

# FIFA 07 -> 2006/07 ... EA FC 26 -> 2025/26.
SEASONS = [f"{y % 100:02d}{(y + 1) % 100:02d}" for y in range(2006, 2026)]

REQUIRED_COLS = ["Date", "HomeTeam", "AwayTeam", "FTHG", "FTAG"]
PAUSE_SECONDS = 0.4  # be polite to a free, community-run host


def expected_rows(league: str, season: str) -> int:
    if league == "ligue1" and int(season[:2]) >= 23:
        return 306
    return EXPECTED[league]


def fetch(season: str, div: str) -> bytes:
    url = f"{BASE}/{season}/{div}.csv"
    req = urllib.request.Request(url, headers={"User-Agent": "footy-draft-calibration/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def audit(path: Path, league: str, season: str) -> tuple[int, list[str]]:
    """Return (data row count, problems) for one downloaded file."""
    problems: list[str] = []
    # football-data ships a UTF-8 BOM and trailing blank lines.
    with path.open(newline="", encoding="utf-8-sig") as fh:
        rows = [r for r in csv.DictReader(fh) if (r.get("HomeTeam") or "").strip()]

    if not rows:
        return 0, ["empty"]

    missing = [c for c in REQUIRED_COLS if c not in rows[0]]
    if missing:
        problems.append(f"missing columns: {','.join(missing)}")

    want = expected_rows(league, season)
    if len(rows) != want:
        problems.append(f"{len(rows)} matches, expected {want}")

    if "B365H" not in rows[0]:
        problems.append("no B365 odds (fine pre-2005, unexpected here)")

    return len(rows), problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--force", action="store_true", help="re-download files already on disk")
    ap.add_argument("--verify", action="store_true", help="audit what's on disk, fetch nothing")
    args = ap.parse_args()

    fetched = skipped = failed = 0
    total_matches = 0
    warnings: list[str] = []

    for div, league in LEAGUES.items():
        (OUT / league).mkdir(parents=True, exist_ok=True)

        for season in SEASONS:
            path = OUT / league / f"{season}.csv"
            label = f"{league}/{season}"

            if args.verify:
                if not path.exists():
                    warnings.append(f"{label}: MISSING")
                    failed += 1
                    continue
            elif path.exists() and not args.force:
                skipped += 1
            else:
                try:
                    path.write_bytes(fetch(season, div))
                    fetched += 1
                    print(f"  fetched {label}", flush=True)
                except urllib.error.HTTPError as e:
                    warnings.append(f"{label}: HTTP {e.code}")
                    failed += 1
                    continue
                except Exception as e:  # network flake — resumable, so just note it
                    warnings.append(f"{label}: {type(e).__name__} {e}")
                    failed += 1
                    continue
                time.sleep(PAUSE_SECONDS)

            n, problems = audit(path, league, season)
            total_matches += n
            for p in problems:
                warnings.append(f"{label}: {p}")

    print()
    print(f"fetched {fetched}   skipped {skipped}   failed {failed}")
    print(f"total matches on disk: {total_matches:,}")

    if warnings:
        print(f"\n{len(warnings)} warning(s):")
        for w in warnings:
            print(f"  ! {w}")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
