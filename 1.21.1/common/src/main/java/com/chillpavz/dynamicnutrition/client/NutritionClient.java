package com.chillpavz.dynamicnutrition.client;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.DynamicNutrition;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;
import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;

/**
 * The client half of the food table sync, shared by both loaders.
 *
 * <p>Both loaders hand a received payload to {@link #acceptTable}, which is the ONLY place the
 * client's copy of the table changes, and therefore the only place the tooltip cache needs
 * dropping. Keeping those two facts in one method is what stops them drifting apart, which would
 * present as a tooltip that is permanently one datapack reload out of date.
 */
public final class NutritionClient {

    private NutritionClient() {
    }

    public static void acceptTable(NutritionSyncPayload payload) {
        DynamicNutrition.table().acceptSynced(payload.unpackValues(), payload.unpackOrigins());
        FoodTooltip.invalidate();
        SyncedEffectSettings.accept(payload.effectsEnabled(), payload.effectPercents());
        Constants.LOG.debug("Client table now holds {} foods", payload.entries().size());
    }
}
