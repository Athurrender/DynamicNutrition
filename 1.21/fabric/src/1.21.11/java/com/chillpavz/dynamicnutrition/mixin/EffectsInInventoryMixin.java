package com.chillpavz.dynamicnutrition.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import com.chillpavz.dynamicnutrition.client.EffectDisplay;

/**
 * The list beside the inventory: hides this mod's effects when the player asks, and gives its two
 * effects a hover that lists what each stands for. Vanilla draws every active effect there with no
 * filter, and only shows a hover when an effect's name is cut off.
 *
 * <p>The hover needs to know WHICH effect a line of text belongs to, and {@code renderText} is not
 * told. {@code getEffectName} is, and vanilla calls it for each effect immediately before that
 * effect's text, so the instance is remembered there.
 *
 * <p><b>Every static member of a mixin must be private, and nothing outside may reference this
 * class.</b>
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {

    @Unique
    private MobEffectInstance dynamicnutrition$drawing;

    @Redirect(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> dynamicnutrition$effects(LocalPlayer player) {
        return EffectDisplay.forInventory(player.getActiveEffects());
    }

    @Inject(method = "getEffectName", require = 1, at = @At("HEAD"))
    private void dynamicnutrition$remember(MobEffectInstance instance,
                                           CallbackInfoReturnable<Component> info) {
        this.dynamicnutrition$drawing = instance;
    }

    @Inject(method = "renderText", require = 1, at = @At("HEAD"))
    private void dynamicnutrition$hover(GuiGraphics graphics, Component name,
                                        Component duration, Font font, int x, int y, int width,
                                        int height, int mouseX, int mouseY, CallbackInfo info) {
        EffectDisplay.hover(graphics, font, this.dynamicnutrition$drawing, name, x, y, width, height,
                mouseX, mouseY);
    }
}
