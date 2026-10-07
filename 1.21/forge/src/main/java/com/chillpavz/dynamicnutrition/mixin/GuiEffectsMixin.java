package com.chillpavz.dynamicnutrition.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import com.chillpavz.dynamicnutrition.client.EffectDisplay;

/**
 * Hides this mod's effects from the icons at the top right when the player asks, on 26.1, where the HUD still lives in Gui.
 *
 * <p>Named by STRING and optional: the jar spans versions where only one of Gui.extractEffects and
 * Hud.extractEffects exists, and the other one's mixin is simply not applied.
 *
 * <p><b>Every static member of a mixin must be private, and nothing outside may reference this
 * class.</b>
 */
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class GuiEffectsMixin {

    @Redirect(method = "renderEffects", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> dynamicnutrition$effects(LocalPlayer player) {
        return EffectDisplay.forHud(player.getActiveEffects());
    }
}
