package com.chillpavz.dynamicnutrition.event;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.DynamicNutrition;
import com.chillpavz.dynamicnutrition.advancement.NutritionAdvancements;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.network.NutritionSync;
import com.chillpavz.dynamicnutrition.nutrition.MealHistory;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.player.NutrientStatus;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * The loader-independent half of every hook. Each loader module reaches these from its own event or
 * mixin, so the actual behaviour exists once.
 */
public final class NutritionEvents {

    /** How often the decay check runs. Every tick is wasteful; a second is well inside a bar's resolution. */
    private static final int DECAY_INTERVAL_TICKS = 20;

    private NutritionEvents() {
    }

    /**
     * A player finished eating something.
     *
     * <p>Creative and spectator are skipped: a creative player is not managing a food supply, and
     * charging them nutrition would put a debuff on somebody who cannot act on it.
     */
    public static void onFoodEaten(ServerPlayer player, ItemStack stack) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        NutritionValues values = DynamicNutrition.table().resolve(player.serverLevel(), stack.getItem());
        if (values.isEmpty()) {
            return;
        }
        PlayerNutrition nutrition = Services.STORAGE.get(player);

        // Variety FIRST, then the meal is recorded. Counting the meal before scaling it would make
        // every food penalise itself on the way in, which is one repeat out of step with what the
        // tooltip showed the player a moment earlier.
        float multiplier = MealHistory.multiplier(nutrition.meals(), stack.getItem());
        NutritionValues effective = values.scaled(multiplier);
        nutrition.consume(effective);
        nutrition.rememberMeal(MealHistory.key(stack.getItem()));
        // Always dirty: the meal history changed even when every value was already at its ceiling,
        // and the client needs it to show the right number on the next tooltip.
        Services.STORAGE.markDirty(player);

        // The advancement asks about the food itself, not about what this player got out of it, so
        // it is deliberately given the UNSCALED values. Earning it should not depend on how
        // repetitive your diet happened to be.
        NutritionAdvancements.onFoodEaten(player, stack.getItem(), values);
        Constants.LOG.debug("{} ate {} (x{}) -> {}", player.getName().getString(), stack.getItem(),
                multiplier, nutrition);
    }

    /**
     * Server tick for one player. Charges any drop in food-plus-saturation to the nutrient levels.
     *
     * <p>Decay is driven off the food bar rather than off a timer so that a player who is not doing
     * anything is not being drained. It also means the mod inherits vanilla's own sense of how hard
     * the player is working without needing an access widener for exhaustion.
     */
    public static void onPlayerTick(ServerPlayer player) {
        // Every tick, not every second: after milk the client must get the effects back at once,
        // not a second later. A set lookup that is empty almost always.
        NutrientEffects.resendIfRefused(player);
        if (player.tickCount % DECAY_INTERVAL_TICKS != 0) {
            return;
        }
        // Before the gamemode check, not after. A creative player still wants tooltips, and this is
        // also the path that catches a datapack reload, which neither loader offers a usable hook
        // for. It is an integer compare in the common case.
        NutritionSync.syncIfStale(player);
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        // Also before the gamemode check: a creative player can carry leftover effects too.
        if (NutrientEffects.tidy(player, nutrition)) {
            Services.STORAGE.markDirty(player);
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float foodPoints = player.getFoodData().getFoodLevel()
                + player.getFoodData().getSaturationLevel();
        float dropped = nutrition.takeFoodPointDrop(foodPoints);
        if (dropped > 0 && nutrition.decay(dropped)) {
            Services.STORAGE.markDirty(player);
        }
        updateStatus(player, nutrition);
    }

    /**
     * Keep the player's status effect and their last-announced status in step with their values.
     *
     * <p>The effect call is idempotent by construction, so this being on a tick does NOT mean an
     * attribute modifier is reapplied on a tick, which is a shipped bug in this space: a max health
     * modifier reapplied every tick resets the player's health and hurts them.
     *
     * <p>The announcement is on the SERVER, and deliberately. In single player the integrated server
     * and the client share this object and the server updates it first, so a client-side "has this
     * changed" test never fires. Detecting the transition here behaves the same way in single player
     * and on a dedicated server.
     */
    private static void updateStatus(ServerPlayer player, PlayerNutrition nutrition) {
        NutrientStatus now = nutrition.overallStatus();
        // Deliberately NOT marked dirty. The streak is server-side bookkeeping for one advancement
        // and the client never reads it, so syncing it would put a packet on the wire every second
        // per player for a number nobody looks at. It still persists, because the attachment is
        // serialized from its current value at save time rather than from the dirty flag.
        nutrition.trackSustained(now, DECAY_INTERVAL_TICKS);
        NutritionAdvancements.onStatus(player, nutrition, now);
        // The side each nutrient holds is stored and synced: the client reads it for the hover list
        // and the fog, so a change must reach it.
        boolean held = NutritionConfig.effectsEnabled
                ? NutrientEffects.apply(player, nutrition)
                : NutrientEffects.clear(player, nutrition);
        if (held) {
            Services.STORAGE.markDirty(player);
        }
        if (now == nutrition.lastStatus()) {
            return;
        }
        nutrition.setLastStatus(now);
        Services.STORAGE.markDirty(player);
        if (NutritionConfig.announceStatusChanges) {
            announce(player, nutrition, now);
        }
    }

    /**
     * Say what changed, in words, on the action bar.
     *
     * <p><b>Naming the nutrient is the entire point.</b> "Where is this debuff coming from" is the
     * most human complaint about mods of this kind, and it comes up again and again: somebody
     * spends days wondering whether they are cursed before working out that they gave all their
     * meat to their dogs.
     */
    private static void announce(ServerPlayer player, PlayerNutrition nutrition,
                                 NutrientStatus status) {
        String key = "status." + Constants.MOD_ID + "." + status.name().toLowerCase();
        MutableComponent message;
        List<Nutrient> low = nutrition.deficient();
        if (status == NutrientStatus.MALNOURISHED && !low.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < low.size(); i++) {
                if (i > 0) {
                    names.append(Component.literal(", "));
                }
                names.append(Component.translatable(low.get(i).translationKey())
                        .withStyle(Style.EMPTY.withColor(low.get(i).textColor())));
            }
            message = Component.translatable(key + ".detail", names);
        } else {
            message = Component.translatable(key);
        }
        // displayClientMessage is gone at 26.x; the action bar is sendSystemMessage(component, true).
        player.sendSystemMessage(message, true);
    }

    /**
     * A player joined. Sends the food table straight away rather than waiting for the tick check, so
     * the first tooltip they hover already has data.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        NutritionSync.sendNow(player);
    }

    /** Called after the player object is replaced on death or on a dimension change. */
    public static void onRespawn(ServerPlayer player, boolean wasDeath) {
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        if (wasDeath && nutrition.onDeath()) {
            Services.STORAGE.markDirty(player);
        }
    }
}
