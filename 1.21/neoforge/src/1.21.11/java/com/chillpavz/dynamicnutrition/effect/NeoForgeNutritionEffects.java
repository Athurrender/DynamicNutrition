package com.chillpavz.dynamicnutrition.effect;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.chillpavz.dynamicnutrition.Constants;

/** NeoForge registration for the nutrient effects, and the rule that milk does not clear them. */
public final class NeoForgeNutritionEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Constants.MOD_ID);

    static {
        for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
            EFFECTS.register(registration.key().identifier().getPath(), registration::effect);
        }
    }

    private NeoForgeNutritionEffects() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        // Milk, a totem and /effect clear all come through here, one effect at a time. Cancelling
        // keeps ours; NutrientEffects.mayRemove lets this mod's own removals through. NeoForge
        // leaves a cancelled effect out of the remove packets, so the resend this reports is only
        // a precaution here; on Fabric it is the fix.
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Remove.class, event -> {
            if (!NutrientEffects.mayRemove(event.getEffect())) {
                event.setCanceled(true);
                NutrientEffects.refused(event.getEntity());
            }
        });
        // Resistance. Fires right after vanilla's own mob effect and enchantment reductions and
        // before absorption: the point Fabric reaches with a mixin on getDamageAfterMagicAbsorb,
        // whose return value NeoForge ignores.
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class, event ->
                event.setNewDamage(NutrientEffects.resist(event.getEntity(), event.getSource(),
                        event.getNewDamage())));
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
