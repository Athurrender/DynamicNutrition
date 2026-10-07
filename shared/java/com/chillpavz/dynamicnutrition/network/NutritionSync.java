package com.chillpavz.dynamicnutrition.network;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.DynamicNutrition;
import com.chillpavz.dynamicnutrition.platform.Services;

/**
 * Decides when a player's copy of the food table is out of date, and sends it.
 *
 * <p>Both loaders have a join event and neither has a usable "a datapack reload finished" one, so
 * rather than wiring two different reload hooks this watches the table's own generation counter from
 * the per-player server tick that already runs. A player who has never been sent the table, a player
 * whose table predates a {@code /reload}, and a player who has just joined all present as the same
 * mismatch and are all handled by the same three lines.
 *
 * <p><b>The map is weak on purpose.</b> A {@code ServerPlayer} is replaced on death and on a
 * dimension change, so a strong map would pin every player object the server has ever made. The
 * replacement simply reads as never having been sent the table, which resends it, which is correct.
 */
public final class NutritionSync {

    private static final Map<ServerPlayer, Long> SENT = new WeakHashMap<>();

    /**
     * What a player's copy is keyed on: the table's generation AND the config's revision, so a change
     * to the effect settings resends as surely as a {@code /reload} does.
     */
    private static long version() {
        return ((long) NutritionConfig.revision << 32) | (DynamicNutrition.table().generation() & 0xFFFFFFFFL);
    }

    private NutritionSync() {
    }

    /** Send the table to this player if their copy is missing or stale. Cheap when it is not. */
    public static void syncIfStale(ServerPlayer player) {
        long generation = version();
        Long had;
        synchronized (SENT) {
            had = SENT.get(player);
        }
        if (had != null && had == generation) {
            return;
        }
        send(player, generation);
    }

    /** Send unconditionally. Used on join, so the table is there before the first tooltip. */
    public static void sendNow(ServerPlayer player) {
        send(player, version());
    }

    private static void send(ServerPlayer player, long generation) {
        ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
        if (level == null) {
            return;
        }
        // A no-op once done. It matters on a dedicated server, where a player can in principle
        // arrive before anything has asked the table to resolve.
        DynamicNutrition.table().prewarm(level);

        NutritionSyncPayload payload = NutritionSyncPayload.of(
                DynamicNutrition.table().snapshot(), DynamicNutrition.table().originSnapshot());
        try {
            Services.NETWORK.sendTable(player, payload);
        } catch (Throwable t) {
            // A send that fails must not take the tick with it. Losing the table costs the player
            // their tooltips, and they keep everything else.
            Constants.LOG.warn("Could not send the nutrition table to {}: {}",
                    player.getName().getString(), t.toString());
            return;
        }
        synchronized (SENT) {
            SENT.put(player, generation);
        }
        Constants.LOG.debug("Sent {} nutrition entries to {}",
                payload.entries().size(), player.getName().getString());
    }
}
