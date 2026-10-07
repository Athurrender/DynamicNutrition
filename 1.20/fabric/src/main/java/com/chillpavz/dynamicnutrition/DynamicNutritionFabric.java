package com.chillpavz.dynamicnutrition;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

import com.chillpavz.dynamicnutrition.command.NutritionCommands;
import com.chillpavz.dynamicnutrition.config.ClothCompat;
import com.chillpavz.dynamicnutrition.effect.FabricNutritionEffects;
import com.chillpavz.dynamicnutrition.event.NutritionEvents;
import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.nutrition.FabricNutritionDataLoader;
import com.chillpavz.dynamicnutrition.platform.FabricNutritionStorage;
import com.chillpavz.dynamicnutrition.platform.FabricNutritionSync;

public class DynamicNutritionFabric implements ModInitializer {

    private static final FabricNutritionStorage STORAGE = new FabricNutritionStorage();

    @Override
    public void onInitialize() {
        DynamicNutrition.init();
        ClothCompat.init();

        FabricNutritionEffects.register();

        // No payload types to register on this band: a channel is a plain id, and the client
        // announces the ones it listens on, which is what canSend checks before every send.

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricNutritionDataLoader(DynamicNutrition.table()));

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                NutritionCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(DynamicNutrition::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DynamicNutrition.onServerStopped());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                NutritionEvents.onPlayerTick(player);
                // Drained once per tick, after everything that could have set it.
                FabricNutritionSync.flush(player);
            }
        });

        // No attachment to copy itself on this band, so the values are copied here. COPY_FROM runs
        // inside the respawn, before AFTER_RESPAWN, so the death is charged to the copied values.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                STORAGE.get(newPlayer).copyFrom(STORAGE.get(oldPlayer)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            NutritionEvents.onRespawn(newPlayer, !alive);
            // FLAGGED, not sent: drained at the end of the server tick, after the client has its
            // new player entity. Sent now, it could land on the old one.
            FabricNutritionSync.markDirty(newPlayer);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            NutritionEvents.onPlayerJoin(handler.getPlayer());
            // The client entity is new and holds the defaults.
            FabricNutritionSync.sendNow(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FabricNutritionSync.forget(handler.getPlayer()));

        // A dimension change gives the client a fresh entity too, and this fires after it has one.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                FabricNutritionSync.sendNow(player));

        // NOTE: nothing here may reference the mixin class. Doing so throws
        // IllegalClassLoadError and kills the server thread; EatHookState exists for exactly that
        // reason and logs "Eat hook is active." the first time the injection runs.
    }
}
