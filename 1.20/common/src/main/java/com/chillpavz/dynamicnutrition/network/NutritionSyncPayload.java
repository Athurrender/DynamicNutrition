package com.chillpavz.dynamicnutrition.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
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
{

    /**
     * One food on the wire.
     *
     * @param values one entry per nutrient in the header, same order, zeros included
     * @param source which pipeline stage answered, as an ordinal
     * @param from   the contributing ingredients, for a recipe-derived food only
     */
    public record Entry(List<Integer> values, int source, List<Item> from) {
    }

    /** The channel this travels on. A plain id on this band: the payload type object is newer. */
    public static final ResourceLocation ID =
            new ResourceLocation(Constants.MOD_ID, "nutrition_sync");

    // Hard caps on every length read off the wire, so a malformed or hostile packet cannot make the
    // client allocate without limit.
    private static final int MAX_NUTRIENTS = 256;
    private static final int MAX_FOODS = 65536;
    private static final int MAX_EFFECTS = 64;

    /**
     * Written by hand on this band: {@code StreamCodec} arrived at 1.20.5. The same fields in the
     * same order as the newer bands, with the same caps.
     */
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(nutrientNames.size());
        for (String name : nutrientNames) {
            buf.writeUtf(name);
        }
        buf.writeVarInt(entries.size());
        for (Map.Entry<Item, Entry> entry : entries.entrySet()) {
            buf.writeId(BuiltInRegistries.ITEM, entry.getKey());
            Entry value = entry.getValue();
            buf.writeVarInt(value.values().size());
            for (int v : value.values()) {
                buf.writeVarInt(v);
            }
            buf.writeVarInt(value.source());
            buf.writeVarInt(value.from().size());
            for (Item from : value.from()) {
                buf.writeId(BuiltInRegistries.ITEM, from);
            }
        }
        buf.writeBoolean(effectsEnabled);
        buf.writeVarInt(effectPercents.size());
        for (Map.Entry<String, Integer> entry : effectPercents.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public static NutritionSyncPayload read(FriendlyByteBuf buf) {
        int nameCount = capped(buf.readVarInt(), MAX_NUTRIENTS);
        List<String> names = new ArrayList<>(nameCount);
        for (int i = 0; i < nameCount; i++) {
            names.add(buf.readUtf());
        }
        int foodCount = capped(buf.readVarInt(), MAX_FOODS);
        Map<Item, Entry> entries = new HashMap<>();
        for (int i = 0; i < foodCount; i++) {
            Item item = buf.readById(BuiltInRegistries.ITEM);
            int valueCount = capped(buf.readVarInt(), MAX_NUTRIENTS);
            List<Integer> values = new ArrayList<>(valueCount);
            for (int v = 0; v < valueCount; v++) {
                values.add(buf.readVarInt());
            }
            int source = buf.readVarInt();
            int fromCount = capped(buf.readVarInt(), NutritionOrigin.MAX_FROM);
            List<Item> from = new ArrayList<>(fromCount);
            for (int f = 0; f < fromCount; f++) {
                from.add(buf.readById(BuiltInRegistries.ITEM));
            }
            if (item != null) {
                entries.put(item, new Entry(values, source, from));
            }
        }
        boolean enabled = buf.readBoolean();
        int effectCount = capped(buf.readVarInt(), MAX_EFFECTS);
        Map<String, Integer> percents = new HashMap<>();
        for (int i = 0; i < effectCount; i++) {
            percents.put(buf.readUtf(), buf.readVarInt());
        }
        return new NutritionSyncPayload(names, entries, enabled, percents);
    }

    private static int capped(int size, int max) {
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("list of " + size + " is over the cap of " + max);
        }
        return size;
    }

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
