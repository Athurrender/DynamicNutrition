package com.chillpavz.dynamicnutrition.platform;

import net.minecraft.server.level.ServerPlayer;

import com.chillpavz.dynamicnutrition.network.ForgeNutritionChannel;
import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.platform.services.INetworkSender;

public class ForgeNetworkSender implements INetworkSender {

    @Override
    public void sendTable(ServerPlayer player, NutritionSyncPayload payload) {
        // The channel is optional, so Forge drops this for a connection that cannot accept it
        // rather than disconnecting. No explicit check is needed here.
        ForgeNutritionChannel.sendTo(player, payload);
    }
}
