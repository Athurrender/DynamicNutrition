package com.chillpavz.dynamicnutrition.nutrition;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Inverse recipe index: output {@link Item} to the recipes that produce it.
 *
 * <p><b>Why this class exists rather than a scan.</b> The obvious implementation of "what is this
 * food made of" walks every recipe in the game looking for one whose output matches. In a large
 * modpack that is tens of thousands of recipes per uncached lookup, and the lookups are triggered by
 * JEI, REI or EMI indexing every item's tooltip at once on first login. Mods of this kind have
 * shipped that, and the symptom is a multi-second to multi-minute hang on first join, reported as
 * the mod being broken. The index is built once and reused.
 *
 * <p><b>Staleness is detected by IDENTITY, not by a callback.</b> A {@code /reload} or a world
 * change gives the level a new {@link RecipeManager} instance, so comparing the instance the index
 * was built from against the current one is a complete and callback-free staleness check.
 *
 * <p>The whole class is server-side by construction: {@code Level.recipeAccess()} returns the narrow
 * {@code RecipeAccess} interface, which cannot enumerate anything. Only {@code ServerLevel} narrows
 * the return type to {@link RecipeManager}, which is where {@code getRecipes()} lives.
 */
public final class RecipeIndex {

    private Map<Item, List<Recipe<?>>> byOutput = null;
    private Object builtFrom = null;

    /** Recipes producing this item, empty if none. Builds or rebuilds the index as needed. */
    public List<Recipe<?>> recipesFor(ServerLevel level, Item item) {
        RecipeManager manager = level.getRecipeManager();
        if (byOutput == null || builtFrom != manager) {
            build(manager, level.registryAccess());
        }
        List<Recipe<?>> found = byOutput.get(item);
        return found != null ? found : Collections.emptyList();
    }

    private void build(RecipeManager manager, RegistryAccess registries) {
        long start = System.currentTimeMillis();
        Map<Item, List<Recipe<?>>> index = new HashMap<>();
        Set<Item> outputs = new HashSet<>();
        int skipped = 0;

        for (Recipe<?> holder : manager.getRecipes()) {
            outputs.clear();
            try {
                // One result per recipe on this band. The 26.x recipe display tree, with several
                // possible results, arrived at 1.21.2.
                ItemStack result = holder.getResultItem(registries);
                if (result != null && !result.isEmpty()) {
                    outputs.add(result.getItem());
                }
            } catch (Throwable t) {
                // Some modded recipes throw from getResultItem outside a crafting context. Skipping the
                // offender is right; letting it abort the build would lose the whole index and
                // silently disable derivation for every food in the game.
                skipped++;
                continue;
            }
            for (Item out : outputs) {
                index.computeIfAbsent(out, k -> new java.util.ArrayList<>()).add(holder);
            }
        }

        byOutput = index;
        builtFrom = manager;
        Constants.LOG.info("Built recipe output index: {} items, {} ms{}",
                index.size(), System.currentTimeMillis() - start,
                skipped > 0 ? ", skipped " + skipped + " recipes that threw from getResultItem()" : "");
    }

    /** Drop the index. Called on server stop so nothing holds a dead RecipeManager. */
    public void clear() {
        byOutput = null;
        builtFrom = null;
    }

    /** Whether an index is currently built, for diagnostics and the audit. */
    public boolean isBuilt() {
        return byOutput != null;
    }

    public int size() {
        return byOutput == null ? 0 : byOutput.size();
    }
}
