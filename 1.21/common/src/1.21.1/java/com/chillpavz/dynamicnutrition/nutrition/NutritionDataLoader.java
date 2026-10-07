package com.chillpavz.dynamicnutrition.nutrition;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Loads {@code data/&lt;namespace&gt;/nutrition/*.json} into the explicit table.
 *
 * <p>Format is a flat map of item id to nutrient values:
 * <pre>
 * {
 *   "minecraft:carrot": { "carbohydrates": 7, "protein": 1, "vitamins": 27, "minerals": 6 }
 * }
 * </pre>
 *
 * <p>Later datapacks override earlier ones per ITEM rather than per file, which is what a pack
 * author expects: adding one food should not require restating the other forty.
 *
 * <p>Unknown item ids and unknown nutrient names are SKIPPED WITH A WARNING rather than failing the
 * load. A pack that mentions a food from a mod the player has not installed is the normal case, not
 * an error, and refusing the whole file over one line would take the other thirty-nine down with it.
 */
public class NutritionDataLoader extends SimpleJsonResourceReloadListener {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "nutrition");

    private static final Codec<Map<String, Map<String, Integer>>> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT));

    private final NutritionTable table;

    public NutritionDataLoader(NutritionTable table) {
        // Untyped on this band: the files arrive as JSON and are decoded with CODEC in apply.
        super(new Gson(), "nutrition");
        this.table = table;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> json,
                         ResourceManager manager, ProfilerFiller profiler) {
        // Decoded one file at a time, so one malformed file is reported and skipped rather than
        // taking the other files down with it, which is what the typed listener on 26.x does too.
        Map<ResourceLocation, Map<String, Map<String, Integer>>> files = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : json.entrySet()) {
            CODEC.parse(JsonOps.INSTANCE, file.getValue())
                    .resultOrPartial(error -> Constants.LOG.warn("{}: {}", file.getKey(), error))
                    .ifPresent(values -> files.put(file.getKey(), values));
        }
        Map<Item, NutritionValues> out = new HashMap<>();
        int unknownItems = 0;
        int unknownNutrients = 0;

        for (Map.Entry<ResourceLocation, Map<String, Map<String, Integer>>> file : files.entrySet()) {
            for (Map.Entry<String, Map<String, Integer>> entry : file.getValue().entrySet()) {
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
                if (itemId == null) {
                    Constants.LOG.warn("{}: {} is not a valid item id", file.getKey(), entry.getKey());
                    continue;
                }
                // BLOCK and ITEM are DEFAULTED registries: an absent id resolves to AIR, not null.
                // Checking containsKey first is the only correct way to ask, and getting it wrong
                // maps air itself to a nutrition value, which in a food lookup reads as the mod
                // firing constantly rather than as a lookup bug.
                if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                    unknownItems++;
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(itemId);

                Map<Nutrient, Integer> values = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> pair : entry.getValue().entrySet()) {
                    Nutrient nutrient = Nutrients.byName(pair.getKey());
                    if (nutrient == null) {
                        unknownNutrients++;
                        Constants.LOG.warn("{}: {} names unknown nutrient '{}'",
                                file.getKey(), itemId, pair.getKey());
                        continue;
                    }
                    values.put(nutrient, pair.getValue());
                }
                NutritionValues resolved = NutritionValues.of(values);
                if (!resolved.isEmpty()) {
                    out.put(item, resolved);
                }
            }
        }

        if (unknownItems > 0) {
            Constants.LOG.info("Skipped {} nutrition entries for items that are not installed",
                    unknownItems);
        }
        if (unknownNutrients > 0) {
            Constants.LOG.warn("Skipped {} entries naming a nutrient this mod does not track",
                    unknownNutrients);
        }
        table.setExplicit(out);
    }
}
