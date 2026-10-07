package com.chillpavz.dynamicnutrition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.event.EatHookState;
import com.chillpavz.dynamicnutrition.event.NutritionEvents;

/**
 * The Fabric eat hook, and the mod's ONLY mixin.
 *
 * <p>Fabric API has no "finished eating" event at 26.2, so this is the hook. NeoForge has
 * {@code LivingEntityUseItemEvent.Finish} and needs no mixin at all.
 *
 * <p>Injected at HEAD rather than RETURN so the stack still has its food components: by the time
 * the method returns the stack has been consumed and may already be a bowl.
 *
 * <p><b>EVERY STATIC MEMBER OF A MIXIN MUST BE PRIVATE, AND NOTHING OUTSIDE MAY REFERENCE THIS
 * CLASS.</b> Both rules were broken here once and the launch failed twice over; see
 * {@link EatHookState} for what happened. State and reporting live in that ordinary class.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"), require = 0)
    private void dynamicnutrition$onFinishUsing(Level level, LivingEntity user,
                                                CallbackInfoReturnable<ItemStack> info) {
        EatHookState.markApplied();
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
            return;
        }
        try {
            NutritionEvents.onFoodEaten(player, (ItemStack) (Object) this);
        } catch (Throwable t) {
            // Degrade, never crash: a failure here must not stop the player finishing their meal.
            Constants.LOG.error("Failed to apply nutrition for an eaten item", t);
        }
    }
}
