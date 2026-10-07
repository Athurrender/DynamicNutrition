package com.chillpavz.dynamicnutrition;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

import com.chillpavz.dynamicnutrition.client.DynamicNutritionNeoForgeClient;
import com.chillpavz.dynamicnutrition.client.NutritionClient;
import com.chillpavz.dynamicnutrition.client.OwnValuesClient;

import com.chillpavz.dynamicnutrition.command.NutritionCommands;
import com.chillpavz.dynamicnutrition.config.ClothCompat;
import com.chillpavz.dynamicnutrition.effect.NeoForgeNutritionEffects;
import com.chillpavz.dynamicnutrition.event.NutritionEvents;
import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;
import com.chillpavz.dynamicnutrition.network.PlayerNutritionPayload;
import com.chillpavz.dynamicnutrition.platform.NeoForgeNutritionSync;
import com.chillpavz.dynamicnutrition.nutrition.NutritionDataLoader;
import com.chillpavz.dynamicnutrition.platform.NeoForgeNutritionStorage;

@Mod(Constants.MOD_ID)
public class DynamicNutritionNeoForge {

    public DynamicNutritionNeoForge(IEventBus modBus, ModContainer container) {
        DynamicNutrition.init();
        ClothCompat.init();

        // Registries go on the MOD bus, and so does payload registration.
        NeoForgeNutritionStorage.ATTACHMENTS.register(modBus);
        NeoForgeNutritionEffects.register(modBus);

        // The holders only exist once the deferred registry has actually run, so the handover to
        // the common code waits for FMLCommonSetupEvent rather than happening in this constructor.
        modBus.addListener(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.class,
                event -> NeoForgeNutritionEffects.bind());

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            // optional() so a client without this mod is not disconnected for failing to know the
            // channel; it simply never receives the table, which costs it tooltips and nothing else.
            // "2" since 1.5.0, when the payload started carrying the effect settings.
            var registrar = event.registrar("2").optional();
            registrar.playToClient(NutritionSyncPayload.TYPE, NutritionSyncPayload.STREAM_CODEC,
                    // NutritionClient is dist safe: it touches the table, the payload and the
                    // tooltip cache, and no client-only Minecraft class. Pointing this at the
                    // NeoForge client wiring class instead would put a client reference in a
                    // constructor that also runs on a dedicated server.
                    (payload, context) -> NutritionClient.acceptTable(payload));
            // The player's own values: NeoForge 21.1 attachments cannot sync, so they travel here.
            registrar.playToClient(PlayerNutritionPayload.TYPE, PlayerNutritionPayload.STREAM_CODEC,
                    (payload, context) -> OwnValuesClient.accept(payload.nutrition()));
        });

        // Everything below is on the GAME bus, the server data reload listener included. Getting
        // the bus wrong fails silently, with the listener never called and every food resolving
        // to nothing.
        IEventBus gameBus = NeoForge.EVENT_BUS;

        gameBus.addListener(AddReloadListenerEvent.class, event ->
                event.addListener(new NutritionDataLoader(DynamicNutrition.table())));

        gameBus.addListener(RegisterCommandsEvent.class, event ->
                NutritionCommands.register(event.getDispatcher()));

        gameBus.addListener(ServerStartedEvent.class, event ->
                DynamicNutrition.onServerStarted(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event ->
                DynamicNutrition.onServerStopped());

        // The eat hook. NeoForge has a real event for this, so unlike Fabric there is no mixin.
        gameBus.addListener(LivingEntityUseItemEvent.Finish.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onFoodEaten(player, event.getItem());
            }
        });

        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onPlayerTick(player);
                // Drained once per tick, after everything that could have set it.
                NeoForgeNutritionSync.flush(player);
            }
        });

        // Client-only wiring, kept in a separate class so nothing client-shaped is even loaded on a
        // dedicated server. A field on this band; it became FMLEnvironment.getDist() later.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DynamicNutritionNeoForgeClient.init(modBus, container);
        }

        // Clone fires for a death respawn AND for an end-portal return; only the first should cost
        // anything, which is what the flag is for.
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onPlayerJoin(player);
                // The client entity is new and its attachment is at the defaults.
                NeoForgeNutritionSync.sendNow(player);
            }
        });

        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event ->
                NeoForgeNutritionSync.forget(event.getEntity()));

        // A dimension change gives the client a fresh entity too. The event fires after the
        // client has been told about the new level, so sending at once is safe here.
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NeoForgeNutritionSync.sendNow(player);
            }
        });

        // LOWEST, because NeoForge copies copyOnDeath attachments in its own Clone listener at the
        // default priority. Run beside it and ours can charge the death to the fresh defaults,
        // which the floor ignores, before the copy overwrites them: a death then costs nothing.
        gameBus.addListener(EventPriority.LOWEST, PlayerEvent.Clone.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onRespawn(player, event.isWasDeath());
                // FLAGGED, not sent: Clone fires before the client is told about its new player
                // entity, so a payload sent now could land on the old one. The flag is drained at
                // the end of the new player's first tick, after the respawn packet.
                NeoForgeNutritionSync.markDirty(player);
            }
        });
    }
}
