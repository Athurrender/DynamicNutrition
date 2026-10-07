package com.chillpavz.dynamicnutrition.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chillpavz.dynamicnutrition.effect.NutrientEffects;

/**
 * The mining half of the minerals effects on Fabric. Mining speed is not an attribute on this band
 * (it arrived at 1.20.5), so the factor is multiplied into vanilla's own dig speed, on both sides,
 * where Forge uses its break speed event.
 */
@Mixin(Player.class)
public abstract class PlayerDigSpeedMixin {

    @Inject(method = "getDestroySpeed", require = 1, at = @At("RETURN"), cancellable = true)
    private void dynamicnutrition$nutrientDigSpeed(BlockState state, CallbackInfoReturnable<Float> info) {
        float factor = NutrientEffects.breakSpeedFactor((Player) (Object) this);
        if (factor != 1.0F) {
            info.setReturnValue(info.getReturnValue() * factor);
        }
    }
}
