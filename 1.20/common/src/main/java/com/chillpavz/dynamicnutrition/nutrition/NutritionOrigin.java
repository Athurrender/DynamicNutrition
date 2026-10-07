package com.chillpavz.dynamicnutrition.nutrition;

import java.util.List;

import net.minecraft.world.item.Item;

/**
 * Where a food's nutrients came from, so the answer can be shown rather than only trusted.
 *
 * <p>Showing this in the advanced tooltip, rather than only in an export, costs
 * a string and answers the question a pack author actually has, which is not "what is this worth"
 * but "why does the mod think that". It is gated to advanced tooltips (F3+H) so an ordinary player
 * never sees it.
 *
 * @param source which stage of the pipeline answered
 * @param from   for {@link Source#RECIPE}, the ingredients that contributed, in registry order and
 *               capped. Empty for every other stage.
 */
public record NutritionOrigin(NutritionOrigin.Source source, List<Item> from) {

    /** Anything longer is unreadable in a tooltip and pointless on the wire. */
    public static final int MAX_FROM = 4;

    public static final NutritionOrigin NONE = new NutritionOrigin(Source.NONE, List.of());

    public enum Source {
        /** Real per-nutrient numbers from a datapack. Every vanilla food is this. */
        EXPLICIT,
        /** An item tag naming the nutrient directly. */
        TAG,
        /** Inherited from what the food is crafted from. */
        RECIPE,
        /** Guessed from the food convention tags. The stage that means nothing is ever worth zero. */
        HEURISTIC,
        /** Nothing matched. Shown so "the mod ignored this" is distinguishable from "no data". */
        NONE;

        private static final Source[] VALUES = values();

        /** Decode from the wire, tolerating a value this build does not know. */
        public static Source byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }
    }

    public static NutritionOrigin of(Source source) {
        return new NutritionOrigin(source, List.of());
    }

    public static NutritionOrigin ofRecipe(List<Item> contributors) {
        return new NutritionOrigin(Source.RECIPE,
                List.copyOf(contributors.subList(0, Math.min(contributors.size(), MAX_FROM))));
    }

    /** Translation key for the stage's own name. */
    public String translationKey() {
        return "tooltip.dynamicnutrition.source." + source.name().toLowerCase();
    }
}
