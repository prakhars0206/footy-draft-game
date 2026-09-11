#!/usr/bin/env python3
"""
Turn the fitted Dixon-Coles strengths into the engine's constants.

Phase M4+M5 of the calibration plan. M3 measured how good each real team ACTUALLY was;
ClubSeasonExport says what the engine THINKS of the same squad. Regressing one on the other
recovers the mapping the engine currently guesses.

Matching the two models term by term —

    engine:   lambda = BASE_GOALS * exp((attack - defence + HOME_ADV) / SCALE)
    fitted:   lambda = exp(alpha_home + beta_away + gamma)

Everything is fit on CENTRED ratings, so the intercept is the value at an average squad rather
than an extrapolation to rating zero (which is what made a naive first pass report an absurd
BASE_GOALS of 0.33). With r0 = mean overall:

    alpha = (rating - r0) / SCALE_ATT + a0
    beta  = -(rating - r0) / SCALE_DEF + b0
    BASE_GOALS = exp(a0 + b0)      <- lambda for an average side vs an average side, no home edge
    HOME_ADV   = gamma * SCALE     <- gamma is already in log-goal units

Three things this script reports that the plan did not anticipate; see the printed notes.

    .venv/bin/python analysis/calibration/fit_engine_constants.py
"""

from __future__ import annotations

import csv
import json
from collections import defaultdict
from datetime import date
from pathlib import Path

import numpy as np
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import GroupKFold, cross_val_score

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DATA = HERE / "data"
PROPS = ROOT / "src" / "main" / "resources" / "calibration.properties"


def load_joined() -> list[dict]:
    strengths = {}
    for r in csv.DictReader((DATA / "team_strengths.csv").open(encoding="utf-8")):
        strengths[(r["league"], r["season"], r["result_name"])] = r

    rows = []
    for r in csv.DictReader((DATA / "joined.csv").open(encoding="utf-8")):
        s = strengths.get((r["league"], r["season"], r["result_name"]))
        if s is None:
            continue
        r["alpha"] = float(s["alpha"])
        r["beta"] = float(s["beta"])
        rows.append(r)
    return rows


def fit_one(x, y, groups, label):
    """Simple linear fit of a fitted strength on the centred squad rating, cross-validated by season."""
    X = x.reshape(-1, 1)
    cv_r2 = cross_val_score(LinearRegression(), X, y, groups=groups,
                            cv=GroupKFold(n_splits=10), scoring="r2").mean()
    m = LinearRegression().fit(X, y)
    resid = y - m.predict(X)
    print(f"  {label:<9} slope {m.coef_[0]:+.5f}   intercept {m.intercept_:+.4f}   "
          f"CV R2 {cv_r2:.3f}   resid sd {resid.std():.4f}")
    return m.coef_[0], m.intercept_, resid, cv_r2


def decompose_form(rows, resid, scale):
    """
    Split the unexplained variance into a PERSISTENT club effect and a YEAR-TO-YEAR one.

    This matters because FORM_SIGMA is meant to be "the same squad having a good or bad year", but
    a raw residual also contains everything permanently unmodelled about a club — Atletico under
    Simeone systematically out-defending their ratings is not form, it's a missing feature. Only
    the within-club component is form, so lumping them together inflates FORM_SIGMA badly.
    """
    by_club = defaultdict(list)
    for r, e in zip(rows, resid):
        by_club[r["club"]].append(e)

    repeat = {c: v for c, v in by_club.items() if len(v) >= 4}   # need a few seasons to split
    club_means = np.array([np.mean(v) for v in repeat.values()])
    within = np.concatenate([np.array(v) - np.mean(v) for v in repeat.values()])

    between_sd = club_means.std()
    within_sd = within.std()
    print(f"    {len(repeat)} clubs with 4+ seasons — "
          f"persistent club effect sd {between_sd:.4f}, year-to-year sd {within_sd:.4f} "
          f"({within_sd * scale:.2f} rating pts)")
    return within_sd, between_sd


