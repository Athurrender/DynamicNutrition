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
                    "crab", "seafood", "fillet", "fillets", "calamari", "clam", "clams", "oyster",
                    "oysters", "mussel", "mussels", "scallop", "scallops", "lobster", "prawn", "prawns",
                    "octopus", "fugu"),
            word(List.of(Nutrients.PROTEIN, Nutrients.FAT),
                    "meat", "meats", "beef", "pork", "porkchop", "mutton", "chicken", "steak",
                    "bacon", "ham", "sausage", "sausages", "jerky", "egg", "eggs", "cheese", "milk",
                    "shank", "chops", "ribs", "drumstick", "patty", "lamb", "rabbit", "leg", "legs",
                    "gristle", "tripe", "custard", "tofu", "soy", "soymilk", "soybean", "soybeans", "omelet",
                    "omelette"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS),
                    "juice", "fruit", "fruits", "berry", "berries", "grape", "grapes", "apple",
                    "apples", "cherry", "cherries", "pineapple", "melon", "hamimelon", "durian"),
            word(List.of(Nutrients.VITAMINS, Nutrients.MINERALS),
                    "vegetable", "vegetables", "salad", "greens", "mushroom", "mushrooms", "cabbage",
                    "carrot", "carrots", "beetroot", "tomato", "tomatoes", "onion", "onions", "lettuce",
                    "spinach", "kale", "cauliflower", "broccoli", "eggplant", "cucumber", "zucchini",
                    "pepper", "peppers"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.MINERALS),
                    "potato", "potatoes"),
            word(List.of(Nutrients.FAT, Nutrients.MINERALS),
                    "nut", "nuts", "seed", "seeds", "peanut", "peanuts", "acorn", "acorns"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.FAT),
                    "cookie", "cookies", "cake", "cakes", "pie", "pies", "chocolate", "brownie",
                    "brownies", "muffin", "muffins", "donut", "donuts", "waffle", "waffles", "tart", "tarts", "pancake",
                    "pancakes"),
            word(List.of(Nutrients.CARBOHYDRATES, Nutrients.VITAMINS, Nutrients.MINERALS),
                    "soup", "stew"),
            word(List.of(Nutrients.CARBOHYDRATES),
                    "bread", "dough", "pasta", "noodle", "noodles", "rice", "grain", "grains", "corn",
                    "toast", "popcorn", "cereal", "porridge", "oatmeal", "risotto", "nachos", "jello",
                    "popsicle",
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
     * Words that only name a container or a serving when they LEAD a name: {@code plate_of_stuffed_hoglin}
     * is a serving of {@code stuffed_hoglin}.
     */
    private static final List<String> SERVED_IN = List.of("plate_of_", "bowl_of_", "cup_of_", "mug_of_",
            "glass_of_", "bottle_of_", "jar_of_", "slice_of_", "piece_of_", "serving_of_");

    /**
     * The names this item could be a serving or a variant of, in order:
     * <ol>
     *   <li>{@code <name>_block}: the feast block a serving comes from ({@code dragon_meat_stew} from
     *       {@code dragon_meat_stew_block}), handed out by right-clicking with a bowl, no recipe.</li>
     *   <li>The name with trailing words dropped, longest first: {@code mushroom_stew_cup} gives
     *       {@code mushroom_stew}, then {@code mushroom}. A trailing word is usually the container or the
     *       portion.</li>
     *   <li>The name without a leading container ({@link #SERVED_IN}), then that with trailing words
     *       dropped.</li>
     * </ol>
     * Other leading words are never dropped: they change what the food IS ({@code cooked_beef} is not
     * {@code beef} for this purpose, and the recipe walk already finds it).
     */
    public static List<String> stems(String path) {
        List<String> stems = new ArrayList<>();
        stems.add(path + "_block");
        addTrailing(stems, path);
        for (String lead : SERVED_IN) {
            if (path.startsWith(lead) && path.length() > lead.length()) {
                String rest = path.substring(lead.length());
                stems.add(rest);
                addTrailing(stems, rest);
            }
        }
        return stems;
    }

    private static void addTrailing(List<String> stems, String path) {
        int cut = path.lastIndexOf('_');
        while (cut > 0) {
            stems.add(path.substring(0, cut));
            cut = path.lastIndexOf('_', cut - 1);
        }
    }

    /** Words that only name a container or a portion when they END a name. */
    private static final Set<String> SERVING_WORDS = Set.of("cup", "bowl", "bottle", "glass", "mug",
            "jar", "plate", "slice", "slices", "piece", "pieces", "serving", "side", "portion");

    /**
     * Whether {@code name} is {@code stem} served differently, so the stem's real values carry over:
     * {@code mushroom_stew_cup} is mushroom stew, but {@code beef_omelet} is not beef. Only container
     * and portion words may lie between them, at the end ({@link #SERVING_WORDS}) or the start
     * ({@code plate_of_}, {@code bowl_of_}...).
     */
    public static boolean isServing(String name, String stem) {
        if (name.startsWith(stem + "_")) {
            for (String word : name.substring(stem.length() + 1).split("_")) {
                if (!SERVING_WORDS.contains(word)) {
                    return false;
                }
            }
            return true;
        }
        for (String lead : SERVED_IN) {
            if (name.equals(lead + stem)) {
                return true;
            }
        }
        return false;
    }

    /** Number of word groups, so an audit can assert the table has not been silently emptied. */
    public static int groupCount() {
        return WORDS.size();
    }
}
