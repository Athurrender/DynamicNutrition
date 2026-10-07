package com.chillpavz.dynamicnutrition.nutrition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
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
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static final List<Rule> RULES = List.of(
            // Specific first.
            new Rule(c("foods/cooked_meat"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("foods/raw_meat"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
            new Rule(c("foods/cooked_fish"), List.of(Nutrients.PROTEIN, Nutrients.MINERALS)),
            new Rule(c("foods/raw_fish"), List.of(Nutrients.PROTEIN, Nutrients.MINERALS)),
            // A crop's own identity before the generic labels: corn is tagged c:foods/vegetable as
            // well as c:crops/grain, and rice c:seeds as well as c:crops/rice. Read as a vegetable or
            // a seed (fat and minerals), corn and rice dishes lost their carbohydrate.
            new Rule(c("crops/grain"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/rice"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/corn"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("seeds/corn"), List.of(Nutrients.CARBOHYDRATES)),
            // Soybeans are tagged c:seeds too, and soy is a protein food.
            new Rule(c("crops/soybeans"), List.of(Nutrients.PROTEIN, Nutrients.FAT)),
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
            // Juice is fruit with the fibre taken out: sugar and vitamin C. Juices are often made
            // from a fluid (grapes stomped into a basin, then bottled), which leaves the recipe walk
            // nothing to follow, so the tag is the only thing that can place them.
            new Rule(c("drinks/juice"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("drinks/honey"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/wheat"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/potato"), List.of(Nutrients.CARBOHYDRATES, Nutrients.MINERALS)),
            new Rule(c("crops/beetroot"), List.of(Nutrients.VITAMINS, Nutrients.MINERALS)),
            new Rule(c("crops/carrot"), List.of(Nutrients.VITAMINS)),
            new Rule(c("crops/melon"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("crops/pumpkin"), List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS)),
            new Rule(c("crops/sugar_cane"), List.of(Nutrients.CARBOHYDRATES)),
            new Rule(c("crops/cocoa_bean"), List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT)),
            new Rule(c("seeds"), List.of(Nutrients.FAT, Nutrients.MINERALS)));

    /**
     * Broadest possible net, LAST, after the subtag names and the item's own name: anything a mod
     * bothered to call food is at least a carbohydrate source, which beats resolving to nothing. As a
     * rule in the list it came first for every food tagged only {@code c:foods}, and ham, roast
     * chicken, crab, shrimp, cheese and salads were all pure carbohydrate.
     */
    private static final TagKey<Item> ANY_FOOD = c("foods");

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
        Set<Nutrient> named = fromSubtagNames(holder.tags().map(tag -> tag.location().getNamespace()
                + ":" + tag.location().getPath()).toList());
        if (named.isEmpty() && holder.is(ANY_FOOD)) {
            named = FoodWords.forTagPath(BuiltInRegistries.ITEM.getKey(item).getPath());
            if (named.isEmpty()) {
                named = new LinkedHashSet<>(List.of(Nutrients.CARBOHYDRATES));
            }
        }
        return named;
    }

    /**
     * No rule matched, so read the names of the item's own convention SUBTAGS. Mods add subtags no
     * rule can list ({@code c:foods/raw_dragon_meat}, {@code c:foods/shulker_meat}) and often
     * nothing else, not even {@code c:foods}; the last word of the name still says what the food is.
     *
     * <p>Sorted first, so an item with two such subtags gets the same answer on every start: a
     * holder's tags come in no particular order.
     */
    static Set<Nutrient> fromSubtagNames(List<String> tagIds) {
        List<String> paths = new java.util.ArrayList<>();
        for (String id : tagIds) {
            if (id.startsWith("c:foods/") || id.startsWith("c:drinks/")) {
                paths.add(id.substring(id.indexOf('/') + 1));
            }
        }
        paths.sort(null);
        for (String path : paths) {
            Set<Nutrient> found = FoodWords.forTagPath(path);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return Set.of();
    }

    /** Number of rules, so an audit can assert the table has not been silently emptied. */
    public static int ruleCount() {
        return RULES.size();
    }
}
