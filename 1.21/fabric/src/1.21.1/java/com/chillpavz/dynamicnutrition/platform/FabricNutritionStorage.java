package com.chillpavz.dynamicnutrition.platform;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

public class FabricNutritionStorage implements INutritionStorage {

    /**
     * Persistent, copied across death, and synced to the OWNING player only.
     *
     * <p>{@code targetOnly()} rather than {@code all()} on purpose: nobody else needs to know what
     * another player has eaten, and syncing it to everyone would put a payload per player on every
     * change for a screen only its owner can open.
     */
    public static final AttachmentType<PlayerNutrition> TYPE = AttachmentRegistry
            .<PlayerNutrition>builder()
            .initializer(PlayerNutrition::new)
            .persistent(PlayerNutrition.CODEC)
            .copyOnDeath()
            .syncWith(PlayerNutrition.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "nutrition"));

    @Override
    public PlayerNutrition get(Player player) {
        return player.getAttachedOrCreate(TYPE);
    }

    @Override
    public void markDirty(Player player) {
        // MUST be a COPY, not the same instance. Fabric's setAttached does
        //     if (Objects.equals(oldValue, newValue)) { skip markChanged and skip sync }
        // so re-setting the same object is a complete no-op and the client never hears about it.
        // NeoForge has no such check, which is exactly why this bug was Fabric-only and why both
        // loaders now do the identical thing. See PlayerNutrition.copy().
        player.setAttached(TYPE, player.getAttachedOrCreate(TYPE).copy());
    }
}
