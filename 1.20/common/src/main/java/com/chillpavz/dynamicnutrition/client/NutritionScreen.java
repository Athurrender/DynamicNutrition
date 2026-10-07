package com.chillpavz.dynamicnutrition.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.NutrientStatus;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * The nutrition screen.
 *
 * <p><b>Everything unusual here is a direct answer to something mods of this kind get wrong</b>,
 * taken from several hundred issues and comments players have raised about them.
 *
 * <ul>
 *   <li><b>It does not pause the game.</b> A read-only panel that freezes a multiplayer world is
 *       just wrong.</li>
 *   <li><b>The panel SIZES ITSELF</b> to the number of nutrients and the longest translated label,
 *       so a datapack adding a sixth nutrient does not overflow a fixed box, and a long translation
 *       in another language does not either.</li>
 *   <li><b>Every string is MEASURED, never assumed.</b> With the forced-unicode font, a layout
 *       that assumes a width the font does not have renders visibly dislocated.</li>
 *   <li><b>The status is written in words as well as shown as a bar.</b> "I had a debuff for days
 *       and had no idea where it came from" is the most repeated complaint about mods of this
 *       kind.</li>
 * </ul>
 */
public class NutritionScreen extends Screen {

    /** A plain texture on this band, which has no GUI sprite atlas. */
    private static final ResourceLocation PANEL =
            new ResourceLocation(Constants.MOD_ID, "textures/gui/sprites/panel.png");

    /** The panel sprite's size and nine slice border. These MUST match panel.png.mcmeta. */
    private static final int PANEL_SPRITE = 8;
    private static final int PANEL_BORDER = 3;
    private static final ResourceLocation BARS =
            new ResourceLocation(Constants.MOD_ID, "textures/gui/bars.png");
    private static final ResourceLocation ARROWS =
            new ResourceLocation(Constants.MOD_ID, "textures/gui/arrows.png");

    /** The back arrow sheet: two 10x8 arrows side by side, normal then hover, with a 1px gap. */
    private static final int ARROW_W = 10;
    private static final int ARROW_H = 8;
    private static final int ARROWS_TEX_W = 21;
    private static final int ARROWS_TEX_H = 8;

    // TEN segments of ten pixels plus a one pixel end cap. Ten because a nutrient runs 0 to 100, so
    // one segment is worth ten points and a player can read the value off the bar; the sheet is
    // generated at this width rather than cropped here.
    private static final int BAR_W = 101;
    private static final int BAR_H = 5;
    private static final int BAR_PITCH = 10;
    private static final int BARS_TEX_W = 101;
    private static final int BARS_TEX_H = 50;

    private static final int PAD = 8;
    private static final int ROW_H = 22;
    private static final int ICON = 16;
    private static final int GAP = 4;

    /** Vanilla's own dark label colour on a light panel. */
    private static final int TEXT = 0xFF3F3F3F;
    private static final int TEXT_WARN = 0xFF9B2622;

    /**
     * Threshold ticks: red for the low line, green for the target, and WHITE once the value is past
     * them.
     *
     * <p>The colour says which line it is while the line still matters. Once the nutrient has
     * passed it the question changes from "how far do I have to go" to "am I still above it", and a
     * white tick answers that against any of the five fills at a glance.
     */
    private static final int MARK_LOW = 0xFFE05A5A;
    private static final int MARK_TARGET = 0xFF6ADE6A;
    private static final int MARK_PASSED = 0xFFFFFFFF;

    private final Screen parent;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int barX;
    private int valueX;

