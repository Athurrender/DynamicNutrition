package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.Minecraft;

import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Writes the local player's own values, received from the server, into the instance the storage
 * hands out. Used where the loader does not sync that state itself: NeoForge on this band, whose
 * attachments cannot sync yet. (Forge has its own copy of this, reached through its capability.)
 *
 * <p>Filled IN PLACE rather than replaced, so anything already holding the instance sees the new
 * values. Client only, and kept out of {@link NutritionClient}, which a dedicated server loads.
 */
public final class OwnValuesClient {

    private OwnValuesClient() {
    }

    public static void accept(PlayerNutrition received) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            // Landed before the player entity exists. The server sends again on the player's next
            // change, and always on join, respawn and dimension change, so dropping this is safe.
            return;
        }
        Services.STORAGE.get(player).copyFrom(received);
    }
}
