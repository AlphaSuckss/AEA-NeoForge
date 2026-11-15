package dev.alphasucks.aea.compat;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.addon.AddonTypes;
import dev.alphasucks.aea.compat.legacy.LegacyApoli;
import dev.alphasucks.aea.compat.sync.SyncActions;
import dev.alphasucks.aea.compat.sync.SyncConditions;
import dev.alphasucks.aea.compat.sync.SyncPowers;
import dev.alphasucks.aea.compat.sync.SyncPowers2;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.alias.NamespaceAlias;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.power.PowerTypeRegistry;

import java.util.Map;

/**
 * Registers everything AEA adds to Overgrown's Apoli:
 * <ul>
 *   <li>the {@code sync:} namespace (Sync 1.11.8 forward port — Overgrown's improved implementations are used where they exist);</li>
 *   <li>Apoli 2.9.2 / Origins 1.10.2 types Overgrown dropped;</li>
 *   <li>addon types the supported packs use (AEA, Apugli, Mob Origins, ...).</li>
 * </ul>
 */
public final class AEATypes {
    private AEATypes() {}

    public static void registerAll() {
        int before = PowerTypeRegistry.view().size();

        // sync:x / aea:x fall back to apoli:x — Overgrown's Apoli already ships the newer versions of most Sync types.
        NamespaceAlias.addAlias("sync", "apoli");
        NamespaceAlias.addAlias("aea", "apoli");

        sync();
        legacy();
        addons();

        AEA.LOGGER.info("[AEA] Registered {} extra power type(s) plus legacy actions/conditions.", PowerTypeRegistry.view().size() - before);
    }

    private static void sync() {
        // Powers
        Reg.power("sync:mobs_ignore", new SyncPowers.MobsIgnore());
        Reg.power("sync:flip_model", new SyncPowers.FlipModel());
        Reg.power("sync:entity_texture_overlay", new SyncPowers.TextureOverlay());
        Reg.power("sync:prevent_creative_flight", new SyncPowers.PreventCreativeFlight());
        Reg.power("sync:prevent_sprinting_particles", new SyncPowers.PreventSprintParticles());
        Reg.power("sync:modify_enchantment_damage_dealt", new SyncPowers.EnchDamage());
        Reg.power("sync:modify_enchantment_damage_taken", new SyncPowers.EnchDamage());
        Reg.power("sync:body_part_damage_modifier", new SyncPowers2.BodyPartDamage());
        Reg.power("sync:action_on_sending_message", new SyncPowers2.SendingMessage());
        Reg.power("sync:set_entity_aspects", new SyncPowers.Inert());
        Reg.power("sync:modify_jade_tooltip", new SyncPowers.Inert());
        Reg.powerAlias("sync:custom_projectile", "apoli:fire_projectile");

        // Entity actions
        Reg.action(ActionTypes.ENTITY, "sync:attach_rope", new SyncActions.AttachRope(false));
        Reg.action(ActionTypes.ENTITY, "sync:attach_rope_entity", new SyncActions.AttachRope(true));
        Reg.action(ActionTypes.ENTITY, "sync:print", new SyncActions.Print());
        Reg.action(ActionTypes.ENTITY, "sync:item_cooldown", new SyncActions.ItemCooldown());
        Reg.action(ActionTypes.ENTITY, "sync:cycle_stolen_power", new SyncActions.CycleStolen());
        Reg.action(ActionTypes.ENTITY, "sync:toggle_transfer_mode", new SyncActions.ToggleTransferMode());
        Reg.action(ActionTypes.ENTITY, "sync:use_selected_stolen_power", new SyncActions.UseSelectedStolen());
        Reg.actionAlias(ActionTypes.ENTITY, "sync:custom_projectile", "apoli:fire_projectile");

        // Bi-entity actions
        Reg.action(ActionTypes.BI_ENTITY, "sync:rope_leash", new SyncActions.RopeLeash());
        Reg.action(ActionTypes.BI_ENTITY, "sync:convert_entity", new SyncActions.ConvertEntity());
        Reg.action(ActionTypes.BI_ENTITY, "sync:save_location", new SyncActions.SaveLocationBi());
        Reg.action(ActionTypes.BI_ENTITY, "sync:suppress_power", new SyncActions.SuppressPower(false));
        Reg.action(ActionTypes.BI_ENTITY, "sync:liberate_power", new SyncActions.SuppressPower(true));
        Reg.action(ActionTypes.BI_ENTITY, "sync:transfer", new SyncActions.Transfer());

        // Entity conditions
        Reg.condition(ConditionTypes.ENTITY, "sync:velocity", new SyncConditions.Velocity());
        Reg.condition(ConditionTypes.ENTITY, "sync:in_pose", new SyncConditions.InPose());
        Reg.condition(ConditionTypes.ENTITY, "sync:attached_to_rope", new SyncConditions.AttachedToRope());
        Reg.condition(ConditionTypes.ENTITY, "sync:has_aspect", new SyncConditions.HasAspect());
        Reg.condition(ConditionTypes.ENTITY, "sync:is_selected_stolen_power", new SyncConditions.IsSelectedStolenPower());
        Reg.conditionRenamed(ConditionTypes.ENTITY, "sync:perspective", "apoli:perspective", Map.of("perspectives", "perspective"));

        // Bi-entity / item conditions
        Reg.condition(ConditionTypes.BI_ENTITY, "sync:colliding", new SyncConditions.Colliding());
        Reg.condition(ConditionTypes.BI_ENTITY, "sync:command", new SyncConditions.BiCommand());
        Reg.condition(ConditionTypes.ITEM, "sync:on_cooldown", new SyncConditions.OnCooldown());
    }

