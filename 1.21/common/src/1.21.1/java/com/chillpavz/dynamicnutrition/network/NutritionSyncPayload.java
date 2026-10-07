package com.chillpavz.dynamicnutrition.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.effect.NutrientEffects;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.nutrition.NutritionOrigin;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;

/**
 * The whole resolved food table, sent server to client in ONE packet.
 *
 * <p>One packet rather than a request per item is not an optimisation, it is the fix for the bug
 * that has made mods of this kind unusable before now: recipe viewers index every item's tooltip
 * at once, so a request-response design turns first login into a stall. The client never resolves anything, and
 * it cannot: recipe enumeration needs a {@code RecipeManager}, which only exists server side, and
 * on Fabric recipes are not synced to the client at all.
 *
 * <p><b>Nutrients are sent by NAME in a header and referenced by index thereafter.</b> Sending the
 * name per food would multiply the payload for no gain, and sending a bare index with no header
 * would silently mis-key every value the day a datapack adds a sixth nutrient. The header costs
 * about fifty bytes once.
 *
 * <p>Foods that resolve to nothing are omitted. Absent therefore means "no nutrients", which is what
 * the client wants to render anyway, and on a large modpack it is most of the item registry.
 *
 * <p>It also carries the server's nutrient effect settings, which are tiny and change with the same
 * rarity: the client needs them to say in a bar's tooltip which effects are on, and to draw the
 * Blindness fog at the server's strength rather than at whatever its own config says.
 *
 * @param nutrientNames  the server's nutrients, in its own order
 * @param entries        per item, its values and where they came from
 * @param effectsEnabled whether the server applies the nutrient effects at all
 * @param effectPercents each effect's strength by id, 0 to 100
 */
public record NutritionSyncPayload(List<String> nutrientNames,
                                   Map<Item, NutritionSyncPayload.Entry> entries,
                                   boolean effectsEnabled,
                                   Map<String, Integer> effectPercents)
        implements CustomPacketPayload {

    /**
     * One food on the wire.
     *
     * @param values one entry per nutrient in the header, same order, zeros included
     * @param source which pipeline stage answered, as an ordinal
     * @param from   the contributing ingredients, for a recipe-derived food only
     */
    public record Entry(List<Integer> values, int source, List<Item> from) {
    }

    public static final CustomPacketPayload.Type<NutritionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "nutrition_sync"));

    /**
     * Generous ceilings. A large modpack can carry several thousand foods, and the default
     * collection limits are sized for ordinary gameplay packets rather than a one-off table.
     */
    private static final int MAX_NUTRIENTS = 256;
    private static final int MAX_FOODS = 65536;

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM =
            ByteBufCodecs.registry(Registries.ITEM);

    // THE THREE ARGUMENT FORM, NOT `x.apply(collection(factory, max))`. The CodecOperation overload
    // `collection(IntFunction, int)` was ADDED IN 26.2 and does not exist at 26.1, so a jar built
    // against 26.2 and installed on 26.1 throws NoSuchMethodError while registering its payload,
    // before the main menu. This form takes the element codec directly and exists in both.
    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> NAMES =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, MAX_NUTRIENTS);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> VALUES =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT, MAX_NUTRIENTS);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Item>> FROM =
            ByteBufCodecs.collection(ArrayList::new, ITEM, NutritionOrigin.MAX_FROM);

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY =
            StreamCodec.composite(
                    VALUES, Entry::values,
                    ByteBufCodecs.VAR_INT, Entry::source,
                    FROM, Entry::from,
                    Entry::new);

    private static final int MAX_EFFECTS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, NutritionSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NAMES, NutritionSyncPayload::nutrientNames,
                    ByteBufCodecs.map(HashMap::new, ITEM, ENTRY, MAX_FOODS),
                    NutritionSyncPayload::entries,
                    ByteBufCodecs.BOOL, NutritionSyncPayload::effectsEnabled,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT,
                            MAX_EFFECTS),
                    NutritionSyncPayload::effectPercents,
                    NutritionSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ---------------------------------------------------------------- building

    /** Pack a resolved table for the wire, dropping everything that feeds nothing. */
    public static NutritionSyncPayload of(Map<Item, NutritionValues> table,
                                          Map<Item, NutritionOrigin> origins) {
        List<Nutrient> order = Nutrients.all();
        List<String> names = new ArrayList<>(order.size());
        for (Nutrient nutrient : order) {
            names.add(nutrient.name());
        }

        Map<Item, Entry> out = new HashMap<>();
        for (Map.Entry<Item, NutritionValues> entry : table.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<Integer> row = new ArrayList<>(order.size());
            for (Nutrient nutrient : order) {
                row.add(entry.getValue().get(nutrient));
            }
            NutritionOrigin origin = origins.getOrDefault(entry.getKey(), NutritionOrigin.NONE);
            out.put(entry.getKey(), new Entry(row, origin.source().ordinal(), origin.from()));
        }
        Map<String, Integer> percents = new HashMap<>();
        for (NutrientEffects.Spec spec : NutrientEffects.ALL) {
            percents.put(spec.id(), NutritionConfig.effectPercent(spec.id()));
        }
        return new NutritionSyncPayload(names, out, NutritionConfig.effectsEnabled, percents);
    }

    // ---------------------------------------------------------------- reading

    /**
     * Unpack into the client's own nutrient objects.
     *
     * <p>A name this client does not know is dropped rather than failing the packet. That is the
     * case where the server has a datapack nutrient the client's copy of the mod does not define,
     * and losing one column beats losing the whole table.
     */
    public Map<Item, NutritionValues> unpackValues() {
        List<Nutrient> byIndex = new ArrayList<>(nutrientNames.size());
        for (String name : nutrientNames) {
            byIndex.add(Nutrients.byName(name));
        }

        Map<Item, NutritionValues> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Entry> entry : entries.entrySet()) {
            Map<Nutrient, Integer> row = new LinkedHashMap<>();
            List<Integer> encoded = entry.getValue().values();
            for (int i = 0; i < byIndex.size() && i < encoded.size(); i++) {
                Nutrient nutrient = byIndex.get(i);
                if (nutrient != null && encoded.get(i) > 0) {
                    row.put(nutrient, encoded.get(i));
                }
            }
            NutritionValues resolved = NutritionValues.of(row);
            if (!resolved.isEmpty()) {
                out.put(entry.getKey(), resolved);
            }
        }
        return out;
    }

    /** The matching origin table, so the advanced tooltip can say which stage answered. */
    public Map<Item, NutritionOrigin> unpackOrigins() {
        Map<Item, NutritionOrigin> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Entry> entry : entries.entrySet()) {
            out.put(entry.getKey(), new NutritionOrigin(
                    NutritionOrigin.Source.byOrdinal(entry.getValue().source()),
                    List.copyOf(entry.getValue().from())));
        }
        return out;
    }
}
