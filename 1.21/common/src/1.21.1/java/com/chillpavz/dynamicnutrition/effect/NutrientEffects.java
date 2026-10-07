package com.chillpavz.dynamicnutrition.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.NutrientStatus;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * One effect per nutrient above its green line, and one below its red line, each at a configurable
 * fraction of the vanilla effect it is named after.
 *
 * <pre>
 *   carbohydrates   Speed          or  Slowness        the fast-burning fuel
 *   protein         Strength       or  Weakness        the muscle that does the work
 *   fat             Resistance     or  Hunger          the reserve and the padding
 *   vitamins        Regeneration   or  Blindness       healing and the senses
 *   minerals        Haste          or  Mining Fatigue  bone, grip, oxygen to the muscles
 * </pre>
 *
 * <h2>A fraction of each vanilla effect, done by hand</h2>
 * A vanilla effect only comes in whole levels, and level I of Resistance or Regeneration held
 * permanently is a lot: a varied diet keeps all five above their green lines all the time. So each
 * of these does the vanilla effect's own job at {@code percent / 100} of its level I strength. 100
 * is exactly level I, the default 50 is half, and 0 switches that one effect off entirely.
 * <ul>
 *   <li>Speed, Slowness, Strength, Weakness: the vanilla attribute modifier, scaled.</li>
 *   <li>Haste and Mining Fatigue: vanilla multiplies mining speed by 1.2 and by 0.3 per level, and
 *       {@code BLOCK_BREAK_SPEED} multiplies the same value, so a modifier on it reproduces both
 *       exactly at 100. Their attack speed modifier is scaled as well.</li>
 *   <li>Regeneration: 1 health per 50 ticks at level I, so 0.4 a second, scaled.</li>
 *   <li>Hunger: 0.005 exhaustion a tick at level I, so 0.1 a second, scaled.</li>
 *   <li>Resistance: 20% less damage at level I, applied where vanilla applies it; see
 *       {@link #resist}.</li>
 *   <li>Blindness: the fog closes in towards vanilla's five blocks; see the client's fog
 *       environment. It only fogs the view: sprinting and critical hits are unaffected.</li>
 * </ul>
 * The attribute modifiers are applied by hand, TRANSIENT, and re-checked every second, so a change
 * in the config reaches a player within a second and nothing is ever written into a save.
 *
 * <h2>Shown as two effects, not ten</h2>
 * None of the ten is an effect instance. The player sees Well Nourished while any buff is on and
 * Malnourished while any debuff is on, both at once when a diet is good in one place and short in
 * another, and hovering either in the inventory lists what it stands for. Which side of each
 * nutrient is held is remembered in the attachment ({@code PlayerNutrition.heldStatus}), which is
 * also how the client knows. A potion or beacon of the same vanilla kind therefore shows as its own
 * icon, and nothing of this mod's ever doubles it.
 *
 * <h2>Milk does not clear them</h2>
 * The cure for a deficiency is food. The mechanics are not effects, so milk never reaches them, and
 * each loader refuses the removal of the two display effects unless this class is doing it, through
 * {@link #mayRemove}. A refused removal can still have told the CLIENT they were gone (Fabric API
 * sends the remove packet for everything {@code removeAllEffects} copied, including what it then
 * put back), so the player is sent both again on their next tick; see {@link #refused}.
 */
public final class NutrientEffects {

    /** How a spec does its job. */
    public enum Kind { ATTRIBUTES, REGENERATION, HUNGER, RESISTANCE, BLINDNESS }

    /** One vanilla attribute modifier, at its full, level I amount. */
    public record Modifier(Holder<Attribute> attribute, double full, AttributeModifier.Operation operation) {
    }

    /**
     * One nutrient effect: a mechanic, not an effect instance.
     *
     * @param id           its config id, and the vanilla effect whose name it is shown under
     * @param nutrient     the nutrient it belongs to
     * @param above        true for the effect given above the green line, false for below the red
     * @param legacyEffect the effect 1.5.0 registered under this id. Kept registered only so that a
     *                     save naming it still loads; see {@link #tidy}
     */
    public record Spec(String id, Nutrient nutrient, boolean above, NutritionMobEffect legacyEffect,
                       Kind kind, List<Modifier> modifiers) {

        /** The id 1.5.0 registered this effect under. */
        public ResourceKey<MobEffect> legacyKey() {
            return ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, id));
        }

        /** Vanilla's name for the effect this imitates, in the player's language. */
        public MutableComponent displayName() {
            return Component.translatable("effect.minecraft." + id);
        }
    }

    /** Level I in vanilla: health a second from Regeneration, exhaustion a second from Hunger. */
    public static final float REGENERATION_PER_SECOND = 20.0F / 50.0F;
    public static final float HUNGER_EXHAUSTION_PER_SECOND = 0.005F * 20.0F;
    /** Level I Resistance: vanilla takes 5 of 25 parts of the damage away. */
    public static final float RESISTANCE_AT_FULL = 5.0F / 25.0F;

    public static final List<Spec> ALL = List.of(
            spec("speed", Nutrients.CARBOHYDRATES, true, 3402751, Kind.ATTRIBUTES,
                    new Modifier(Attributes.MOVEMENT_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)),
            spec("slowness", Nutrients.CARBOHYDRATES, false, 9154528, Kind.ATTRIBUTES,
                    new Modifier(Attributes.MOVEMENT_SPEED, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)),
            spec("strength", Nutrients.PROTEIN, true, 16762624, Kind.ATTRIBUTES,
                    new Modifier(Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE)),
            spec("weakness", Nutrients.PROTEIN, false, 4738376, Kind.ATTRIBUTES,
                    new Modifier(Attributes.ATTACK_DAMAGE, -4.0, AttributeModifier.Operation.ADD_VALUE)),
            spec("resistance", Nutrients.FAT, true, 9520880, Kind.RESISTANCE),
            spec("hunger", Nutrients.FAT, false, 5797459, Kind.HUNGER),
            spec("regeneration", Nutrients.VITAMINS, true, 13458603, Kind.REGENERATION),
            spec("blindness", Nutrients.VITAMINS, false, 2039587, Kind.BLINDNESS),
            spec("haste", Nutrients.MINERALS, true, 14270531, Kind.ATTRIBUTES,
                    new Modifier(Attributes.BLOCK_BREAK_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Modifier(Attributes.ATTACK_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)),
            spec("mining_fatigue", Nutrients.MINERALS, false, 4866583, Kind.ATTRIBUTES,
                    new Modifier(Attributes.BLOCK_BREAK_SPEED, -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Modifier(Attributes.ATTACK_SPEED, -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));

    /**
     * The two effects a player actually sees: Well Nourished while any nutrient buff is on,
     * Malnourished while any debuff is on. Both ids are 1.0.0's, so a 1.0.0 save that names them
     * loads straight into the new meaning. Neither carries an attribute modifier of its own.
     */
    public static final ResourceKey<MobEffect> DISPLAY_WELL_NOURISHED = ResourceKey.create(Registries.MOB_EFFECT, id("well_nourished"));
    public static final ResourceKey<MobEffect> DISPLAY_MALNOURISHED = ResourceKey.create(Registries.MOB_EFFECT, id("malnourished"));
    private static final MobEffect WELL_NOURISHED_EFFECT = new NutritionMobEffect(MobEffectCategory.BENEFICIAL, 0x63B23A);
    private static final MobEffect MALNOURISHED_EFFECT = new NutritionMobEffect(MobEffectCategory.HARMFUL, 0xB23A3A);

    /**
     * What the unreleased test builds before 1.5.0 put on players: VANILLA effects of these kinds,
     * level I, infinite, ambient, no particles. 1.5.0 never took them off, so a test world that ran
     * those builds showed each one twice. Removed ONCE per player by {@link #migrate}, and only in exactly
     * that shape: a potion is never infinite, a beacon shows particles and runs out, and
     * {@code /effect give} is never ambient.
     */
    private static final List<Holder<MobEffect>> TEST_BUILD_VANILLA = List.of(
            MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DAMAGE_BOOST, MobEffects.WEAKNESS,
            MobEffects.DAMAGE_RESISTANCE, MobEffects.HUNGER, MobEffects.REGENERATION, MobEffects.DARKNESS,
            MobEffects.DIG_SPEED, MobEffects.DIG_SLOWDOWN);

    /** The effect shape a player's save is brought up to. See {@code PlayerNutrition.effectFormat}. */
    public static final int EFFECT_FORMAT = 2;

    private static volatile Holder<MobEffect> wellNourished;
    private static volatile Holder<MobEffect> malnourished;
    private static final Map<String, Holder<MobEffect>> LEGACY = new HashMap<>();

    /** Players whose client may have been told a display effect was removed when it was not. */
    private static final Set<ServerPlayer> RESEND = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean resendFailureLogged;

    /** True only while this class is taking an effect off. See {@link #mayRemove}. */
    private static boolean removingOwn;

    private NutrientEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    private static Spec spec(String id, Nutrient nutrient, boolean above, int color, Kind kind,
                             Modifier... modifiers) {
        MobEffectCategory category = above ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL;
        return new Spec(id, nutrient, above, new NutritionMobEffect(category, color, id), kind,
                List.of(modifiers));
    }

    // ---------------------------------------------------------------- registration

    /** One effect to register, for the loaders. */
    public record Registration(ResourceKey<MobEffect> key, MobEffect effect) {
    }

    /** Everything each loader must register: the two display effects, then the ten legacy ids. */
    public static List<Registration> registrations() {
        List<Registration> out = new ArrayList<>();
        out.add(new Registration(DISPLAY_WELL_NOURISHED, WELL_NOURISHED_EFFECT));
        out.add(new Registration(DISPLAY_MALNOURISHED, MALNOURISHED_EFFECT));
        for (Spec spec : ALL) {
            out.add(new Registration(spec.legacyKey(), spec.legacyEffect()));
        }
        return out;
    }

    /** The two display effects' objects, for the NeoForge client extension. */
    public static List<MobEffect> displayEffects() {
        return List.of(WELL_NOURISHED_EFFECT, MALNOURISHED_EFFECT);
    }

    /**
     * Each loader hands the registered holders back. A {@code MobEffectInstance} needs a holder,
     * which only exists once registration has run, so common code cannot make its own.
     */
    public static void bind(Map<ResourceKey<MobEffect>, Holder<MobEffect>> holders) {
        wellNourished = holders.get(DISPLAY_WELL_NOURISHED);
        malnourished = holders.get(DISPLAY_MALNOURISHED);
        synchronized (LEGACY) {
            for (Spec spec : ALL) {
                Holder<MobEffect> holder = holders.get(spec.legacyKey());
                if (holder != null) {
                    LEGACY.put(spec.id(), holder);
                }
            }
        }
    }

    /** Well Nourished for true, Malnourished for false; null before registration. */
    public static Holder<MobEffect> display(boolean buffs) {
        return buffs ? wellNourished : malnourished;
    }

    public static Spec byId(String id) {
        for (Spec spec : ALL) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }

    /** The effect a nutrient gives above its green line, or below its red one; null if none. */
    public static Spec forNutrient(Nutrient nutrient, boolean above) {
        for (Spec spec : ALL) {
            if (spec.nutrient() == nutrient && spec.above() == above) {
                return spec;
            }
        }
        return null;
    }

    /** Whether an effect belongs to this mod at all, display or legacy. */
    public static boolean isOurs(Holder<MobEffect> effect) {
        return effect != null && effect.unwrapKey()
                .map(key -> Constants.MOD_ID.equals(key.location().getNamespace()))
                .orElse(false);
    }

    /** Whether an effect is Well Nourished or Malnourished. */
    public static boolean isDisplay(Holder<MobEffect> effect) {
        return effect != null && (effect.is(DISPLAY_WELL_NOURISHED) || effect.is(DISPLAY_MALNOURISHED));
    }

    /**
     * Whether a removal the loader is about to perform may go ahead. Anything but the two display
     * effects, yes: the legacy ids are on their way off anyway. Those two only when this class asked
     * for it: milk, a totem or {@code /effect clear} would otherwise take them off, and the next
     * second's check would put them straight back.
     */
    public static boolean mayRemove(Holder<MobEffect> effect) {
        return removingOwn || !isDisplay(effect);
    }

    /**
     * A loader refused a removal for this entity. The server still has the effect, but the client
     * may have been told otherwise: Fabric API's early-remove check puts a refused effect back into
     * the map AFTER vanilla has copied it for {@code onEffectsRemoved}, which sends the remove packet
     * regardless. So the player's display effects are sent again on their next tick. On NeoForge
     * the packet is never sent and the resend is merely redundant.
     */
    public static void refused(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            synchronized (RESEND) {
                RESEND.add(player);
            }
        }
    }

    /** Every tick, per player: send the display effects again after a refused removal. */
    public static void resendIfRefused(ServerPlayer player) {
        synchronized (RESEND) {
            if (RESEND.isEmpty() || !RESEND.remove(player)) {
                return;
            }
        }
        try {
            for (Holder<MobEffect> holder : List.of(wellNourished, malnourished)) {
                MobEffectInstance instance = player.getEffect(holder);
                if (instance != null) {
                    player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(),
                            instance, false));
                }
            }
        } catch (Throwable t) {
            if (!resendFailureLogged) {
                resendFailureLogged = true;
                Constants.LOG.warn("Could not resend the nutrition effects after a refused removal; "
                        + "they show again when the diet next changes: {}", t.toString());
            }
        }
    }

    /** The server's strength for a spec, 0 to 1. 0 when the effects are switched off. */
    public static float fraction(Spec spec) {
        if (!NutritionConfig.effectsEnabled) {
            return 0.0F;
        }
        return NutritionConfig.effectPercent(spec.id()) / 100.0F;
    }

    /**
     * Whether a spec is on, given the side its nutrient holds and the spec's strength. The ONE rule:
     * the server applies the mechanics by it, and the client lists them and draws the fog by it.
     */
    public static boolean isActive(Spec spec, NutrientStatus held, float fraction) {
        return fraction > 0
                && held == (spec.above() ? NutrientStatus.ON_TARGET : NutrientStatus.MALNOURISHED);
    }

    /**
     * What a display effect stands for right now, each effect in its nutrient's text colour, for
     * the inventory hover. Empty for any other effect.
     *
     * @param held     the side held per nutrient, as the viewer's attachment says
     * @param strength the strength per spec, as the server last said
     */
    public static List<Component> describe(Holder<MobEffect> effect,
                                           Function<Nutrient, NutrientStatus> held,
                                           ToDoubleFunction<Spec> strength) {
        List<Component> out = new ArrayList<>();
        if (!isDisplay(effect)) {
            return out;
        }
        boolean buffs = effect.is(DISPLAY_WELL_NOURISHED);
        for (Nutrient nutrient : Nutrients.all()) {
            Spec spec = forNutrient(nutrient, buffs);
            if (spec != null && isActive(spec, held.apply(nutrient), (float) strength.applyAsDouble(spec))) {
                out.add(spec.displayName().withColor(nutrient.textColor()));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- the per-second check

    /**
     * Bring every nutrient's effects into line with its value, and run what they do for one second.
     *
     * <p>Each nutrient's side is sticky ({@link NutrientStatus#HYSTERESIS}), and the side held is
     * the memory, stored in the attachment. The two display effects are then made to match: present
     * while anything on their side is on, gone otherwise. Idempotent: it adds only what is missing
     * and removes only what it put there.
     *
     * @return true if the side held changed for any nutrient, so the caller marks the attachment
     *         dirty and the client hears about it
     */
    public static boolean apply(ServerPlayer player, PlayerNutrition nutrition) {
        boolean changed = false;
        boolean buff = false;
        boolean debuff = false;
        for (Nutrient nutrient : Nutrients.all()) {
            NutrientStatus status = NutrientStatus.of(nutrient, nutrition.get(nutrient),
                    nutrition.heldStatus(nutrient));
            changed |= nutrition.setHeldStatus(nutrient, status);
            NutrientStatus held = nutrition.heldStatus(nutrient);
            for (boolean above : new boolean[] {true, false}) {
                Spec spec = forNutrient(nutrient, above);
                if (spec == null) {
                    continue;
                }
                float fraction = fraction(spec);
                boolean active = isActive(spec, held, fraction);
                run(player, spec, active ? fraction : 0.0F);
                buff |= active && above;
                debuff |= active && !above;
            }
        }
        present(player, wellNourished, buff);
        present(player, malnourished, debuff);
        return changed;
    }

    /**
     * Take everything off: every modifier, both display effects and the sides held. For when the
     * effects are switched off.
     *
     * @return true if the attachment changed
     */
    public static boolean clear(ServerPlayer player, PlayerNutrition nutrition) {
        boolean changed = false;
        for (Spec spec : ALL) {
            run(player, spec, 0.0F);
        }
        for (Nutrient nutrient : Nutrients.all()) {
            changed |= nutrition.setHeldStatus(nutrient, null);
        }
        present(player, wellNourished, false);
        present(player, malnourished, false);
        return changed;
    }

    /**
     * Housekeeping, every second in EVERY game mode, because a creative player can carry either
     * leftover as well: the ten 1.5.0 effect ids, stripped on sight, and the test builds' vanilla
     * effects, stripped once.
     *
     * @return true if the attachment changed
     */
    public static boolean tidy(ServerPlayer player, PlayerNutrition nutrition) {
        stripLegacy(player);
        return migrate(player, nutrition);
    }

    private static void present(ServerPlayer player, Holder<MobEffect> effect, boolean wanted) {
        if (effect == null) {
            return;
        }
        boolean has = player.hasEffect(effect);
        if (wanted && !has) {
            // Infinite, level I, AMBIENT (the pale beacon frame, a standing condition rather than a
            // potion), NO particles, icon shown.
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0,
                    true, false, true));
        } else if (!wanted && has) {
            removeOwn(player, effect);
        }
    }

    private static void removeOwn(ServerPlayer player, Holder<MobEffect> effect) {
        removingOwn = true;
        try {
            player.removeEffect(effect);
        } finally {
            removingOwn = false;
        }
    }

    /**
     * The ten 1.5.0 effect ids: still REGISTERED, because a world saved with one active names it
     * and an unknown id in a save can cost that player every other active effect on load. Never
     * given, and taken off on sight.
     */
    private static void stripLegacy(ServerPlayer player) {
        List<Holder<MobEffect>> legacy;
        synchronized (LEGACY) {
            legacy = List.copyOf(LEGACY.values());
        }
        for (Holder<MobEffect> holder : legacy) {
            if (player.hasEffect(holder)) {
                removeOwn(player, holder);
            }
        }
    }

    /** Exactly what those test builds applied: level I, infinite, ambient, no particles. */
    public static boolean isTestBuildShape(MobEffectInstance instance) {
        return instance.getAmplifier() == 0 && instance.isInfiniteDuration()
                && instance.isAmbient() && !instance.isVisible();
    }

    /**
     * Once per player: take off any vanilla effect in exactly the test builds' shape, then record
     * that it is done, so an effect another mod later gives in that shape is never touched.
     */
    private static boolean migrate(ServerPlayer player, PlayerNutrition nutrition) {
        if (nutrition.effectFormat() >= EFFECT_FORMAT) {
            return false;
        }
        int removed = 0;
        for (Holder<MobEffect> vanilla : TEST_BUILD_VANILLA) {
            MobEffectInstance instance = player.getEffect(vanilla);
            if (instance != null && isTestBuildShape(instance)) {
                removeOwn(player, vanilla);
                removed++;
            }
        }
        if (removed > 0) {
            Constants.LOG.info("Removed {} leftover effects of an earlier test build from {}",
                    removed, player.getName().getString());
        }
        nutrition.setEffectFormat(EFFECT_FORMAT);
        return true;
    }

    /** One second of what a spec does at this strength; 0 takes its modifiers off. */
    private static void run(ServerPlayer player, Spec spec, float fraction) {
        for (int i = 0; i < spec.modifiers().size(); i++) {
            Modifier modifier = spec.modifiers().get(i);
            setModifier(player, modifier, id("effect." + spec.id() + "." + i),
                    modifier.full() * fraction);
        }
        if (fraction <= 0) {
            return;
        }
        if (spec.kind() == Kind.REGENERATION && player.getHealth() < player.getMaxHealth()) {
            player.heal(REGENERATION_PER_SECOND * fraction);
        } else if (spec.kind() == Kind.HUNGER) {
            player.causeFoodExhaustion(HUNGER_EXHAUSTION_PER_SECOND * fraction);
        }
    }

    /**
     * Put one transient modifier at exactly this amount, or remove it at zero, and touch nothing
     * when it is already right, so a steady diet sends no attribute update at all.
     */
    private static void setModifier(ServerPlayer player, Modifier modifier, ResourceLocation id,
                                    double amount) {
        AttributeInstance attribute = player.getAttribute(modifier.attribute());
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (amount == 0.0) {
            if (existing != null) {
                attribute.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.amount() == amount) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, modifier.operation()));
    }

    // ---------------------------------------------------------------- Resistance

    /**
     * Resistance, applied just after the point where vanilla applies its own. Honours the same two
     * damage tags vanilla does. Called from Fabric's mixin on {@code getDamageAfterMagicAbsorb} and
     * from NeoForge's {@code LivingDamageEvent.Pre}; never throws, because it is on every hit taken.
     */
    public static float resist(LivingEntity entity, DamageSource source, float amount) {
        try {
            if (amount <= 0 || !(entity instanceof ServerPlayer player)
                    || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                    || source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                return amount;
            }
            Spec spec = byId("resistance");
            if (spec == null) {
                return amount;
            }
            float fraction = fraction(spec);
            if (!isActive(spec, Services.STORAGE.get(player).heldStatus(spec.nutrient()), fraction)) {
                return amount;
            }
            return amount * (1.0F - RESISTANCE_AT_FULL * fraction);
        } catch (Throwable t) {
            return amount;
        }
    }
}
