package com.chillpavz.dynamicnutrition.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import com.chillpavz.dynamicnutrition.Constants;

import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;

public class DynamicNutritionFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // At 26.2 this is keymapping.v1.KeyMappingHelper. The older keybinding.v1.KeyBindingHelper
        // no longer exists, and the class name is the only thing that changed.
        KeyMappingHelper.registerKeyMapping(DynamicNutritionKeys.OPEN_SCREEN);

        NutrientBlindnessFog.install();

        ClientPlayNetworking.registerGlobalReceiver(NutritionSyncPayload.TYPE,
                (payload, context) -> NutritionClient.acceptTable(payload));

        // Fabric's callback carries no player, so the local one is read HERE rather than inside
        // FoodTooltip, which keeps that class free of any client-only Minecraft reference.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                FoodTooltip.appendTo(stack, lines, flag.isAdvanced(),
                        net.minecraft.client.Minecraft.getInstance().player));

        // Attached to the food bar, so the strip sits with the thing it describes and moves with it
        // if another mod reorders the HUD.
        HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "nutrients"),
                NutritionHud::render);

        // AFTER_INIT rather than a one-off, because the inventory screen re-runs init() whenever the
        // recipe book is toggled, which is also when the panel moves.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!NutritionButton.enabled() || !(screen instanceof InventoryScreen inventory)) {
                return;
            }
            Screens.getWidgets(screen).add(new NutritionButton(
                    NutritionButton.x(inventory.leftPos),
                    NutritionButton.y(inventory.topPos), screen));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // No "is a screen already open" guard, because Minecraft exposes no current-screen
            // accessor at 26.2 and none is needed: vanilla only dispatches keybinds while no screen
            // is open, so consumeClick cannot fire underneath one.
            while (DynamicNutritionKeys.OPEN_SCREEN.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new NutritionScreen(null));
                }
            }
        });
    }
}
