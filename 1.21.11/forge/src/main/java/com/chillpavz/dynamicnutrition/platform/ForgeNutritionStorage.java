package com.chillpavz.dynamicnutrition.platform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * Forge has no attachment API, so per-player state lives on a CAPABILITY.
 *
 * <p>That difference is not cosmetic. Fabric and NeoForge attachments carry three things for free:
 * persistence, copy across death, and a sync to the owning client. A capability carries only the
 * first, and only because this provider serialises it. The other two are supplied by
 * {@link ForgeNutritionSync}: the clone event copies the object, and a payload sends it. So the
 * common code is unchanged and {@link #markDirty} still means "the client needs to hear about
 * this", but on this loader something has to act on that, rather than the loader doing it.
 *
 * <p>The capability is attached to EVERY player on both sides. The client needs one too, because
 * the screen, the HUD strip and the tooltips all read the player's values through this same
 * interface and must not care which side they are on.
 */
public class ForgeNutritionStorage implements INutritionStorage {

    public static final Capability<PlayerNutrition> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<PlayerNutrition>() { });

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "nutrition");

    /**
     * The provider Forge attaches, and the thing that saves the values into the player's own NBT.
     *
     * <p>It serialises through {@link PlayerNutrition#CODEC}, the same codec the other two loaders
     * persist with, so a world can move between loaders without the values being re-read by a
     * second hand-written format that could drift from it.
     */
    public static final class Provider implements ICapabilitySerializable<Tag> {

        private final PlayerNutrition nutrition = new PlayerNutrition();
        private final LazyOptional<PlayerNutrition> optional = LazyOptional.of(() -> this.nutrition);

        public PlayerNutrition nutrition() {
            return this.nutrition;
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability,
                                                 @Nullable Direction side) {
            return capability == CAPABILITY ? this.optional.cast() : LazyOptional.empty();
        }

        @Override
        public Tag serializeNBT(HolderLookup.Provider registries) {
            return PlayerNutrition.CODEC
                    .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.nutrition)
                    .resultOrPartial(Constants.LOG::error)
                    .orElseGet(CompoundTag::new);
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, Tag tag) {
            PlayerNutrition.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                    .resultOrPartial(Constants.LOG::error)
                    .ifPresent(this.nutrition::copyFrom);
        }
    }

    @Override
    public PlayerNutrition get(Player player) {
        // Never null by contract. A player without the capability can only happen if another mod
        // cancelled the attach event, in which case a detached object keeps the mod running on
        // defaults rather than throwing on every tick.
        return player.getCapability(CAPABILITY).orElseGet(PlayerNutrition::new);
    }

    @Override
    public void markDirty(Player player) {
        // No loader-side dirtiness to trip here: the capability is a plain object and nothing
        // watches it. The flag is ours, and ForgeNutritionSync drains it on the player's next tick.
        ForgeNutritionSync.markDirty(player);
    }
}
