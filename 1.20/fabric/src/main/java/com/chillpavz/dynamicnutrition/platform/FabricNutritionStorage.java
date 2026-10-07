package com.chillpavz.dynamicnutrition.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Per-player state on Fabric at this band: a field on the player ({@link NutritionHolder}), saved
 * in the player's NBT under {@link #KEY}. Fabric API 0.92 has no attachment API, so the three
 * things an attachment does on the newer bands are three jobs here: persistence (this class and
 * {@code PlayerNutritionMixin}), the copy across a death ({@code ServerPlayerEvents.COPY_FROM}),
 * and the sync to the owning client ({@link FabricNutritionSync}).
 */
public class FabricNutritionStorage implements INutritionStorage {

    /** Where the values live in the player's own NBT. */
    public static final String KEY = Constants.MOD_ID + ":nutrition";

    @Override
    public PlayerNutrition get(Player player) {
        return ((NutritionHolder) player).dynamicnutrition$nutrition();
    }

    @Override
    public void markDirty(Player player) {
        // Nothing watches the object; the flag is ours, drained at the end of the server tick.
        FabricNutritionSync.markDirty(player);
    }

    /** Saved through CODEC, the codec every band saves with, so there is one list of fields. */
    public static void save(PlayerNutrition nutrition, CompoundTag tag) {
        PlayerNutrition.CODEC.encodeStart(NbtOps.INSTANCE, nutrition)
                .resultOrPartial(Constants.LOG::error)
                .ifPresent(encoded -> tag.put(KEY, encoded));
    }

    /** Filled IN PLACE, because the object is created with the player and handed out by reference. */
    public static void load(PlayerNutrition nutrition, CompoundTag tag) {
        if (!tag.contains(KEY)) {
            return;
        }
        PlayerNutrition.CODEC.parse(NbtOps.INSTANCE, tag.get(KEY))
                .resultOrPartial(Constants.LOG::error)
                .ifPresent(nutrition::copyFrom);
    }
}
