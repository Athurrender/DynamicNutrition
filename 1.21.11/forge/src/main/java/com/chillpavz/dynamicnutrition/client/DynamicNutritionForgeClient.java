package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
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

    public static void init(BusGroup modBus) {
        NutrientBlindnessFog.install();

        // Keybind registration is a MOD bus event; everything else below is on the game bus. Same
        // split as everywhere else in this mod, and getting it wrong fails silently.
        RegisterKeyMappingsEvent.getBus(modBus).addListener(event ->
                event.register(DynamicNutritionKeys.OPEN_SCREEN));

        // The HUD strip, added just above vanilla's food bar inside the hotbar stack, so it draws
        // over the region the hunger bar owns rather than under it. The strip computes its own
        // position, so this decides z-order and nothing else.
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.HOTBAR_AND_DECOS,
                Identifier.withDefaultNamespace("food"),
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "nutrients"),
                NutritionHud::render));

        // Init.Post fires again whenever the screen re-inits, which the inventory does when the
        // recipe book is toggled and the panel moves.
        ScreenEvent.Init.Post.BUS.addListener(event -> {
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

        ItemTooltipEvent.BUS.addListener(event ->
                FoodTooltip.appendTo(event.getItemStack(), event.getToolTip(),
                        event.getFlags().isAdvanced(), event.getEntity()));

        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            Minecraft client = Minecraft.getInstance();
            // No "is a screen open" guard: vanilla only dispatches keybinds while no screen is open.
            while (DynamicNutritionKeys.OPEN_SCREEN.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new NutritionScreen(null));
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
