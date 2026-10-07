package com.chillpavz.dynamicnutrition;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import com.chillpavz.dynamicnutrition.command.NutritionCommands;
import com.chillpavz.dynamicnutrition.config.ClothCompat;
import com.chillpavz.dynamicnutrition.effect.FabricNutritionEffects;
import com.chillpavz.dynamicnutrition.event.NutritionEvents;
import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.nutrition.FabricNutritionDataLoader;
import com.chillpavz.dynamicnutrition.platform.FabricNutritionStorage;

public class DynamicNutritionFabric implements ModInitializer {

    /** The respawn phase that runs after Fabric API has copied the attachment. See onInitialize. */
    private static final ResourceLocation AFTER_ATTACHMENT_COPY =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "after_attachment_copy");

    @Override
    public void onInitialize() {
        DynamicNutrition.init();
        ClothCompat.init();

        // Touching the class is what registers the attachment type.
        FabricNutritionStorage.TYPE.identifier();

        FabricNutritionEffects.register();

        // The payload type has to be registered on BOTH sides, and this is the common initializer,
        // so it belongs here rather than in the client one. Registering it only client side would
        // make canSend answer false on an integrated server and the table would never be sent.
        //
        // Plain register: Fabric API has no registerLarge on this band. A clientbound custom
        // payload may be 1 MiB here, and the table is roughly ten bytes a food, so a pack would
        // need about a hundred thousand foods to reach it.
        PayloadTypeRegistry.playS2C().register(
                NutritionSyncPayload.TYPE, NutritionSyncPayload.STREAM_CODEC);

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricNutritionDataLoader(DynamicNutrition.table()));

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                NutritionCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(DynamicNutrition::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DynamicNutrition.onServerStopped());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                NutritionEvents.onPlayerTick(player);
            }
        });

        // Fabric API copies copyOnDeath attachments in its OWN AFTER_RESPAWN listener, in the
        // default phase. Registered alongside it, ours can run first, charge the death to the fresh
        // defaults (which the floor then ignores) and have the copy overwrite the result, so a death
        // silently costs nothing. A phase ordered after the default makes the copy land first.
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_ATTACHMENT_COPY);
        ServerPlayerEvents.AFTER_RESPAWN.register(AFTER_ATTACHMENT_COPY, (oldPlayer, newPlayer, alive) ->
                NutritionEvents.onRespawn(newPlayer, !alive));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                NutritionEvents.onPlayerJoin(handler.getPlayer()));

        // NOTE: nothing here may reference the mixin class. Doing so throws
        // IllegalClassLoadError and kills the server thread; EatHookState exists for exactly that
        // reason and logs "Eat hook is active." the first time the injection runs.
    }
}
