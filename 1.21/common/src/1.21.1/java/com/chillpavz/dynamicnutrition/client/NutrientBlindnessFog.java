package com.chillpavz.dynamicnutrition.client;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;
import com.chillpavz.dynamicnutrition.platform.Services;

/**
 * The fog for the vitamins debuff, at the server's strength.
 *
 * <p>Vanilla Blindness pulls the fog in to five blocks. At 100% this sets exactly the numbers
 * vanilla's does, and below that the far distance is interpolated GEOMETRICALLY between the render
 * distance and five blocks, so 50% at a 128 block render distance is about 25 blocks. A linear
 * blend would put half strength at 66 blocks, which barely reads as fog at all.
 *
 * <p>Whether it is on is read from the player's SYNCED values, the side the vitamins hold, by the
 * same rule the server applies ({@link NutrientEffects#isActive}): the debuff has no effect
 * instance of its own, only a line under Malnourished.
 *
 * <p>It only fogs the view. Vanilla Blindness also stops sprinting and critical hits, which are
 * checked against the vanilla effect by name elsewhere and are not reproduced.
 *
 * <p><b>How it is applied on this band.</b> Vanilla's effect fog here is a package private list of
 * functions that only run for an entity that HAS the effect's instance, which this debuff never
 * does, so it cannot join that list as it does on 26.x. The numbers are worked out here and each
 * loader applies them at the end of vanilla's own fog setup: NeoForge and Forge through their fog
 * events, Fabric through a mixin. Vanilla's own Blindness and Darkness, lava, powder snow and water
 * all keep priority, which matches the order vanilla uses.
 */
public final class NutrientBlindnessFog {

    private static final float VANILLA_DISTANCE = 5.0F;

    /**
     * Whether this fog replaces the ordinary fog for this entity. Instance method, and the same
     * name and arguments it has on 26.x, so the client test asks both bands the same question.
     */
    public boolean isApplicable(FogType type, Entity entity) {
        if (type != FogType.NONE || !(entity instanceof Player player)) {
            return false;
        }
        if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS)) {
            return false;
        }
        return strength(player) > 0;
    }

    /**
     * The fog distances to use, as {@code {start, end}}, or null to leave vanilla's alone.
     *
     * @param sky whether this is the sky pass, which vanilla Blindness fogs from zero to 80% of
     *            the distance rather than from 25% to all of it
     */
    public static float[] distances(FogType type, Entity entity, float renderDistance, boolean sky) {
        try {
            if (!new NutrientBlindnessFog().isApplicable(type, entity)) {
                return null;
            }
            float strength = Math.max(0.0F, Math.min(1.0F, strength(entity)));
            float far = renderDistance <= VANILLA_DISTANCE ? renderDistance
                    : (float) (Math.pow(renderDistance, 1.0 - strength)
                            * Math.pow(VANILLA_DISTANCE, strength));
            return sky ? new float[] {0.0F, far * 0.8F} : new float[] {far * 0.25F, far};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * What to multiply the fog colour by. Vanilla Blindness takes its fog to BLACK by scaling the
     * colour with the square of a brightness it sets to zero; without this the fog is pale blue.
     * Scaled the same way: 100% is vanilla's black, 50% a quarter of the brightness.
     */
    public static float colorScale(FogType type, Entity entity) {
        try {
            if (!new NutrientBlindnessFog().isApplicable(type, entity)) {
                return 1.0F;
            }
            float brightness = 1.0F - Math.max(0.0F, Math.min(1.0F, strength(entity)));
            return brightness * brightness;
        } catch (RuntimeException e) {
            return 1.0F;
        }
    }

    /** The fog's strength for this entity, 0 unless it is a player whose vitamins debuff is on. */
    private static float strength(Entity entity) {
        try {
            NutrientEffects.Spec spec = NutrientEffects.byId("blindness");
            if (spec == null || !(entity instanceof LivingEntity) || !(entity instanceof Player player)) {
                return 0.0F;
            }
            float fraction = SyncedEffectSettings.fraction(spec);
            return NutrientEffects.isActive(spec,
                    Services.STORAGE.get(player).heldStatus(spec.nutrient()), fraction) ? fraction : 0.0F;
        } catch (Throwable t) {
            return 0.0F;
        }
    }
}
