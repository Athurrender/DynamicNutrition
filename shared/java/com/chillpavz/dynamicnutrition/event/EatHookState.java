package com.chillpavz.dynamicnutrition.event;

import com.chillpavz.dynamicnutrition.Constants;

/**
 * Whether the Fabric eat hook is alive.
 *
 * <p><b>This class exists because a MIXIN CLASS CANNOT BE TOUCHED BY ORDINARY CODE.</b> The first
 * attempt put the flag and its getter on the mixin itself and read it from the mod initialiser,
 * which failed twice over in one launch:
 * <ul>
 *   <li>a mixin may not contain a non-private static method, so the whole mixin was REFUSED with
 *       {@code InvalidMixinException: contains non-private static method hasApplied()Z} and the eat
 *       hook silently never applied;</li>
 *   <li>and merely referencing the mixin class from the initialiser threw
 *       {@code IllegalClassLoadError: Mixin is defined in ... and cannot be referenced directly},
 *       which killed the server thread.</li>
 * </ul>
 * So the diagnostic meant to catch a silent mixin failure both caused one and crashed the game. The
 * rule it teaches is absolute: <b>a mixin talks to the mod through an ordinary class, never the
 * other way round, and every static member of a mixin must be private.</b>
 *
 * <p>The log line is the actual diagnostic, per the umbrella's rule that with {@code require = 0}
 * "the mixin never applied" and "the hook never fired" are indistinguishable unless something is
 * logged on entry. Its ABSENCE from a log where the player definitely ate is the diagnosis.
 */
public final class EatHookState {

    private static volatile boolean applied = false;

    private EatHookState() {
    }

    /** Called by the mixin the first time the injection actually runs. */
    public static void markApplied() {
        if (!applied) {
            applied = true;
            Constants.LOG.info("Eat hook is active.");
        }
    }

    public static boolean hasApplied() {
        return applied;
    }
}
