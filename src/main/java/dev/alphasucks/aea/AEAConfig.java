package dev.alphasucks.aea;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AEAConfig {
    private AEAConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue LEGACY_PACKS;
    private static final ModConfigSpec.BooleanValue LOG_UPGRADES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("legacy_datapacks");
        LEGACY_PACKS = b.comment("Upgrade data packs made for Minecraft 1.20.4 and older (Origins 1.10 / Apoli 2.9 / Sync 1.11) when they load.")
            .define("enabled", true);
        LOG_UPGRADES = b.comment("Log every command line AEA rewrites (debugging).")
            .define("log_rewrites", false);
        b.pop();
        SPEC = b.build();
    }

    public static boolean legacyPacks() {
        try {
            return !SPEC.isLoaded() || LEGACY_PACKS.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean logRewrites() {
        try {
            return SPEC.isLoaded() && LOG_UPGRADES.get();
        } catch (Throwable t) {
            return false;
        }
    }
}
