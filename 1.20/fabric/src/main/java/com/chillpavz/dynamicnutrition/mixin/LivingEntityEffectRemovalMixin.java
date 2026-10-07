package com.chillpavz.dynamicnutrition.mixin;

import java.util.Iterator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;

/**
 * Milk, a totem and {@code /effect clear} must not take this mod's two display effects away: the
 * cure for a deficiency is food. On the 26.x band Fabric API supplies
 * {@code ServerMobEffectEvents.ALLOW_EARLY_REMOVE} for exactly this; no such event exists in
 * Fabric API on this band, so the two removal paths are intercepted directly.
 *
 * <p>On this band vanilla's {@code removeAllEffects} walks the map with an iterator, telling the
 * client about each effect ({@code onEffectRemoved}) and then removing it
 * ({@code Iterator.remove}). Both calls are intercepted, so a refused effect is neither announced
 * nor removed and the client never loses it. Every refusal is still reported through
 * {@link NutrientEffects#refused}, which resends the effect on the player's next tick: harmless here,
 * and it keeps the common code identical on every band.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectRemovalMixin {

    @Shadow
    protected abstract void onEffectRemoved(MobEffectInstance instance);

    /** Whether the entry {@code removeAllEffects} is looking at right now is one to keep. */
    @Unique
    private boolean dynamicnutrition$keeping;

    @Redirect(method = "removeAllEffects", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;onEffectRemoved(Lnet/minecraft/world/effect/MobEffectInstance;)V"))
    private void dynamicnutrition$announceUnlessOurs(LivingEntity self, MobEffectInstance instance) {
        this.dynamicnutrition$keeping = !NutrientEffects.mayRemove(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(instance.getEffect()));
        if (this.dynamicnutrition$keeping) {
            NutrientEffects.refused(self);
        } else {
            this.onEffectRemoved(instance);
        }
    }

    @Redirect(method = "removeAllEffects", require = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/Iterator;remove()V"))
    private void dynamicnutrition$removeUnlessOurs(Iterator<MobEffectInstance> iterator) {
        if (!this.dynamicnutrition$keeping) {
            iterator.remove();
        }
        this.dynamicnutrition$keeping = false;
    }

    /**
     * The single-effect path: a totem, {@code /effect clear <effect>} and any mod calling
     * {@code removeEffect}. This mod's own removals go through {@link NutrientEffects#mayRemove},
     * which knows to allow them.
     */
    @Inject(method = "removeEffect", require = 1, at = @At("HEAD"), cancellable = true)
    private void dynamicnutrition$refuseSingleRemoval(MobEffect effect,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!NutrientEffects.mayRemove(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect))) {
            NutrientEffects.refused((LivingEntity) (Object) this);
            cir.setReturnValue(false);
        }
    }
}
