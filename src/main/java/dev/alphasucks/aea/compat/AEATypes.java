package dev.alphasucks.aea.compat;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.addon.AddonTypes;
import dev.alphasucks.aea.compat.legacy.LegacyTypes;
import dev.alphasucks.aea.compat.sync.SyncTypes;
import dev.overgrown.apoli.alias.NamespaceAlias;
import dev.overgrown.apoli.power.PowerTypeRegistry;

/**
 * Registers everything AEA adds to Overgrown's Apoli:
 * <ul>
 *   <li>the {@code sync:} namespace (Sync 1.11.8 forward port — Overgrown's improved implementations are used where they exist);</li>
 *   <li>Apoli 2.9.2 / Origins 1.10.2 types Overgrown dropped;</li>
 *   <li>addon types the supported packs use (AEA, Apugli, Mob Origins, ...).</li>
 * </ul>
 * The GUI feature registers its own types from {@code AeaGui}.
 */
public final class AEATypes {
    private AEATypes() {}

    public static void registerAll() {
        int before = PowerTypeRegistry.view().size();

        // sync:x / aea:x fall back to apoli:x — Overgrown's Apoli already ships the newer versions of most Sync types.
        NamespaceAlias.addAlias("sync", "apoli");
        NamespaceAlias.addAlias("aea", "apoli");

        SyncTypes.register();
        LegacyTypes.register();
        AddonTypes.register();
        dev.alphasucks.aea.mindcontrol.MindControlTypes.register();

        AEA.LOGGER.info("[AEA] Registered {} extra power type(s) plus legacy actions/conditions.", PowerTypeRegistry.view().size() - before);
    }
}
