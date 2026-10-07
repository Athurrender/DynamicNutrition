package com.chillpavz.dynamicnutrition.effect;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegisterEvent;

/** Forge registration for the nutrient effects, and the rule that milk does not clear them. */
public final class ForgeNutritionEffects {

    private ForgeNutritionEffects() {
    }

    public static void register(BusGroup modBus) {
        RegisterEvent.getBus(modBus).addListener(event -> {
            if (!Registries.MOB_EFFECT.equals(event.getRegistryKey())) {
                return;
            }
            for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
                event.register(Registries.MOB_EFFECT, registration.key().identifier(),
                        registration::effect);
            }
        });

        // Milk, a totem and /effect clear all come through here, one effect at a time. Cancelling
        // keeps ours; NutrientEffects.mayRemove lets this mod's own removals through.
        // Cancellable on EventBus 8: a Predicate listener that returns true cancels the event.
        MobEffectEvent.Remove.BUS.addListener(event -> {
            if (NutrientEffects.mayRemove(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(event.getEffect()))) {
                return false;
            }
            NutrientEffects.refused(event.getEntity());
            return true;
        });

        // Resistance. Forge fires LivingDamageEvent after vanilla's armour, enchantment and mob
        // effect reductions and before absorption, which is the point Fabric reaches with a mixin
        // on getDamageAfterMagicAbsorb. Unlike NeoForge, classic Forge READS the amount back:
        // ForgeHooks.onLivingDamage returns a float and LivingEntity uses it, so setAmount works.
        // The explicit Consumer is required, not stylistic: a CancellableEventBus offers both
        // addListener(Consumer) and addListener(Predicate), and a lambda returning void is
        // ambiguous between them. This listener adjusts the damage and never cancels.
        LivingDamageEvent.BUS.addListener((Consumer<LivingDamageEvent>) event ->
                event.setAmount(NutrientEffects.resist(event.getEntity(), event.getSource(),
                        event.getAmount())));
    }

    /**
     * Hand the holders to the common code, once the registry has been filled. Separate from
     * {@link #register} because the holders do not exist until registration has actually run.
     */
    public static void bind() {
        Map<ResourceKey<MobEffect>, Holder<MobEffect>> holders = new HashMap<>();
        for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
            holders.put(registration.key(), BuiltInRegistries.MOB_EFFECT.getOrThrow(registration.key()));
        }
        NutrientEffects.bind(holders);
    }
}
