package dev.alphasucks.aea.compat.addon;

import com.mojang.serialization.Codec;
import dev.alphasucks.aea.AEA;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The {@code effect_type} field: {@code beneficial}, {@code harmful} or {@code neutral}, one or a list. */
public final class EffectTypes {
    private EffectTypes() {}

    public enum Type implements StringRepresentable {
        BENEFICIAL(MobEffectCategory.BENEFICIAL),
        HARMFUL(MobEffectCategory.HARMFUL),
        NEUTRAL(MobEffectCategory.NEUTRAL);

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        public final MobEffectCategory category;

        Type(MobEffectCategory category) {
            this.category = category;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<List<Type>> CODEC = Codec.withAlternative(Type.CODEC.listOf(), Type.CODEC.xmap(List::of, l -> l.get(0)));

    public static boolean matches(List<Type> types, Holder<MobEffect> effect) {
        MobEffectCategory category = effect.value().getCategory();
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).category == category) return true;
        }
        return false;
    }

    /** Every registered effect of these types. Effects are registered before data packs load, so this is complete. */
    public static List<ResourceLocation> effectsOf(List<Type> types) {
        List<ResourceLocation> out = new ArrayList<>();
        BuiltInRegistries.MOB_EFFECT.holders().forEach(holder -> {
            if (matches(types, holder)) out.add(holder.key().location());
        });
        return out;
    }

    private static final String SENTINEL = "effect_type/";

    /** Stands in for an effect ID where a type only takes one effect, such as {@code apoli:status_effect}. */
    public static ResourceLocation sentinel(List<Type> types) {
        StringBuilder path = new StringBuilder(SENTINEL);
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) path.append('/');
            path.append(types.get(i).getSerializedName());
        }
        return AEA.id(path.toString());
    }

    /** The types a sentinel ID stands for, or null when the ID is a real effect. */
    public static List<Type> fromSentinel(ResourceLocation id) {
        if (!id.getNamespace().equals("aea") || !id.getPath().startsWith(SENTINEL)) return null;
        List<Type> types = new ArrayList<>();
        for (String name : id.getPath().substring(SENTINEL.length()).split("/")) {
            for (Type type : Type.values()) {
                if (type.getSerializedName().equals(name)) types.add(type);
            }
        }
        return types;
    }
}
