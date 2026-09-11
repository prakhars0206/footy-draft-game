#!/usr/bin/env python3
"""
Fit the Dixon-Coles model to real top-5-league results by maximum likelihood.

Phase M3 of the calibration plan. This is the step that replaces eyeballing with measurement.

The model (identical in form to MatchEngine.sampleScore / MatchEngine.tau):

    lambda_home = exp(alpha_home + beta_away + gamma)
    lambda_away = exp(alpha_away + beta_home)
    P(i,j)      = tau(i,j; lambda, mu, rho) * Poisson(i; lambda) * Poisson(j; mu)

Parameterisation
  alpha (attack) and beta (defence) are fit PER league-season — team strength is a property of
  that squad in that year, not of the club forever.
  gamma (home advantage) and rho (low-score correction) are GLOBAL. They aren't team-specific, and
  pooling across ~100 league-seasons determines them far better than 380 matches could.

Identifiability
  alpha and beta are only defined up to an additive constant (add c to every attack, subtract c
  from every defence, likelihood unchanged). Without a constraint the optimiser drifts forever.
  We impose sum(alpha) = 0 within each league-season, the standard fix.

Outputs
  data/team_strengths.csv  one row per team-season: fitted alpha, beta  -> M4's regression target
  data/global_params.json  gamma, rho, per-season gamma, and fit diagnostics

    .venv/bin/python analysis/calibration/fit_dixon_coles.py
    .venv/bin/python analysis/calibration/fit_dixon_coles.py --league prem   # the spike
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.optimize import minimize
from scipy.stats import spearmanr

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RESULTS = ROOT / "data" / "historical_data"
DATA = HERE / "data"

LEAGUE_NAME = {
    "prem": "Premier League",
    "laliga": "La Liga",
    "seriea": "Serie A",
    "bundesliga": "Bundesliga",
    "ligue1": "Ligue 1",
}

MAX_GOALS = 12  # matches MatchEngine.GRID


def season_label(code: str) -> str:
    return f"{2000 + int(code[:2])}/{code[2:]}"


def load_fixtures(leagues: list[str]) -> dict:
    """(league label, season label) -> list of (home, away, hg, ag)."""
    out = {}
    for lg in leagues:
        for path in sorted((RESULTS / lg).glob("*.csv")):
            fixtures = []
            with path.open(newline="", encoding="utf-8-sig") as fh:
                for r in csv.DictReader(fh):
                    h, a = (r.get("HomeTeam") or "").strip(), (r.get("AwayTeam") or "").strip()
                    try:
                        hg, ag = int(r["FTHG"]), int(r["FTAG"])
                    except (ValueError, TypeError, KeyError):
                        continue
                    if h and a:
                        fixtures.append((h, a, hg, ag))
            if fixtures:
                out[(LEAGUE_NAME[lg], season_label(path.stem))] = fixtures
    return out


# ----------------------------------------------------------------------------- likelihood

def tau(hg, ag, lh, la, rho):
    """
    Dixon-Coles dependence factor — adjusts only the four low-score cells, 1 elsewhere.
    Mirrors MatchEngine.tau() exactly.
    """
    t = np.ones_like(lh)
    m00 = (hg == 0) & (ag == 0)
    m01 = (hg == 0) & (ag == 1)
    m10 = (hg == 1) & (ag == 0)
    m11 = (hg == 1) & (ag == 1)
    t[m00] = 1 - lh[m00] * la[m00] * rho
    t[m01] = 1 + lh[m01] * rho
    t[m10] = 1 + la[m10] * rho
    t[m11] = 1 - rho
    return t


def block_nll(x, b, gamma, rho):
    """
    Negative log-likelihood for ONE league-season, given the global gamma/rho.

    x = [n-1 free alphas, n betas]. The last alpha is fixed by the sum-to-zero constraint, which
    is what makes alpha/beta identifiable at all (otherwise adding c to every attack and
    subtracting c from every defence leaves the likelihood unchanged and the optimiser drifts).
    """
    n = b["n_teams"]
    free_a = x[:n - 1]
    alpha = np.concatenate([free_a, [-free_a.sum()]])
    beta = x[n - 1:]

    lh = np.exp(alpha[b["hi"]] + beta[b["ai"]] + gamma)
    la = np.exp(alpha[b["ai"]] + beta[b["hi"]])
    hg, ag = b["hg"], b["ag"]

    t = np.maximum(tau(hg, ag, lh, la, rho), 1e-10)
    # log Poisson(hg; lh) + log Poisson(ag; la) + log tau. The log(k!) terms are constant in the
    # parameters, so they're dropped — the optimum is unaffected, only the nll's absolute level.
    return -np.sum(hg * np.log(lh) - lh + ag * np.log(la) - la + np.log(t))


def total_nll(blocks, xs, gamma, rho):
    return sum(block_nll(x, b, gamma, rho) for b, x in zip(blocks, xs))


def fit(blocks, max_outer=25, tol=1e-4, verbose=True):
    """
    Block coordinate descent.

    Fitting all ~3,800 parameters jointly is hopeless with finite-difference gradients: each
    gradient costs ~3,800 likelihood evaluations over all 36k matches. But given gamma and rho the
    blocks are completely independent — a league-season's alpha/beta appear in no other block. So:

        1. hold (gamma, rho) fixed, fit each league-season separately   (100 small problems)
        2. hold every alpha/beta fixed, fit (gamma, rho)                (2 parameters)
        3. repeat until the total likelihood stops improving

    Each step is cheap and monotonically decreases the same objective, so this converges reliably.
    """
    gamma, rho_raw = 0.26, np.arctanh(-0.1 / 0.2)
    xs = [np.zeros(2 * b["n_teams"] - 1) for b in blocks]
    prev = np.inf

    for it in range(1, max_outer + 1):
        rho = np.tanh(rho_raw) * 0.2

        # --- step 1: per-league-season attack/defence ---
        for i, b in enumerate(blocks):
            r = minimize(block_nll, xs[i], args=(b, gamma, rho), method="L-BFGS-B",
                         options={"maxiter": 500})
            xs[i] = r.x

        # --- step 2: the two global parameters ---
        def g_obj(p):
            return total_nll(blocks, xs, p[0], np.tanh(p[1]) * 0.2)

        rg = minimize(g_obj, np.array([gamma, rho_raw]), method="Nelder-Mead",
                      options={"xatol": 1e-6, "fatol": 1e-6, "maxiter": 300})
        gamma, rho_raw = rg.x
        cur = rg.fun

        if verbose:
            print(f"  iter {it:>2}  nll={cur:,.2f}  gamma={gamma:+.4f}  "
                  f"rho={np.tanh(rho_raw) * 0.2:+.4f}", flush=True)

        if abs(prev - cur) < tol:
            return gamma, np.tanh(rho_raw) * 0.2, xs, cur, True, it
        prev = cur

    return gamma, np.tanh(rho_raw) * 0.2, xs, prev, False, max_outer


def build_blocks(fixtures: dict):
    blocks = []
    for (league, season), fx in sorted(fixtures.items()):
        teams = sorted({t for f in fx for t in (f[0], f[1])})
        idx = {t: i for i, t in enumerate(teams)}
        blocks.append({
            "league": league, "season": season, "teams": teams, "n_teams": len(teams),
            "hi": np.array([idx[f[0]] for f in fx]),
            "ai": np.array([idx[f[1]] for f in fx]),
            "hg": np.array([f[2] for f in fx]),
            "ag": np.array([f[3] for f in fx]),
        })
    return blocks


def unpack(blocks, xs):
    """Per-block parameter vectors -> (block, alpha, beta), re-applying the sum-to-zero constraint."""
    out = []
    for b, x in zip(blocks, xs):
        n = b["n_teams"]
        free_a = x[:n - 1]
        out.append((b, np.concatenate([free_a, [-free_a.sum()]]), x[n - 1:]))
    return out


# ----------------------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--league", action="append", choices=sorted(LEAGUE_NAME))
    ap.add_argument("--maxiter", type=int, default=25, help="max outer coordinate-descent sweeps")
    args = ap.parse_args()

    leagues = args.league or sorted(LEAGUE_NAME)
    fixtures = load_fixtures(leagues)
    blocks = build_blocks(fixtures)
    n_matches = sum(len(b["hg"]) for b in blocks)
    n_params = 2 + sum(2 * b["n_teams"] - 1 for b in blocks)

    print(f"{len(blocks)} league-seasons, {n_matches:,} matches, {n_params:,} parameters")
    print("fitting (block coordinate descent)…", flush=True)

    gamma, rho, xs, nll, converged, iters = fit(blocks, max_outer=args.maxiter)
    fitted = unpack(blocks, xs)

    print(f"\n  converged={converged}  nll={nll:,.1f}  outer iterations={iters}")
    if not converged:
        print("  ! did not converge — raise --maxiter before trusting these numbers")

    print(f"\n  gamma (home adv, log-goals) = {gamma:+.4f}   -> x{np.exp(gamma):.3f} on home lambda")
    print(f"  rho   (low-score correction) = {rho:+.4f}")

    # ---- sanity check: do fitted attack ranks track the real table? ----
    rows, rhos = [], []
    gamma_by_season = defaultdict(list)
    for b, alpha, beta in fitted:
        pts = defaultdict(int)
        for hi, ai, hg, ag in zip(b["hi"], b["ai"], b["hg"], b["ag"]):
            if hg > ag:
                pts[hi] += 3
            elif hg == ag:
                pts[hi] += 1; pts[ai] += 1
            else:
                pts[ai] += 3
        strength = alpha - beta              # net strength: good attack, good (low) defence
        table = [pts[i] for i in range(b["n_teams"])]
        rhos.append(spearmanr(strength, table).statistic)
        gamma_by_season[b["season"]].append(None)

        for i, t in enumerate(b["teams"]):
            rows.append({
                "league": b["league"], "season": b["season"], "result_name": t,
                "alpha": round(float(alpha[i]), 6),
                "beta": round(float(beta[i]), 6),
                "net_strength": round(float(alpha[i] - beta[i]), 6),
                "points": table[i],
            })

    rhos = np.array(rhos)
    print(f"\n  Spearman(fitted net strength, actual points): mean {rhos.mean():.3f}, "
          f"min {rhos.min():.3f}, worst-decile {np.percentile(rhos, 10):.3f}")
    if rhos.mean() < 0.9:
        print("  ! below the 0.9 target — check the fit before trusting M4")

    DATA.mkdir(parents=True, exist_ok=True)
    out = DATA / "team_strengths.csv"
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    print(f"\nwrote {len(rows)} team-seasons -> {out}")

    (DATA / "global_params.json").write_text(json.dumps({
        "gamma": float(gamma),
        "rho": float(rho),
        "n_matches": n_matches,
        "n_league_seasons": len(blocks),
        "nll": float(nll),
        "converged": bool(converged),
        "outer_iterations": int(iters),
        "spearman_mean": float(rhos.mean()),
        "leagues": leagues,
    }, indent=2) + "\n")
    print(f"wrote global params -> {DATA / 'global_params.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
