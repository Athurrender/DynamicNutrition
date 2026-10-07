package com.chillpavz.dynamicnutrition.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.platform.services.INetworkSender;

public class NeoForgeNetworkSender implements INetworkSender {

    @Override
    public void sendTable(ServerPlayer player, NutritionSyncPayload payload) {
        // The payload is registered as optional, so NeoForge drops it for a connection that cannot
        // accept it instead of disconnecting. No explicit check needed here.
        PacketDistributor.sendToPlayer(player, payload);
    }
}
