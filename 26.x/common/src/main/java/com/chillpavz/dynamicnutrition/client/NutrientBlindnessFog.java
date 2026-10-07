package com.chillpavz.dynamicnutrition.client;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.effect.SyncedEffectSettings;
import com.chillpavz.dynamicnutrition.platform.Services;

/**
 * The fog for the vitamins debuff, at the server's strength.
 *
 * <p>Vanilla Blindness is drawn by a fog environment that pulls the fog in to five blocks. This is
 * the same kind of environment for our effect: at 100% it sets exactly the numbers vanilla's does,
 * and below that the far distance is interpolated GEOMETRICALLY between the render distance and
 * five blocks, so 50% at a 128 block render distance is about 25 blocks. A linear blend would put
 * half strength at 66 blocks, which barely reads as fog at all.
 *
 * <p>Whether it is on is read from the player's SYNCED attachment, the side the vitamins hold, by
 * the same rule the server applies ({@link NutrientEffects#isActive}): the debuff has no
 * effect instance of its own, only a line under Malnourished.
 *
 * <p>It only fogs the view. Vanilla Blindness also stops sprinting and critical hits, which are
 * checked against the vanilla effect by name elsewhere and are not reproduced.
 *
 * <p><b>Installed by adding it to {@code FogRenderer}'s own list</b>, directly after vanilla
 * Blindness: that list is a plain mutable {@code ArrayList} at 26.1, 26.2 and 26.3, the renderer
 * takes the first environment that applies, and a real Blindness potion therefore still wins.
 * If the list cannot be reached the effect simply has no fog, and says so once.
 */
public final class NutrientBlindnessFog extends MobEffectFogEnvironment {

    private static final float VANILLA_DISTANCE = 5.0F;

    private static boolean installed;

    /** Add the environment to the renderer's list. Once, from each loader's client setup. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Field field = FogRenderer.class.getDeclaredField("FOG_ENVIRONMENTS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<FogEnvironment> list = (List<FogEnvironment>) field.get(null);
            int at = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof BlindnessFogEnvironment) {
                    at = i + 1;
                }
            }
            list.add(at, new NutrientBlindnessFog());
        } catch (Throwable t) {
            Constants.LOG.warn("Could not install the Blindness fog for the vitamins effect, so it "
                    + "will show its icon without the fog: {}", t.toString());
        }
    }

    @Override
    public Holder<MobEffect> getMobEffect() {
        // Only the base class's own isApplicable asks for this, and it is overridden below. Never
        // null all the same; before registration vanilla Blindness is a harmless stand-in, as its
        // own environment is earlier in the list and always wins.
        Holder<MobEffect> holder = NutrientEffects.display(false);
        return holder != null ? holder : MobEffects.BLINDNESS;
    }

    @Override
    public boolean isApplicable(net.minecraft.world.level.material.FogType type, Entity entity) {
        return entity instanceof Player player && strength(player) > 0;
    }

    @Override
    public void setupFog(FogData data, Camera camera, ClientLevel level, float renderDistance,
                         DeltaTracker delta) {
        // No early return anywhere below: once chosen, this environment MUST fill the fields, because
        // the renderer's FogData starts at zero and an unfilled one is a white screen.
        float strength = Math.max(0.0F, Math.min(1.0F, strength(camera.entity())));
        float far = renderDistance <= VANILLA_DISTANCE ? renderDistance
                : (float) (Math.pow(renderDistance, 1.0 - strength)
                        * Math.pow(VANILLA_DISTANCE, strength));
        // The same four fields and ratios as vanilla's Blindness, set outright as it does: the
        // renderer hands every environment a FRESH FogData and uses the first one that applies, so
        // there is no earlier value to blend with (keeping the smaller of "old" and "new" fogged
        // the view to zero). The render distance fog is set by the renderer afterwards, separately.
        data.environmentalStart = far * 0.25F;
        data.environmentalEnd = far;
        data.skyEnd = far * 0.8F;
        data.cloudEnd = far * 0.8F;
    }

    /**
     * How dark the fog colour is. Vanilla Blindness answers 1.0 for as long as it lasts, which is what
     * makes its fog BLACK rather than sky coloured; without this the fog was pale blue. The renderer
     * uses the first applicable environment that modifies darkness, so a real Blindness potion,
     * earlier in the list, still wins. Scaled: 100% is vanilla's black, 50% half way to it.
     */
    @Override
    public float getModifiedDarkness(LivingEntity entity, float darkness, float partialTick) {
        return Math.max(darkness, Math.max(0.0F, Math.min(1.0F, strength(entity))));
    }

    /** The fog's strength for this entity, 0 unless it is a player whose vitamins debuff is on. */
    private static float strength(Entity entity) {
        try {
            NutrientEffects.Spec spec = NutrientEffects.byId("blindness");
            if (spec == null || !(entity instanceof Player player)) {
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
