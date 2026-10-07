package com.chillpavz.dynamicnutrition.mixin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
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
 * <p>The semantics are deliberately identical to that event's, including its awkward part. Vanilla
 * {@code removeAllEffects} copies the map, clears it, and then reports the COPY as removed, which
 * is what sends the client its removal packets. Refusing inside the clear therefore leaves the
 * server holding an effect the client has been told is gone, so every refusal is reported through
 * {@link NutrientEffects#refused} and the effect is sent again on the player's next tick. That
 * resend already exists in the common code and is what the 26.x band does too, so nothing outside
 * this class differs between the bands.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectRemovalMixin {

    /**
     * Keeps this mod's effects across a bulk clear. Returning without clearing them means the
     * caller's copy still lists them, which is exactly how Fabric API's event behaves.
     */
    @Redirect(method = "removeAllEffects", require = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;clear()V"))
    private void dynamicnutrition$keepOursOnClear(Map<Holder<MobEffect>, MobEffectInstance> map) {
        Map<Holder<MobEffect>, MobEffectInstance> kept = new HashMap<>();
        for (Iterator<Map.Entry<Holder<MobEffect>, MobEffectInstance>> it = map.entrySet().iterator();
                it.hasNext();) {
            Map.Entry<Holder<MobEffect>, MobEffectInstance> entry = it.next();
            if (!NutrientEffects.mayRemove(entry.getKey())) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        map.clear();
        map.putAll(kept);
        if (!kept.isEmpty()) {
            NutrientEffects.refused((LivingEntity) (Object) this);
        }
    }

    /**
     * The single-effect path: milk on some loaders, a totem, {@code /effect clear <effect>} and any
     * mod calling {@code removeEffect}. This mod's own removals go through
     * {@link NutrientEffects#mayRemove}, which knows to allow them.
     */
    @Inject(method = "removeEffect", require = 1, at = @At("HEAD"), cancellable = true)
    private void dynamicnutrition$refuseSingleRemoval(Holder<MobEffect> effect,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!NutrientEffects.mayRemove(effect)) {
            NutrientEffects.refused((LivingEntity) (Object) this);
            cir.setReturnValue(false);
        }
    }
}
