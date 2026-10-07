package com.chillpavz.dynamicnutrition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.chillpavz.dynamicnutrition.client.DynamicNutritionForgeClient;
import com.chillpavz.dynamicnutrition.command.NutritionCommands;
import com.chillpavz.dynamicnutrition.config.ClothCompat;
import com.chillpavz.dynamicnutrition.effect.ForgeNutritionEffects;
import com.chillpavz.dynamicnutrition.event.NutritionEvents;
import com.chillpavz.dynamicnutrition.network.ForgeNutritionChannel;
import com.chillpavz.dynamicnutrition.nutrition.NutritionDataLoader;
import com.chillpavz.dynamicnutrition.platform.ForgeNutritionStorage;
import com.chillpavz.dynamicnutrition.platform.ForgeNutritionSync;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Forge entrypoint.
 *
 * <p>Forge 61 is on EventBus 8, a different shape from both older Forge and NeoForge:
 * {@code @Mod} takes a NO-ARG constructor, mod-bus events are subscribed through the mod's own
 * {@link BusGroup} via {@code SomeEvent.getBus(group)}, and game-bus events expose a static
 * {@code BUS}. Tell the two apart by whether the event implements {@code IModBusEvent}; the wrong
 * bus fails silently.
 *
 * <p>The one real architectural difference from the other two loaders is that per-player state is a
 * CAPABILITY rather than an attachment, so persistence, copy-on-death and the sync to the owning
 * client are three separate jobs here instead of three flags on a builder. See
 * {@link ForgeNutritionStorage}.
 */
@Mod(Constants.MOD_ID)
public class DynamicNutritionForge {

    public DynamicNutritionForge() {
        DynamicNutrition.init();
        ClothCompat.init();
        // Touching the channel class is what builds it, and it must exist before anything sends.
        ForgeNutritionChannel.init();

        BusGroup modBus = ModLoadingContext.get().getActiveContainer().getModBusGroup();

        RegisterCapabilitiesEvent.getBus(modBus).addListener(event ->
                event.register(PlayerNutrition.class));

        ForgeNutritionEffects.register(modBus);

        // The holders only exist once registration has actually run, so the handover to the common
        // code waits for common setup rather than happening in this constructor.
        FMLCommonSetupEvent.getBus(modBus).addListener(event -> ForgeNutritionEffects.bind());

        // ---- game bus ----

        // Every player gets the capability, on BOTH sides: the screen, the HUD strip and the
        // tooltips read the local player's values through the same service call the server uses.
        AttachCapabilitiesEvent.Entities.BUS.addListener(event -> {
            if (event.getObject() instanceof Player) {
                event.addCapability(ForgeNutritionStorage.ID, new ForgeNutritionStorage.Provider());
            }
        });

        AddReloadListenerEvent.BUS.addListener(event ->
                event.addListener(new NutritionDataLoader(DynamicNutrition.table())));

        RegisterCommandsEvent.BUS.addListener(event ->
                NutritionCommands.register(event.getDispatcher()));

        ServerStartedEvent.BUS.addListener(event ->
                DynamicNutrition.onServerStarted(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> DynamicNutrition.onServerStopped());

        // The eat hook. Forge has a real event for this, so unlike Fabric there is no mixin.
        LivingEntityUseItemEvent.Finish.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onFoodEaten(player, event.getItem());
            }
        });

        // A record event on EventBus 8, so the player is an accessor rather than a field.
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> {
            if (event.player() instanceof ServerPlayer player) {
                NutritionEvents.onPlayerTick(player);
                // Drain the dirty flag once per tick, after everything that could have set it.
                // This is the loader's sync on the other two; here it is ours.
                ForgeNutritionSync.flush(player);
            }
        });

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onPlayerJoin(player);
                // The client entity is brand new and its capability is at the defaults, so send
                // unconditionally rather than waiting for something to change.
                ForgeNutritionSync.sendNow(player);
            }
        });

        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event ->
                ForgeNutritionSync.forget(event.getEntity()));

        // Copy across death, which the other two loaders get from copyOnDeath() on the attachment.
        // Clone fires for a death respawn AND for an end-portal return; the common code decides
        // what each one costs, so the values are copied either way and the flag is passed through.
        PlayerEvent.Clone.BUS.addListener(event -> {
            event.getOriginal().reviveCaps();
            event.getOriginal().getCapability(ForgeNutritionStorage.CAPABILITY).ifPresent(old ->
                    event.getEntity().getCapability(ForgeNutritionStorage.CAPABILITY)
                            .ifPresent(fresh -> fresh.copyFrom(old)));
            event.getOriginal().invalidateCaps();
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onRespawn(player, event.isWasDeath());
                // FLAGGED, not sent: Clone fires before the client is told about its new player
                // entity, so a payload sent now lands on the old one and the new one shows the
                // defaults (seen on Forge 52). The flag is drained at the end of the new player's
                // first tick, after the respawn packet.
                ForgeNutritionSync.markDirty(player);
            }
        });

        // A dimension change also gives the client a fresh entity, so it needs the values again.
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ForgeNutritionSync.sendNow(player);
            }
        });

        // Client-only wiring, kept in a separate class so nothing client-shaped is even loaded on
        // a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DynamicNutritionForgeClient.init(modBus);
        }
    }
}
