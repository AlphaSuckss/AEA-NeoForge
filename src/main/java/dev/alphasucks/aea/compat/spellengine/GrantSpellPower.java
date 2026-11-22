package dev.alphasucks.aea.compat.spellengine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code aea:grant_spell}: the holder knows Spell Engine spells as if equipment gave them. They show on Spell Engine's
 * spell hotbar and cast with its own keys, cast time, cost, cooldown and visuals. Passive and modifier spells work
 * too.
 *
 * <p>Spells go in through Spell Engine's server-side spell containers, which it syncs to the client itself. Spell
 * Engine only lets a player cast active spells through the spell item in their main hand; with {@code always} true
 * (the default), granted active spells are added whatever the player holds ({@code SpellContainerSourceMixin}). With
 * it false, they follow Spell Engine's own rules, like a spell book: a held wand, staff or other spell item has to
 * accept them.
 */
public final class GrantSpellPower extends PowerType<GrantSpellPower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "grant_spell");
    public static final PowerTypeUsage.Handle HELD = PowerTypeUsage.handle(ID);
    /** Server-side container keys: spells added whatever is held, and spells that follow Spell Engine's rules. */
    public static final String ALWAYS = "aea:grant_spell", WITH_ITEM = "aea:grant_spell/with_item";

    public record Cfg(List<ResourceLocation> spells, boolean always) {}

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codecs.singleOrList(ResourceLocation.CODEC).fieldOf("spell").forGetter(Cfg::spells),
        Codec.BOOL.optionalFieldOf("always", true).forGetter(Cfg::always)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) update(player);
        });
    }

    /** What was last put in each player's containers, by key. */
    private static final Map<Player, Map<String, List<String>>> GRANTED = new WeakHashMap<>();

    private static void update(ServerPlayer player) {
        Map<String, List<String>> before = GRANTED.get(player);
        if (before == null && !HELD.isHeld()) return;
        List<Cfg> active = PowerLookup.active(player, ID, Cfg.class);
        Map<String, List<String>> now = new LinkedHashMap<>();
        for (Cfg cfg : active) {
            List<String> ids = now.computeIfAbsent(cfg.always() ? ALWAYS : WITH_ITEM, k -> new ArrayList<>());
            for (ResourceLocation id : cfg.spells()) {
                String s = id.toString();
                if (!ids.contains(s)) ids.add(s);
            }
        }
        if (now.equals(before == null ? Map.of() : before)) return;
        if (now.isEmpty()) GRANTED.remove(player);
        else GRANTED.put(player, now);

        LinkedHashMap<String, SpellContainer> containers = ((SpellContainerSource.Owner) player).serverSideSpellContainers();
        containers.remove(ALWAYS);
        containers.remove(WITH_ITEM);
        for (Map.Entry<String, List<String>> e : now.entrySet()) {
            List<String> ids = e.getValue();
            containers.put(e.getKey(), new SpellContainer(SpellContainer.ContentType.ANY, "", "", ids.size(), List.copyOf(ids)));
        }
        SpellContainerSource.setDirtyServerSide(player);
    }
}
