package com.chillpavz.dynamicnutrition.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;

/**
 * One player's nutrient levels.
 *
 * <p>Values are floats internally so that decay is smooth and does not stall on rounding, and are
 * shown to the player as integers. Storage is keyed by nutrient NAME rather than by index, so a
 * datapack adding a sixth nutrient later cannot silently shift everyone's existing values along by
 * one, and an unknown name in a save is dropped rather than corrupting its neighbours.
 *
 * <p>This object lives on a data ATTACHMENT, on both loaders. That is a deliberate improvement on
 * the two shapes this is usually given: {@code @Unique} fields mixed onto the hunger manager, which
 * is loader-shaped and unshareable, and a world {@code SavedData} keyed by UUID string, which never
 * gets cleaned up and does not travel with the player.
 */
public final class PlayerNutrition {

    /** Ceiling for any one nutrient. */
    public static final float MAX = 100.0F;

    /** What a new player starts on: mid-range, so the first meal matters but nothing is urgent. */
    public static final float INITIAL = 50.0F;

    /**
     * How much of the drop in food-plus-saturation is charged to nutrition, before the per-nutrient
     * rate and the level factor. Tuned so a player who eats normally holds station.
     */
    public static final float DECAY_RATE = 0.55F;

    /**
     * Floor on the level factor below. Decay never stops entirely, but a nearly-empty nutrient
     * drains at a quarter of the rate of a full one.
     *
     * <p>This is the fix for the problem every linear-decay nutrition mod has: a player who comes
     * back after a week should not find all five bars flat at zero with no way to read what they
     * were short of. An exponential solves it too; this is the same idea in a form whose
     * behaviour is obvious from the two constants.
     */
    public static final float DECAY_FLOOR = 0.25F;

    /** Points lost from every nutrient on death, floored at {@link #INITIAL}. */
    public static final float DEATH_LOSS = 10.0F;

    /**
     * How many meals are stored, whatever the configured window is.
     *
     * <p>Deliberately larger than the default window so that RAISING the window in the config takes
     * effect on the history a player already has, rather than needing several meals to fill out.
     */
    public static final int MAX_REMEMBERED_MEALS = 32;

