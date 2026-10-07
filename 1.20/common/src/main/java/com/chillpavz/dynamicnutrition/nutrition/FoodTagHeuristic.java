package com.chillpavz.dynamicnutrition.nutrition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Stage four of the resolution pipeline: guess a nutrient set from the convention food tags.
 *
 * <p><b>This stage is the reason the mod works with food mods nobody has heard of.</b> The other
 * three stages need somebody to have done something: an explicit entry, a nutrient tag, or a recipe
 * whose ingredients resolve. Every nutrition mod surveyed leaves an unknown food with NO nutrients
 * at all. The usual answer is a hand-written compatibility file per mod, which is exactly why that
 * approach ends up supporting a list of food mods rather than all of them.
 *
 * <p>The {@code c:foods/*} taxonomy is shipped IDENTICALLY by Fabric API and by NeoForge, verified
 * against both jars at 26.2, so this needs no per-loader code and no dependency beyond the loader.
 * Most food mods tag their items because the rest of the ecosystem already reads these tags.
 *
 * <p>Order matters: the first matching rule wins, so the specific tags are listed before the broad
 * ones. A food carrying several tags gets the most specific reading rather than a union, because a
 * union would drift towards "everything feeds everything", which is the failure mode that makes a
 * balanced-diet mechanic pointless.
 */
public final class FoodTagHeuristic {

    private record Rule(TagKey<Item> tag, List<Nutrient> nutrients) {
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation("c", path));
    }

    private static final List<Rule> RULES = List.of(
            // Specific first.
            new Rule(c("foods/cooked_meat"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("foods/raw_meat"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("foods/cooked_fish"), List.of(Nutrients.PROTEIN, Nutrients.MINERALS)),
            new Rule(c("foods/raw_fish"), List.of(Nutrients.PROTEIN, Nutrients.MINERALS)),
            new Rule(c("foods/berry"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("foods/fruit"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("foods/vegetable"), List.of(Nutrients.VITAMINS, Nutrients.MINERALS)),
            new Rule(c("foods/candy"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("foods/cookie"), List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT)),
            new Rule(c("foods/pie"), List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT)),
            new Rule(c("foods/bread"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("foods/dough"), List.of(Nutrients.CARBOHYDRATES)),
            // A golden food is supernatural rather than nutritious; give it the micros, which is
            // what the vanilla table does for the golden apple and carrot.
            new Rule(c("foods/golden"), List.of(Nutrients.VITAMINS, Nutrients.MINERALS)),
            // Soup is usually resolved by the recipe walk before it reaches here. When it is not,
            // it is at least a mixed dish rather than a single macronutrient.
            new Rule(c("foods/soup"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS,
                    Nutrients.MINERALS)),
            // Non-food ingredients that the recipe walk needs to be able to resolve.
            new Rule(c("eggs"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("drinks/milk"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("drinks/honey"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/wheat"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/potato"), List.of(Nutrients.CARBOHYDRATES, Nutrients.MINERALS)),
            new Rule(c("crops/beetroot"), List.of(Nutrients.VITAMINS, Nutrients.MINERALS)),
            new Rule(c("crops/carrot"), List.of(Nutrients.VITAMINS)),
            new Rule(c("crops/melon"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("crops/pumpkin"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("crops/sugar_cane"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/cocoa_bean"), List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT)),
            new Rule(c("seeds"), List.of(Nutrients.FAT, Nutrients.MINERALS)),
            // Broadest possible net, last. Anything a mod bothered to call food is at least a
            // carbohydrate source, which is a defensible default and beats resolving to nothing.
            new Rule(c("foods"), List.of(Nutrients.CARBOHYDRATES)));

    private FoodTagHeuristic() {
    }

    /**
     * The nutrient set implied by this item's tags, or empty if none of the rules match.
     *
     * <p>Uses the registry holder rather than a stack, because these are item tags and a stack's
     * components are irrelevant to them.
     */
    public static Set<Nutrient> nutrientsFor(Item item) {
        var holder = item.builtInRegistryHolder();
        for (Rule rule : RULES) {
            if (holder.is(rule.tag())) {
                return new LinkedHashSet<>(rule.nutrients());
            }
        }
        return Set.of();
    }

    /** Number of rules, so an audit can assert the table has not been silently emptied. */
    public static int ruleCount() {
        return RULES.size();
    }
}
