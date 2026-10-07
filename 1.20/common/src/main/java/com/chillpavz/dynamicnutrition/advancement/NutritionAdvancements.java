package com.chillpavz.dynamicnutrition.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;
import com.chillpavz.dynamicnutrition.player.NutrientStatus;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Granting the mod's advancements.
 *
 * <h2>Two vanilla rules shape the whole tree, and both fail silently</h2>
 * <ul>
 *   <li><b>A tab does not exist until its root is EARNED</b>, so the root is granted immediately by
 *       a {@code minecraft:tick} trigger with the toast and the chat announcement switched off.
 *       Gating the root on eating something would hide the whole progression behind the thing it
 *       exists to explain.</li>
 *   <li><b>Nothing more than two levels deep is ever visible</b> ({@code VISIBILITY_DEPTH = 2} in
 *       {@code AdvancementVisibilityEvaluator}), so the tree is root, branch, leaf and stops. That
 *       is a real constraint on the design rather than a formatting preference.</li>
 * </ul>
 *
 * <p>Every non-root advancement uses the {@code minecraft:impossible} trigger and is awarded from
 * here, because none of the conditions this mod cares about is expressible as a vanilla trigger.
 */
public final class NutritionAdvancements {

    /** How long a player has to hold ON_TARGET for the long one. One Minecraft day. */
    public static final int SUSTAINED_TICKS = 24000;

    private static final String CRITERION = "granted";

    private static final ResourceLocation BALANCED = id("balanced");
    private static final ResourceLocation SUSTAINED = id("sustained");
    private static final ResourceLocation COMPLETE_MEAL = id("complete_meal");
    private static final ResourceLocation DEFICIENT = id("deficient");
    private static final ResourceLocation RECOVERED = id("recovered");

    /**
     * A cooked dish, which is what {@code complete_meal} actually asks for.
     *
     * <p>All five nutrients on their own is too cheap a test: bread carries a token amount of every
     * one of them, so a player earned this by eating a loaf, which does not feel like an
     * achievement. Requiring a soup or stew as well makes it a dish somebody cooked, and the
     * convention tag means a modded stew qualifies with no work from us.
     */
    private static final TagKey<Item> SOUPS =
            TagKey.create(Registries.ITEM, new ResourceLocation("c", "foods/soup"));

    private NutritionAdvancements() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Constants.MOD_ID, path);
    }

    /**
     * Award one advancement, doing nothing at all if it is not present.
     *
     * <p>A datapack can remove any of these, and a player may have already earned it. Neither is an
     * error, and neither may throw on a player tick.
     */
    private static void award(ServerPlayer player, ResourceLocation advancement) {
        try {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            Advancement found = server.getAdvancements().getAdvancement(advancement);
            if (found != null) {
                player.getAdvancements().award(found, CRITERION);
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Could not award {}: {}", advancement, t.toString());
        }
    }

    /** Called after a meal. */
    public static void onFoodEaten(ServerPlayer player, Item item, NutritionValues values) {
        if (values.nutrients().size() >= Nutrients.count()
                && item.builtInRegistryHolder().is(SOUPS)) {
            award(player, COMPLETE_MEAL);
        }
    }

    /** Called on the same cadence as the status check, with the status already computed. */
    public static void onStatus(ServerPlayer player, PlayerNutrition nutrition,
                                NutrientStatus now) {
        if (now == NutrientStatus.MALNOURISHED) {
            award(player, DEFICIENT);
            nutrition.setWasMalnourished(true);
        }
        if (now == NutrientStatus.ON_TARGET) {
            // ON_TARGET is the WORST of the five, so reaching it means every one of them is up.
            award(player, BALANCED);
            if (nutrition.wasMalnourished()) {
                award(player, RECOVERED);
                nutrition.setWasMalnourished(false);
            }
            if (nutrition.sustainedTicks() >= SUSTAINED_TICKS) {
                award(player, SUSTAINED);
            }
        }
    }
}
