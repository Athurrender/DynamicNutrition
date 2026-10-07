package com.chillpavz.dynamicnutrition.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The Mod Menu entry on the Fabric mods list.
 *
 * <p>Reached through the {@code modmenu} entrypoint, which only Mod Menu reads, so this class is
 * never loaded when Mod Menu is absent. Cloth is a separate question and is guarded separately:
 * with Mod Menu present and cloth missing, the factory returns the screen it was given, so the
 * button closes rather than crashing.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            var screen = ClothCompat.screen(parent);
            return screen == null ? parent : screen;
        };
    }
}
