package com.chillpavz.dynamicnutrition.nutrition;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.config.NutritionConfig;

/**
 * How much a meal is worth given what the player has been eating lately.
 *
 * <p>This is the v1.1 headline mechanic and the combination nobody has shipped: the mods that do
 * repetition do not track nutrients, and the mods that track nutrients do not care about
 * repetition.
 *
 * <h2>Two rules it must never break</h2>
 * <ul>
 *   <li><b>It scales NUTRITION only, never hunger.</b> Touching the hunger bar would fight vanilla
 *       and every other food mod; scaling our own numbers composes with all of them.</li>
 *   <li><b>It can never reduce a meal to nothing.</b> The same complaint has followed mods of this
 *       kind for years, from many separate reporters: a mechanic that zeroes a meal reads as the
 *       mod being broken, and a player whose only food is one crop would be locked out entirely.
 *       The floor is a config value, it is asserted positive by the audit, and it is deliberately
 *       generous at thirty percent.</li>
 * </ul>
 *
 * <p>The history is kept as registry ids rather than {@code Item} references so that it survives a
 * mod being removed: an id nobody can resolve any more simply stops matching anything, which is the
 * correct behaviour and costs nothing.
 */
public final class MealHistory {

    private MealHistory() {
    }

    /** The id this item is remembered under, or null if it somehow has no registry entry. */
    public static String key(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? null : id.toString();
    }

    /**
     * How many of the remembered meals were this same food.
     *
     * <p>Counted over the last {@link NutritionConfig#varietyWindow} entries only, so shrinking the
     * window in the config takes effect immediately rather than after the stored list drains.
     */
    public static int repeatsOf(List<String> history, String key) {
        if (key == null || history.isEmpty()) {
            return 0;
        }
        int window = Math.max(1, NutritionConfig.varietyWindow);
        int from = Math.max(0, history.size() - window);
        int seen = 0;
        for (int i = from; i < history.size(); i++) {
            if (key.equals(history.get(i))) {
                seen++;
            }
        }
        return seen;
    }

    /**
     * The multiplier for eating this food right now, between the floor and 1.
     *
     * <p>The first repeat inside the window is free. That is what keeps an ordinary rotation of six
     * or more foods at or near full value while still landing a player who eats one thing forever on
     * the floor, which is the curve the balance simulation was tuned to produce.
     */
    public static float multiplier(List<String> history, Item item) {
        if (!NutritionConfig.varietyEnabled) {
            return 1.0F;
        }
        int repeats = Math.max(0, repeatsOf(history, key(item)) - FREE_REPEATS);
        if (repeats == 0) {
            return 1.0F;
        }
        float floor = clampPercent(NutritionConfig.varietyFloorPercent) / 100.0F;
        float penalty = clampPercent(NutritionConfig.varietyPenaltyPercent) / 100.0F;
        return Math.max(floor, 1.0F - penalty * repeats);
    }

    /** One repeat inside the window costs nothing. See {@link #multiplier}. */
    public static final int FREE_REPEATS = 1;

    /**
     * A percentage from the config, clamped.
     *
     * <p>The floor is clamped to at least one so that a hand-edited config cannot produce a meal
     * worth literally nothing, which is the failure mode this whole class is written around.
     */
    private static int clampPercent(int value) {
        return Math.max(1, Math.min(100, value));
    }
}
