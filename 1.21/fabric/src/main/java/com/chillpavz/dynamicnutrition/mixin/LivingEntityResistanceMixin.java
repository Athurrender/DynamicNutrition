package com.chillpavz.dynamicnutrition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;

/**
 * The fat buff's Resistance on Fabric, applied where vanilla applies its own: at the end of
 * {@code getDamageAfterMagicAbsorb}, which has the same signature on 26.1, 26.2 and 26.3.
 *
 * <p>Fabric only. NeoForge calls the same method but DISCARDS its return value (it records each
 * reduction on its damage container instead), so a mixin there changes nothing; NeoForge applies
 * the same reduction from {@code LivingDamageEvent.Pre}, which fires at the same point.
 *
 * <p><b>Every static member of a mixin must be private, and nothing outside may reference this
 * class.</b> The logic lives in {@link NutrientEffects#resist}, an ordinary class.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityResistanceMixin {

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true, require = 1)
    private void dynamicnutrition$resist(DamageSource source, float amount,
                                         CallbackInfoReturnable<Float> info) {
        info.setReturnValue(NutrientEffects.resist((LivingEntity) (Object) this, source,
                info.getReturnValue()));
    }
}
