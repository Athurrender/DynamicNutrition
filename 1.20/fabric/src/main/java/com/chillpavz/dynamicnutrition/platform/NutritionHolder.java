package com.chillpavz.dynamicnutrition.platform;

import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * What every player carries on this band: its own nutrition object, added to {@code Player} by a
 * mixin. Fabric API 0.92 has no attachment API, so the state lives on the entity itself.
 *
 * <p>An interface in the platform package rather than anything in the mixin package, because
 * nothing outside a mixin may reference a mixin class.
 */
public interface NutritionHolder {

    PlayerNutrition dynamicnutrition$nutrition();
}