    public NutritionScreen(Screen parent) {
        super(Component.translatable("screen." + Constants.MOD_ID + ".title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Measure, never assume. The longest label decides the layout, so a translation longer than
        // the English one cannot push the bar off the panel.
        int labelW = 0;
        for (Nutrient nutrient : Nutrients.all()) {
            labelW = Math.max(labelW, this.font.width(Component.translatable(nutrient.translationKey())));
        }
        // The value column is sized from the WIDEST value the bar can ever show, not from the
        // current one, or the layout would shift as the player ate.
        int valueW = this.font.width(valueText(100));

        panelW = PAD + ICON + GAP + labelW + GAP + BAR_W + GAP + valueW + PAD;
        panelH = PAD + this.font.lineHeight + GAP + Nutrients.count() * ROW_H + PAD;

        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        barX = left + PAD + ICON + GAP + labelW + GAP;
        valueX = barX + BAR_W + GAP;

        // Top left, with the SAME margin above it as to its left, which is the panel's own content
        // inset so the arrow sits on the same grid as everything else.
        addRenderableWidget(new BackButton(left + PAD, top + PAD, b -> this.onClose()));
    }

    /**
     * The back arrow.
     *
     * <p>Icon only, so it overrides {@code renderWidget} and draws no button frame: a bevelled
     * frame around a ten by eight arrow would leave no room for the arrow. Vanilla's own recipe book button is built the same way.
     *
     * <p>It is shown whether or not there is a parent screen. With one it goes back to the
     * inventory, without one it closes to the world, and both are what a player means by "back".
     */
    private static final class BackButton extends Button {

        private BackButton(int x, int y, Button.OnPress onPress) {
            super(x, y, ARROW_W, ARROW_H, Component.translatable("gui.back"), onPress,
                    DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable("gui.back")));
        }

        // renderWidget is overridable on this band; the icon replaces the whole default button.
        @Override
        protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY,
                                    float partialTick) {
            // The hover arrow is the second one on the sheet. Both keep the dark outline, so
            // contrast never drops on hover; only the interior lights up.
            int u = isHoveredOrFocused() ? ARROW_W + 1 : 0;
            GuiBlit.texture(gfx, ARROWS, getX(), getY(), u, 0,
                    ARROW_W, ARROW_H, ARROWS_TEX_W, ARROWS_TEX_H);
        }
    }

