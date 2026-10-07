package com.chillpavz.dynamicnutrition.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * The keybind, built here so both loaders register the same object.
 *
 * <p><b>The KEYBIND is the primary way in and the inventory button is secondary.</b> That is not a
 * style choice: an inventory button colliding with another mod's is far and away the most reported
 * GUI problem for mods of this kind, and the usual answer to such a report is "there is a config
 * option to move it, and the keybind opens it anyway". Leading with the key makes a collision
 * cosmetic rather than a lockout.
 *
 * <p>N is the default because it is what players of similar mods already press.
 */
public final class DynamicNutritionKeys {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "main"));

    /**
     * The constructor WITHOUT an input type, on purpose. 26.3 merged {@code InputConstants.Type}'s
     * KEYSYM and SCANCODE into KEYBOARD, so naming either one throws on the other side of the
     * change before the main menu. This constructor exists unchanged from 26.1 to 26.3 and picks the
     * keyboard type itself.
     */
    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            "key." + Constants.MOD_ID + ".open_screen",
            defaultKey(),
            CATEGORY);

    /**
     * N, resolved BY NAME when the game is running, never as {@code InputConstants.KEY_N}.
     *
     * <p>{@code KEY_N} is a compile time constant, so its value is baked into this jar, and 26.3
     * renumbered every key (N went from 78 to 17). A jar built against 26.2 therefore bound the
     * default to whatever 78 means at 26.3, which is Page Down. The key's NAME did not change, and
     * asking the running game for it gives that version's own number. If the lookup fails the key
     * starts unbound, which a player can fix in Controls, rather than stopping the client.
     */
    private static int defaultKey() {
        try {
            return InputConstants.getKey("key.keyboard.n").getValue();
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.warn("Could not resolve the default key for the nutrition screen, so it "
                    + "starts unbound. Set it in Controls.", e);
            return InputConstants.UNKNOWN.getValue();
        }
    }

    private DynamicNutritionKeys() {
    }
}
