package com.chillpavz.dynamicnutrition.effect;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
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

        // Fires for milk, a totem, /effect clear AND a plain removeEffect, so the guard has to let
        // this mod's own removals through; NutrientEffects.mayRemove knows which is which.
        // A refusal here does NOT stop the client being told: for removeAllEffects, Fabric API puts
        // the effect back after vanilla has already copied it for the remove packets. So every
        // refusal is reported, and the player is sent the effects again on their next tick.
        ServerMobEffectEvents.ALLOW_EARLY_REMOVE.register((instance, entity, context) -> {
            boolean allowed = NutrientEffects.mayRemove(instance.getEffect());
            if (!allowed) {
                NutrientEffects.refused(entity);
            }
            return allowed;
        });
    }
}
