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
import org.spongepowered.asm.mixin.injection.Redirect;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;

/**
 * Milk must not take this mod's two display effects away on Forge 1.21.11 either.
 *
 * <p>The single-effect path is covered by {@code MobEffectEvent.Remove} (see
 * {@code ForgeNutritionEffects}), but Forge 61's {@code removeAllEffects} posts no event: it copies
 * the map, clears it and calls {@code onEffectsRemoved}. Forge 52 still posted one per effect, which
 * is why only this band needs it. Milk reaches it through {@code ClearAllStatusEffectsConsumeEffect},
 * so milk cleared Well Nourished and Malnourished until the next nutrition update put them back.
 * Same redirect as the Fabric module's: the clear keeps ours.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectRemovalMixin {

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
}
