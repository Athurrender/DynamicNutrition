package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Every textured draw the mod makes, in one place, so the screen, the button and the HUD strip
 * never name a {@code GuiGraphics} overload themselves.
 *
 * <p>This band has no GUI sprite atlas (it arrived at 1.20.2), so everything here is a plain
 * texture: a region at its own size, or a nine slice drawn from a small texture of its own. The
 * seam exists so that the newer bands, where every one of these calls changed shape, only ever had
 * to change this file.
 */
public final class GuiBlit {

    /** Set after the first failed draw, so a broken texture logs once rather than per frame. */
    private static volatile boolean failureLogged;

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

    /**
     * A nine slice of a small standalone texture ({@code spriteW} by {@code spriteH}, border
     * {@code border}), stretched to the given size. Vanilla's own nine slice here assumes a 256
     * pixel sheet and TILES, so it is not used. The border is clamped to half the drawn size.
     *
     * <p><b>The edges and the centre are STRETCHED.</b> That is only pixel identical for a texture
     * whose edges and centre are one colour along their length, which is true of the panel this is
     * used for. Do not reuse it for a patterned border.
     */
    public static void nineSlice(GuiGraphics gfx, ResourceLocation texture, int x, int y, int w,
                                 int h, int spriteW, int spriteH, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        try {
            int bx = Math.min(border, w / 2);
            int by = Math.min(border, h / 2);
            int[] dx = {x, x + bx, x + w - bx, x + w};
            int[] dy = {y, y + by, y + h - by, y + h};
            int[] sx = {0, bx, spriteW - bx, spriteW};
            int[] sy = {0, by, spriteH - by, spriteH};
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    int dw = dx[i + 1] - dx[i];
                    int dh = dy[j + 1] - dy[j];
                    int sw = sx[i + 1] - sx[i];
                    int sh = sy[j + 1] - sy[j];
                    if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) {
                        continue;
                    }
                    gfx.blit(texture, dx[i], dy[j], dw, dh, (float) sx[i], (float) sy[j], sw, sh,
                            spriteW, spriteH);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            if (!failureLogged) {
                failureLogged = true;
                Constants.LOG.error("Could not draw {}, so it will not be drawn", texture, e);
            }
        }
    }
}
