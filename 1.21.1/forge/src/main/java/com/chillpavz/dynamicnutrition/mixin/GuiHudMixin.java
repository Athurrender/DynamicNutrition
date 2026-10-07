package com.chillpavz.dynamicnutrition.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chillpavz.dynamicnutrition.client.NutritionHud;

/**
 * The nutrient strip on Forge 52, which has no HUD event of any kind: the overlay registry was
 * removed at 1.20.6 and HUD layers came back only in a later Forge. So it is drawn after vanilla's
 * whole HUD, which is also where Fabric's HUD callback runs on this band. The strip works out its
 * own position from the food bar's, and hides itself with the HUD, so the order is all this decides.
 */
@Mixin(Gui.class)
public abstract class GuiHudMixin {

    @Inject(method = "render", require = 1, at = @At("TAIL"))
    private void dynamicnutrition$strip(GuiGraphics graphics, DeltaTracker delta, CallbackInfo info) {
        NutritionHud.render(graphics, delta);
    }
}
