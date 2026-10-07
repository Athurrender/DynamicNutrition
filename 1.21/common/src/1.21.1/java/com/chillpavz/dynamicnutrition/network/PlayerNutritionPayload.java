package com.chillpavz.dynamicnutrition.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * One player's own nutrition, server to client. Used by FORGE and, on this band, NEOFORGE: Forge
 * has no attachment API, and NeoForge 21.1's attachments persist and copy but cannot sync (that
 * arrived in a later NeoForge). Fabric's attachment syncs itself, so Fabric never sends this.
 *
 * <p>It goes only to the player it belongs to. Nobody else needs to know what another player has
 * eaten, and broadcasting it would put a payload per player on the wire for a screen only its
 * owner can open. That is the same rule Fabric's sync predicate expresses.
 *
 * <p>It reuses {@link PlayerNutrition#STREAM_CODEC}, which is the codec Fabric syncs with, so all
 * three send the identical bytes and a field added to the state reaches every loader or none.
 */
public record PlayerNutritionPayload(PlayerNutrition nutrition) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerNutritionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "player_nutrition"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerNutritionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    PlayerNutrition.STREAM_CODEC, PlayerNutritionPayload::nutrition,
                    PlayerNutritionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
