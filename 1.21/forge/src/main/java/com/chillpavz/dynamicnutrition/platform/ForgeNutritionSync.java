package com.chillpavz.dynamicnutrition.platform;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.network.ForgeNutritionChannel;
import com.chillpavz.dynamicnutrition.network.PlayerNutritionPayload;

/**
 * The two things a Fabric or NeoForge attachment does for free and a Forge capability does not:
 * send the player's values to their own client, and carry them across a death.
 *
 * <p>Sending is deferred to the end of the player's tick rather than done inside
 * {@code markDirty}. A single tick can move several nutrients, award a meal and change the held
 * status, and each of those marks the state dirty; sending immediately would put three or four
 * identical payloads on the wire for one eaten carrot. Both other loaders coalesce for the same
 * reason, so this keeps the traffic the same shape on all three.
 */
public final class ForgeNutritionSync {

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private ForgeNutritionSync() {
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

    /**
     * Send unconditionally, and clear the flag.
     *
     * <p>Used where the client is known to have nothing: a fresh login and a dimension change both
     * give the player a new client-side entity whose capability is at its defaults. NOT a respawn:
     * Clone fires before the client has its new entity, so a respawn only raises the flag.
     */
    public static void sendNow(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        ForgeNutritionChannel.sendTo(player,
                new PlayerNutritionPayload(ForgeNutritionChannel.storage().get(player)));
    }

    /** A player who has left cannot be sent anything; without this the set grows all session. */
    public static void forget(Player player) {
        DIRTY.remove(player.getUUID());
    }
}
