package com.chillpavz.dynamicnutrition.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chillpavz.dynamicnutrition.client.NutrientBlindnessFog;

/**
 * The vitamins debuff's fog on Fabric, which has no fog event on this band. Both hooks run after
 * vanilla has finished, so they only ever replace what vanilla chose, and only when
 * {@link NutrientBlindnessFog} says the debuff applies. NeoForge and Forge reach the same two
 * points through their fog events.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupFog", require = 1, at = @At("TAIL"))
    private static void dynamicnutrition$fog(Camera camera, FogRenderer.FogMode mode,
                                             float renderDistance, boolean thickFog,
                                             float partialTick, CallbackInfo info) {
        float[] fog = NutrientBlindnessFog.distances(camera.getFluidInCamera(), camera.getEntity(),
                renderDistance, mode == FogRenderer.FogMode.FOG_SKY);
        if (fog != null) {
            RenderSystem.setShaderFogStart(fog[0]);
            RenderSystem.setShaderFogEnd(fog[1]);
        }
    }

    /** After vanilla's last clearColor, so the clear colour is set again with the darkened fog. */
    @Inject(method = "setupColor", require = 1, at = @At("TAIL"))
    private static void dynamicnutrition$darken(Camera camera, float partialTick, ClientLevel level,
                                                int renderDistanceChunks, float bossColorModifier,
                                                CallbackInfo info) {
        float scale = NutrientBlindnessFog.colorScale(camera.getFluidInCamera(), camera.getEntity());
        if (scale < 1.0F) {
            fogRed *= scale;
            fogGreen *= scale;
            fogBlue *= scale;
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        }
    }
}
