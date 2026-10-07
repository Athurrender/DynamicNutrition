package com.chillpavz.dynamicnutrition.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraftforge.fml.ModList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Everything that touches cloth-config, on Forge.
 *
 * <h2>Why this class exists at all, and why a try/catch is not enough</h2>
 * Cloth is a REQUIRED dependency on this band, unlike the 26.x one where it is optional to win the
 * day-zero update after a Minecraft release. There is no such race on a version this old, so
 * requiring it buys a config screen that is always there.
 *
 * <p>The isolation still earns its keep, for two reasons. A required dependency can still fail to
 * initialise, and rule nine says degrade rather than crash: a broken cloth leaves the mod running
 * on its defaults instead of taking the game down. And {@link ClothConfigData} implements a cloth
 * interface, so merely verifying a method that mentions it can trigger the class load, which is why
 * a {@code catch (Throwable)} around the annotated class would not be enough on its own. Nothing
 * outside this class names anything cloth-shaped.
 *
 * <p>Note the Forge build is the unofficial cloth-config-forge port: official cloth ships no Forge
 * build at this Minecraft version. Same mod id, same version number, separate project.
 */
public final class ClothCompat {

    // The Forge and NeoForge builds both use 'cloth_config', with an underscore. The Fabric build
    // uses 'cloth-config' and provides 'cloth-config2', so a check written from either spelling
    // alone is wrong on the other loader. All three are real and all three are different.
    private static final String[] IDS = {"cloth_config"};

    private static boolean available;

    private ClothCompat() {
    }

    /** True only when cloth is actually installed. Every other method here is a no-op otherwise. */
    public static boolean isAvailable() {
        return available;
    }

    /** Called from the mod initializer. Safe with cloth absent, and safe with cloth broken. */
    public static void init() {
        for (String id : IDS) {
            if (ModList.get().isLoaded(id)) {
                available = true;
                break;
            }
        }
        if (!available) {
            // Declared required in mods.toml, so reaching here means the loader let a jar through
            // without it. Say so plainly rather than pretending this is a supported shape.
            Constants.LOG.warn("Cloth Config is required on this version but was not found. "
                    + "There is no config screen; everything else works on the defaults.");
            return;
        }
        try {
            ConfigHolder<ClothConfigData> holder =
                    AutoConfig.register(ClothConfigData.class, GsonConfigSerializer::new);
            apply(holder.getConfig());
            holder.registerSaveListener((h, data) -> {
                apply(data);
                return InteractionResult.SUCCESS;
            });
            holder.registerLoadListener((h, data) -> {
                apply(data);
                return InteractionResult.SUCCESS;
            });
        } catch (Throwable t) {
            // Cloth present but unhappy. Degrade to the defaults rather than refusing to load.
            available = false;
            Constants.LOG.warn("Cloth Config failed to initialise, running on defaults: {}",
                    t.toString());
        }
    }

    /** The screen, or null when cloth is absent. Client side only. */
    public static Screen screen(Screen parent) {
        if (!available) {
            return null;
        }
        try {
            // AutoConfigClient, not AutoConfig: cloth split the GUI entry point out at 26.1 so the
            // server-safe class stops referencing screen types, and AutoConfig.getConfigScreen is
            // gone from 26.1 onward.
            return AutoConfigClient.getConfigScreen(ClothConfigData.class, parent).get();
        } catch (Throwable t) {
            Constants.LOG.warn("Could not open the config screen: {}", t.toString());
            return null;
        }
    }

    /** The one place a cloth value becomes a mod value. */
    private static void apply(ClothConfigData data) {
        NutritionConfig.hudEnabled = data.hudEnabled;
        NutritionConfig.hudOffsetX = data.hudOffsetX;
        NutritionConfig.hudOffsetY = data.hudOffsetY;
        NutritionConfig.buttonEnabled = data.buttonEnabled;
        NutritionConfig.buttonOffsetX = data.buttonOffsetX;
        NutritionConfig.buttonOffsetY = data.buttonOffsetY;
        NutritionConfig.tooltipEnabled = data.tooltipEnabled;
        NutritionConfig.varietyEnabled = data.varietyEnabled;
        NutritionConfig.varietyWindow = data.varietyWindow;
        NutritionConfig.varietyPenaltyPercent = data.varietyPenaltyPercent;
        NutritionConfig.varietyFloorPercent = data.varietyFloorPercent;
        NutritionConfig.effectsEnabled = data.effectsEnabled;
        NutritionConfig.showEffectsOnHud = data.showEffectsOnHud;
        NutritionConfig.showEffectsInInventory = data.showEffectsInInventory;
        NutritionConfig.speedPercent = data.speedPercent;
        NutritionConfig.slownessPercent = data.slownessPercent;
        NutritionConfig.strengthPercent = data.strengthPercent;
        NutritionConfig.weaknessPercent = data.weaknessPercent;
        NutritionConfig.resistancePercent = data.resistancePercent;
        NutritionConfig.hungerPercent = data.hungerPercent;
        NutritionConfig.regenerationPercent = data.regenerationPercent;
        NutritionConfig.blindnessPercent = data.blindnessPercent;
        NutritionConfig.hastePercent = data.hastePercent;
        NutritionConfig.miningFatiguePercent = data.miningFatiguePercent;
        NutritionConfig.announceStatusChanges = data.announceStatusChanges;
        // Last, so the server resends the effect settings to everyone after the new values are in.
        NutritionConfig.revision++;
    }
}
