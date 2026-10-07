package com.chillpavz.dynamicnutrition.network;

import net.minecraft.client.Minecraft;

import com.chillpavz.dynamicnutrition.platform.ForgeNutritionStorage;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Writes a received {@link PlayerNutritionPayload} onto the LOCAL player's capability, so the
 * screen, the HUD strip and the tooltips read the player's real values through the same
 * {@code Services.STORAGE.get(player)} call they use on every other loader.
 *
 * <p>Filling the existing instance rather than replacing it matters: the capability was created
 * with the client-side player entity and is handed out by reference, so a swap would leave
 * anything that already holds it pointing at stale values.
 *
 * <p>Client only. It is reached through a {@code FMLEnvironment.dist} guard, which is why the
 * {@code Minecraft} reference here never loads on a dedicated server.
 */
public final class ForgeNutritionClientState {

    private ForgeNutritionClientState() {
    }

    public static void accept(PlayerNutrition received) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            // The payload can land between the login packet and the player entity existing. The
            // server sends again on the first tick after the join, so dropping this one is safe.
            return;
        }
        player.getCapability(ForgeNutritionStorage.CAPABILITY)
                .ifPresent(current -> current.copyFrom(received));
    }
}
