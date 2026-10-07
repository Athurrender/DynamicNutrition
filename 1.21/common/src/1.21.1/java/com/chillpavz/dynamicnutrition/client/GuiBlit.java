package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Every textured draw the mod makes, in one place, so the screen, the button and the HUD strip
 * never name a {@code GuiGraphics} overload themselves.
 *
 * <p>On this band that is simple: {@code blitSprite} takes a GUI sprite by name and applies the
 * scaling its {@code .mcmeta} declares, including nine slice, and a float UV {@code blit} draws a
 * region of a standalone texture. The seam exists so that the newer bands, where every one of these
 * calls changed shape, only ever had to change this file.
 */
public final class GuiBlit {

    /** Set after the first failed sprite draw, so a broken lookup logs once rather than per frame. */
    private static volatile boolean spriteFailureLogged;

    private GuiBlit() {
    }

    /**
     * A region of a standalone texture at its own size, the same arguments as vanilla's ten argument
     * {@code blit}: {@code (u, v)} is the region's top left in pixels on a sheet of
     * {@code texW} by {@code texH}.
     */
    public static void texture(GuiGraphics gfx, ResourceLocation texture, int x, int y,
                               int u, int v, int w, int h, int texW, int texH) {
        gfx.blit(texture, x, y, (float) u, (float) v, w, h, texW, texH);
    }

    /** A whole GUI sprite stretched to the given size. Missing sprites draw as vanilla's missing texture. */
    public static void sprite(GuiGraphics gfx, ResourceLocation sprite, int x, int y, int w, int h) {
        draw(gfx, sprite, x, y, w, h);
    }

    /**
     * A nine slice GUI sprite. Vanilla slices it from the sprite's own {@code .mcmeta}, so the
     * size and border arguments are not used for drawing here; they are kept so every band calls
     * this with the same arguments, and the audit holds them equal to the {@code .mcmeta}.
     */
    public static void nineSlice(GuiGraphics gfx, ResourceLocation sprite, int x, int y, int w,
                                 int h, int spriteW, int spriteH, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        draw(gfx, sprite, x, y, w, h);
    }

    /**
     * A sprite draw that cannot take the frame down. A missing sprite NAME is not a failure: the
     * atlas answers with its missing texture, as vanilla's draw would. A throw means the lookup
     * itself broke, and then the draw is skipped: an absent icon is a cosmetic fault, a throw on a
     * render path is a crash.
     */
    private static void draw(GuiGraphics gfx, ResourceLocation sprite, int x, int y, int w, int h) {
        try {
            gfx.blitSprite(sprite, x, y, w, h);
        } catch (RuntimeException | LinkageError e) {
            if (!spriteFailureLogged) {
                spriteFailureLogged = true;
                Constants.LOG.error("Could not draw the GUI sprite {}, so it will not be drawn",
                        sprite, e);
            }
        }
    }
}
