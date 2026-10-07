package com.chillpavz.dynamicnutrition.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import com.chillpavz.dynamicnutrition.client.EffectDisplay;

/**
 * The effect list beside the inventory: hides this mod's effects when the player asked for that,
 * never draws a legacy id, and gives the two display effects a hover that lists what each stands
 * for.
 *
 * <p>On this band the list is drawn by {@code EffectRenderingInventoryScreen}, and vanilla shows a
 * tooltip only in the COMPACT layout (icons only, when there is no room for names). So the hover
 * has two halves: in the compact layout our lines are added to vanilla's own tooltip, and in the
 * wide layout, where vanilla draws none, ours is drawn after the list. Either way one tooltip is
 * drawn. The hit test is {@link EffectDisplay#hoveredInInventory}, which mirrors vanilla's layout.
 *
 * <p>Extends the target's superclass only to reach its protected layout fields.
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectsInInventoryMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private EffectsInInventoryMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Redirect(method = "renderEffects", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> dynamicnutrition$effects(LocalPlayer player) {
        return EffectDisplay.forInventory(player.getActiveEffects());
    }

    @Redirect(method = "renderEffects", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V"))
    private void dynamicnutrition$compactHover(GuiGraphics graphics, Font font, List<Component> lines,
                                               Optional<TooltipComponent> image, int mouseX,
                                               int mouseY) {
        List<Component> tooltip = new ArrayList<>(lines);
        EffectDisplay.addHover(dynamicnutrition$hovered(mouseX, mouseY), tooltip);
        graphics.renderTooltip(font, tooltip, image, mouseX, mouseY);
    }

    @Inject(method = "renderEffects", require = 1, at = @At("TAIL"))
    private void dynamicnutrition$wideHover(GuiGraphics graphics, int mouseX, int mouseY,
                                            CallbackInfo info) {
        int x = this.leftPos + this.imageWidth + 2;
        EffectDisplay.wideHover(graphics, this.font, dynamicnutrition$shown(), x, this.topPos,
                this.width - x, mouseX, mouseY);
    }

    private MobEffectInstance dynamicnutrition$hovered(int mouseX, int mouseY) {
        int x = this.leftPos + this.imageWidth + 2;
        return EffectDisplay.hoveredInInventory(dynamicnutrition$shown(), x, this.topPos,
                this.width - x, mouseX, mouseY);
    }

    private Collection<MobEffectInstance> dynamicnutrition$shown() {
        return this.minecraft == null || this.minecraft.player == null ? List.of()
                : EffectDisplay.forInventory(this.minecraft.player.getActiveEffects());
    }
}
