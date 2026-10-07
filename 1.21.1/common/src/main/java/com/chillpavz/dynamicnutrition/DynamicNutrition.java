package com.chillpavz.dynamicnutrition;

import com.chillpavz.dynamicnutrition.nutrition.NutritionTable;
import com.chillpavz.dynamicnutrition.platform.Services;

/**
 * Loader-independent entry point and the single owner of the mod's server-side state.
 *
 * <p>Each loader module calls {@link #init()} once from its own entry point and then wires three
 * hooks to the methods below: the data reload, server start, and server stop.
 */
public final class DynamicNutrition {

    private static final NutritionTable TABLE = new NutritionTable();

    private DynamicNutrition() {
    }

    /** The resolved nutrition table. Server side for resolution, client side for display. */
    public static NutritionTable table() {
        return TABLE;
    }

    public static void init() {
        Constants.LOG.info("{} starting on {} ({})",
                Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
    }

    /**
     * Called once the server is up and its recipes are loaded.
     *
     * <p>Prewarming here rather than lazily is deliberate: resolution walks the recipe graph, and
     * doing that on demand means doing it while a recipe-viewer mod indexes every tooltip at once,
     * which is what turns it into a multi-minute hang on first join.
     */
    public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        TABLE.prewarm(server.overworld());
    }

    public static void onServerStopped() {
        TABLE.invalidate();
    }
}
