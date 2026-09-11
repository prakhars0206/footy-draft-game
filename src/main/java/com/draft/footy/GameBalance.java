package com.draft.footy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Values deliberately CHOSEN for how the game should feel — the counterpart to {@link Calibration}.
 *
 * <p>The split exists because two legitimate goals pull in opposite directions. <b>Realism</b> wants the
 * simulation to reproduce real football. <b>Playability</b> wants a good draft to be visible in the final
 * table rather than drowned by noise. Overloading one number with both jobs means quietly falsifying a
 * measurement, and then nobody can tell which numbers are evidence and which are taste.
 *
 * <p>So measurements live in {@code calibration.properties} and are never hand-edited; the knobs that bend
 * them toward a good game live here in {@code game-balance.properties} and are hand-edited freely — each
 * one carrying a written reason. That makes the honest statement available: <i>"real football has
 * FORM_SIGMA ≈ 2.30; we run at 45% of it so draft quality stays legible."</i>
 *
 * @see Calibration for the values measured from ~36,000 real matches
 */
public final class GameBalance {

    private GameBalance() { }

    /**
     * Fraction of the measured season-form spread the game actually applies.
     * <p>Real football's measured {@code FORM_SIGMA} is ~2.3 rating points (see {@link Calibration}).
     * Running at full strength makes seasons swing so hard that drafting well stops being legible in the
     * result, which is the one thing this game is about. Below 1.0 the game is calmer than reality.
     */
    public static final double FORM_SIGMA_MULTIPLIER;

    /**
     * Hard ceiling on a single team's expected goals in one match.
     * <p>Purely a design clamp, not physics: it stops the exponential producing absurd scorelines in the
     * extreme mismatches the draft can create (a 90-rated XI against a 58-rated minnow is a fixture that
     * never occurs in a real league, so the fit says nothing about it).
     */
    public static final double MAX_LAMBDA;

    static {
        Properties p = new Properties();
        try (InputStream in = GameBalance.class.getResourceAsStream("/game-balance.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // defaults below
        }
        FORM_SIGMA_MULTIPLIER = dbl(p, "form.sigma.multiplier", 1.0);
        MAX_LAMBDA            = dbl(p, "max.lambda", 2.5);
    }

    /** The form spread the simulation actually uses: measured reality, scaled by the playability knob. */
    public static double effectiveFormSigma() {
        return Calibration.FORM_SIGMA * FORM_SIGMA_MULTIPLIER;
    }

    private static double dbl(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("game-balance.properties: " + key + " is not a number: " + v, e);
        }
    }
}
