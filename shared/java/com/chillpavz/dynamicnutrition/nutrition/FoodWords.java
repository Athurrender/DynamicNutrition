package com.chillpavz.dynamicnutrition.nutrition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reading a food from its NAMES, for the foods nothing else can place. Pure string work, so it is the
 * same in every band.
 *
 * <p>Two readings, both last resorts:
 * <ul>
 *   <li><b>A tag's own name.</b> Mods add their own subtags under the convention roots:
 *       {@code c:foods/raw_dragon_meat}, {@code c:foods/shulker_meat}. No rule can list them, but
 *       the last word says plainly what the food is. {@link #forTagPath} reads it.</li>
 *   <li><b>An item's own name.</b> A food that is another food in a different container carries
 *       that food's name with a word added: Miner's Delight makes {@code mushroom_stew_cup} out of
 *       {@code mushroom_stew} through its own data map, with no recipe and no tag to follow.
 *       {@link #stems} lists the names it could be a variant of.</li>
 * </ul>
 *
 * <p>Words are matched whole, never as substrings, so {@code nut} cannot match {@code nutrition}
 * and {@code ham} cannot match {@code hamburger}. The first word that means something wins, read
 * from the END of the name, because English puts the noun last: {@code cheese_bread} is bread.
 */
public final class FoodWords {

    private record Word(Set<String> words, List<Nutrient> nutrients) {
    }

    private static Word word(List<Nutrient> nutrients, String... words) {
        return new Word(Set.of(words), nutrients);
    }

    private static final List<Word> WORDS = List.of(
            word(List.of(Nutrients.PROTEIN, Nutrients.MINERALS),
                    "fish", "cod", "salmon", "tuna", "squid", "tentacle", "tentacles", "shrimp",
                    "crab", "seafood", "fillet", "fillets"),
            word(List.of(Nutrients.PROTEIN, Nutrients.FAT),
                    "meat", "meats", "beef", "pork", "porkchop", "mutton", "chicken", "steak",
                    "bacon", "ham", "sausage", "sausages", "jerky", "egg", "eggs", "cheese", "milk"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS),
                    "juice", "fruit", "fruits", "berry", "berries", "grape", "grapes", "apple",
                    "apples", "cherry", "cherries"),
            word(List.of(Nutrients.VITAMINS, Nutrients.MINERALS),
                    "vegetable", "vegetables", "salad", "greens"),
            word(List.of(Nutrients.FAT, Nutrients.MINERALS),
                    "nut", "nuts", "seed", "seeds"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT),
                    "cookie", "cookies", "cake", "cakes", "pie", "pies", "chocolate"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS, Nutrients.MINERALS),
                    "soup", "stew"),
            word(List.of(Nutrients.CARBOHYDRATES),
                    "bread", "dough", "pasta", "noodle", "noodles", "rice", "grain", "grains",
                    "candy", "sugar"));

    private FoodWords() {
    }

    /**
     * The nutrient set a convention subtag's name implies, or empty.
     *
     * @param path the part after the root, {@code raw_dragon_meat} for {@code c:foods/raw_dragon_meat}
     */
    public static Set<Nutrient> forTagPath(String path) {
        String[] parts = path.split("[_/]");
        for (int i = parts.length - 1; i >= 0; i--) {
            for (Word word : WORDS) {
                if (word.words().contains(parts[i])) {
                    return new LinkedHashSet<>(word.nutrients());
                }
            }
        }
        return Set.of();
    }

    /**
     * The names this item's name extends, longest first: {@code mushroom_stew_cup} gives
     * {@code mushroom_stew}, then {@code mushroom}.
     *
     * <p>Only trailing words are dropped. A leading word changes what the food IS
     * ({@code cooked_beef} is not {@code beef} for this purpose, and the recipe walk already finds it);
     * a trailing one is usually the container or the portion.
     */
    public static List<String> stems(String path) {
        List<String> stems = new ArrayList<>();
        int cut = path.lastIndexOf('_');
        while (cut > 0) {
            stems.add(path.substring(0, cut));
            cut = path.lastIndexOf('_', cut - 1);
        }
        return stems;
    }

    /** Number of word groups, so an audit can assert the table has not been silently emptied. */
    public static int groupCount() {
        return WORDS.size();
    }
}
