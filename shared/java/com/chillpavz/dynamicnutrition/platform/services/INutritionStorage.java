package com.chillpavz.dynamicnutrition.platform.services;

import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Per-loader access to the player's nutrition attachment.
 *
 * <p>Both loaders give persistence, copy-on-death AND sync on the attachment type itself, so this
 * interface is deliberately tiny: there is no packet to write, and the only thing the common code
 * needs is a way to reach the object and a way to say it changed.
 */
public interface INutritionStorage {

    /** Never null: the attachment has an initializer, so a player who has none gets a fresh one. */
    PlayerNutrition get(Player player);

    /**
     * Tell the loader the value changed so it re-syncs to the owning client.
     *
     * <p>Needed because both loaders track dirtiness by the attachment being SET, and mutating the
     * object in place does not trip that. Forgetting this call is a silent desync: the server is
     * right and the screen is stale.
     */
    void markDirty(Player player);
}