    private static Component valueText(int value) {
        return Component.literal(value + " / " + (int) PlayerNutrition.MAX);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // The panel and the rows FIRST, then super, which is the only thing that draws the widgets.
        // Screen.render does nothing but iterate the renderables, so calling it first
        // and then painting the panel drew the back arrow and immediately covered it. It still took
        // clicks and still showed its tooltip, because hit testing does not care about draw order,
        // which is a convincing way for a widget to look like it was never added.
        GuiBlit.nineSlice(gfx, PANEL, left, top, panelW, panelH,
                PANEL_SPRITE, PANEL_SPRITE, PANEL_BORDER);

        int titleX = left + (panelW - this.font.width(this.title)) / 2;
        gfx.drawString(this.font, this.title, titleX, top + PAD, TEXT, false);

        PlayerNutrition nutrition = this.minecraft == null || this.minecraft.player == null
                ? null : Services.STORAGE.get(this.minecraft.player);

        int y = top + PAD + this.font.lineHeight + GAP;
        int index = 0;
        for (Nutrient nutrient : Nutrients.all()) {
            int rowY = y + index * ROW_H;
            int barY = rowY + (ROW_H - BAR_H) / 2;

            gfx.renderItem(iconFor(nutrient), left + PAD, rowY + (ROW_H - ICON) / 2);

            Component label = Component.translatable(nutrient.translationKey());
            gfx.drawString(this.font, label, left + PAD + ICON + GAP,
                    rowY + (ROW_H - this.font.lineHeight) / 2, TEXT, false);

            int value = nutrition == null ? 0 : nutrition.display(nutrient);
            NutrientStatus status = nutrition == null
                    ? NutrientStatus.SAFE : nutrition.status(nutrient);

            drawBar(gfx, index, nutrient, barX, barY, value);

            Component valueLabel = valueText(value);
            // Right-aligned against the measured width, which is what makes the forced-unicode font
            // a non-event rather than the visible dislocation it otherwise causes.
            int vx = valueX + (this.font.width(valueText(100)) - this.font.width(valueLabel));
            gfx.drawString(this.font, valueLabel, vx, rowY + (ROW_H - this.font.lineHeight) / 2,
                    status == NutrientStatus.MALNOURISHED ? TEXT_WARN : TEXT, false);

            if (mouseX >= barX && mouseX < barX + BAR_W && mouseY >= barY && mouseY < barY + BAR_H) {
                List<Component> tip = tooltipFor(nutrient);
                if (!tip.isEmpty()) {
                    // Deferred to the end of the frame, so the widgets drawn after it cannot cover it.
                    setTooltipForNextRenderPass(tip.stream().map(Component::getVisualOrderText).toList());
                }
            }
            index++;
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /**
     * One nutrient's bar: the empty track first, then the fill clipped to the value.
     *
     * <p>Both halves come out of the same sheet, ten rows per nutrient, track above fill. A source
     * pixel outside the fill width is simply not drawn, which is how the rounded end caps survive.
     */
    private void drawBar(GuiGraphics gfx, int index, Nutrient nutrient, int x, int y,
                         int value) {
        int trackV = index * BAR_PITCH;
        int fillV = trackV + BAR_H;
        GuiBlit.texture(gfx, BARS, x, y, 0, trackV, BAR_W, BAR_H,
                BARS_TEX_W, BARS_TEX_H);

        int filled = Math.round(BAR_W * Math.min(value, PlayerNutrition.MAX) / PlayerNutrition.MAX);
        if (filled > 0) {
            GuiBlit.texture(gfx, BARS, x, y, 0, fillV, filled, BAR_H,
                    BARS_TEX_W, BARS_TEX_H);
        }

        // The two thresholds, marked AFTER the fill so a mark the player has passed is still there.
        // Players coming from similar mods will look for these, and a bar with no marks cannot
        // answer "how much more do I need"; the numbers differ per nutrient now, so there is no
        // longer a single figure anybody could learn once and reuse.
        drawThreshold(gfx, x, y, nutrient.malnourishedBelow(), value, MARK_LOW);
        drawThreshold(gfx, x, y, nutrient.targetLow(), value, MARK_TARGET);
    }

    /**
     * A one pixel tick spanning the FULL height of the bar and no more.
     *
     * <p>It covers the bar's own top and bottom border rows, which the first version left alone, so
     * the mark reads as a division of the bar rather than as something sitting inside it. It stops
     * exactly at the bar's edge: overhanging onto the panel made the bar look taller than it is.
     */
    private void drawThreshold(GuiGraphics gfx, int x, int y, float at, int value,
                               int colour) {
        int tick = x + Math.round(BAR_W * at / PlayerNutrition.MAX);
        gfx.fill(tick, y, tick + 1, y + BAR_H, value >= at ? MARK_PASSED : colour);
    }

    /**
     * What the two marks on this bar EARN: "Above 64: Speed" and "Below 22: Slowness".
     *
     * <p>Nothing else. The status in words and a line restating both thresholds were in it before,
     * and with the effect lines beside them they said the same thing three times. The effect names
     * are vanilla's own translated names, the same ones the hover over Well Nourished and
     * Malnourished lists, so what a player reads there can be traced back to a bar. An effect the server has switched off (0%) gets no line, and a bar with neither has
     * no tooltip at all.
     */
    private List<Component> tooltipFor(Nutrient nutrient) {
        List<Component> lines = new ArrayList<>();
        NutrientEffects.Spec above = NutrientEffects.forNutrient(nutrient, true);
        NutrientEffects.Spec below = NutrientEffects.forNutrient(nutrient, false);
        if (above != null && SyncedEffectSettings.fraction(above) > 0) {
            lines.add(Component.translatable("screen." + Constants.MOD_ID + ".effect_above",
                    Math.round(nutrient.targetLow()), above.displayName()));
        }
        if (below != null && SyncedEffectSettings.fraction(below) > 0) {
            lines.add(Component.translatable("screen." + Constants.MOD_ID + ".effect_below",
                    Math.round(nutrient.malnourishedBelow()), below.displayName()));
        }
        return lines;
    }

    /** The item shown beside each nutrient. Config will make these choosable; these are defaults. */
    private static ItemStack iconFor(Nutrient nutrient) {
        String id = switch (nutrient.name()) {
            case "carbohydrates" -> "minecraft:bread";
            case "protein" -> "minecraft:cooked_beef";
            case "fat" -> "minecraft:cooked_porkchop";
            case "vitamins" -> "minecraft:carrot";
            case "minerals" -> "minecraft:dried_kelp";
            default -> "minecraft:apple";
        };
        ResourceLocation key = new ResourceLocation(id);
        // Defaulted registry: an absent id resolves to AIR rather than null, so ask before taking.
        return BuiltInRegistries.ITEM.containsKey(key)
                ? new ItemStack(BuiltInRegistries.ITEM.get(key))
                : ItemStack.EMPTY;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Closing on the inventory key as well as escape is what a player expects from a panel they
        // opened from the inventory, which is what a player expects from a panel they opened
        // from there.
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        if (DynamicNutritionKeys.OPEN_SCREEN.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    /**
     * A read-only panel should not freeze the world. It is also simply wrong in multiplayer, where
     * it does nothing at all and only misleads the person who opened it.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
