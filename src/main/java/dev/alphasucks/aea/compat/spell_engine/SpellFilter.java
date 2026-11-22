package dev.alphasucks.aea.compat.spell_engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Which Spell Engine spells something applies to: {@code "spell"} (a spell id, a {@code #}spell tag, or a list of
 * them) and {@code "school"} (a Spell Power school id such as {@code spell_power:fire}, or a list). Both empty means
 * every spell; with both, a spell has to match both.
 */
public final class SpellFilter {
    public static final SpellFilter ANY = new SpellFilter(List.of(), List.of());

    private static final Codec<String> ENTRY = Codec.STRING.validate(s -> {
        String raw = s.startsWith("#") ? s.substring(1) : s;
        return ResourceLocation.tryParse(raw) == null ? DataResult.error(() -> "Not a spell id or #tag: " + s) : DataResult.success(s);
    });

    public static final MapCodec<SpellFilter> FIELDS = RecordCodecBuilder.mapCodec(i -> i.group(
        Codecs.singleOrList(ENTRY).optionalFieldOf("spell", List.of()).forGetter(SpellFilter::raw),
        Codecs.singleOrList(ResourceLocation.CODEC).optionalFieldOf("school", List.of()).forGetter(SpellFilter::schools)
    ).apply(i, SpellFilter::new));

    private final List<String> raw;
    private final List<ResourceLocation> ids = new ArrayList<>();
    private final List<TagKey<Spell>> tags = new ArrayList<>();
    private final List<ResourceLocation> schools;

    public SpellFilter(List<String> raw, List<ResourceLocation> schools) {
        this.raw = raw;
        this.schools = schools;
        for (String s : raw) {
            if (s.startsWith("#")) tags.add(TagKey.create(SpellRegistry.KEY, ResourceLocation.parse(s.substring(1))));
            else ids.add(ResourceLocation.parse(s));
        }
    }

    public List<String> raw() {
        return raw;
    }

    public List<ResourceLocation> schools() {
        return schools;
    }

    /** Plain spell ids listed (tags left out). */
    public List<ResourceLocation> ids() {
        return ids;
    }

    public boolean any() {
        return raw.isEmpty() && schools.isEmpty();
    }

    public boolean test(Holder<Spell> spell) {
        if (spell == null) return false;
        if (!raw.isEmpty()) {
            boolean hit = false;
            for (int i = 0; i < ids.size() && !hit; i++) hit = spell.is(ids.get(i));
            for (int i = 0; i < tags.size() && !hit; i++) hit = spell.is(tags.get(i));
            if (!hit) return false;
        }
        if (schools.isEmpty()) return true;
        Spell value = spell.value();
        return value.school != null && schools.contains(value.school.id);
    }
}