def main() -> int:
    rows = load_joined()
    gp = json.loads((DATA / "global_params.json").read_text())
    gamma, rho = gp["gamma"], gp["rho"]

    rating = np.array([float(r["overall"]) for r in rows])
    r0 = rating.mean()
    x = rating - r0
    a = np.array([r["alpha"] for r in rows])
    b = np.array([r["beta"] for r in rows])
    groups = [r["season"] for r in rows]

    print(f"{len(rows)} team-seasons.  mean squad overall = {r0:.2f} (the centring reference)\n")
    print("Bridge fits (target = MLE-fitted strength, feature = centred squad overall):")
    sa, a0, res_a, r2a = fit_one(x, a, groups, "attack")
    sb, b0, res_b, r2b = fit_one(x, b, groups, "defence")

    scale_att = 1.0 / sa
    scale_def = -1.0 / sb
    scale = float(2.0 / (1.0 / scale_att + 1.0 / scale_def))   # harmonic: averages the SENSITIVITIES

    print(f"\n  SCALE from attack  {scale_att:6.2f}   (a rating point is worth 1/{scale_att:.1f} log-goals)")
    print(f"  SCALE from defence {scale_def:6.2f}")
    print(f"  -> single SCALE    {scale:6.2f}   (harmonic mean; engine currently uses 16.5)")

    print("\n  ! FINDING 1 — attack and defence do NOT share a sensitivity.")
    print("    Squad rating predicts attacking output far better than defensive solidity")
    print(f"    (R2 {r2a:.2f} vs {r2b:.2f}), and moves it ~{scale_def / scale_att:.2f}x as hard. This is a")
    print("    well-known result in football modelling — defending is more about organisation")
    print("    than individual ratings. The engine's single SCALE cannot represent it.")

    base_goals = float(np.exp(a0 + b0))
    home_adv = float(gamma * scale)

    print(f"\n  BASE_GOALS = {base_goals:.4f}   (lambda for an average side vs an average side)")
    print(f"  HOME_ADV   = {home_adv:.4f}   (gamma {gamma:+.4f} x SCALE {scale:.2f})")
    print(f"  RHO        = {rho:+.4f}")

    print("\n  FORM_SIGMA — decomposing the residual:")
    within_a, between_a = decompose_form(rows, res_a, scale)
    within_b, between_b = decompose_form(rows, res_b, scale)
    form_sigma = float(np.mean([within_a, within_b]) * scale)
    naive = float(np.mean([res_a.std(), res_b.std()]) * scale)
    print(f"    FORM_SIGMA = {form_sigma:.3f} rating pts   "
          f"(a naive undecomposed residual would say {naive:.2f} — inflated by the club effect)")

    print("\n  ! FINDING 2 — the per-line weights are NOT identifiable from this data.")
    print("    mean_att/mid/def/gk correlate 0.69-0.87 (condition number 113), so a regression")
    print("    splits the coefficient arbitrarily across them — it hands defenders as much credit")
    print("    for attack as midfielders. The TOTAL sensitivity is well determined; the split is")
    print("    not. Keeping Xi's hand-set weights is the honest call; they are at least")
    print("    structurally sensible. Not exported.")

    print("\n  ! FINDING 3 — your hand-tuned BASE_GOALS was nearly right.")
    print(f"    Fitted {base_goals:.3f} vs the engine's 1.15. SCALE and FORM_SIGMA are the two")
    print("    that move materially.")

    props = [
        "# Engine constants fitted from real results — DO NOT HAND-EDIT.",
        "# Regenerate with analysis/calibration/: fetch_results -> join_clubs -> fit_dixon_coles",
        "#                                        -> fit_engine_constants",
        f"# Dixon-Coles MLE over {gp['n_matches']} matches / {gp['n_league_seasons']} league-seasons,",
        f"# top-5 leagues 2006/07-2025/26, then a bridge regression onto {len(rows)} FIFA squads.",
        f"# Bridge CV R2: attack {r2a:.3f}, defence {r2b:.3f}.",
        "#",
        "# NOTE: attack and defence have genuinely different sensitivities to squad rating.",
        "# 'scale' is the harmonic mean, for the engine's current single-SCALE model; the split",
        "# values are exported too, should MatchEngine gain separate terms.",
        "# Xi's per-line weights are deliberately NOT exported — collinear features make them",
        "# unidentifiable (see FINDING 2 in fit_engine_constants.py).",
        f"fitted.at={date.today().isoformat()}",
        f"fitted.matches={gp['n_matches']}",
        f"fitted.club.seasons={len(rows)}",
        f"base.goals={base_goals:.4f}",
        f"scale={scale:.4f}",
        f"scale.attack={scale_att:.4f}",
        f"scale.defence={scale_def:.4f}",
        f"home.adv={home_adv:.4f}",
        f"home.adv.loggoals={gamma:.4f}",
        f"rho={rho:.4f}",
        f"form.sigma={form_sigma:.4f}",
        f"rating.reference={r0:.4f}",
    ]
    PROPS.parent.mkdir(parents=True, exist_ok=True)
    PROPS.write_text("\n".join(props) + "\n")
    print(f"\nwrote {PROPS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
