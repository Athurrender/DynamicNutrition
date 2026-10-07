package com.chillpavz.dynamicnutrition.platform.services;

import net.minecraft.server.level.ServerPlayer;

import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;

/**
 * Per-loader packet sending.
 *
 * <p>Deliberately one method. The player's own nutrient values need no packet at all, because both
 * loaders sync a data attachment for free; the only thing that genuinely has to be sent is the
 * resolved food table, which the client cannot compute.
 */
public interface INetworkSender {

    /**
     * Send the table to one player.
     *
     * <p>Implementations must tolerate a player whose client does not have the mod. On a vanilla
     * client an unknown payload is a disconnect, so each loader's own "can this be sent" check has
     * to be honoured rather than assumed.
     */
    void sendTable(ServerPlayer player, NutritionSyncPayload payload);
}
