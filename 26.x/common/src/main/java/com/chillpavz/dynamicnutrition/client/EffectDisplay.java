package com.chillpavz.dynamicnutrition.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * How this mod's two effects are shown on the client: whether they are drawn at the top right and
 * in the list beside the inventory (this player's own preference), and what hovering one in that
 * list says.
 *
 * <p>NeoForge asks per effect through its own client extension and gathers the hover text through an
 * event; Fabric has no hook for either, so its mixins hand vanilla's collection through
 * {@link #filter} and call {@link #hover} for each entry drawn. The ten 1.5.0 effect ids are never
 * drawn at all: the server takes them off within a second, and this keeps that second invisible.
 */
public final class EffectDisplay {

    private EffectDisplay() {
    }

    public static Collection<MobEffectInstance> forHud(Collection<MobEffectInstance> effects) {
        return filter(effects, SyncedEffectSettings.showOnHud());
    }

    public static Collection<MobEffectInstance> forInventory(Collection<MobEffectInstance> effects) {
        return filter(effects, SyncedEffectSettings.showInInventory());
    }

    /** Whether one effect is drawn, given this player's preference for this mod's two. */
    public static boolean visible(Holder<MobEffect> effect, boolean showOurs) {
        if (!NutrientEffects.isOurs(effect)) {
            return true;
        }
        return showOurs && NutrientEffects.isDisplay(effect);
    }

    /** A copy with only what should be drawn. Never throws: this is on the render path. */
    public static Collection<MobEffectInstance> filter(Collection<MobEffectInstance> effects,
                                                       boolean showOurs) {
        try {
            List<MobEffectInstance> out = new ArrayList<>(effects.size());
            for (MobEffectInstance instance : effects) {
                if (visible(instance.getEffect(), showOurs)) {
                    out.add(instance);
                }
            }
            return out;
        } catch (Throwable t) {
            return effects;
        }
    }

    /**
     * The effects a display effect stands for, for the LOCAL player. Their sides come from the
     * synced attachment and the strengths from the server's synced settings, so this lists what the
     * server is actually applying. Empty for any other effect, and on any failure.
     */
    public static List<Component> describe(MobEffectInstance instance) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (instance == null || client.player == null
                    || !NutrientEffects.isDisplay(instance.getEffect())) {
                return List.of();
            }
            PlayerNutrition nutrition = Services.STORAGE.get(client.player);
            return NutrientEffects.describe(instance.getEffect(), nutrition::heldStatus,
                    SyncedEffectSettings::fraction);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /**
     * NeoForge: add what a hovered display effect stands for to the tooltip being gathered. The list
     * is empty when vanilla shows the name beside the icon, so the name goes first in that case.
     */
    public static void addHover(MobEffectInstance instance, List<Component> tooltip) {
        List<Component> lines = describe(instance);
        if (lines.isEmpty()) {
            return;
        }
        try {
            if (tooltip.isEmpty()) {
                tooltip.add(instance.getEffect().value().getDisplayName());
            }
            tooltip.addAll(lines);
        } catch (Throwable t) {
            // An unmodifiable list from some other mod: the plain tooltip is the answer.
        }
    }

    /**
     * Fabric: the same hover, called at the start of vanilla's text for each inventory entry, with
     * vanilla's own hover box. Set BEFORE vanilla sets its own (only when the name is cut off), so
     * ours is the one kept: a later tooltip in the same frame does not replace an earlier one.
     */
    public static void hover(GuiGraphicsExtractor graphics, Font font, MobEffectInstance instance,
                             Component name, int x, int y, int width, int height,
                             int mouseX, int mouseY) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return;
        }
        List<Component> lines = describe(instance);
        if (lines.isEmpty()) {
            return;
        }
        try {
            List<Component> tooltip = new ArrayList<>(lines.size() + 1);
            tooltip.add(name);
            tooltip.addAll(lines);
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        } catch (Throwable t) {
            // On the render path: no tooltip rather than an exception every frame.
        }
    }
}
