package com.chillpavz.dynamicnutrition.network;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.client.NutritionClient;
import com.chillpavz.dynamicnutrition.platform.ForgeNutritionStorage;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;

/**
 * The one channel both payloads ride on. Clientbound only; nothing is sent to the server.
 *
 * <p>A {@code SimpleChannel} on this band, with each payload's own hand-written reader and writer.
 * A client or server WITHOUT the channel is accepted ({@code acceptMissingOr}), so a vanilla client
 * is not disconnected for failing to know it. It simply never receives anything, which costs it the
 * tooltips and the screen and nothing else.
 */
public final class ForgeNutritionChannel {

    /** "2" matches the newer bands: the table started carrying the effect settings at 1.5.0. */
    private static final String VERSION = "2";

    private static final INutritionStorage STORAGE = new ForgeNutritionStorage();

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    static {
        CHANNEL.registerMessage(0, NutritionSyncPayload.class, NutritionSyncPayload::write,
                NutritionSyncPayload::read, (payload, context) -> {
                    context.get().enqueueWork(() -> NutritionClient.acceptTable(payload));
                    context.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PlayerNutritionPayload.class, PlayerNutritionPayload::write,
                PlayerNutritionPayload::read, (payload, context) -> {
                    context.get().enqueueWork(() -> {
                        if (FMLEnvironment.dist == Dist.CLIENT) {
                            ForgeNutritionClientState.accept(payload.nutrition());
                        }
                    });
                    context.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private ForgeNutritionChannel() {
    }

    /** Touching this class is what builds the channel, so the mod entrypoint calls it once. */
    public static void init() {
    }

    public static INutritionStorage storage() {
        return STORAGE;
    }

    public static void sendTo(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
