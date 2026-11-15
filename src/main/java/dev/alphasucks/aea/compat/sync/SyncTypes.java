package dev.alphasucks.aea.compat.sync;

import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.compat.power.InertPower;
import dev.alphasucks.aea.compat.sync.action.bientity.ConvertEntityAction;
import dev.alphasucks.aea.compat.sync.action.bientity.RopeLeashAction;
import dev.alphasucks.aea.compat.sync.action.bientity.SaveLocationAction;
import dev.alphasucks.aea.compat.sync.action.bientity.SuppressPowerAction;
import dev.alphasucks.aea.compat.sync.action.bientity.TransferAction;
import dev.alphasucks.aea.compat.sync.action.entity.AttachRopeAction;
import dev.alphasucks.aea.compat.sync.action.entity.CycleStolenPowerAction;
import dev.alphasucks.aea.compat.sync.action.entity.ItemCooldownAction;
import dev.alphasucks.aea.compat.sync.action.entity.PrintAction;
import dev.alphasucks.aea.compat.sync.action.entity.ToggleTransferModeAction;
import dev.alphasucks.aea.compat.sync.action.entity.UseSelectedStolenPowerAction;
import dev.alphasucks.aea.compat.sync.condition.bientity.CollidingCondition;
import dev.alphasucks.aea.compat.sync.condition.bientity.CommandCondition;
import dev.alphasucks.aea.compat.sync.condition.entity.AttachedToRopeCondition;
import dev.alphasucks.aea.compat.sync.condition.entity.HasAspectCondition;
import dev.alphasucks.aea.compat.sync.condition.entity.InPoseCondition;
import dev.alphasucks.aea.compat.sync.condition.entity.IsSelectedStolenPowerCondition;
import dev.alphasucks.aea.compat.sync.condition.entity.VelocityCondition;
import dev.alphasucks.aea.compat.sync.condition.item.OnCooldownCondition;
import dev.alphasucks.aea.compat.sync.power.ActionOnSendingMessagePower;
import dev.alphasucks.aea.compat.sync.power.BodyPartDamageModifierPower;
import dev.alphasucks.aea.compat.sync.power.EntityTextureOverlayPower;
import dev.alphasucks.aea.compat.sync.power.FlipModelPower;
import dev.alphasucks.aea.compat.sync.power.MobsIgnorePower;
import dev.alphasucks.aea.compat.sync.power.ModifyEnchantmentDamagePower;
import dev.alphasucks.aea.compat.sync.power.PreventCreativeFlightPower;
import dev.alphasucks.aea.compat.sync.power.PreventSprintingParticlesPower;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.condition.ConditionTypes;

import java.util.Map;

/**
 * The {@code sync:} namespace: the Sync 1.11.8 types Overgrown's Apoli doesn't ship (or ships with other fields). The
 * rest of {@code sync:x} falls back to {@code apoli:x} through the namespace alias set up in AEATypes.
 */
public final class SyncTypes {
    private SyncTypes() {}

    public static void register() {
        // Powers
        Reg.power("sync:mobs_ignore", new MobsIgnorePower());
        Reg.power("sync:flip_model", new FlipModelPower());
        Reg.power("sync:entity_texture_overlay", new EntityTextureOverlayPower());
        Reg.power("sync:prevent_creative_flight", new PreventCreativeFlightPower());
        Reg.power("sync:prevent_sprinting_particles", new PreventSprintingParticlesPower());
        Reg.power("sync:modify_enchantment_damage_dealt", new ModifyEnchantmentDamagePower());
        Reg.power("sync:modify_enchantment_damage_taken", new ModifyEnchantmentDamagePower());
        Reg.power("sync:body_part_damage_modifier", new BodyPartDamageModifierPower());
        Reg.power("sync:action_on_sending_message", new ActionOnSendingMessagePower());
        Reg.power("sync:set_entity_aspects", new InertPower());
        Reg.power("sync:modify_jade_tooltip", new InertPower());
        Reg.powerAlias("sync:custom_projectile", "apoli:fire_projectile");

        // Entity actions
        Reg.action(ActionTypes.ENTITY, "sync:attach_rope", new AttachRopeAction(false));
        Reg.action(ActionTypes.ENTITY, "sync:attach_rope_entity", new AttachRopeAction(true));
        Reg.action(ActionTypes.ENTITY, "sync:print", new PrintAction());
        Reg.action(ActionTypes.ENTITY, "sync:item_cooldown", new ItemCooldownAction());
        Reg.action(ActionTypes.ENTITY, "sync:cycle_stolen_power", new CycleStolenPowerAction());
        Reg.action(ActionTypes.ENTITY, "sync:toggle_transfer_mode", new ToggleTransferModeAction());
        Reg.action(ActionTypes.ENTITY, "sync:use_selected_stolen_power", new UseSelectedStolenPowerAction());
        Reg.actionAlias(ActionTypes.ENTITY, "sync:custom_projectile", "apoli:fire_projectile");

        // Bi-entity actions
        Reg.action(ActionTypes.BI_ENTITY, "sync:rope_leash", new RopeLeashAction());
        Reg.action(ActionTypes.BI_ENTITY, "sync:convert_entity", new ConvertEntityAction());
        Reg.action(ActionTypes.BI_ENTITY, "sync:save_location", new SaveLocationAction());
        Reg.action(ActionTypes.BI_ENTITY, "sync:suppress_power", new SuppressPowerAction(false));
        Reg.action(ActionTypes.BI_ENTITY, "sync:liberate_power", new SuppressPowerAction(true));
        Reg.action(ActionTypes.BI_ENTITY, "sync:transfer", new TransferAction());

        // Entity conditions
        Reg.condition(ConditionTypes.ENTITY, "sync:velocity", new VelocityCondition());
        Reg.condition(ConditionTypes.ENTITY, "sync:in_pose", new InPoseCondition());
        Reg.condition(ConditionTypes.ENTITY, "sync:attached_to_rope", new AttachedToRopeCondition());
        Reg.condition(ConditionTypes.ENTITY, "sync:has_aspect", new HasAspectCondition());
        Reg.condition(ConditionTypes.ENTITY, "sync:is_selected_stolen_power", new IsSelectedStolenPowerCondition());
        Reg.conditionRenamed(ConditionTypes.ENTITY, "sync:perspective", "apoli:perspective", Map.of("perspectives", "perspective"));

        // Bi-entity / item conditions
        Reg.condition(ConditionTypes.BI_ENTITY, "sync:colliding", new CollidingCondition());
        Reg.condition(ConditionTypes.BI_ENTITY, "sync:command", new CommandCondition());
        Reg.condition(ConditionTypes.ITEM, "sync:on_cooldown", new OnCooldownCondition());
    }
}
