package com.chillpavz.dynamicnutrition.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.platform.services.INetworkSender;

public class FabricNetworkSender implements INetworkSender {

    @Override
    public void sendTable(ServerPlayer player, NutritionSyncPayload payload) {
        // A client without this mod has not registered the channel, and sending anyway is a
        // disconnect rather than a warning. Fabric will happily let you do it, so the check is ours.
        if (!ServerPlayNetworking.canSend(player, NutritionSyncPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, payload);
    }
}
