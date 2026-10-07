package com.chillpavz.dynamicnutrition.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A {@link MobEffect} this mod can construct, which can borrow a vanilla effect's NAME.
 *
 * <p>{@code MobEffect}'s constructors are protected, which is the first reason this class exists.
 * The second is the ten effect ids 1.5.0 used, one per nutrient effect: they are still registered
 * so saves naming them load, and should one ever be drawn in the moment before it is taken off, it
 * carries the vanilla name it imitates ("Haste", "Slowness") by answering with vanilla's own
 * translation key, and vanilla's icon through the atlas entry in
 * {@code assets/minecraft/atlases/gui.json}, so no vanilla pixels are copied into this jar. Well
 * Nourished and Malnourished use their own names and icons.
 */
public class NutritionMobEffect extends MobEffect {

    /** The vanilla effect whose name this one shows, e.g. {@code "haste"}, or null for its own. */
    private final String vanillaName;

    public NutritionMobEffect(MobEffectCategory category, int color) {
        this(category, color, null);
    }

    public NutritionMobEffect(MobEffectCategory category, int color, String vanillaName) {
        super(category, color);
        this.vanillaName = vanillaName;
    }

    @Override
    public String getDescriptionId() {
        return vanillaName == null ? super.getDescriptionId() : "effect.minecraft." + vanillaName;
    }
}
