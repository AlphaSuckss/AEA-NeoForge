package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Dynamic;
import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.compat.legacy.action.entity.SetResourceAction;
import dev.alphasucks.aea.compat.legacy.action.item.LegacyMergeNbtAction;
import dev.alphasucks.aea.compat.legacy.action.meta.SideMetaAction;
import dev.alphasucks.aea.compat.legacy.condition.bientity.UndirectedCondition;
import dev.alphasucks.aea.compat.legacy.condition.biome.CategoryCondition;
import dev.alphasucks.aea.compat.legacy.condition.block.BlockDistanceCondition;
import dev.alphasucks.aea.compat.legacy.condition.block.MaterialCondition;
import dev.alphasucks.aea.compat.legacy.condition.entity.EntityDistanceCondition;
import dev.alphasucks.aea.compat.legacy.condition.fluid.FluidCondition;
import dev.alphasucks.aea.compat.legacy.condition.item.LegacyNbtCondition;
import dev.alphasucks.aea.compat.legacy.power.DamageOverTimePower;
import dev.alphasucks.aea.compat.legacy.power.ModifierPower;
import dev.alphasucks.aea.compat.power.InertPower;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.BlockAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.power.LegacyPowerShapes;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Apoli 2.9.2 / Origins 1.10.2 types that Overgrown's Apoli dropped or changed. */
public final class LegacyTypes {
    private LegacyTypes() {}

    public static void register() {
        Reg.action(ActionTypes.ENTITY, "apoli:set_resource", new SetResourceAction());
        Reg.action(ActionTypes.ENTITY, "apoli:side", new SideMetaAction<>(EntityAction.CODEC, EntityAction::run, EntityCtx::level));
        Reg.action(ActionTypes.BI_ENTITY, "apoli:side", new SideMetaAction<>(BiEntityAction.CODEC, BiEntityAction::run, BiEntityCtx::level));
        Reg.action(ActionTypes.BLOCK, "apoli:side", new SideMetaAction<>(BlockAction.CODEC, BlockAction::run, BlockCtx::level));
        Reg.action(ActionTypes.ITEM, "apoli:side", new SideMetaAction<>(ItemAction.CODEC, ItemAction::run, ItemCtx::level));

        Reg.condition(ConditionTypes.BIOME, "apoli:category", new CategoryCondition());
        Reg.condition(ConditionTypes.BLOCK, "apoli:material", new MaterialCondition());
        Reg.condition(ConditionTypes.FLUID, "apoli:fluid", new FluidCondition());

        Reg.power("apoli:modify_lava_speed", new ModifierPower());
        Reg.power("apoli:modify_insomnia_ticks", new ModifierPower());
        Reg.power("apoli:modify_fluid_render", new InertPower()); // client cosmetic
        registerShapes();

        // Item nbt conditions on vanilla tags (CustomModelData, Enchantments, Potion, display...) — see ApoliJsonUpgrader.
        Reg.condition(ConditionTypes.ITEM, "aea:legacy_nbt", new LegacyNbtCondition());
        Reg.action(ActionTypes.ITEM, "aea:legacy_merge_nbt", new LegacyMergeNbtAction());

        // Apoli 2.9.2 behaviours that Overgrown changed (ApoliJsonUpgrader rewrites legacy packs to these).
        Reg.condition(ConditionTypes.BI_ENTITY, "aea:legacy_undirected", new UndirectedCondition());
        Reg.condition(ConditionTypes.ENTITY, "aea:legacy_distance_from_coordinates", new EntityDistanceCondition("world_origin"));
        Reg.condition(ConditionTypes.ENTITY, "aea:legacy_distance_from_spawn", new EntityDistanceCondition("world_spawn"));
        Reg.condition(ConditionTypes.BLOCK, "aea:legacy_distance_from_coordinates", new BlockDistanceCondition("world_origin"));
        Reg.condition(ConditionTypes.BLOCK, "aea:legacy_distance_from_spawn", new BlockDistanceCondition("world_spawn"));
        Reg.condition(ConditionTypes.ENTITY, "apoli:distance_from_spawn", new EntityDistanceCondition("world_spawn"));
        Reg.condition(ConditionTypes.BLOCK, "apoli:distance_from_spawn", new BlockDistanceCondition("world_spawn"));
        Reg.power("aea:legacy_damage_over_time", new DamageOverTimePower());
        Reg.conditionAlias(ConditionTypes.ENTITY, "aea:legacy_nbt", "apoli:nbt");
        Reg.conditionAlias(ConditionTypes.BLOCK, "aea:legacy_nbt", "apoli:nbt");
    }

    /** Reshapes removed power types into ones Overgrown has. */
    private static void registerShapes() {
        // apoli:launch -> action_on_key_press with add_velocity + sound + cloud particles.
        LegacyPowerShapes.register(ResourceLocation.fromNamespaceAndPath("apoli", "launch"), new LegacyPowerShapes.Shape() {
            @Override
            public <T> Dynamic<T> reshape(Dynamic<T> p) {
                float speed = p.get("speed").asNumber().result().map(Number::floatValue).orElse(1f);
                Dynamic<T> velocity = p.emptyMap()
                    .set("type", p.createString("apoli:add_velocity"))
                    .set("y", p.createFloat(speed))
                    .set("space", p.createString("world"));
                List<Dynamic<T>> actions = new ArrayList<>();
                actions.add(velocity);
                p.get("sound").asString().result().ifPresent(s -> actions.add(p.emptyMap()
                    .set("type", p.createString("apoli:play_sound"))
                    .set("sound", p.createString(s))));
                actions.add(p.emptyMap()
                    .set("type", p.createString("apoli:spawn_particles"))
                    .set("particle", p.createString("minecraft:cloud"))
                    .set("count", p.createInt(4))
                    .set("speed", p.createFloat(0.1f))
                    .set("force", p.createBoolean(false)));
                Dynamic<T> and = p.emptyMap()
                    .set("type", p.createString("apoli:and"))
                    .set("actions", p.createList(actions.stream()));
                return p.set("type", p.createString("apoli:action_on_key_press"))
                    .remove("speed").remove("sound")
                    .set("entity_action", and);
            }
        });
    }
}
