package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.ClothCompat;

/**
 * Client wiring for NeoForge. Only ever touched from the client dist, so nothing here can load on a
 * dedicated server.
 */
public final class DynamicNutritionNeoForgeClient {

    private DynamicNutritionNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        NutrientBlindnessFog.install();

        // NeoForge asks each effect's client extension whether to draw it. The two display effects
        // answer with this player's preferences; the ten 1.5.0 ids are never drawn, as the server
        // takes them off within a second. Fabric has no such hook and uses mixins for the same thing.
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            IClientMobEffectExtensions display = new IClientMobEffectExtensions() {
                @Override
                public boolean isVisibleInInventory(MobEffectInstance instance) {
                    return EffectDisplay.visible(instance.getEffect(), SyncedEffectSettings.showInInventory());
                }

                @Override
                public boolean isVisibleInGui(MobEffectInstance instance) {
                    return EffectDisplay.visible(instance.getEffect(), SyncedEffectSettings.showOnHud());
                }
            };
            for (MobEffect effect : NutrientEffects.displayEffects()) {
                event.registerMobEffect(display, effect);
            }
            for (NutrientEffects.Spec spec : NutrientEffects.ALL) {
                event.registerMobEffect(display, spec.legacyEffect());
            }
        });

        // Hovering Well Nourished or Malnourished beside the inventory lists what it stands for.
        // Fired by NeoForge for every hovered effect, whether or not its name is cut off.
        NeoForge.EVENT_BUS.addListener(GatherEffectScreenTooltipsEvent.class, event ->
                EffectDisplay.addHover(event.getEffectInstance(), event.getTooltip()));

        // Keybind registration is a MOD bus event; the tick is a GAME bus event. Same split as
        // everywhere else in this mod, and getting it wrong fails silently.
        modBus.addListener(RegisterKeyMappingsEvent.class, event ->
                event.register(DynamicNutritionKeys.OPEN_SCREEN));

        // Also a MOD bus event. Registered above the food level layer so the strip draws over the
        // same region the hunger bar owns rather than under it.
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                        Identifier.fromNamespaceAndPath(Constants.MOD_ID, "nutrients"),
                        NutritionHud::render));

        // Init.Post is a GAME bus event and fires again whenever the screen re-inits, which the
        // inventory does when the recipe book is toggled and the panel moves.
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            if (!NutritionButton.enabled()
                    || !(event.getScreen() instanceof InventoryScreen inventory)) {
                return;
            }
            event.addListener(new NutritionButton(
                    NutritionButton.x(inventory.leftPos),
                    NutritionButton.y(inventory.topPos), inventory));
        });

        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event ->
                FoodTooltip.appendTo(event.getItemStack(), event.getToolTip(),
                        event.getFlags().isAdvanced(), event.getEntity()));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            // No "is a screen open" guard: Minecraft exposes no current-screen accessor at 26.2 and
            // vanilla only dispatches keybinds while no screen is open.
            while (DynamicNutritionKeys.OPEN_SCREEN.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new NutritionScreen(null));
                }
            }
        });
        // The mods-list Config button. Registered only when cloth is actually present, because a
        // factory that always returns null gives the player an enabled button that does nothing.
        if (ClothCompat.isAvailable()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> {
                Screen screen = ClothCompat.screen(parent);
                return screen == null ? parent : screen;
            });
        }

        Constants.LOG.debug("Client keybind and screen registered");
    }
}
