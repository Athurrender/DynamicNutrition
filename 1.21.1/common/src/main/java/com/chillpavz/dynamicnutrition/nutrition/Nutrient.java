package com.chillpavz.dynamicnutrition.nutrition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * One tracked nutrient.
 *
 * <p>Nothing outside this package should switch on a nutrient's identity. Everything is looked up
 * through {@link Nutrients} by name so that adding a sixth nutrient from a datapack later is a
 * change in one class rather than a change everywhere.
 *
 * @param name      the nutrient's path, e.g. {@code carbohydrates}
 * @param order     display order in the screen, ascending
 * @param decayRate how fast this nutrient drains relative to the others. Uniform decay is the
 *                  single biggest design weakness in mods of this kind: five bars that empty at the
 *                  same speed behave as one bar with five labels. Carbohydrates burn fastest, fat
 *                  is storage and barely moves.
 * @param barColor  the nutrient's identity colour, matching its bar in the sheet. Used where the
 *                  thing being drawn is a solid shape on the light GUI panel or on the world.
 * @param malnourishedBelow below this, this nutrient counts as missing from the diet
 * @param targetLow          at or above this, this nutrient counts as being kept up
 * @param textColor the same hue lightened for TEXT on the dark tooltip ground. The bar palette is
 *                  chosen for a {@code #C6C6C6} panel, and two of its five (the dark red and the
 *                  dark blue) are close to unreadable against a tooltip. Two fields rather than one
 *                  because a single compromise colour would be wrong in both places.
 */
public record Nutrient(String name, int order, float decayRate, float malnourishedBelow,
                      float targetLow, int barColor, int textColor) {

    /** The item tag that assigns this nutrient directly, {@code #dynamicnutrition:nutrient/<name>}. */
    public TagKey<Item> tag() {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "nutrient/" + name));
    }

    /** Translation key for the screen and the tooltip. */
    public String translationKey() {
        return "nutrient." + Constants.MOD_ID + "." + name;
    }

    @Override
    public String toString() {
        return name;
    }
}
