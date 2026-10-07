package com.chillpavz.dynamicnutrition.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.ClothCompat;

/**
 * Client wiring for Forge. Only ever touched from the client dist, so nothing here can load on a
 * dedicated server.
 *
 * <p>One thing is NOT here and is worth saying so: hiding this mod's two effect icons. NeoForge
 * does it with a client extension per mob effect, but Forge's equivalent is only reachable by
 * overriding {@code initializeClient} on the effect class, which lives in common and therefore
 * cannot name anything loader specific. Forge uses the same two mixins Fabric does instead, in
 * {@code dynamicnutrition.forge.mixins.json}.
 */
public final class DynamicNutritionForgeClient {

    private DynamicNutritionForgeClient() {
    }

    public static void init(IEventBus modBus) {
        // The vitamins debuff's fog. Both events fire after vanilla has chosen its fog, and the
        // distances are applied only when the event is cancelled, so it is cancelled only when the
        // debuff applies.
        MinecraftForge.EVENT_BUS.addListener((ViewportEvent.RenderFog event) -> {
            float[] fog = NutrientBlindnessFog.distances(event.getType(), event.getCamera().getEntity(),
                    Minecraft.getInstance().gameRenderer.getRenderDistance(),
                    event.getMode() == FogRenderer.FogMode.FOG_SKY);
            if (fog != null) {
                event.setNearPlaneDistance(fog[0]);
                event.setFarPlaneDistance(fog[1]);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ViewportEvent.ComputeFogColor event) -> {
            float scale = NutrientBlindnessFog.colorScale(event.getCamera().getFluidInCamera(),
                    event.getCamera().getEntity());
            if (scale < 1.0F) {
                event.setRed(event.getRed() * scale);
                event.setGreen(event.getGreen() * scale);
                event.setBlue(event.getBlue() * scale);
            }
        });

        // Keybind registration is a MOD bus event; everything else below is on the game bus. Same
        // split as everywhere else in this mod, and getting it wrong fails silently.
        modBus.addListener((RegisterKeyMappingsEvent event) ->
                event.register(DynamicNutritionKeys.OPEN_SCREEN));

        // The HUD strip is drawn by GuiHudMixin: Forge 52 has no HUD event to register it with.

        // Init.Post fires again whenever the screen re-inits, which the inventory does when the
        // recipe book is toggled and the panel moves.
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            if (!NutritionButton.enabled()
                    || !(event.getScreen() instanceof InventoryScreen inventory)) {
                return;
            }
            // getGuiLeft/getGuiTop, not leftPos/topPos: Forge patches public accessors onto
            // AbstractContainerScreen, so this module needs none of the access widening the other
            // two loaders do. Same two numbers.
            event.addListener(new NutritionButton(
                    NutritionButton.x(inventory.getGuiLeft()),
                    NutritionButton.y(inventory.getGuiTop()), inventory));
        });

        MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent event) ->
                FoodTooltip.appendTo(event.getItemStack(), event.getToolTip(),
                        event.getFlags().isAdvanced(), event.getEntity()));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> {
            Minecraft client = Minecraft.getInstance();
            // No "is a screen open" guard: vanilla only dispatches keybinds while no screen is open.
            while (DynamicNutritionKeys.OPEN_SCREEN.consumeClick()) {
                if (client.player != null) {
                    client.setScreen(new NutritionScreen(null));
                }
            }
        });

        // The mods-list Config button. Registered only when cloth is actually present, because a
        // factory that always returns null gives the player an enabled button that does nothing.
        if (ClothCompat.isAvailable()) {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> {
                        Screen screen = ClothCompat.screen(parent);
                        return screen == null ? parent : screen;
                    }));
        }

        Constants.LOG.debug("Client keybind and screen registered");
    }
}
