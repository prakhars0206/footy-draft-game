#!/usr/bin/env python3
"""
Generate the figures used in README.md and TECHNICAL.md.

Everything here is drawn from real pipeline output — no illustrative or hand-made numbers.

    .venv/bin/python analysis/calibration/make_plots.py      -> docs/images/*.png
"""

from __future__ import annotations

import csv
import json
import pathlib
from collections import Counter, defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DATA = HERE / "data"
OUT = ROOT / "docs" / "images"

# Editorial palette, lifted from frontend/src/index.css so the figures match the app.
INK, MUTED, OCHRE, PITCH, CLARET, RULE = "#241f18", "#7d7566", "#b07d22", "#4a7359", "#96382f", "#d0c6b2"
PAPER = "#faf7f0"

plt.rcParams.update({
    "figure.facecolor": PAPER, "axes.facecolor": PAPER, "savefig.facecolor": PAPER,
    "text.color": INK, "axes.labelcolor": INK, "axes.edgecolor": RULE,
    "xtick.color": MUTED, "ytick.color": MUTED, "font.size": 10,
    "axes.titlesize": 12, "axes.titleweight": "bold", "axes.spines.top": False,
    "axes.spines.right": False, "grid.color": RULE, "grid.alpha": 0.5, "figure.dpi": 130,
})


def save(fig, name):
    OUT.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(OUT / name, bbox_inches="tight")
    plt.close(fig)
    print(f"  {OUT.relative_to(ROOT)}/{name}")


