package com.chillpavz.dynamicnutrition.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * The cloth-config data class, Fabric copy.
 *
 * <p>It is duplicated per loader rather than shared because {@code common} cannot see cloth at all,
 * and it holds no logic: {@link ClothCompat} copies every value into {@link NutritionConfig}, which
 * is the only thing the rest of the mod reads.
 *
 * <p><b>Nothing outside {@link ClothCompat} may name this class.</b> It implements a cloth
 * interface, so merely loading it needs cloth on the classpath, and cloth is optional.
 *
 * <p>The {@code Category} annotations are the tabs of the config screen and nothing else: the saved
 * file stays flat, so a file written before the tabs existed loads unchanged.
 */
@Config(name = Constants.MOD_ID)
public class ClothConfigData implements ConfigData {

    // ---------------------------------------------------------------- HUD

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip
    public boolean hudEnabled = true;

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = -120, max = 120)
    public int hudOffsetX = 0;

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = -80, max = 40)
    public int hudOffsetY = 0;

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip
    public boolean announceStatusChanges = true;

    // ---------------------------------------------------------------- inventory

    @ConfigEntry.Category("inventory")
    @ConfigEntry.Gui.Tooltip
    public boolean buttonEnabled = true;

    @ConfigEntry.Category("inventory")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = -160, max = 4)
    public int buttonOffsetX = 0;

    @ConfigEntry.Category("inventory")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = -4, max = 150)
    public int buttonOffsetY = 0;

    @ConfigEntry.Category("inventory")
    @ConfigEntry.Gui.Tooltip
    public boolean tooltipEnabled = true;

    // ---------------------------------------------------------------- variety

    @ConfigEntry.Category("variety")
    @ConfigEntry.Gui.Tooltip
    public boolean varietyEnabled = true;

    @ConfigEntry.Category("variety")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 32)
    public int varietyWindow = 8;

    @ConfigEntry.Category("variety")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 100)
    public int varietyPenaltyPercent = 20;

    @ConfigEntry.Category("variety")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 100)
    public int varietyFloorPercent = 30;

    // ---------------------------------------------------------------- effects

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    public boolean effectsEnabled = true;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    public boolean showEffectsOnHud = true;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    public boolean showEffectsInInventory = true;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int speedPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int slownessPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int strengthPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int weaknessPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int resistancePercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int hungerPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int regenerationPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int blindnessPercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int hastePercent = 50;

    @ConfigEntry.Category("effects")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int miningFatiguePercent = 50;
}
