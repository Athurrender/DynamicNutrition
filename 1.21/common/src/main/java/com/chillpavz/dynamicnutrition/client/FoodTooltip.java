package com.chillpavz.dynamicnutrition.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.DynamicNutrition;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.nutrition.MealHistory;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.nutrition.NutritionOrigin;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * The nutrient line on a food's tooltip.
 *
 * <h2>Why this class is a cache and not a function</h2>
 * A tooltip is rebuilt <b>every frame</b> while it is hovered, and a recipe viewer builds every
 * item's tooltip while it indexes. Doing real work here is measurable in tens of frames per second
 * in a small instance, and takes a modpack below one.
 *
 * <p>So the rules for anything added to this class are absolute:
 * <ul>
 *   <li><b>Never resolve.</b> Resolution is server side and cannot happen here at all; this reads
 *       the synced table, which is a map lookup.</li>
 *   <li><b>Never build a {@link Component} per frame.</b> The finished lines are built once per
 *       item and handed out by reference thereafter.</li>
 *   <li><b>Never key the cache on the stack.</b> Stacks are created and thrown away constantly;
 *       the key is the {@link Item}.</li>
 * </ul>
 *
 * <p>The cache is dropped whenever a new table arrives, which is the only thing that can change an
 * answer. See {@link #invalidate()}.
 */
public final class FoodTooltip {

    private static final Map<Item, List<Component>> PLAIN = new HashMap<>();
    private static final Map<Item, List<Component>> ADVANCED = new HashMap<>();

    /**
     * The meal revision the cache was built at.
     *
     * <p>A food's displayed value depends on what the player has been eating, so the cache cannot be
     * permanent; and a tooltip is rebuilt every frame while hovered, so it must not be rebuilt per
     * frame either. Comparing one integer gives both: the cache lives until the player eats.
     */
    private static int builtRevision = -1;

    private FoodTooltip() {
    }

    /** Drop every cached line. Called when a new table arrives, and nowhere else. */
    public static void invalidate() {
        synchronized (PLAIN) {
            PLAIN.clear();
            ADVANCED.clear();
        }
    }

    /**
     * Append this item's nutrient lines, if it has any.
     *
     * <p>An item with no entry in the table gets nothing at all, not an empty header. Most of the
     * item registry is not food, and a tooltip line saying so on every stone block would be noise.
     */
    public static void appendTo(ItemStack stack, List<Component> lines, boolean advanced,
                                Player viewer) {
        if (stack.isEmpty() || !NutritionConfig.tooltipEnabled) {
            return;
        }
        PlayerNutrition nutrition = viewer == null ? null : Services.STORAGE.get(viewer);
        lines.addAll(linesFor(stack.getItem(), advanced, nutrition));
    }

    private static List<Component> linesFor(Item item, boolean advanced, PlayerNutrition nutrition) {
        int revision = nutrition == null ? 0 : nutrition.mealRevision();
        Map<Item, List<Component>> cache = advanced ? ADVANCED : PLAIN;
        synchronized (PLAIN) {
            if (revision != builtRevision) {
                PLAIN.clear();
                ADVANCED.clear();
                builtRevision = revision;
            }
            List<Component> hit = cache.get(item);
            if (hit != null) {
                return hit;
            }
        }
        List<Component> built = build(item, advanced, nutrition);
        synchronized (PLAIN) {
            cache.put(item, built);
        }
        return built;
    }

    private static List<Component> build(Item item, boolean advanced, PlayerNutrition nutrition) {
        NutritionValues base = DynamicNutrition.table().cached(item);
        if (base == null || base.isEmpty()) {
            return List.of();
        }
        // Show what this player would ACTUALLY get, which is the whole point of a variety mechanic
        // a player can act on. Showing the unscaled number and quietly awarding less is how a
        // mechanic gets reported as a bug.
        float multiplier = nutrition == null
                ? 1.0F : MealHistory.multiplier(nutrition.meals(), item);
        NutritionValues values = base.scaled(multiplier);
        if (values.isEmpty()) {
            values = base;
            multiplier = 1.0F;
        }

        // One line, not five. Five foods in an inventory would otherwise add twenty-five lines of
        // tooltip between the player and the thing they were looking at. Short labels come from the
        // lang file so a translator can shorten them further where a language needs it.
        MutableComponent summary = Component.empty();
        boolean first = true;
        for (Nutrient nutrient : Nutrients.all()) {
            int value = values.get(nutrient);
            if (value <= 0) {
                continue;
            }
            if (!first) {
                summary.append(Component.literal("  "));
            }
            first = false;
            summary.append(Component
                    .translatable("tooltip." + Constants.MOD_ID + ".short." + nutrient.name())
                    .append(Component.literal(" " + value))
                    .withColor(nutrient.textColor()));
        }

        List<Component> lines = new ArrayList<>(3);
        lines.add(summary);

        if (multiplier < 1.0F) {
            // Only when it applies. A line on every food saying nothing is wrong would be noise on
            // the tooltip of every food in the game.
            lines.add(Component.translatable("tooltip." + Constants.MOD_ID + ".variety",
                    Math.round(multiplier * 100)).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (advanced) {
            // Showing the trail rather than only exporting it answers the question
            // a pack author actually has, and gating it to F3+H keeps it away from everyone else.
            NutritionOrigin origin = DynamicNutrition.table().origin(item);
            MutableComponent line =
                    Component.translatable(origin.translationKey()).withStyle(ChatFormatting.DARK_GRAY);
            if (!origin.from().isEmpty()) {
                MutableComponent from = Component.empty();
                for (int i = 0; i < origin.from().size(); i++) {
                    if (i > 0) {
                        from.append(Component.literal(", "));
                    }
                    // getName takes a stack at 26.2; the description id is what we actually want.
                    from.append(Component.translatable(origin.from().get(i).getDescriptionId()));
                }
                line = Component.translatable("tooltip." + Constants.MOD_ID + ".source.from", from)
                        .withStyle(ChatFormatting.DARK_GRAY);
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }
}
