package com.chillpavz.dynamicnutrition.nutrition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What one food is worth, per nutrient. Immutable, and empty is a legitimate value meaning
 * "this item feeds nothing".
 *
 * <p>Only nutrients with a positive value are stored, so {@link #nutrients()} is the food's
 * nutrient SET and {@link #isEmpty()} answers "did anything resolve".
 */
public final class NutritionValues {

    public static final NutritionValues EMPTY = new NutritionValues(Map.of());

    private final Map<Nutrient, Integer> values;

    private NutritionValues(Map<Nutrient, Integer> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static NutritionValues of(Map<Nutrient, Integer> values) {
        Map<Nutrient, Integer> copy = new LinkedHashMap<>();
        for (Nutrient n : Nutrients.all()) {
            Integer v = values.get(n);
            if (v != null && v > 0) {
                copy.put(n, v);
            }
        }
        return copy.isEmpty() ? EMPTY : new NutritionValues(copy);
    }

    /**
     * Spread a total evenly across a set of nutrients.
     *
     * <p>This is how every stage except the explicit table produces its numbers: the SET comes from
     * tags, from the recipe graph or from the food-tag heuristic, and the MAGNITUDE comes from the
     * food's own nutrition and saturation. The explicit table bypasses this entirely, which is why
     * vanilla food gets real per-nutrient values and a modded food gets a reasonable split.
     */
    public static NutritionValues spread(Set<Nutrient> nutrients, float total) {
        if (nutrients.isEmpty() || total <= 0) {
            return EMPTY;
        }
        int each = Math.round(total / nutrients.size());
        if (each <= 0) {
            return EMPTY;
        }
        Map<Nutrient, Integer> out = new LinkedHashMap<>();
        for (Nutrient n : Nutrients.all()) {
            if (nutrients.contains(n)) {
                out.put(n, each);
            }
        }
        return new NutritionValues(out);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int get(Nutrient nutrient) {
        return values.getOrDefault(nutrient, 0);
    }

    /** The nutrients this food actually provides. */
    public Set<Nutrient> nutrients() {
        return values.keySet();
    }

    public Map<Nutrient, Integer> asMap() {
        return values;
    }

    public int total() {
        int sum = 0;
        for (int v : values.values()) {
            sum += v;
        }
        return sum;
    }

    /** Scaled copy, used for the cooking bonus. Values are re-rounded, never truncated. */
    public NutritionValues scaled(float factor) {
        if (isEmpty() || factor == 1.0F) {
            return this;
        }
        Map<Nutrient, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Nutrient, Integer> e : values.entrySet()) {
            int v = Math.round(e.getValue() * factor);
            if (v > 0) {
                out.put(e.getKey(), v);
            }
        }
        return out.isEmpty() ? EMPTY : new NutritionValues(out);
    }

    /** Every value clamped to at most {@code cap}. */
    public NutritionValues capped(int cap) {
        Map<Nutrient, Integer> out = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<Nutrient, Integer> e : values.entrySet()) {
            int v = Math.min(e.getValue(), cap);
            changed |= v != e.getValue();
            out.put(e.getKey(), v);
        }
        return changed ? new NutritionValues(out) : this;
    }

    @Override
    public String toString() {
        if (values.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Nutrient, Integer> e : values.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
