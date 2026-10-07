package com.chillpavz.dynamicnutrition.nutrition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Resolves what every item is worth, in four stages, and caches the answer.
 *
 * <h2>The pipeline</h2>
 * <ol>
 *   <li><b>Explicit values</b> from {@code data/&lt;ns&gt;/nutrition/*.json}. Real per-nutrient
 *       numbers. This is what every vanilla food uses.</li>
 *   <li><b>Nutrient tags</b> on the item, {@code #dynamicnutrition:nutrient/&lt;name&gt;}. A pack
 *       author saying "this is a protein source" without having to pick numbers.</li>
 *   <li><b>The recipe graph.</b> A crafted food inherits the union of its ingredients' nutrients,
 *       recursively, so bread is a carbohydrate because wheat is.</li>
 *   <li><b>The food-tag heuristic.</b> See {@link FoodTagHeuristic}. The stage that means an
 *       unknown food is never worth nothing.</li>
 *   <li><b>The food's own name</b>, for a food that is another food in a different container:
 *       {@code mushroom_stew_cup} is worth what {@code mushroom_stew} is. See {@link FoodWords}.
 *       Edible items only, and only when everything above found nothing.</li>
 * </ol>
 *
 * <p>Stages 2 to 5 produce a nutrient SET; the MAGNITUDE then comes from the food's own nutrition
 * and saturation, split across that set. Only stage 1 carries real per-nutrient values, which is why
 * the shipped vanilla table is worth generating from real composition data.
 *
 * <h2>Server only</h2>
 * The recipe walk cannot run on a client: {@code Level.recipeAccess()} returns an interface that
 * cannot enumerate recipes, and on Fabric recipes are not synced to the client at all. The resolved
 * table is therefore computed server side and sent, never recomputed locally.
 */
public final class NutritionTable {

    /**
     * How much one food may contribute to a single nutrient. The modded food space is unbounded and
     * one overpowered stew should not trivialise the system. Matches the generator's own cap.
     */
    public static final int MAX_PER_NUTRIENT = 32;

    /**
     * Multiplier for a food produced by a cooking recipe from another food.
     *
     * <p>The reason to cook is applied HERE and not in the shipped table, because the table is
     * composition and cooking does not create nutrients: it drives off water and renders out fat, so
     * cooked meat carries about what the raw did. Deriving the bonus from the recipe graph is also
     * what makes it work for modded foods with no configuration.
     */
    public static final float COOKED_BONUS = 1.25F;

    /**
     * The least a food can be worth in total, before it is split across its nutrients.
     *
     * <p>There is a whole class of consumable with nutrition 0 and saturation 0: drinks, mostly.
     * Five of them turned up in a modded export (apple cider, condensed milk, milk tea and friends)
     * resolving to a perfectly good nutrient SET and then to nothing, because the magnitude comes
     * from the food's own hunger values and those are zero. A drink is not filling and it is still
     * food, so it gets a floor rather than nothing.
     */
    public static final float MIN_MAGNITUDE = 4.0F;

    /**
     * Fail-safe on the recursive walk, separate from the cycle guard and doing a different job: the
     * guard stops A-from-B-from-A, this bounds an unexpectedly deep linear chain.
     */
    private static final int MAX_DEPTH = 20;

    /** Above this, the prewarm is slow enough to be worth naming in the log. See {@link #prewarm}. */
    private static final long WATCHDOG_WARN_MS = 10_000L;

    private final Map<Item, NutritionValues> explicit = new HashMap<>();
    private final Map<Item, NutritionValues> resolved = new HashMap<>();
    private final Map<Item, NutritionOrigin> origins = new HashMap<>();
    private final Set<Item> resolving = new HashSet<>();
    /**
     * The nutrient SET each resolved item stands for, food or not. A non-food ingredient (wheat, a
     * raw skewer, a feast block) is worth nothing to eat, so its VALUES are empty, but the recipe walk
     * and the name stage need what it would feed. See {@link #setOf}.
     */
    private final Map<Item, Set<Nutrient>> sets = new HashMap<>();
    /** Item path to the items with that path, for the name stage. Built on first use. */
    private Map<String, List<Item>> byPath = null;
    private final RecipeIndex recipes = new RecipeIndex();
    private boolean prewarmed = false;

    /**
     * Bumped every time the table is thrown away, which is what a datapack reload does.
     *
     * <p>This is how a client is told its copy is stale WITHOUT a reload callback on either loader:
     * each player carries the generation they were last sent, and a mismatch is the signal to
     * resend. It costs one integer compare per player per second and it covers join, {@code
     * /reload} and a world change with the same three lines.
     */
    private int generation = 1;

    // ---------------------------------------------------------------- explicit table

    /** Replace the explicit table. Called by the data loader on every reload. */
    public synchronized void setExplicit(Map<Item, NutritionValues> values) {
        explicit.clear();
        explicit.putAll(values);
        invalidate();
        Constants.LOG.info("Loaded {} explicit nutrition entries", explicit.size());
    }

    /** Drop every derived answer. The explicit table survives; a reload replaces that separately. */
    public synchronized void invalidate() {
        resolved.clear();
        sets.clear();
        origins.clear();
        resolving.clear();
        recipes.clear();
        byPath = null;
        prewarmed = false;
        generation++;
    }

    /** See {@link #generation}. */
    public synchronized int generation() {
        return generation;
    }

    // ---------------------------------------------------------------- lookup

    /** The cached answer for this item, or null if it has not been resolved yet. */
    public synchronized NutritionValues cached(Item item) {
        return resolved.get(item);
    }

    /** Every answer resolved so far. Used to build the bulk sync payload. */
    public synchronized Map<Item, NutritionValues> snapshot() {
        return Map.copyOf(resolved);
    }

    /** Which pipeline stage answered for this item. Never null. */
    public synchronized NutritionOrigin origin(Item item) {
        return origins.getOrDefault(item, NutritionOrigin.NONE);
    }

    public synchronized Map<Item, NutritionOrigin> originSnapshot() {
        return Map.copyOf(origins);
    }

    /** Install a table received from the server. Client side only. */
    public synchronized void acceptSynced(Map<Item, NutritionValues> values,
                                          Map<Item, NutritionOrigin> sources) {
        resolved.clear();
        resolved.putAll(values);
        origins.clear();
        origins.putAll(sources);
        Constants.LOG.debug("Accepted {} synced nutrition entries", values.size());
    }

    /**
     * Resolve this item, server side, caching the result.
     *
     * @return never null; {@link NutritionValues#EMPTY} means the item genuinely feeds nothing
     */
    public synchronized NutritionValues resolve(ServerLevel level, Item item) {
        NutritionValues hit = resolved.get(item);
        if (hit != null) {
            return hit;
        }
        return resolve(level, item, 0);
    }

    private NutritionValues resolve(ServerLevel level, Item item, int depth) {
        NutritionValues hit = resolved.get(item);
        if (hit != null) {
            return hit;
        }

        // Stage 1: explicit values win outright and carry real per-nutrient numbers.
        NutritionValues exact = explicit.get(item);
        if (exact != null) {
            NutritionValues capped = exact.capped(MAX_PER_NUTRIENT);
            resolved.put(item, capped);
            sets.put(item, dominant(capped));
            origins.put(item, NutritionOrigin.of(NutritionOrigin.Source.EXPLICIT));
            return capped;
        }

        // Stages 2 to 4 produce a SET; the magnitude comes from the food itself.
        boolean limited = false;
        NutritionOrigin origin = NutritionOrigin.NONE;
        Set<Nutrient> set = tagNutrients(item);
        if (!set.isEmpty()) {
            origin = NutritionOrigin.of(NutritionOrigin.Source.TAG);
        }

        if (set.isEmpty()) {
            if (depth >= MAX_DEPTH || !resolving.add(item)) {
                // Depth cap or a cycle. Return WITHOUT caching: an empty answer cached here would
                // become permanent, and a shallower entry point can still resolve this properly.
                limited = true;
            } else {
                List<Item> contributors = new ArrayList<>();
                try {
                    set = recipeNutrients(level, item, depth, contributors);
                } finally {
                    resolving.remove(item);
                }
                if (!set.isEmpty()) {
                    origin = NutritionOrigin.ofRecipe(contributors);
                }
            }
        }

        if (set.isEmpty() && !limited) {
            set = FoodTagHeuristic.nutrientsFor(item);
            if (!set.isEmpty()) {
                origin = NutritionOrigin.of(NutritionOrigin.Source.HEURISTIC);
            }
        }

        // Stage 5: a food that is another food in a different container, or a serving of a feast
        // block. A candidate can be LONGER than the name (dragon_meat_stew_block), so this runs
        // under the same cycle guard as the recipe walk, and the depth cap still bounds it.
        NutritionValues named = null;
        if (set.isEmpty() && !limited && depth < MAX_DEPTH && isEdible(item) && resolving.add(item)) {
            try {
                for (Item stem : stemsOf(item)) {
                    NutritionValues stemValues = resolve(level, stem, depth + 1);
                    Set<Nutrient> stemSet = sets.getOrDefault(stem, stemValues.nutrients());
                    if (!stemSet.isEmpty()) {
                        set = new LinkedHashSet<>(stemSet);
                        origin = NutritionOrigin.ofName(stem);
                        // Real values only when this is the stem SERVED differently (a cup of
                        // stew), never for a dish named after an ingredient (a beef omelet is not
                        // beef) or a feast block: those give the set, and the split below applies.
                        boolean serving = !stemValues.isEmpty() && FoodWords.isServing(
                                BuiltInRegistries.ITEM.getKey(item).getPath(),
                                BuiltInRegistries.ITEM.getKey(stem).getPath());
                        named = serving ? sameFoodIn(item, stem, stemValues) : null;
                        if (named == null) {
                            // A dish named after an ingredient is that ingredient AND the dish:
                            // egg_cookie is egg and cookie, so its own words join the set.
                            set.addAll(FoodWords.forTagPath(BuiltInRegistries.ITEM.getKey(item).getPath()));
                        }
                        break;
                    }
                }
            } finally {
                resolving.remove(item);
            }
        }

        // Stage 6: the food's own name, last word first (spider_meat, tentacles, scrambled_egg). Only
        // for an edible item every other stage left empty; the same words as the tag-name reading.
        if (set.isEmpty() && !limited && isEdible(item)) {
            set = FoodWords.forTagPath(BuiltInRegistries.ITEM.getKey(item).getPath());
            if (!set.isEmpty()) {
                origin = NutritionOrigin.of(NutritionOrigin.Source.WORDS);
            }
        }

        NutritionValues values = named != null ? named
                : NutritionValues.spread(set, magnitude(item, set.size()));
        if (!values.isEmpty() && isCooked(level, item)) {
            values = values.scaled(COOKED_BONUS);
        }
        values = values.capped(MAX_PER_NUTRIENT);

        if (limited) {
            return values;
        }
        resolved.put(item, values);
        sets.put(item, set);
        origins.put(item, values.isEmpty() ? NutritionOrigin.NONE : origin);
        return values;
    }

    /**
     * What an explicit food passes on as an ingredient: the nutrients it is notably rich in, at least a
     * quarter of its largest value. A honey bottle is carbohydrate 27 and minerals 1; passing on both
     * made everything sweetened with honey a mineral source.
     */
    private static Set<Nutrient> dominant(NutritionValues values) {
        int max = 0;
        for (Nutrient nutrient : values.nutrients()) {
            max = Math.max(max, values.get(nutrient));
        }
        Set<Nutrient> rich = new LinkedHashSet<>();
        for (Nutrient nutrient : values.nutrients()) {
            if (values.get(nutrient) * 4 >= max) {
                rich.add(nutrient);
            }
        }
        return rich;
    }

    /**
     * What an item would feed, food or not: its resolved set when it has one, else its values'.
     *
     * <p>Reading an ingredient's VALUES instead lost every non-food ingredient: wheat, sugar, a milk
     * bucket, a raw skewer resolve a perfectly good set and then spread it over a hunger value of
     * zero, which is nothing. Every recipe that went through one of them inherited nothing from it.
     */
    private Set<Nutrient> setOf(ServerLevel level, Item item, int depth) {
        NutritionValues values = resolve(level, item, depth);
        Set<Nutrient> set = sets.get(item);
        return set != null ? set : values.nutrients();
    }

    // ---------------------------------------------------------------- stages

    /** Stage 2: an explicit nutrient tag on the item. */
    private static Set<Nutrient> tagNutrients(Item item) {
        Set<Nutrient> found = new LinkedHashSet<>();
        var holder = item.builtInRegistryHolder();
        for (Nutrient nutrient : Nutrients.all()) {
            if (holder.is(nutrient.tag())) {
                found.add(nutrient);
            }
        }
        return found;
    }

    /**
     * Stage 3: the union of the nutrients of everything this item is crafted from.
     *
     * @param contributors filled with the ingredients that actually supplied something, so the
     *                     advanced tooltip can say where the answer came from
     */
    private Set<Nutrient> recipeNutrients(ServerLevel level, Item item, int depth,
                                          List<Item> contributors) {
        // An item's own mod says what it is; another mod's alternative recipe does not. In one food
        // pack sugar is also crafted from a strider egg, a date syrup and a crate, and taking the union
        // of every recipe made sugar protein and fat, and everything sweetened with it too. So the
        // item's own namespace first, then vanilla's, then everyone else's, first tier that answers.
        String own = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        var all = recipes.recipesFor(level, item);
        for (int tier = 0; tier < 3; tier++) {
            Set<Nutrient> found = new LinkedHashSet<>();
            List<Item> from = new ArrayList<>();
            for (var holder : all) {
                if (tierOf(namespaceOf(holder), own) != tier) {
                    continue;
                }
                List<Ingredient> ingredients = ingredientsOf(holder);
                if (ingredients == null) {
                    continue;
                }
                for (Ingredient ingredient : ingredients) {
                    // An ingredient can accept many items; the first is a deterministic representative.
                    Item ingredientItem = firstItem(ingredient);
                    if (ingredientItem == null || ingredientItem == item) {
                        continue;
                    }
                    Set<Nutrient> ingredientNutrients = setOf(level, ingredientItem, depth + 1);
                    if (!ingredientNutrients.isEmpty() && !from.contains(ingredientItem)) {
                        from.add(ingredientItem);
                    }
                    found.addAll(ingredientNutrients);
                }
            }
            if (!found.isEmpty()) {
                contributors.addAll(from);
                return found;
            }
        }
        return new LinkedHashSet<>();
    }

    /** 0 for the item's own namespace, 1 for vanilla's, 2 for any other mod's. */
    private static int tierOf(String namespace, String own) {
        if (namespace.equals(own)) {
            return 0;
        }
        return namespace.equals("minecraft") ? 1 : 2;
    }

    /** The namespace of a recipe's id: the mod (or datapack) that defines it. */
    private static String namespaceOf(RecipeHolder<?> holder) {
        return holder.id().identifier().getNamespace();
    }

    /**
     * The items this item's name extends, longest name first, and for one name its own namespace
     * first, then vanilla, then every other mod in registry order. Never the item itself.
     */
    private List<Item> stemsOf(Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (byPath == null) {
            Map<String, List<Item>> index = new HashMap<>();
            for (Item each : BuiltInRegistries.ITEM) {
                index.computeIfAbsent(BuiltInRegistries.ITEM.getKey(each).getPath(),
                        k -> new ArrayList<>()).add(each);
            }
            byPath = index;
        }
        List<Item> found = new ArrayList<>();
        for (String stem : FoodWords.stems(id.getPath())) {
            List<Item> named = byPath.getOrDefault(stem, List.of());
            for (String namespace : List.of(id.getNamespace(), "minecraft")) {
                for (Item candidate : named) {
                    if (candidate != item && !found.contains(candidate)
                            && BuiltInRegistries.ITEM.getKey(candidate).getNamespace().equals(namespace)) {
                        found.add(candidate);
                    }
                }
            }
            // Another mod's item only by a name of two words or more: mushroom_stew_cup may be
            // farmersdelight's or vanilla's stew, but a coffee pie is not Rustic Delight's coffee.
            if (stem.indexOf('_') > 0) {
                for (Item candidate : named) {
                    if (candidate != item && !found.contains(candidate)) {
                        found.add(candidate);
                    }
                }
            }
        }
        return found;
    }

    /**
     * A food in another container is the SAME food, so it takes that food's real values rather than
     * an even split: a cup of mushroom stew is mostly vitamins and minerals, like the bowl. Scaled by
     * the two foods' own hunger totals, so a smaller serving is worth proportionally less. Null when
     * either has no hunger value to compare, and the even split applies instead.
     */
    private static NutritionValues sameFoodIn(Item item, Item stem, NutritionValues stemValues) {
        int all = Nutrients.all().size();
        float own = magnitude(item, all);
        float from = magnitude(stem, all);
        if (own <= 0 || from <= 0) {
            return null;
        }
        NutritionValues scaled = stemValues.scaled(own / from);
        return scaled.isEmpty() ? null : scaled;
    }

    /**
     * The magnitude a derived food is worth in total, before it is split across its nutrients.
     *
     * <p>Nutrition plus saturation is the game's own measure of how substantial a food is, and using
     * it means a derived value is always in proportion to what vanilla already says. The cap is
     * per-nutrient, so a food covering more nutrients is allowed a larger total.
     */
    private static float magnitude(Item item, int nutrientCount) {
        if (nutrientCount <= 0) {
            return 0;
        }
        FoodProperties food = item.getDefaultInstance().get(DataComponents.FOOD);
        if (food == null) {
            // Not food. It still resolves a nutrient SET, which is what the recipe walk needs from
            // an ingredient like wheat, but it is worth nothing to eat.
            return 0;
        }
        float total = Math.max(food.nutrition() + food.saturation(), MIN_MAGNITUDE);
        return Math.min(total, (float) MAX_PER_NUTRIENT * nutrientCount);
    }

    /** True if a cooking recipe produces this item from something that is itself a food. */
    private boolean isCooked(ServerLevel level, Item item) {
        for (RecipeHolder<?> holder : recipes.recipesFor(level, item)) {
            RecipeType<?> type = holder.value().getType();
            if (type != RecipeType.SMELTING && type != RecipeType.SMOKING
                    && type != RecipeType.CAMPFIRE_COOKING) {
                continue;
            }
            PlacementInfo info;
            try {
                info = holder.value().placementInfo();
            } catch (Throwable t) {
                continue;
            }
            if (info == null || info == PlacementInfo.NOT_PLACEABLE) {
                continue;
            }
            for (Ingredient ingredient : info.ingredients()) {
                var first = ingredient.items().findFirst();
                if (first.isPresent()
                        && first.get().value().getDefaultInstance().has(DataComponents.FOOD)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A recipe's ingredients, or null for one that cannot tell us anything: not placeable (a special,
     * code driven recipe), impossible to place, or one that throws.
     */
    private static List<Ingredient> ingredientsOf(RecipeHolder<?> holder) {
        try {
            PlacementInfo info = holder.value().placementInfo();
            if (info == null || info == PlacementInfo.NOT_PLACEABLE || info.isImpossibleToPlace()) {
                return null;
            }
            return info.ingredients();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The item that stands for an ingredient: vanilla's when the ingredient accepts one, else the first.
     * A tag's first item is whatever mod sorted first: in one food pack {@code #c:eggs} began with a
     * vegan mod's silken tofu, and every dough made with "an egg" stopped being a protein source.
     */
    private static Item firstItem(Ingredient ingredient) {
        Item first = null;
        for (var it = ingredient.items().iterator(); it.hasNext();) {
            Item each = it.next().value();
            if (first == null) {
                first = each;
            }
            if (BuiltInRegistries.ITEM.getKey(each).getNamespace().equals("minecraft")) {
                return each;
            }
        }
        return first;
    }

    // ---------------------------------------------------------------- prewarm

    /**
     * Resolve every food up front, so the cost lands during world load where a loading screen is
     * already showing rather than at the first tooltip a player hovers.
     *
     * <p>This is half of the fix for the first-login hang; the other half is that the result is bulk
     * synced to the client instead of being requested per item.
     */
    public synchronized void prewarm(ServerLevel level) {
        if (prewarmed) {
            return;
        }
        long start = System.currentTimeMillis();
        int considered = 0;
        int empty = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (!isEdible(item)) {
                    continue;
                }
                considered++;
                if (resolve(level, item, 0).isEmpty()) {
                    empty++;
                }
            } catch (Throwable t) {
                // One item that throws from getDefaultInstance must not cost the whole prewarm.
                Constants.LOG.debug("Skipped {} during prewarm: {}", item, t.toString());
            }
        }
        prewarmed = true;
        long elapsed = System.currentTimeMillis() - start;
        Constants.LOG.info("Resolved nutrition for {} foods in {} ms ({} with no nutrients)",
                considered, elapsed, empty);
        // A mod of this kind has been killed by the server WATCHDOG doing this work: over 180
        // seconds walking recipes for ~2000 items inside a tick, and the watchdog shot the server
        // thread. Prewarming at server-started is the structural fix, because it runs before the
        // tick loop. This is the belt and braces: if the work is slow enough to look like a hang,
        // say so and name the mod, so a modpack author knows where the time went instead of
        // guessing. Anything not prewarmed still resolves lazily, so this is never fatal.
        if (elapsed > WATCHDOG_WARN_MS) {
            Constants.LOG.warn("Nutrition prewarm took {} ms. This runs once at server start and "
                    + "not inside a tick, so it cannot trip the server watchdog, but it does add "
                    + "to load time on a large modpack.", elapsed);
        }
        if (empty > 0) {
            Constants.LOG.warn("{} food items resolved to no nutrients. "
                    + "Run /dynamicnutrition unassigned to list them.", empty);
        }
    }

    /**
     * Whether a player can actually eat this.
     *
     * <p><b>Carrying {@code FOOD} is not the same as being edible</b>, and vanilla proves it: read
     * out of {@code Items}, every fish bucket is registered with
     * {@code .component(DataComponents.FOOD, Foods.COD)} and no {@code CONSUMABLE}, so a bucket of
     * cod has a cod's nutrition and cannot be eaten. Asking only about FOOD put all four fish
     * buckets on the "this food has no nutrients" report, where they looked like a gap in the
     * pipeline rather than items that are not food.
     */
    public static boolean isEdible(Item item) {
        var stack = item.getDefaultInstance();
        return stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE);
    }

    public boolean isPrewarmed() {
        return prewarmed;
    }

    public int cacheSize() {
        return resolved.size();
    }

    public int explicitSize() {
        return explicit.size();
    }
}
