package com.chillpavz.dynamicnutrition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * One player's own nutrition, server to client. On this band BOTH loaders send it: Forge has no
 * attachment API, and Fabric API 0.92 has none either, so neither loader syncs this state itself.
 *
 * <p>It goes only to the player it belongs to. Nobody else needs to know what another player has
 * eaten, and broadcasting it would put a payload per player on the wire for a screen only its
 * owner can open.
 *
 * <p>It is written with {@link PlayerNutrition#CODEC}, the codec the values are saved with, so a
 * field added to the state reaches the client with no second list of fields to update.
 */
public record PlayerNutritionPayload(PlayerNutrition nutrition) {

    /** The channel this travels on. A plain id on this band: the payload type object is newer. */
    public static final ResourceLocation ID =
            new ResourceLocation(Constants.MOD_ID, "player_nutrition");

    public void write(FriendlyByteBuf buf) {
        PlayerNutrition.write(buf, nutrition);
    }

    public static PlayerNutritionPayload read(FriendlyByteBuf buf) {
        return new PlayerNutritionPayload(PlayerNutrition.read(buf));
    }
}
