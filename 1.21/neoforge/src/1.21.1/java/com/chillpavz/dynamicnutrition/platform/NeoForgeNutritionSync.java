package com.chillpavz.dynamicnutrition.platform;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import com.chillpavz.dynamicnutrition.network.PlayerNutritionPayload;

/**
 * Sends a player's values to their own client. On 26.x the NeoForge attachment does this itself;
 * NeoForge 21.1's attachment builder has no sync, so on this band it is done here, the same way the
 * Forge module does it for its capability.
 *
 * <p>Sending is deferred to the end of the player's tick rather than done inside
 * {@code markDirty}: one tick can move several nutrients, award a meal and change the held status,
 * and sending on each would put three or four identical payloads on the wire for one carrot.
 */
public final class NeoForgeNutritionSync {

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private NeoForgeNutritionSync() {
    }

    /** Remember that this player's client is now behind. Cheap, and safe to call repeatedly. */
    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer) {
            DIRTY.add(player.getUUID());
        }
    }

    /** Send if anything moved. Called at the end of the player's tick. */
    public static void flush(ServerPlayer player) {
        if (DIRTY.remove(player.getUUID())) {
            sendNow(player);
        }
    }

    /** Send unconditionally, and clear the flag. For a join and a dimension change. */
    public static void sendNow(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        // Registered optional, so NeoForge drops it for a client without the mod.
        PacketDistributor.sendToPlayer(player,
                new PlayerNutritionPayload(new NeoForgeNutritionStorage().get(player)));
    }

    /** A player who has left cannot be sent anything; without this the set grows all session. */
    public static void forget(Player player) {
        DIRTY.remove(player.getUUID());
    }
}
