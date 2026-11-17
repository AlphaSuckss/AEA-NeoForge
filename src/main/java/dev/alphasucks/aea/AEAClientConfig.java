package dev.alphasucks.aea;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings for each player's own game ({@code config/aea-client.toml}). */
public final class AEAClientConfig {
    private AEAClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ORIGIN_GRID;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("origin_screen");
        ORIGIN_GRID = b.comment("Show every origin of a layer in a grid on the choose-origin screen, instead of stepping through them with arrows. Needs Origins.")
            .define("grid", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean originGrid() {
        try {
            return !SPEC.isLoaded() || ORIGIN_GRID.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
