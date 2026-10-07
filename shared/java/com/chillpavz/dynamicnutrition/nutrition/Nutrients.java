package com.chillpavz.dynamicnutrition.nutrition;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The set of tracked nutrients.
 *
 * <p>The five below are the mod's identity and ship built in. They are deliberately reached through
 * {@link #byName} rather than as public constants used in switches, because the plan is for a
 * datapack to be able to declare a sixth; when that lands, only this class changes.
 */
public final class Nutrients {

    // The bar colours are the five canonical hues at full saturation, each mapped to the category
    // it fits best: orange for bread and wheat, red for meat, yellow for butter and oil, green for
    // vegetables, blue for water and salts. The text colours are the same five lightened for a dark
    // tooltip; see Nutrient's own note on why there are two.
    //
    // THE TWO THRESHOLDS DIFFER PER NUTRIENT, AND THE REASON IS THE BODY, NOT THE BALANCE SHEET.
    // The band between them is "adequate but not rewarded": its WIDTH is how much tolerance the
    // real nutrient has, and its POSITION is how much of it the body actually demands. Every value
    // below is argued from how long a human can go without, which is the one thing a nutrition mod
    // should be opinionated about.
    //
    // The deliberate consequence is that the five are NOT equally hard. Fat and minerals are easy,
    // because in the real world their deficiencies are rare; protein and vitamins are demanding,
    // because in the real world they are the classic ones. Reachability is what is checked instead
    // of fairness, in nutritiondata/check_balance.py.

    /**
     * Glycogen runs out in about a day, so this swings harder than anything else and a low reading
     * is normal rather than alarming. It also has the widest tolerance of the five: the body makes
     * glucose when it has to, and real diets run from very low carbohydrate to very high without
     * anyone being ill. Low floor, and the target sits under the daily trough on purpose.
     */
    public static final Nutrient CARBOHYDRATES =
            new Nutrient("carbohydrates", 0, 1.30F, 22.0F, 64.0F, 0xCC6600, 0xFF9A3C);

    /**
     * The only one of the five with NO storage pool at all. What is not eaten is taken out of
     * muscle, and it is needed in bulk rather than in traces, so it has the highest floor and a high
     * bar for the reward.
     */
    public static final Nutrient PROTEIN =
            new Nutrient("protein", 1, 0.90F, 35.0F, 75.0F, 0xB20000, 0xFF5555);

    /**
     * Weeks to months of reserve, and the genuinely essential fatty acids are needed in tiny
     * amounts. Real fat deficiency is close to unheard of in anyone eating at all, so this has the
     * lowest floor by a distance and the easiest reward.
     */
    public static final Nutrient FAT =
            new Nutrient("fat", 2, 0.60F, 8.0F, 45.0F, 0xF2F200, 0xFFFF55);

    /**
     * The deficiency diseases with names: scurvy, beriberi, pellagra. Water-soluble stores empty in
     * weeks and the consequences are sharp, so a high floor; and the surplus is excreted rather than
     * banked, so staying up there takes a varied diet every day rather than one big meal.
     */
    public static final Nutrient VITAMINS =
            new Nutrient("vitamins", 3, 1.00F, 30.0F, 78.0F, 0x00E500, 0x55FF55);

    /**
     * Bone and ferritin hold months of calcium and iron, so the body rides out a shortage that would
     * already be showing in protein or vitamins. Low floor, moderate bar, wide band.
     */
    public static final Nutrient MINERALS =
            new Nutrient("minerals", 4, 0.80F, 12.0F, 58.0F, 0x0051F2, 0x55AAFF);

    private static final List<Nutrient> ALL =
            List.of(CARBOHYDRATES, PROTEIN, FAT, VITAMINS, MINERALS);

    private static final Map<String, Nutrient> BY_NAME =
            ALL.stream().collect(Collectors.toUnmodifiableMap(Nutrient::name, Function.identity()));

    private Nutrients() {
    }

    /** Every nutrient, in display order. */
    public static List<Nutrient> all() {
        return ALL;
    }

    public static int count() {
        return ALL.size();
    }

    /** The nutrient with this name, or null. Callers must handle null: a datapack can name anything. */
    public static Nutrient byName(String name) {
        return BY_NAME.get(name);
    }
}
