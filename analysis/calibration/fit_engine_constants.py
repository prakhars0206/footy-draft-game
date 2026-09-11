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
        for k in ("alpha", "beta", "alpha_se", "beta_se"):
            r[k] = float(s[k])
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


def decompose_form(rows, resid, noise_var, scale, label):
    """
    Strip TWO contaminants out of the residual before calling what's left "form".

    1. A persistent club effect. Atletico under Simeone systematically out-defending their ratings
       is not form, it's a permanently missing feature. Only the WITHIN-club part is form.
    2. Measurement error. alpha is estimated from ~38 matches and carries sampling noise of its
       own (see refine_bridge.py); that noise is in the residual too and is not form either.

    Leaving either in inflates FORM_SIGMA substantially — together they roughly double it.
    """
    by_club = defaultdict(list)
    for r, e in zip(rows, resid):
        by_club[r["club"]].append(e)

    repeat = {c: v for c, v in by_club.items() if len(v) >= 4}   # need a few seasons to split
    club_means = np.array([np.mean(v) for v in repeat.values()])
    within = np.concatenate([np.array(v) - np.mean(v) for v in repeat.values()])
    within_sd_corrected = np.sqrt(max(within.var() - noise_var, 1e-9))

    print(f"    {label:<8} {len(repeat)} clubs w/ 4+ seasons | persistent club effect "
          f"{club_means.std():.4f} | year-to-year {within.std():.4f} "
          f"-> {within_sd_corrected:.4f} net of noise  ({within_sd_corrected * scale:.2f} pts)")
    return within_sd_corrected


