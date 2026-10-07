package com.chillpavz.dynamicnutrition.effect;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

/** Fabric registration for the nutrition effects, and the rule that milk does not clear them. */
public final class FabricNutritionEffects {

    private FabricNutritionEffects() {
    }

    public static void register() {
        // registerForHolder rather than register: a MobEffectInstance needs a Holder, and taking it
        // straight from the registration is one call instead of a registration plus a lookup.
        Map<ResourceKey<MobEffect>, Holder<MobEffect>> holders = new HashMap<>();
        for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
            holders.put(registration.key(), Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                    registration.key(), registration.effect()));
        }
        NutrientEffects.bind(holders);

        // Milk, a totem and /effect clear must not take these away. Fabric API has no effect event
        // on this band, so LivingEntityEffectRemovalMixin intercepts the two removal paths itself
        // and calls the same NutrientEffects.mayRemove / refused pair the 26.x band's event
        // handler calls. Nothing else in the mod differs between the two bands because of it.
    }
}
