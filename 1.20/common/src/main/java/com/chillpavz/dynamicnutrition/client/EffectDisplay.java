package com.chillpavz.dynamicnutrition.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * <p>NeoForge asks per effect through its own client extension; Fabric and Forge hand vanilla's
 * collection through {@link #filter} in a mixin. The hover is added to vanilla's own tooltip where
 * there is one ({@link #addHover}) and drawn by {@link #wideHover} where vanilla has none. The ten 1.5.0 effect ids are never
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
                if (visible(holder(instance), showOurs)) {
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
                    || !NutrientEffects.isDisplay(holder(instance))) {
                return List.of();
            }
            PlayerNutrition nutrition = Services.STORAGE.get(client.player);
            return NutrientEffects.describe(holder(instance), nutrition::heldStatus,
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
                tooltip.add(instance.getEffect().getDisplayName());
            }
            tooltip.addAll(lines);
        } catch (Throwable t) {
            // An unmodifiable list from some other mod: the plain tooltip is the answer.
        }
    }

    /**
     * Which effect in the inventory's list is under the mouse, by vanilla's own layout on this
     * band: a column {@code x} along from the panel, rows {@code spacing} apart from {@code top},
     * 120 wide when there is room for the labels and 33 wide when there is not, and the LAST row
     * containing the mouse wins, exactly as vanilla's own loop decides it.
     *
     * @param shown     what the list draws, after {@link #forInventory}
     * @param available the width from {@code x} to the screen's edge
     */
    public static MobEffectInstance hoveredInInventory(Collection<MobEffectInstance> shown, int x,
                                                       int top, int available, int mouseX,
                                                       int mouseY) {
        if (shown.isEmpty() || available < 32) {
            return null;
        }
        int wide = available >= 120 ? 120 : 33;
        if (mouseX < x || mouseX > x + wide) {
            return null;
        }
        int spacing = shown.size() > 5 ? 132 / (shown.size() - 1) : 33;
        List<MobEffectInstance> sorted = new ArrayList<>(shown);
        Collections.sort(sorted);
        MobEffectInstance hit = null;
        int rowY = top;
        for (MobEffectInstance instance : sorted) {
            if (mouseY >= rowY && mouseY <= rowY + spacing) {
                hit = instance;
            }
            rowY += spacing;
        }
        return hit;
    }

    /**
     * The hover in the WIDE layout, where vanilla draws no tooltip of its own on this band: our
     * effect's name and what it stands for, if the effect under the mouse is one of ours. The
     * compact layout is handled by adding to vanilla's own tooltip instead ({@link #addHover}), so
     * one tooltip is ever drawn.
     */
    public static void wideHover(GuiGraphics graphics, Font font, Collection<MobEffectInstance> shown,
                                 int x, int top, int available, int mouseX, int mouseY) {
        if (available < 120) {
            return;
        }
        try {
            MobEffectInstance hit = hoveredInInventory(shown, x, top, available, mouseX, mouseY);
            List<Component> lines = describe(hit);
            if (lines.isEmpty()) {
                return;
            }
            List<Component> tooltip = new ArrayList<>(lines.size() + 1);
            tooltip.add(hit.getEffect().getDisplayName());
            tooltip.addAll(lines);
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        } catch (Throwable t) {
            // On the render path: no tooltip rather than an exception every frame.
        }
    }

    /**
     * An instance's effect as the registry's own holder. {@code getEffect()} returns the bare
     * {@code MobEffect} on this band; the mod's rules are written against holders, which is what
     * the newer bands hand out, so the conversion happens here, once.
     */
    public static Holder<MobEffect> holder(MobEffectInstance instance) {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(instance.getEffect());
    }
}
