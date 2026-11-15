package dev.alphasucks.aea.legacy;

import javax.annotation.Nullable;

/** Numeric ids from before 1.20.2 that newer data no longer understands. */
public final class LegacyIds {
    private LegacyIds() {}

    private static final String[] EFFECTS = {
        null, "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
        "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
        "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption",
        "saturation", "glowing", "levitation", "luck", "unluck", "slow_falling", "conduit_power", "dolphins_grace",
        "bad_omen", "hero_of_the_village", "darkness"
    };

    @Nullable
    public static String effectName(int id) {
        return id > 0 && id < EFFECTS.length ? "minecraft:" + EFFECTS[id] : null;
    }
}
