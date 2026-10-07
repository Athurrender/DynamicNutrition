package com.chillpavz.dynamicnutrition.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.resources.ResourceLocation;

import com.chillpavz.dynamicnutrition.Constants;

import com.chillpavz.dynamicnutrition.network.NutritionSyncPayload;

public class DynamicNutritionFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // keybinding.v1.KeyBindingHelper on this band. From 26.1 it is keymapping.v1
        // .KeyMappingHelper.registerKeyMapping, and the class and method names are the only
        // things that changed.
        KeyBindingHelper.registerKeyBinding(DynamicNutritionKeys.OPEN_SCREEN);

        ClientPlayNetworking.registerGlobalReceiver(NutritionSyncPayload.TYPE,
                (payload, context) -> NutritionClient.acceptTable(payload));

        // Fabric's callback carries no player, so the local one is read HERE rather than inside
        // FoodTooltip, which keeps that class free of any client-only Minecraft reference.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                FoodTooltip.appendTo(stack, lines, flag.isAdvanced(),
                        net.minecraft.client.Minecraft.getInstance().player));

        // Fabric API has no HUD layers on this band, only a callback after the vanilla HUD. The
        // strip works out its own position from the food bar's, so the order is all this decides.
        HudRenderCallback.EVENT.register(NutritionHud::render);

        // AFTER_INIT rather than a one-off, because the inventory screen re-runs init() whenever the
        // recipe book is toggled, which is also when the panel moves.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!NutritionButton.enabled() || !(screen instanceof InventoryScreen inventory)) {
                return;
            }
            Screens.getButtons(screen).add(new NutritionButton(
                    NutritionButton.x(inventory.leftPos),
                    NutritionButton.y(inventory.topPos), screen));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // No "is a screen already open" guard, because Minecraft exposes no current-screen
            // accessor at 26.2 and none is needed: vanilla only dispatches keybinds while no screen
            // is open, so consumeClick cannot fire underneath one.
            while (DynamicNutritionKeys.OPEN_SCREEN.consumeClick()) {
                if (client.player != null) {
                    client.setScreen(new NutritionScreen(null));
                }
            }
        });
    }
}