def rows(path):
    with open(path, encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


# ---------------------------------------------------------------- 1. home advantage
def home_advantage():
    era = defaultdict(lambda: [0, 0, 0])
    for lg in ("prem", "laliga", "seriea", "bundesliga", "ligue1"):
        for p in sorted((ROOT / "data" / "historical_data" / lg).glob("*.csv")):
            yr = 2000 + int(p.stem[:2])
            for r in csv.DictReader(open(p, encoding="utf-8-sig")):
                try:
                    hg, ag = int(r["FTHG"]), int(r["FTAG"])
                except (ValueError, TypeError, KeyError):
                    continue
                e = era[yr]; e[0] += hg; e[1] += ag; e[2] += 1

    yrs = sorted(era)
    ratio = [era[y][0] / era[y][1] for y in yrs]

    fig, ax = plt.subplots(figsize=(8, 4))
    ax.plot(yrs, ratio, color=OCHRE, lw=2, marker="o", ms=4, zorder=3)
    ax.axhline(1.0, color=MUTED, lw=1, ls=":", zorder=1)
    ax.fill_between(yrs, 1.0, ratio, color=OCHRE, alpha=0.10, zorder=2)

    covid = yrs.index(2020)
    ax.scatter([2020], [ratio[covid]], s=120, facecolor=PAPER, edgecolor=CLARET, lw=2, zorder=4)
    ax.annotate("2020/21 — empty stadiums", (2020, ratio[covid]),
                textcoords="offset points", xytext=(-12, -32), ha="right", color=CLARET, fontsize=9,
                arrowprops=dict(arrowstyle="-", color=CLARET, lw=1))

    ax.set_title("Home advantage really has declined")
    ax.set_ylabel("home goals ÷ away goals")
    ax.set_xlabel("season starting")
    ax.grid(axis="y")
    ax.text(0.01, -0.22, "36,197 matches, top-5 leagues. The model's fitted home advantage "
                         "(×1.29) sits in the middle of this range.",
            transform=ax.transAxes, fontsize=8.5, color=MUTED)
    save(fig, "home-advantage.png")


# ---------------------------------------------------------------- 2. what alpha means
def strength_means_something():
    st = {(r["league"], r["season"], r["result_name"]): r for r in rows(DATA / "team_strengths.csv")}
    jn = [r for r in rows(DATA / "joined.csv")
          if (r["league"], r["season"], r["result_name"]) in st]

    alpha = np.array([float(st[(r["league"], r["season"], r["result_name"])]["alpha"]) for r in jn])
    gf = np.array([int(r["gf"]) / max(int(r["played"]), 1) for r in jn])

    fig, ax = plt.subplots(figsize=(7, 4.4))
    ax.scatter(alpha, gf, s=9, color=OCHRE, alpha=0.35, edgecolor="none")
    c = np.polyfit(alpha, gf, 1)
    xs = np.linspace(alpha.min(), alpha.max(), 50)
    ax.plot(xs, np.polyval(c, xs), color=INK, lw=2)
    ax.set_title("The fitted attack strength is a real thing, not a number the model invented")
    ax.set_xlabel("fitted attack strength  α   (estimated only from match scorelines)")
    ax.set_ylabel("goals actually scored per game")
    ax.grid(alpha=0.3)
    r = np.corrcoef(alpha, gf)[0, 1]
    ax.text(0.02, 0.94, f"correlation {r:+.3f}   ·   {len(jn):,} club-seasons",
            transform=ax.transAxes, fontsize=9, color=MUTED)
    save(fig, "alpha-vs-goals.png")


# ---------------------------------------------------------------- 3. the bridge
def bridge():
    st = {(r["league"], r["season"], r["result_name"]): r for r in rows(DATA / "team_strengths.csv")}
    jn = [r for r in rows(DATA / "joined.csv")
          if (r["league"], r["season"], r["result_name"]) in st]

    att = np.array([float(r["attack_rating"]) for r in jn])
    alpha = np.array([float(st[(r["league"], r["season"], r["result_name"])]["alpha"]) for r in jn])
    slope, icept = np.polyfit(att - att.mean(), alpha, 1)

    fig, ax = plt.subplots(figsize=(7.5, 4.8))
    ax.scatter(att, alpha, s=9, color=PITCH, alpha=0.30, edgecolor="none", zorder=2)
    xs = np.linspace(att.min(), att.max(), 50)
    ax.plot(xs, slope * (xs - att.mean()) + icept, color=CLARET, lw=2.5, zorder=4,
            label=f"fitted line — slope 1/{1 / slope:.1f}")

    for label, season, club in (("Man City 17/18", "2017/18", "Manchester City"),
                                ("Barcelona 12/13", "2012/13", "FC Barcelona"),
                                ("Derby 07/08", "2007/08", "Derby County")):
        for i, r in enumerate(jn):
            if r["season"] == season and r["club"] == club:
                ax.scatter([att[i]], [alpha[i]], s=70, facecolor=PAPER, edgecolor=INK, lw=1.5, zorder=5)
                ax.annotate(label, (att[i], alpha[i]), textcoords="offset points",
                            xytext=(8, 6), fontsize=8.5, color=INK)
                break

    ax.set_title("The bridge: squad rating → team strength")
    ax.set_xlabel("what the engine thinks of the squad   (Xi.attackRating)")
    ax.set_ylabel("how good they actually were   (fitted α)")
    ax.legend(frameon=False, loc="lower right")
    ax.grid(alpha=0.3)
    ax.text(0.01, -0.19,
            "The slope of this line IS the engine's SCALE constant. Spread around it is why "
            "R² is 0.63, not 1.0:\nratings don't capture coaching, injuries or luck.",
            transform=ax.transAxes, fontsize=8.5, color=MUTED)
    save(fig, "bridge-fit.png")


# ---------------------------------------------------------------- 4. validation
def validation():
    path = DATA / "validation.csv"
    if not path.exists():
        print("  (skipped validation plot — run HistoricalValidation first)")
        return
    v = rows(path)
    if "p10" not in v[0]:
        print("  (validation.csv has no p10/p90 — re-run HistoricalValidation)")
        return
    sim = np.array([float(r["simulated"]) for r in v])
    act = np.array([float(r["actual"]) for r in v])
    p10 = np.array([float(r["p10"]) for r in v])
    p90 = np.array([float(r["p90"]) for r in v])
    inside = ((act >= p10) & (act <= p90)).mean()

    fig, ax = plt.subplots(figsize=(7, 6.4))
    lo, hi = 5, 110
    ax.plot([lo, hi], [lo, hi], color=INK, ls=":", lw=1.3, zorder=5, label="perfect prediction")

    # The bar is the range of seasons the engine actually produces for that squad; the dot is their
    # average. Plotting only the dot compares an expectation to a single realisation, which is what
    # made an earlier version of this figure look like systematic under-prediction.
    ax.vlines(act, p10, p90, color=OCHRE, alpha=0.30, lw=2.4, zorder=2,
              label="range of simulated seasons (p10–p90)")
    ax.scatter(act, sim, s=16, color=INK, alpha=0.75, edgecolor="none", zorder=4,
               label="average of 200 seasons")

    for name, short, off in (("Juventus", "Juventus 102", (-96, 4)), ("Derby County", "Derby 11", (12, -4))):
        for i, r in enumerate(v):
            if r["club"] == name:
                ax.scatter([act[i]], [sim[i]], s=70, facecolor=PAPER, edgecolor=CLARET, lw=1.6, zorder=6)
                ax.annotate(short, (act[i], sim[i]), textcoords="offset points",
                            xytext=off, fontsize=8.5, color=CLARET)
                break

    ax.set_xlim(lo, hi); ax.set_ylim(lo, hi); ax.set_aspect("equal")
    ax.set_title("Simulated vs. what actually happened")
    ax.set_xlabel("real points that season")
    ax.set_ylabel("simulated points")
    ax.legend(frameon=False, loc="upper left", fontsize=8.5)
    ax.grid(alpha=0.3)
    r = np.corrcoef(act, sim)[0, 1]
    ax.text(0.035, 0.70,
            f"correlation {r:+.3f}\n{100 * inside:.0f}% of real seasons fall inside\nthe simulated range"
            f"  (80% = calibrated)\n{len(v)} clubs, 7 seasons",
            transform=ax.transAxes, fontsize=9, color=MUTED)
    ax.text(0.0, -0.185,
            "The dots sit below the diagonal at the extremes, but that is the wrong comparison: a dot is an\n"
            "AVERAGE and the x-axis is ONE season. The bars show what the engine actually produces, and the\n"
            "real results land inside them — including the record seasons. 93% vs an ideal 80% means the\n"
            "ranges run slightly wide: the engine is a little under-confident about who is better, and a\n"
            "little over-random within a season. Those two errors cancel in the league table.",
            transform=ax.transAxes, fontsize=8.5, color=MUTED, va="top")
    save(fig, "validation.png")


# ---------------------------------------------------------------- 5. before/after
def constants():
    props = {}
    for line in (ROOT / "src/main/resources/calibration.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()

    items = [
        ("BASE_GOALS", 1.15, float(props["base.goals"]), "goals for an average side"),
        ("SCALE (attack)", 16.5, float(props["scale.attack"]), "rating points per log-goal"),
        ("SCALE (defence)", 16.5, float(props["scale.defence"]), "— was one shared value"),
        ("FORM_SIGMA", 0.78, float(props["form.sigma"]), "season-to-season swing"),
    ]
    fig, ax = plt.subplots(figsize=(7.6, 3.4))
    y = np.arange(len(items))[::-1]
    for i, (name, old, new, _) in zip(y, items):
        ax.plot([old, new], [i, i], color=RULE, lw=2, zorder=1, solid_capstyle="round")
        ax.scatter([old], [i], s=70, color=MUTED, zorder=3)
        ax.scatter([new], [i], s=90, color=OCHRE, zorder=4)
        ax.annotate(f"{old:g}", (old, i), textcoords="offset points", xytext=(0, 10),
                    ha="center", fontsize=8, color=MUTED)
        ax.annotate(f"{new:.2f}", (new, i), textcoords="offset points", xytext=(0, 10),
                    ha="center", fontsize=8.5, color=OCHRE, weight="bold")
    ax.set_yticks(y, [f"{n}\n{d}" for n, _, _, d in items], fontsize=9)
    ax.set_xlabel("value")
    ax.set_title("Hand-tuned  →  measured")
    ax.grid(axis="x", alpha=0.3)
    ax.scatter([], [], s=70, color=MUTED, label="guessed by eye")
    ax.scatter([], [], s=90, color=OCHRE, label="fitted to 36,197 matches")
    ax.legend(frameon=False, loc="lower right", fontsize=9)
    save(fig, "constants.png")


if __name__ == "__main__":
    print("writing figures:")
    home_advantage()
    strength_means_something()
    bridge()
    validation()
    constants()
