package com.chillpavz.dynamicnutrition.nutrition;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric needs the reload listener to carry an id, and {@code IdentifiableResourceReloadListener} is
 * a Fabric interface that {@code common} cannot reference. This adapter is the whole difference.
 */
public class FabricNutritionDataLoader extends NutritionDataLoader
        implements IdentifiableResourceReloadListener {

    public FabricNutritionDataLoader(NutritionTable table) {
        super(table);
    }

    @Override
    public ResourceLocation getFabricId() {
        return NutritionDataLoader.ID;
    }
}
