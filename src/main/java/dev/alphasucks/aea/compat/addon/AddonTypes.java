package dev.alphasucks.aea.compat.addon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Comparison;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** Types from AEA (1.20.1), Apugli, Mob Origins and other addons that the provided packs rely on. */
public final class AddonTypes {
    private AddonTypes() {}

    public static final ResourceLocation VELOCITY_IMMUNITY = ResourceLocation.fromNamespaceAndPath("aea", "velocity_immunity");
    public static final ResourceLocation WALK_ON_POWDER_SNOW = ResourceLocation.fromNamespaceAndPath("moborigins", "walk_on_powder_snow");
    public static final ResourceLocation MODIFY_DROPS = ResourceLocation.fromNamespaceAndPath("apoli", "modify_drops");

    // ---------------------------------------------------------------- aea:velocity_immunity
    /** AEA 1.5.4: blocks apoli:add_velocity on the holder (entity form when "entity", bi-entity form when "bientity" and the optional bientity_condition passes for actor/holder). */
    public record VelocityImmunityCfg(boolean entity, boolean bientity, Optional<dev.overgrown.apoli.condition.BiEntityCondition> bientityCondition) {
        public boolean blocksBi(net.minecraft.world.entity.Entity actor, net.minecraft.world.entity.Entity target) {
            return bientity && bientityCondition.map(c -> c.test(dev.overgrown.apoli.condition.context.BiEntityCtx.of(actor, target, target.level()))).orElse(true);
        }
    }

    public static final class VelocityImmunity extends PowerType<VelocityImmunityCfg> {
        @Override
        public MapCodec<VelocityImmunityCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("entity", true).forGetter(VelocityImmunityCfg::entity),
                Codec.BOOL.optionalFieldOf("bientity", true).forGetter(VelocityImmunityCfg::bientity),
                dev.overgrown.apoli.condition.BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(VelocityImmunityCfg::bientityCondition)
            ).apply(i, VelocityImmunityCfg::new));
        }
    }

    // ---------------------------------------------------------------- moborigins:walk_on_powder_snow
    public static final class Flag extends PowerType<Boolean> {
        @Override
        public MapCodec<Boolean> configCodec() {
            return MapCodec.unit(Boolean.TRUE);
        }
    }

    // ---------------------------------------------------------------- apoli:modify_drops
    public record ModifyDropsCfg(Optional<ItemAction> itemAction, Optional<BlockCondition> blockCondition) {}

    public static final class ModifyDrops extends PowerType<ModifyDropsCfg> {
        @Override
        public MapCodec<ModifyDropsCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                ItemAction.CODEC.optionalFieldOf("item_action").forGetter(ModifyDropsCfg::itemAction),
                BlockCondition.CODEC.optionalFieldOf("block_condition").forGetter(ModifyDropsCfg::blockCondition)
            ).apply(i, ModifyDropsCfg::new));
        }
    }

    // ---------------------------------------------------------------- damage condition: attacker_action
    public record AttackerActionCfg(Optional<EntityAction> action, Optional<DamageCondition> condition, Optional<EntityCondition> entityCondition) {}

    public static final class AttackerAction implements ConditionType<DamageCtx, AttackerActionCfg> {
        @Override
        public MapCodec<AttackerActionCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                EntityAction.CODEC.optionalFieldOf("entity_action").forGetter(AttackerActionCfg::action),
                DamageCondition.CODEC.optionalFieldOf("condition").forGetter(AttackerActionCfg::condition),
                EntityCondition.CODEC.optionalFieldOf("attacker_condition").forGetter(AttackerActionCfg::entityCondition)
            ).apply(i, AttackerActionCfg::new));
        }

        @Override
        public boolean test(AttackerActionCfg c, DamageCtx ctx) {
            Entity attacker = ctx.source().getEntity();
            if (attacker == null) return false;
            if (c.condition.isPresent() && !c.condition.get().test(ctx)) return false;
            if (c.entityCondition.isPresent() && !c.entityCondition.get().test(EntityCtx.of(attacker, attacker.level()))) return false;
            c.action.ifPresent(a -> a.run(EntityCtx.of(attacker, attacker.level())));
            return true;
        }
    }

    // ---------------------------------------------------------------- damage condition: command (runs as the damaged entity)
    public record CommandCfg(String command, Comparison comparison, int compareTo) {}

    public static final class DamageCommand implements ConditionType<DamageCtx, CommandCfg> {
        @Override
        public MapCodec<CommandCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("command").forGetter(CommandCfg::command),
                Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(CommandCfg::comparison),
                Codec.INT.optionalFieldOf("compare_to", 1).forGetter(CommandCfg::compareTo)
            ).apply(i, CommandCfg::new));
        }

        @Override
        public boolean test(CommandCfg c, DamageCtx ctx) {
            Entity target = ctx.target();
            if (target == null) return false;
            MinecraftServer server = target.getServer();
            if (server == null) return false;
            int[] result = {0};
            CommandSourceStack src = target.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                .withCallback((ok, v) -> result[0] = v);
            server.getCommands().performPrefixedCommand(src, c.command);
            return c.comparison.compare(result[0], c.compareTo);
        }
    }
}
