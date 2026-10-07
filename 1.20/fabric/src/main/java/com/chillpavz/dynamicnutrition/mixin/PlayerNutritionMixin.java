package com.chillpavz.dynamicnutrition.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chillpavz.dynamicnutrition.platform.FabricNutritionStorage;
import com.chillpavz.dynamicnutrition.platform.NutritionHolder;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Gives every player, on both sides, its own nutrition object, and saves it in the player's own
 * NBT. Fabric API 0.92 has no attachment API, so this is the persistence an attachment gives for
 * free on the newer bands; the copy across a death and the sync to the client are done by
 * {@code DynamicNutritionFabric} and {@code FabricNutritionSync}.
 */
@Mixin(Player.class)
public abstract class PlayerNutritionMixin implements NutritionHolder {

    @Unique
    private final PlayerNutrition dynamicnutrition$nutrition = new PlayerNutrition();

    @Override
    public PlayerNutrition dynamicnutrition$nutrition() {
        return this.dynamicnutrition$nutrition;
    }

    @Inject(method = "addAdditionalSaveData", require = 1, at = @At("TAIL"))
    private void dynamicnutrition$save(CompoundTag tag, CallbackInfo info) {
        FabricNutritionStorage.save(this.dynamicnutrition$nutrition, tag);
    }

    @Inject(method = "readAdditionalSaveData", require = 1, at = @At("TAIL"))
    private void dynamicnutrition$load(CompoundTag tag, CallbackInfo info) {
        FabricNutritionStorage.load(this.dynamicnutrition$nutrition, tag);
    }
}