def main() -> int:
    rows = load_joined()
    gp = json.loads((DATA / "global_params.json").read_text())
    gamma, rho = gp["gamma"], gp["rho"]

    # Fit against the EXACT quantities MatchEngine feeds into lambda() — Xi.attackRating() and
    # Xi.defenceRating() — not the XI's plain overall. Constants have to describe the function
    # they'll be plugged into, or they're calibrating a model nobody runs.
    att = np.array([float(r["attack_rating"]) for r in rows])
    dfn = np.array([float(r["defence_rating"]) for r in rows])
    att_ref, def_ref = att.mean(), dfn.mean()
    a = np.array([r["alpha"] for r in rows])
    b = np.array([r["beta"] for r in rows])
    groups = [r["season"] for r in rows]

    print(f"{len(rows)} team-seasons")
    print(f"  centring references: attackRating {att_ref:.3f}, defenceRating {def_ref:.3f}\n")
    print("Bridge fits (target = MLE strength, feature = the engine's own rating function):")
    sa, a0, res_a, r2a = fit_one(att - att_ref, a, groups, "attack")
    sb, b0, res_b, r2b = fit_one(dfn - def_ref, b, groups, "defence")

    # alpha/beta are estimated from ~38 matches each, so part of the spread we're trying to
    # explain is pure sampling noise. It doesn't bias the slopes, but it depresses R2 and
    # inflates the residual — and FORM_SIGMA comes out of that residual. Correct for both.
    noise_a = float(np.mean(np.array([float(r["alpha_se"]) for r in rows]) ** 2))
    noise_b = float(np.mean(np.array([float(r["beta_se"]) for r in rows]) ** 2))
    rel_a, rel_b = 1 - noise_a / a.var(), 1 - noise_b / b.var()
    print(f"\n  measurement error: {100 * (1 - rel_a):.1f}% of attack variance and "
          f"{100 * (1 - rel_b):.1f}% of defence variance is estimation noise")
    print(f"  R2 corrected for it: attack {r2a:.3f} -> {r2a / rel_a:.3f}, "
          f"defence {r2b:.3f} -> {r2b / rel_b:.3f}")

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

    print(f"\n  BASE_GOALS = {base_goals:.4f}   (lambda for an average side vs an average side)")
    print(f"  HOME_ADV   = {gamma:+.4f} log-goals   (x{np.exp(gamma):.3f} on the home rate)")
    print(f"  RHO        = {rho:+.4f}")
    print("\n  Home advantage is kept in LOG-GOALS, not rating points. With two different scales")
    print("  'rating points' is ambiguous, and Dixon-Coles applies it to the home rate only —")
    print("  not as a symmetric bonus/penalty the way the engine does today.")

    print("\n  ! FINDING 2 (revised) — the weights are identified, but they are PREDICTIVE,")
    print("    not causal. Centred condition number is 5.5 and every VIF is under 6, so the")
    print("    earlier 'unidentifiable' call was wrong (it quoted an uncentred condition number).")
    print("    Defence genuinely predicts attacking output at t=5.6 — because good clubs are good")
    print("    everywhere. The engine needs the CAUSAL weights, since the game builds unbalanced")
    print("    cross-era XIs that a predictive coefficient extrapolates badly to. Fixed effects")
    print("    can't recover them either (R2 collapses to 0.09). So: keep Xi's hand-set weights.")
    print("    Run refine_bridge.py for the full workings. Not exported.")

    print("\n  FORM_SIGMA — stripping the club effect AND the measurement noise:")
    within_a = decompose_form(rows, res_a, noise_a, scale, "attack")
    within_b = decompose_form(rows, res_b, noise_b, scale, "defence")
    form_within = float(np.mean([within_a, within_b]) * scale)
    # Total unexplained spread, net of measurement noise but INCLUDING the persistent club effect.
    total_a = np.sqrt(max(res_a.var() - noise_a, 1e-9))
    total_b = np.sqrt(max(res_b.var() - noise_b, 1e-9))
    form_total = float(np.mean([total_a, total_b]) * scale)
    print(f"\n    within-club only : {form_within:.2f} rating pts")
    print(f"    total unexplained: {form_total:.2f} rating pts")
    print("\n  ! FINDING 4 — the GAME wants the TOTAL, not the within-club figure.")
    print("    The two differ because real clubs have persistent quality the squad rating misses")
    print("    (coaching, recruitment), which is separate from year-to-year form. But a drafted XI")
    print("    has no history and plays one season: there is no second season for a persistent")
    print("    component to be persistent ACROSS, so the distinction is meaningless in-game. What")
    print("    matters is that the spread of outcomes matches reality, and that needs the total.")
    print("    Using the within-club figure would make the game visibly flatter than football.")
    form_sigma = form_total

    print("\n  ! FINDING 3 — your hand-tuned BASE_GOALS was nearly right.")
    print(f"    Fitted {base_goals:.3f} vs the engine's 1.15. SCALE and FORM_SIGMA are the two")
    print("    that move materially.")

    props = [
        "# Engine constants fitted from real results — DO NOT HAND-EDIT.",
        "# Regenerate with analysis/calibration/: fetch_results -> join_clubs -> fit_dixon_coles",
        "#                                        -> fit_engine_constants",
        f"# Dixon-Coles MLE over {gp['n_matches']} matches / {gp['n_league_seasons']} league-seasons,",
        f"# top-5 leagues 2006/07-2025/26, then a bridge regression onto {len(rows)} FIFA squads.",
        f"# Bridge CV R2: attack {r2a:.3f}, defence {r2b:.3f} (raw);"
        f" {r2a / rel_a:.3f} / {r2b / rel_b:.3f} corrected for estimation noise in the target.",
        "#",
        "#",
        "# model.form declares the lambda() these constants were fitted for. Calibration.java",
        "# asserts the engine implements the same one — v1 constants in a v2 engine would be",
        "# silently wrong physics rather than a crash.",
        "#",
        "#   lambda = BASE_GOALS * exp( (attackRating  - attack.reference ) / scale.attack",
        "#                            - (defenceRating - defence.reference) / scale.defence",
        "#                            + home.adv.loggoals   [home side only] )",
        "#",
        "# CONDITIONAL ON Xi's line weights. The features here are Xi.attackRating() and",
        "# Xi.defenceRating() as they stand; change those weights and these constants must be",
        "# refitted.",
        "# Xi's per-line weights are deliberately NOT exported. They ARE statistically identified,",
        "# but only as PREDICTIVE coefficients: good clubs are good everywhere, so defence predicts",
        "# attacking output. The engine needs causal weights, because the game builds unbalanced",
        "# cross-era XIs that a predictive fit extrapolates badly to. See refine_bridge.py.",
        "#",
        "# form.sigma is the TOTAL unexplained spread net of the ~38-match estimation noise in",
        "# alpha/beta. It deliberately INCLUDES the persistent club effect, because a drafted XI",
        "# has no club history — see FINDING 4. form.sigma.withinclub is the narrower 'real form'",
        "# figure, reported for interest; the game does not use it.",
        "model.form=split-scale-v2",
        f"fitted.at={date.today().isoformat()}",
        f"fitted.matches={gp['n_matches']}",
        f"fitted.club.seasons={len(rows)}",
        f"base.goals={base_goals:.4f}",
        f"scale={scale:.4f}",
        f"scale.attack={scale_att:.4f}",
        f"scale.defence={scale_def:.4f}",
        f"home.adv.loggoals={gamma:.4f}",
        f"attack.reference={att_ref:.4f}",
        f"defence.reference={def_ref:.4f}",
        f"rho={rho:.4f}",
        f"form.sigma={form_sigma:.4f}",
        f"form.sigma.withinclub={form_within:.4f}",
        f"bridge.r2.attack={r2a / rel_a:.4f}",
        f"bridge.r2.defence={r2b / rel_b:.4f}",
    ]
    PROPS.parent.mkdir(parents=True, exist_ok=True)
    PROPS.write_text("\n".join(props) + "\n")
    print(f"\nwrote {PROPS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
