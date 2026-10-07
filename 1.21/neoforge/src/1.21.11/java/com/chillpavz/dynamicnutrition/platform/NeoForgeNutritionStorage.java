package com.chillpavz.dynamicnutrition.platform;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.platform.services.INutritionStorage;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

public class NeoForgeNutritionStorage implements INutritionStorage {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MOD_ID);

    /**
     * Persistent, copied across death, and synced to the OWNING player only.
     *
     * <p>NeoForge takes a {@code MapCodec} where Fabric takes a {@code Codec}, and a plain
     * {@code BiPredicate} where Fabric has a named {@code targetOnly()}. Same three capabilities,
     * spelled differently, which is the whole reason this class exists.
     */
    public static final Supplier<AttachmentType<PlayerNutrition>> TYPE = ATTACHMENTS.register(
            "nutrition", () -> AttachmentType.builder(PlayerNutrition::new)
                    .serialize(PlayerNutrition.MAP_CODEC)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, PlayerNutrition.STREAM_CODEC)
                    .build());

    @Override
    public PlayerNutrition get(Player player) {
        return player.getData(TYPE.get());
    }

    @Override
    public void markDirty(Player player) {
        // NeoForge's setData syncs unconditionally, so a copy is not strictly required here.
        // It is used anyway so both loaders do the identical thing: Fabric NEEDS the copy, and two
        // implementations that differ in a detail like this is how a loader-specific bug gets
        // reintroduced later. See PlayerNutrition.copy().
        player.setData(TYPE.get(), player.getData(TYPE.get()).copy());
    }
}
