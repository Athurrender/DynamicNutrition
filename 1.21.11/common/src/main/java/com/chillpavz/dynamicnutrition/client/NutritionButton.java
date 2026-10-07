package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;

/**
 * The small button in the inventory that opens the nutrition screen.
 *
 * <h2>It is the SECOND way in, on purpose</h2>
 * An inventory button colliding with another mod's is the single most reported GUI problem for
 * mods of this kind. The recurring shapes are all worth designing against: rendering underneath
 * another mod's sort button and losing the hover to it, a position config that does not survive a
 * client restart, a transparent pixel against the vanilla button texture, and a default position
 * that covers the localised "Crafting" label in every language that is not English.
 *
 * <p>So the keybind is the primary route and this is secondary. A collision then costs a player a
 * button, not access to the feature, and the fix is one config option away.
 *
 * <p>It also ships NO pixels: the icon is vanilla's own {@code hud/food_full} sprite, blitted by
 * identifier. Every mod does that, it needs permission from nobody, and it is the same drumstick
 * players already associate with hunger.
 */
public class NutritionButton extends Button {

    public static final int SIZE = 9;

    /**
     * Default position, relative to the inventory panel's top left.
     *
     * <p>The panel is 176 by 166. This is its top right corner, which is clear of the recipe book
     * button (around x 104, y 62) and clear of the "Crafting" label baked into the texture further
     * left. Both of those are what the reports above are about.
     */
    public static final int DEFAULT_X = 163;

    /**
     * Four, so the gap above the button matches the gap to its right.
     *
     * <p>The panel is 176 wide and the button is nine, so at x 163 the right margin is four. A top
     * margin of seven put it visibly off centre in its own corner.
     */
    public static final int DEFAULT_Y = 4;

    private static final Identifier ICON = Identifier.withDefaultNamespace("hud/food_full");
    private static final int HOVER = 0x50FFFFFF;

    public NutritionButton(int x, int y, Screen parent) {
        super(x, y, SIZE, SIZE, Component.translatable("screen." + Constants.MOD_ID + ".title"),
                b -> open(parent), DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.translatable("screen." + Constants.MOD_ID + ".title")));
    }

    /** True when the player has asked for the button at all. */
    public static boolean enabled() {
        return NutritionConfig.buttonEnabled;
    }

    /** The vanilla inventory panel, which is what the offsets are CLAMPED to. */
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    public static int x(int leftPos) {
        return leftPos + clamp(DEFAULT_X + NutritionConfig.buttonOffsetX, PANEL_W);
    }

    public static int y(int topPos) {
        return topPos + clamp(DEFAULT_Y + NutritionConfig.buttonOffsetY, PANEL_H);
    }

    /**
     * Keep the button on the panel whatever the config says.
     *
     * <p>The offsets used to allow plus or minus two hundred, which is larger than the panel, so a
     * player exploring the config pushed the button off it and had no way to tell whether they had
     * broken something or switched it off. The slider range is narrower now AND this clamps, because
     * a range alone cannot account for the default position it is added to.
     */
    private static int clamp(int value, int extent) {
        return Math.max(0, Math.min(value, extent - SIZE));
    }

    /**
     * The parent is passed IN rather than looked up, because {@code Minecraft} exposes no
     * current-screen accessor at 26.2 at any visibility. Whoever adds the button already has the
     * screen it is being added to, so this costs nothing and needs no access widener.
     */
    private static void open(Screen parent) {
        Minecraft.getInstance().setScreenAndShow(new NutritionScreen(parent));
    }

    /**
     * The whole render. {@code renderWidget} is final on {@code AbstractButton}, and
     * this is the hook it leaves open; not calling {@code renderDefaultSprite} is what makes the
     * button icon-only, because at nine pixels square a bevelled button frame would leave no room
     * for anything inside it. Vanilla's own recipe book button is built the same way.
     */
    @Override
    protected void renderContents(GuiGraphics gfx, int mouseX, int mouseY,
                                   float partialTick) {
        GuiBlit.sprite(gfx, ICON, getX(), getY(), SIZE, SIZE);
        if (isHoveredOrFocused()) {
            gfx.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, HOVER);
        }
    }
}
