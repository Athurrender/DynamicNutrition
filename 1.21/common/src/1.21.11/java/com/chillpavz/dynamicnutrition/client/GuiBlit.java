package com.chillpavz.dynamicnutrition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Every textured draw the mod makes, routed through the ONE blit overload whose signature does not
 * name the render pipeline type.
 *
 * <h2>Why nothing here passes a pipeline</h2>
 * 26.3 moved {@code RenderPipeline} to a different package. Source that writes
 * {@code blit(RenderPipelines.GUI_TEXTURED, ...)} recompiles unchanged, but the compiled call names
 * the class by its full name, so a jar built against 26.2 throws {@code NoSuchFieldError} and
 * {@code NoSuchMethodError} on 26.3 the first time it draws anything. That is the HUD strip, every
 * frame.
 *
 * <p>{@code blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)} exists with the same signature from
 * 26.1 to 26.3, and draws with {@code GUI_TEXTURED} in white, which is exactly what every call here
 * used to ask for. There is no sprite overload without a pipeline, so sprites are drawn the way
 * vanilla draws them internally: look the sprite up on the GUI atlas and blit its UV rectangle from
 * the atlas texture.
 *
 * <h2>The lookup is vanilla's own, not {@code getSprite(SpriteId)}</h2>
 * {@code GuiGraphics.getSprite(SpriteId)} goes through a table keyed by the atlas TEXTURE
 * id and throws for anything else, so building the key from {@code AtlasIds.GUI} (the atlas id)
 * crashed the inventory on every version. Vanilla's own GUI sprite draw takes the atlas by id from
 * the atlas manager and asks it for the sprite by name, which is what {@link #lookup} does.
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
    public static void texture(GuiGraphics gfx, Identifier texture, int x, int y,
                               int u, int v, int w, int h, int texW, int texH) {
        gfx.blit(texture, x, y, x + w, y + h,
                (float) u / texW, (float) (u + w) / texW,
                (float) v / texH, (float) (v + h) / texH);
    }

    /** A whole GUI sprite stretched to the given size. Missing sprites draw as vanilla's missing texture. */
    public static void sprite(GuiGraphics gfx, Identifier sprite, int x, int y, int w, int h) {
        TextureAtlasSprite s = lookup(sprite);
        if (s == null) {
            return;
        }
        gfx.blit(s.atlasLocation(), x, y, x + w, y + h, s.getU0(), s.getU1(), s.getV0(), s.getV1());
    }

    /**
     * A nine slice GUI sprite. The border is clamped to half the drawn size the way vanilla clamps it.
     *
     * <p><b>The edges and the centre are STRETCHED, where vanilla tiles them.</b> That is only
     * pixel identical for a sprite whose edges and centre are one colour along their length, which
     * is true of the panel this is used for. Do not reuse it for a patterned border.
     */
    public static void nineSlice(GuiGraphics gfx, Identifier sprite, int x, int y, int w,
                                 int h, int spriteW, int spriteH, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        TextureAtlasSprite s = lookup(sprite);
        if (s == null) {
            return;
        }
        int bx = Math.min(border, w / 2);
        int by = Math.min(border, h / 2);
        int[] dx = {x, x + bx, x + w - bx, x + w};
        int[] dy = {y, y + by, y + h - by, y + h};
        int[] sx = {0, bx, spriteW - bx, spriteW};
        int[] sy = {0, by, spriteH - by, spriteH};
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                if (dx[i + 1] <= dx[i] || dy[j + 1] <= dy[j]) {
                    continue;
                }
                gfx.blit(s.atlasLocation(), dx[i], dy[j], dx[i + 1], dy[j + 1],
                        s.getU((float) sx[i] / spriteW), s.getU((float) sx[i + 1] / spriteW),
                        s.getV((float) sy[j] / spriteH), s.getV((float) sy[j + 1] / spriteH));
            }
        }
    }

    /**
     * The sprite from the GUI atlas, or null when it cannot be looked up. A missing sprite NAME is
     * not a failure: the atlas answers with its missing texture, as vanilla's draw would. Null means
     * the lookup itself threw, and then the draw is skipped: an absent icon is a cosmetic fault, a
     * throw on a render path is a crash.
     */
    private static TextureAtlasSprite lookup(Identifier sprite) {
        try {
            return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI)
                    .getSprite(sprite);
        } catch (RuntimeException | LinkageError e) {
            if (!spriteFailureLogged) {
                spriteFailureLogged = true;
                Constants.LOG.error("Could not look up the GUI sprite {}, so it will not be drawn",
                        sprite, e);
            }
            return null;
        }
    }
}
