package com.chillpavz.dynamicnutrition.effect;

import net.minecraftforge.common.MinecraftForge;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/** Forge registration for the nutrient effects, and the rule that milk does not clear them. */
public final class ForgeNutritionEffects {

    private ForgeNutritionEffects() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterEvent event) -> {
            if (!Registries.MOB_EFFECT.equals(event.getRegistryKey())) {
                return;
            }
            for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
                event.register(Registries.MOB_EFFECT, registration.key().location(),
                        registration::effect);
            }
        });

        // Milk, a totem and /effect clear all come through here, one effect at a time. Cancelling
        // keeps ours; NutrientEffects.mayRemove lets this mod's own removals through.
        // EventBus 6: cancelled with setCanceled, as on NeoForge.
        MinecraftForge.EVENT_BUS.addListener((MobEffectEvent.Remove event) -> {
            if (NutrientEffects.mayRemove(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(event.getEffect()))) {
                return;
            }
            NutrientEffects.refused(event.getEntity());
            event.setCanceled(true);
        });

        // Resistance. Forge fires LivingDamageEvent after vanilla's armour, enchantment and mob
        // effect reductions and before absorption, which is the point Fabric reaches with a mixin
        // on getDamageAfterMagicAbsorb. Unlike NeoForge, classic Forge READS the amount back:
        // ForgeHooks.onLivingDamage returns a float and LivingEntity uses it, so setAmount works.
        MinecraftForge.EVENT_BUS.addListener((LivingDamageEvent event) ->
                event.setAmount(NutrientEffects.resist(event.getEntity(), event.getSource(),
                        event.getAmount())));

        // The mining half of the minerals effects. Mining speed is not an attribute on this band
        // (it arrived at 1.20.5), so the factor goes into Forge's break speed event, which fires
        // on both sides, where Fabric multiplies it into getDestroySpeed.
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.BreakSpeed event) ->
                event.setNewSpeed(event.getNewSpeed()
                        * NutrientEffects.breakSpeedFactor(event.getEntity())));
    }

    /**
     * Hand the holders to the common code, once the registry has been filled. Separate from
     * {@link #register} because the holders do not exist until registration has actually run.
     */
    public static void bind() {
        Map<ResourceKey<MobEffect>, Holder<MobEffect>> holders = new HashMap<>();
        for (NutrientEffects.Registration registration : NutrientEffects.registrations()) {
            holders.put(registration.key(), BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(registration.key()));
        }
        NutrientEffects.bind(holders);
    }
}