    public static final MapCodec<PlayerNutrition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                    .optionalFieldOf("values", Map.of())
                    .forGetter(PlayerNutrition::rawValues),
            Codec.FLOAT.optionalFieldOf("saved_food", 0.0F)
                    .forGetter(p -> p.savedFoodPoints),
            Codec.STRING.optionalFieldOf("last_status", NutrientStatus.SAFE.name())
                    .forGetter(p -> p.lastStatus.name()),
            Codec.INT.optionalFieldOf("sustained_ticks", 0)
                    .forGetter(p -> p.sustainedTicks),
            Codec.BOOL.optionalFieldOf("was_malnourished", false)
                    .forGetter(p -> p.wasMalnourished),
            Codec.STRING.listOf().optionalFieldOf("meals", List.of())
                    .forGetter(p -> p.meals),
            Codec.INT.optionalFieldOf("meal_revision", 0)
                    .forGetter(p -> p.mealRevision),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("effect_status", Map.of())
                    .forGetter(p -> new LinkedHashMap<>(p.effectStatus)),
            Codec.INT.optionalFieldOf("effect_format", 0)
                    .forGetter(p -> p.effectFormat)
    ).apply(i, PlayerNutrition::new));

    public static final Codec<PlayerNutrition> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerNutrition> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

    private final Map<String, Float> values = new HashMap<>();

    /**
     * The player's food-plus-saturation at the last tick we looked. Decay is driven by watching this
     * FALL, because {@code FoodData} exposes no exhaustion getter at 26.2 and reading exhaustion
     * directly would need an access widener for no real gain.
     */
    private float savedFoodPoints;

    /**
     * The status this player was last told about, persisted.
     *
     * <p>Stored as the enum NAME rather than its ordinal so that adding a fourth status later, which
     * v1.1 will, cannot silently re-label everybody's saved value.
     *
     * <p>It lives on the server's copy for a reason the umbrella records: in single player the
     * integrated server and the client share this object, so a client-side "did this change" test
     * is always false and the announcement never fires. The transition is therefore detected here,
     * server side, which behaves identically in single player and on a dedicated server.
     */
    private NutrientStatus lastStatus = NutrientStatus.SAFE;

    /** How long this player has held ON_TARGET without a break, in ticks. For an advancement. */
    private int sustainedTicks;

    /**
     * Whether this player has been malnourished since the last time they were on target.
     *
     * <p>A flag rather than a comparison of consecutive statuses, because the recovery it records
     * almost never happens in one step: a player climbs out through SAFE, so the moment they reach
     * ON_TARGET their previous status is SAFE and the fact that they were ever short is gone.
     */
    private boolean wasMalnourished;

    /**
     * The registry ids of this player's recent meals, oldest first.
     *
     * <p>Ids rather than {@code Item} references so the list survives a mod being removed: an id
     * nobody can resolve any more simply stops matching, which is correct and costs nothing.
     */
    private final List<String> meals = new ArrayList<>();

    /**
     * Bumped every time a meal is recorded.
     *
     * <p>This exists for the TOOLTIP. A food's displayed value depends on what the player has been
     * eating, so the per-item cache cannot be permanent; but a tooltip is rebuilt every frame while
     * hovered, so it must not be rebuilt per frame either. One integer compared against the
     * revision the cache was built at gives both.
     */
    private int mealRevision;

    /**
     * Per nutrient, the side of it whose effects this player currently holds: ON_TARGET or
     * MALNOURISHED by nutrient name, absent for neither.
     *
     * <p>This is the memory the hysteresis needs. Until 1.5.0 each nutrient's effect instance was
     * that memory; the effects are now one pair of display effects for all five, so the status
     * held has to be remembered here. It is SYNCED with the rest of the attachment, which is how
     * the client knows which effects are on (the hover list, the Blindness fog) without a packet
     * of its own. Optional in the codec, so an older save reads as "nothing held" and the first
     * second's check fills it in.
     */
    private final Map<String, String> effectStatus = new HashMap<>();

    /**
     * Which shape of effects this player's save was last brought up to. 0 is anything before 1.5,
     * including the unreleased builds that gave vanilla effects; see
     * {@code NutrientEffects.migrate}. Stored so that migration runs once per player, never again.
     */
    private int effectFormat;

    public PlayerNutrition() {
        this(Map.of(), 0.0F, NutrientStatus.SAFE.name(), 0, false, List.of(), 0, Map.of(), 0);
    }

    private PlayerNutrition(Map<String, Float> stored, float savedFoodPoints, String lastStatus,
                            int sustainedTicks, boolean wasMalnourished, List<String> meals,
                            int mealRevision, Map<String, String> effectStatus, int effectFormat) {
        this.effectStatus.putAll(effectStatus);
        this.effectFormat = effectFormat;
        this.savedFoodPoints = savedFoodPoints;
        this.lastStatus = statusByName(lastStatus);
        this.sustainedTicks = sustainedTicks;
        this.wasMalnourished = wasMalnourished;
        this.meals.addAll(meals);
        this.mealRevision = mealRevision;
        for (Nutrient nutrient : Nutrients.all()) {
            Float v = stored.get(nutrient.name());
            values.put(nutrient.name(), v != null ? clamp(v) : INITIAL);
        }
    }

    private Map<String, Float> rawValues() {
        return new LinkedHashMap<>(values);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(MAX, v));
    }

    /** An unknown name in a save reads as SAFE rather than failing the load. */
    private static NutrientStatus statusByName(String name) {
        for (NutrientStatus status : NutrientStatus.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return NutrientStatus.SAFE;
    }

    // ---------------------------------------------------------------- reading

    public float get(Nutrient nutrient) {
        Float v = values.get(nutrient.name());
        return v != null ? v : INITIAL;
    }

    /** The value a player sees. Rounding is done once, here, so the screen and the tooltip agree. */
    public int display(Nutrient nutrient) {
        return Math.round(get(nutrient));
    }

    // ---------------------------------------------------------------- writing

    /** @return true if the value actually moved, so callers can avoid a pointless sync */
    public boolean set(Nutrient nutrient, float value) {
        float clamped = clamp(value);
        Float old = values.put(nutrient.name(), clamped);
        return old == null || Math.abs(old - clamped) > 1.0e-4F;
    }

    public boolean add(Nutrient nutrient, float delta) {
        return set(nutrient, get(nutrient) + delta);
    }

    /**
     * Apply what a food is worth.
     *
     * @return true if anything changed
     */
    public boolean consume(NutritionValues food) {
        boolean changed = false;
        for (Map.Entry<Nutrient, Integer> entry : food.asMap().entrySet()) {
            changed |= add(entry.getKey(), entry.getValue());
        }
        return changed;
    }

    /**
     * Charge a drop in the player's food and saturation to their nutrient levels.
     *
     * @param foodPointsLost how far food-plus-saturation fell since the last check, always positive
     * @return true if anything changed
     */
    public boolean decay(float foodPointsLost) {
        if (foodPointsLost <= 0) {
            return false;
        }
        boolean changed = false;
        for (Nutrient nutrient : Nutrients.all()) {
            float current = get(nutrient);
            if (current <= 0) {
                continue;
            }
            float levelFactor = DECAY_FLOOR + (1.0F - DECAY_FLOOR) * (current / MAX);
            float loss = foodPointsLost * DECAY_RATE * nutrient.decayRate() * levelFactor;
            changed |= set(nutrient, current - loss);
        }
        return changed;
    }

    /** Death costs a flat amount, but never drops a player below where they started. */
    public boolean onDeath() {
        boolean changed = false;
        for (Nutrient nutrient : Nutrients.all()) {
            float current = get(nutrient);
            if (current > INITIAL) {
                changed |= set(nutrient, Math.max(INITIAL, current - DEATH_LOSS));
            }
        }
        savedFoodPoints = 0.0F;
        return changed;
    }

    // ---------------------------------------------------------------- decay bookkeeping

    /**
     * Work out how far food-plus-saturation has fallen and remember the new figure.
     *
     * @return the drop, or 0 if it rose or held. A RISE is not decay: it means the player ate, which
     *         is handled by the eat hook, so charging it here as well would double count.
     */
    public float takeFoodPointDrop(float currentFoodPoints) {
        float previous = savedFoodPoints;
        savedFoodPoints = currentFoodPoints;
        if (previous <= 0.0F) {
            // First look at this player, or straight after a death. Establish the baseline without
            // charging anything, or a fresh login would decay by the player's entire food bar.
            return 0.0F;
        }
        return Math.max(0.0F, previous - currentFoodPoints);
    }

    /**
     * A detached copy with the same values.
     *
     * <p><b>This exists because of a hard difference between the two loaders' attachment APIs.</b>
     * NeoForge's {@code setData} puts the value and then calls {@code syncData} unconditionally, so
     * re-setting the same instance is a valid way to say "this changed". <b>Fabric's
     * {@code setAttached} does {@code if (Objects.equals(old, new)) return;} before marking anything
     * changed</b>, so re-setting the same instance is a complete no-op and nothing ever syncs.
     *
     * <p>Read out of Fabric's own bytecode after the first in-game test showed the client stuck on
     * the initial value while the server was correct. Since this class deliberately does NOT
     * override {@code equals}, a copy is never equal to the original, so setting a copy marks the
     * attachment changed on both loaders. Both {@code markDirty} implementations use it, so the two
     * cannot drift.
     */
    public PlayerNutrition copy() {
        PlayerNutrition out = new PlayerNutrition();
        out.copyFrom(this);
        return out;
    }

    /**
     * Take every value from another instance, in place.
     *
     * <p>Needed where the object itself cannot be replaced: a Forge capability is created with the
     * player and handed out by reference, so loading a save and copying across a death both have
     * to fill the existing instance rather than swap it. {@link #copy()} delegates here so there is
     * exactly ONE list of fields to keep up to date; a field added to the class and forgotten here
     * would otherwise be dropped on death on one loader and survive on the others.
     */
    public void copyFrom(PlayerNutrition other) {
        this.values.clear();
        this.values.putAll(other.values);
        this.savedFoodPoints = other.savedFoodPoints;
        this.lastStatus = other.lastStatus;
        this.sustainedTicks = other.sustainedTicks;
        this.wasMalnourished = other.wasMalnourished;
        this.meals.clear();
        this.meals.addAll(other.meals);
        this.mealRevision = other.mealRevision;
        this.effectStatus.clear();
        this.effectStatus.putAll(other.effectStatus);
        this.effectFormat = other.effectFormat;
    }

    // ---------------------------------------------------------------- status

    /** The status this player was last told about. See the field's own note on why it is stored. */
    public NutrientStatus lastStatus() {
        return lastStatus;
    }

    public void setLastStatus(NutrientStatus status) {
        this.lastStatus = status;
    }

    /** This player's recent meals, oldest first. Never null, and safe to read on the client. */
    public List<String> meals() {
        return meals;
    }

    /** See {@link #mealRevision}. */
    public int mealRevision() {
        return mealRevision;
    }

    /**
     * Remember a meal, trimming the history to the configured window.
     *
     * <p>Trimmed to a generous ceiling rather than to the window itself, so that raising the window
     * in the config takes effect on the history the player already has instead of needing several
     * meals to fill out.
     */
    public void rememberMeal(String key) {
        if (key == null) {
            return;
        }
        meals.add(key);
        while (meals.size() > MAX_REMEMBERED_MEALS) {
            meals.remove(0);
        }
        mealRevision++;
    }

    /**
     * Forget every remembered meal.
     *
     * <p>Bumps the revision as a meal would, because the tooltip cache is keyed on it and the
     * displayed values change the moment the history does.
     */
    public void forgetMeals() {
        meals.clear();
        mealRevision++;
    }

    /**
     * The side of this nutrient whose effects are held: ON_TARGET, MALNOURISHED, or null for
     * neither. An unknown name in a save reads as null.
     */
    public NutrientStatus heldStatus(Nutrient nutrient) {
        String name = effectStatus.get(nutrient.name());
        if (NutrientStatus.ON_TARGET.name().equals(name)) {
            return NutrientStatus.ON_TARGET;
        }
        return NutrientStatus.MALNOURISHED.name().equals(name) ? NutrientStatus.MALNOURISHED : null;
    }

    /**
     * Remember the side held for one nutrient. SAFE and null both mean neither.
     *
     * @return true if it changed, so the caller marks the attachment dirty and the client hears
     */
    public boolean setHeldStatus(Nutrient nutrient, NutrientStatus status) {
        String wanted = status == NutrientStatus.ON_TARGET || status == NutrientStatus.MALNOURISHED
                ? status.name() : null;
        String old = wanted == null ? effectStatus.remove(nutrient.name())
                : effectStatus.put(nutrient.name(), wanted);
        return !java.util.Objects.equals(old, wanted);
    }

    public int effectFormat() {
        return effectFormat;
    }

    public void setEffectFormat(int format) {
        this.effectFormat = format;
    }

    public int sustainedTicks() {
        return sustainedTicks;
    }

    /**
     * Advance or reset the on-target streak.
     *
     * @param ticks how long has passed since the last call
     * @return true if the value changed
     */
    public boolean trackSustained(NutrientStatus status, int ticks) {
        int before = sustainedTicks;
        sustainedTicks = status == NutrientStatus.ON_TARGET ? sustainedTicks + ticks : 0;
        return sustainedTicks != before;
    }

    public boolean wasMalnourished() {
        return wasMalnourished;
    }

    public void setWasMalnourished(boolean value) {
        this.wasMalnourished = value;
    }

    /** Every nutrient currently below the malnourished line, in display order. */
    public java.util.List<Nutrient> deficient() {
        java.util.List<Nutrient> out = new java.util.ArrayList<>();
        for (Nutrient nutrient : Nutrients.all()) {
            if (status(nutrient) == NutrientStatus.MALNOURISHED) {
                out.add(nutrient);
            }
        }
        return out;
    }

    /** How this player is doing on one nutrient. */
    public NutrientStatus status(Nutrient nutrient) {
        return NutrientStatus.of(nutrient, get(nutrient));
    }

    /**
     * The player's overall status: the WORST of the five.
     *
     * <p>Worst rather than average on purpose. A player who is perfect on four nutrients and empty
     * on the fifth has a problem, and averaging would hide exactly the case the mod exists to
     * surface.
     */
    public NutrientStatus overallStatus() {
        NutrientStatus worst = NutrientStatus.ON_TARGET;
        for (Nutrient nutrient : Nutrients.all()) {
            // The last announced status is passed in so the answer is STICKY. Every nutrient is a
            // sawtooth between meals, so a line sitting inside that swing would otherwise be
            // crossed twice a cycle and the player would be told their diet changed once a day
            // while eating exactly the same food. See NutrientStatus.HYSTERESIS.
            NutrientStatus s = NutrientStatus.of(nutrient, get(nutrient), lastStatus);
            if (s.ordinal() < worst.ordinal()) {
                worst = s;
            }
        }
        return worst;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Nutrient nutrient : Nutrients.all()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(nutrient.name()).append('=').append(display(nutrient));
        }
        return sb.toString();
    }
}
