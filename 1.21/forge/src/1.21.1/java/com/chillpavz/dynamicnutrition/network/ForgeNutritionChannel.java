package com.chillpavz.dynamicnutrition.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.client.NutritionClient;
import com.chillpavz.dynamicnutrition.platform.ForgeNutritionStorage;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;

/**
 * The one channel both payloads ride on. Clientbound only; nothing is sent to the server.
 *
 * <p>{@code optional()} so a client without this mod is not disconnected for failing to know the
 * channel. It simply never receives anything, which costs it the tooltips and the screen and
 * nothing else, and the server keeps working for it exactly as vanilla.
 */
public final class ForgeNutritionChannel {

    private static final INutritionStorage STORAGE = new ForgeNutritionStorage();

    private static final Channel<CustomPacketPayload> CHANNEL =
            ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "main"))
                    // "2" matches the NeoForge registrar version: the payload started carrying the
                    // effect settings at 1.5.0 and the two loaders must not disagree about that.
                    .networkProtocolVersion(2)
                    .optional()
                    .payloadChannel()
                    .play()
                    .clientbound()
                    .add(NutritionSyncPayload.TYPE, NutritionSyncPayload.STREAM_CODEC,
                            (payload, context) -> {
                                context.enqueueWork(() -> NutritionClient.acceptTable(payload));
                                context.setPacketHandled(true);
                            })
                    .add(PlayerNutritionPayload.TYPE, PlayerNutritionPayload.STREAM_CODEC,
                            (payload, context) -> {
                                context.enqueueWork(() -> {
                                    if (FMLEnvironment.dist == Dist.CLIENT) {
                                        ForgeNutritionClientState.accept(payload.nutrition());
                                    }
                                });
                                context.setPacketHandled(true);
                            })
                    .build();

    private ForgeNutritionChannel() {
    }

    /** Touching this class is what builds the channel, so the mod entrypoint calls it once. */
    public static void init() {
    }

    public static INutritionStorage storage() {
        return STORAGE;
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
