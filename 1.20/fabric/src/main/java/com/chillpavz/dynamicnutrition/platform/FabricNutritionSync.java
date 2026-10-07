package com.chillpavz.dynamicnutrition.platform;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.network.PlayerNutritionPayload;

/**
 * Sends a player's values to their own client. On the newer bands the Fabric attachment does this
 * itself; Fabric API 0.92 has no attachments, so it is done here, the same way the Forge module
 * does it for its capability.
 *
 * <p>Deferred to the end of the server tick rather than sent inside {@code markDirty}: one tick can
 * move several nutrients, award a meal and change the held status, and sending on each would put
 * several identical payloads on the wire for one carrot.
 */
public final class FabricNutritionSync {

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private FabricNutritionSync() {
    }

    /** Remember that this player's client is now behind. Cheap, and safe to call repeatedly. */
    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer) {
            DIRTY.add(player.getUUID());
        }
    }

    /** Send if anything moved. Called at the end of every server tick, per player. */
    public static void flush(ServerPlayer player) {
        if (DIRTY.remove(player.getUUID())) {
            sendNow(player);
        }
    }

    /** Send unconditionally, and clear the flag. For a join and a dimension change. */
    public static void sendNow(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        // A client without this mod has not registered the channel, and sending anyway is a
        // disconnect rather than a warning, so the check is ours.
        if (!ServerPlayNetworking.canSend(player, PlayerNutritionPayload.ID)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        new PlayerNutritionPayload(new FabricNutritionStorage().get(player)).write(buf);
        ServerPlayNetworking.send(player, PlayerNutritionPayload.ID, buf);
    }

    /** A player who has left cannot be sent anything; without this the set grows all session. */
    public static void forget(Player player) {
        DIRTY.remove(player.getUUID());
    }
}
