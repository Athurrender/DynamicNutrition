package com.chillpavz.dynamicnutrition.effect;

import java.util.Map;

import com.chillpavz.dynamicnutrition.config.NutritionConfig;

/**
 * The SERVER's nutrient effect settings, as the client last received them.
 *
 * <p>What the client draws from them (the bar tooltip's effect lines, the Blindness fog) has to
 * follow the server, because the server is what applies the effects. Before anything has been
 * received, the local config answers, which is also the right answer in single player.
 */
public final class SyncedEffectSettings {

    private static volatile boolean received;
    private static volatile boolean enabled;
    private static volatile Map<String, Integer> percents = Map.of();

    private SyncedEffectSettings() {
    }

    public static void accept(boolean effectsEnabled, Map<String, Integer> effectPercents) {
        enabled = effectsEnabled;
        percents = Map.copyOf(effectPercents);
        received = true;
    }

    /** The server's strength for a spec on this client, 0 to 1, and 0 when effects are off. */
    public static float fraction(NutrientEffects.Spec spec) {
        if (!received) {
            return NutrientEffects.fraction(spec);
        }
        if (!enabled) {
            return 0.0F;
        }
        int value = percents.getOrDefault(spec.id(), 0);
        return Math.max(0, Math.min(100, value)) / 100.0F;
    }

    /** Only for the display filters, which are this client's own preference. */
    public static boolean showOnHud() {
        return NutritionConfig.showEffectsOnHud;
    }

    public static boolean showInInventory() {
        return NutritionConfig.showEffectsInInventory;
    }
}
