package dev.alphasucks.aea.compat.addon;

import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.compat.addon.condition.damage.AttackerActionCondition;
import dev.alphasucks.aea.compat.addon.condition.damage.CommandCondition;
import dev.alphasucks.aea.compat.addon.power.ModifyDropsPower;
import dev.alphasucks.aea.compat.addon.power.VelocityImmunityPower;
import dev.alphasucks.aea.compat.addon.power.WalkOnPowderSnowPower;
import dev.alphasucks.aea.mobai.MobAi;
import dev.overgrown.apoli.condition.ConditionTypes;

/** Types from AEA (1.20.1), Apugli, Mob Origins and other addons that the supported packs rely on. */
public final class AddonTypes {
    private AddonTypes() {}

    public static void register() {
        Reg.power("aea:velocity_immunity", new VelocityImmunityPower());
        MobAi.register();
        Reg.power("moborigins:walk_on_powder_snow", new WalkOnPowderSnowPower());
        Reg.power("apoli:modify_drops", new ModifyDropsPower());
        Reg.powerAlias("apugli:mobs_ignore", "sync:mobs_ignore");
        Reg.powerAlias("apoli:damage_immunity", "apoli:invulnerability");

        Reg.conditionAlias(ConditionTypes.DAMAGE, "apoli:damage_type", "apoli:type");
        Reg.condition(ConditionTypes.DAMAGE, "apoli:attacker_action", new AttackerActionCondition());
        Reg.condition(ConditionTypes.DAMAGE, "apoli:command", new CommandCondition());
    }
}