    private static void legacy() {
        Reg.action(ActionTypes.ENTITY, "apoli:set_resource", new LegacyApoli.SetResource());
        Reg.action(ActionTypes.ENTITY, "apoli:side", new LegacyApoli.SideEntity());
        Reg.action(ActionTypes.BI_ENTITY, "apoli:side", new LegacyApoli.SideBiEntity());
        Reg.action(ActionTypes.BLOCK, "apoli:side", new LegacyApoli.SideBlock());
        Reg.action(ActionTypes.ITEM, "apoli:side", new LegacyApoli.SideItem());

        Reg.condition(ConditionTypes.BIOME, "apoli:category", new LegacyApoli.BiomeCategory());
        Reg.condition(ConditionTypes.BLOCK, "apoli:material", new LegacyApoli.Material());
        Reg.condition(ConditionTypes.FLUID, "apoli:fluid", new LegacyApoli.FluidIs());

        Reg.power("apoli:modify_lava_speed", new LegacyApoli.ModifierPower());
        Reg.power("apoli:modify_insomnia_ticks", new LegacyApoli.ModifierPower());
        Reg.power("apoli:modify_fluid_render", new LegacyApoli.Inert());
        LegacyApoli.registerShapes();

        // Item nbt conditions on vanilla tags (CustomModelData, Enchantments, Potion, display...) — see ApoliJsonUpgrader.
        Reg.condition(ConditionTypes.ITEM, "aea:legacy_nbt", new dev.alphasucks.aea.compat.legacy.LegacyItemNbt());
        Reg.action(ActionTypes.ITEM, "aea:legacy_merge_nbt", new dev.alphasucks.aea.compat.legacy.LegacyItemMerge.Action());

        // Apoli 2.9.2 behaviours that Overgrown changed (ApoliJsonUpgrader rewrites legacy packs to these).
        Reg.condition(ConditionTypes.BI_ENTITY, "aea:legacy_undirected", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.Undirected());
        Reg.condition(ConditionTypes.ENTITY, "aea:legacy_distance_from_coordinates", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.EntityDistance("world_origin"));
        Reg.condition(ConditionTypes.ENTITY, "aea:legacy_distance_from_spawn", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.EntityDistance("world_spawn"));
        Reg.condition(ConditionTypes.BLOCK, "aea:legacy_distance_from_coordinates", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.BlockDistance("world_origin"));
        Reg.condition(ConditionTypes.BLOCK, "aea:legacy_distance_from_spawn", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.BlockDistance("world_spawn"));
        Reg.condition(ConditionTypes.ENTITY, "apoli:distance_from_spawn", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.EntityDistance("world_spawn"));
        Reg.condition(ConditionTypes.BLOCK, "apoli:distance_from_spawn", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.BlockDistance("world_spawn"));
        Reg.power("aea:legacy_damage_over_time", new dev.alphasucks.aea.compat.legacy.LegacyApoliTypes.DamageOverTime());
        Reg.conditionAlias(ConditionTypes.ENTITY, "aea:legacy_nbt", "apoli:nbt");
        Reg.conditionAlias(ConditionTypes.BLOCK, "aea:legacy_nbt", "apoli:nbt");
    }

    private static void addons() {
        Reg.power("aea:velocity_immunity", new AddonTypes.VelocityImmunity());
        dev.alphasucks.aea.mobai.MobAi.register();
        Reg.power("moborigins:walk_on_powder_snow", new AddonTypes.Flag());
        Reg.power("apoli:modify_drops", new AddonTypes.ModifyDrops());
        Reg.powerAlias("apugli:mobs_ignore", "sync:mobs_ignore");
        Reg.powerAlias("apoli:damage_immunity", "apoli:invulnerability");

        Reg.conditionAlias(ConditionTypes.DAMAGE, "apoli:damage_type", "apoli:type");
        Reg.condition(ConditionTypes.DAMAGE, "apoli:attacker_action", new AddonTypes.AttackerAction());
        Reg.condition(ConditionTypes.DAMAGE, "apoli:command", new AddonTypes.DamageCommand());
    }
}
