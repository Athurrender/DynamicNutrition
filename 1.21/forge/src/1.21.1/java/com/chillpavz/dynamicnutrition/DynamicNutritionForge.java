package com.chillpavz.dynamicnutrition;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import net.minecraftforge.eventbus.api.IEventBus;
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
 * <p>Forge 52 is on EventBus 6: mod-bus events go on the mod's own {@link IEventBus} and game-bus
 * events on {@code MinecraftForge.EVENT_BUS}, each listener typed by its lambda parameter. Tell the
 * two apart by whether the event implements {@code IModBusEvent}; the wrong bus fails silently.
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

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.register(PlayerNutrition.class));

        ForgeNutritionEffects.register(modBus);

        // The holders only exist once registration has actually run, so the handover to the common
        // code waits for common setup rather than happening in this constructor.
        modBus.addListener((FMLCommonSetupEvent event) -> ForgeNutritionEffects.bind());

        // ---- game bus ----

        // Every player gets the capability, on BOTH sides: the screen, the HUD strip and the
        // tooltips read the local player's values through the same service call the server uses.
        // A GENERIC event on EventBus 6, so it needs addGenericListener with the target class.
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> event) -> {
            if (event.getObject() instanceof Player) {
                event.addCapability(ForgeNutritionStorage.ID, new ForgeNutritionStorage.Provider());
            }
        });

        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                event.addListener(new NutritionDataLoader(DynamicNutrition.table())));

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NutritionCommands.register(event.getDispatcher()));

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                DynamicNutrition.onServerStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> DynamicNutrition.onServerStopped());

        // The eat hook. Forge has a real event for this, so unlike Fabric there is no mixin.
        MinecraftForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onFoodEaten(player, event.getItem());
            }
        });

        // A plain field on EventBus 6; it became a record accessor at EventBus 8.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent.Post event) -> {
            if (event.player instanceof ServerPlayer player) {
                NutritionEvents.onPlayerTick(player);
                // Drain the dirty flag once per tick, after everything that could have set it.
                // This is the loader's sync on the other two; here it is ours.
                ForgeNutritionSync.flush(player);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                NutritionEvents.onPlayerJoin(player);
                // The client entity is brand new and its capability is at the defaults, so send
                // unconditionally rather than waiting for something to change.
                ForgeNutritionSync.sendNow(player);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                ForgeNutritionSync.forget(event.getEntity()));

        // Copy across death, which the other two loaders get from copyOnDeath() on the attachment.
        // Clone fires for a death respawn AND for an end-portal return; the common code decides
        // what each one costs, so the values are copied either way and the flag is passed through.
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
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
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
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
