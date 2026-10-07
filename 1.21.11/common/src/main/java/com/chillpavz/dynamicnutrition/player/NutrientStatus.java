package com.chillpavz.dynamicnutrition.player;

import com.chillpavz.dynamicnutrition.nutrition.Nutrient;

/**
 * How a player is doing, overall and per nutrient.
 *
 * <p><b>There are THREE states in v1 and not four, and that was decided by arithmetic.</b> The
 * obvious fourth is ENGORGED, which is what similar mods use, and the balance simulation in
 * {@code nutritiondata/check_balance.py} ruled it out: with a hard ceiling and no diminishing
 * returns, a nutrient you eat regularly pins near the top, so "too much of one thing" and "plenty of
 * everything" are the same reading. A varied diet parked every nutrient at 78 to 94 out of 100,
 * which would have put the reward for playing well out of reach and the penalty for playing well two
 * points away.
 *
 * <p>Engorgement becomes meaningful once meal-versus-snack exists to make over-eating avoidable,
 * which is scheduled for v1.1. Adding the state before then would ship a punishment a player cannot
 * act on, and the umbrella already records the owner's verdict on that shape of design.
 */
public enum NutrientStatus {

    /** Below the floor. Something is actually missing from this player's diet. */
    MALNOURISHED,

    /** Between the floor and the target band. Fine, but nothing is being earned. */
    SAFE,

    /** In the target band. This is what eating a varied diet buys. */
    ON_TARGET;

    /**
     * How far a value has to move back past a line before the status is allowed to change.
     *
     * <p><b>Without this the status flickers, and the tighter the lines the worse it gets.</b>
     * Eating and decaying make every nutrient a sawtooth: measured over ten days of a varied diet,
     * carbohydrates swing between 65 and 93. A line anywhere inside that swing is crossed twice a
     * cycle, which means the effect appearing and vanishing and the action bar firing about once a
     * day, for a player whose diet never actually changed.
     *
     * <p>Three points is enough to clear the swing at the lines chosen, and it is small enough that
     * a player who genuinely slips still gets told promptly.
     */
    public static final float HYSTERESIS = 3.0F;

    /**
     * Where a value falls for THIS nutrient, with no memory.
     *
     * <p>The two lines live on {@link Nutrient} rather than here, because they differ per nutrient
     * for reasons about the real nutrient rather than about balance. See the note on the constants
     * in {@code Nutrients}.
     *
     * <p>This is the DISPLAY answer: it is what the screen's marks show, so the bar and the reading
     * beside it always agree. The status that drives effects and announcements uses the overload
     * below.
     */
    public static NutrientStatus of(Nutrient nutrient, float value) {
        return of(nutrient, value, null);
    }

    /**
     * The same question, but sticky: a status already held is harder to leave than it was to enter.
     *
     * @param holding the status the player is currently being treated as having, or null for none
     */
    public static NutrientStatus of(Nutrient nutrient, float value, NutrientStatus holding) {
        float low = nutrient.malnourishedBelow();
        float target = nutrient.targetLow();
        if (holding == MALNOURISHED) {
            low += HYSTERESIS;
        } else if (holding == ON_TARGET) {
            target -= HYSTERESIS;
        }
        if (value < low) {
            return MALNOURISHED;
        }
        return value >= target ? ON_TARGET : SAFE;
    }
}
