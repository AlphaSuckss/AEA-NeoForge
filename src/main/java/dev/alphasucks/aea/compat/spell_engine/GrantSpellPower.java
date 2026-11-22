package dev.alphasucks.aea.compat.spell_engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerContainerImpl;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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

    /** Aux key holding a signature of the active grants last written to the player's containers. */
    private static final ResourceLocation APPLIED = ResourceLocation.fromNamespaceAndPath("aea", "grant_spell/applied");

    /** Per player per tick: only rebuilds the containers when the active grants change. */
    private static void update(ServerPlayer player) {
        if (!(PowerContainer.of(player) instanceof PowerContainerImpl container)) return;
        int before = container.getAuxIntOr(APPLIED, 0);
        if (before == 0 && !HELD.isHeld()) return;
        int now = ActiveConfigs.signature(player, container, ID, Cfg.class);
        LinkedHashMap<String, SpellContainer> containers = ((SpellContainerSource.Owner) player).serverSideSpellContainers();
        // Aux data is saved with the player; Spell Engine's containers may not be, so check they are still there.
        if (now == before && (now == 0 || containers.containsKey(ALWAYS) || containers.containsKey(WITH_ITEM))) return;
        if (now == 0) container.removeAux(APPLIED);
        else container.setAuxInt(APPLIED, now);

        List<String> always = new ArrayList<>(), withItem = new ArrayList<>();
        List<ResourceLocation> powers = container.powersOfType(ID);
        for (int i = 0; i < powers.size(); i++) {
            Cfg cfg = ActiveConfigs.config(player, container, powers.get(i), Cfg.class);
            if (cfg == null) continue;
            List<String> ids = cfg.always() ? always : withItem;
            for (ResourceLocation id : cfg.spells()) {
                String s = id.toString();
                if (!ids.contains(s)) ids.add(s);
            }
        }
        put(containers, ALWAYS, always);
        put(containers, WITH_ITEM, withItem);
        SpellContainerSource.setDirtyServerSide(player);
    }

    private static void put(LinkedHashMap<String, SpellContainer> containers, String key, List<String> ids) {
        if (ids.isEmpty()) containers.remove(key);
        else containers.put(key, new SpellContainer(SpellContainer.ContentType.ANY, "", "", ids.size(), List.copyOf(ids)));
    }
}
