package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.BlockAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.BiomeCtx;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.FluidCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.LegacyPowerShapes;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerResources;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Apoli 2.9.2 / Origins 1.10.2 types that Overgrown's Apoli dropped. */
public final class LegacyApoli {
    private LegacyApoli() {}

    public static final ResourceLocation MODIFY_LAVA_SPEED = ResourceLocation.fromNamespaceAndPath("apoli", "modify_lava_speed");
    public static final ResourceLocation MODIFY_INSOMNIA = ResourceLocation.fromNamespaceAndPath("apoli", "modify_insomnia_ticks");

    // ---------------------------------------------------------------- apoli:set_resource
    public record SetResourceCfg(ResourceLocation resource, int value) {}

    public static final class SetResource implements ActionType<EntityCtx, SetResourceCfg> {
        @Override
        public MapCodec<SetResourceCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.fieldOf("resource").forGetter(SetResourceCfg::resource),
                Codec.INT.fieldOf("value").forGetter(SetResourceCfg::value)
            ).apply(i, SetResourceCfg::new));
        }

        @Override
        public void run(SetResourceCfg c, EntityCtx ctx) {
            if (ctx.level().isClientSide()) return;
            PowerResources.write(PowerContainer.of(ctx.raw()), c.resource, c.value);
        }
    }

    // ---------------------------------------------------------------- apoli:side (meta)
    public record SideCfg<A>(boolean client, A action) {}

    static <A> MapCodec<SideCfg<A>> sideCodec(Codec<A> action) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("side").forGetter(c -> c.client() ? "client" : "server"),
            action.fieldOf("action").forGetter(SideCfg::action)
        ).apply(i, (s, a) -> new SideCfg<>(s.toLowerCase(Locale.ROOT).equals("client"), a)));
    }

    public static final class SideEntity implements ActionType<EntityCtx, SideCfg<EntityAction>> {
        @Override public MapCodec<SideCfg<EntityAction>> codec() { return sideCodec(EntityAction.CODEC); }
        @Override public void run(SideCfg<EntityAction> c, EntityCtx ctx) { if (c.client() == ctx.level().isClientSide()) c.action().run(ctx); }
    }

    public static final class SideBiEntity implements ActionType<BiEntityCtx, SideCfg<BiEntityAction>> {
        @Override public MapCodec<SideCfg<BiEntityAction>> codec() { return sideCodec(BiEntityAction.CODEC); }
        @Override public void run(SideCfg<BiEntityAction> c, BiEntityCtx ctx) { if (c.client() == ctx.level().isClientSide()) c.action().run(ctx); }
    }

    public static final class SideBlock implements ActionType<BlockCtx, SideCfg<BlockAction>> {
        @Override public MapCodec<SideCfg<BlockAction>> codec() { return sideCodec(BlockAction.CODEC); }
        @Override public void run(SideCfg<BlockAction> c, BlockCtx ctx) { if (c.client() == ctx.level().isClientSide()) c.action().run(ctx); }
    }

    public static final class SideItem implements ActionType<ItemCtx, SideCfg<ItemAction>> {
        @Override public MapCodec<SideCfg<ItemAction>> codec() { return sideCodec(ItemAction.CODEC); }
        @Override public void run(SideCfg<ItemAction> c, ItemCtx ctx) { if (c.client() == ctx.level().isClientSide()) c.action().run(ctx); }
    }

    // ---------------------------------------------------------------- apoli:category (biome)
    public static final class BiomeCategory implements ConditionType<BiomeCtx, String> {
        @Override public MapCodec<String> codec() { return Codec.STRING.fieldOf("category"); }

        @Override
        public boolean test(String category, BiomeCtx ctx) {
            return ctx.biome().is(TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("apoli", "category/" + category.toLowerCase(Locale.ROOT))));
        }
    }

    // ---------------------------------------------------------------- apoli:material (block)
    public static final class Material implements ConditionType<BlockCtx, List<String>> {
        @Override
        public MapCodec<List<String>> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("material").forGetter(l -> Optional.empty()),
                Codec.STRING.listOf().optionalFieldOf("materials", List.of()).forGetter(l -> l)
            ).apply(i, (one, many) -> {
                List<String> all = new ArrayList<>(many);
                one.ifPresent(all::add);
                return all;
            }));
        }

        @Override
        public boolean test(List<String> materials, BlockCtx ctx) {
            for (String m : materials) {
                if (ctx.state().is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("apoli", "material/" + m.toLowerCase(Locale.ROOT))))) return true;
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- apoli:fluid (fluid)
    public static final class FluidIs implements ConditionType<FluidCtx, ResourceLocation> {
        @Override public MapCodec<ResourceLocation> codec() { return IdCodecs.ID.fieldOf("fluid"); }

        @Override
        public boolean test(ResourceLocation fluid, FluidCtx ctx) {
            return BuiltInRegistries.FLUID.getKey(ctx.state().getType()).equals(fluid);
        }
    }

    // ---------------------------------------------------------------- apoli:modify_lava_speed / modify_insomnia_ticks
    public static final class ModifierPower extends PowerType<List<AttributeModifier>> {
        @Override
        public MapCodec<List<AttributeModifier>> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                AttributeModifier.CODEC.optionalFieldOf("modifier").forGetter(l -> Optional.empty()),
                AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(l -> l)
            ).apply(i, (one, many) -> {
                List<AttributeModifier> all = new ArrayList<>(many);
                one.ifPresent(all::add);
                return AttributeModifierHelper.ensureSorted(all);
            }));
        }
    }

    // ---------------------------------------------------------------- apoli:modify_fluid_render (client cosmetic)
    public record FluidRenderCfg(Optional<Object> blockCondition, Optional<Object> fluidCondition, ResourceLocation fluid) {}

    public static final class Inert extends PowerType<Object> {
        @Override public MapCodec<Object> configCodec() { return MapCodec.unit(Boolean.TRUE); }
    }

    // ---------------------------------------------------------------- shapes for removed power types
    public static void registerShapes() {
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

    static Codec<List<String>> strings() {
        return Codecs.singleOrList(Codec.STRING);
    }
}
