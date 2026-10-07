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
                    .serialize(PlayerNutrition.CODEC)
                    .copyOnDeath()
                    // No .sync(...): NeoForge 21.1 attachments cannot sync. NeoForgeNutritionSync
                    // sends the values instead, as the Forge module does for its capability.
                    .build());

    @Override
    public PlayerNutrition get(Player player) {
        return player.getData(TYPE.get());
    }

    @Override
    public void markDirty(Player player) {
        // The attachment is mutated in place and saved from there, so nothing needs setting; what
        // it cannot do on this band is tell the client, which is this flag's job.
        NeoForgeNutritionSync.markDirty(player);
    }
}
