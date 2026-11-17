package dev.alphasucks.aea.compat.addon.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code aea:prevent_spawn_point}: the holder's spawn point can't be set or moved. Every way the game sets one goes
 * through {@code ServerPlayer.setRespawnPosition}, which fires {@link PlayerSetSpawnEvent}; this cancels it. Clearing
 * the spawn point (a bed that was broken, an anchor that ran out) is left alone.
 */
public final class PreventSpawnPointPower extends PowerType<PreventSpawnPointPower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "prevent_spawn_point");

    public enum Source implements StringRepresentable {
        BED("bed"), RESPAWN_ANCHOR("respawn_anchor"), COMMAND("command"), OTHER("other");

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);
        private final String name;

        Source(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private static final Codec<Set<Source>> SOURCES = Codec.withAlternative(Source.CODEC.listOf(), Source.CODEC.xmap(List::of, l -> l.get(0)))
        .xmap(l -> l.isEmpty() ? EnumSet.allOf(Source.class) : EnumSet.copyOf(l), List::copyOf);

    public record Cfg(Set<Source> source, Optional<Component> message) {
        boolean blocks(Source from) {
            return source.contains(from);
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        SOURCES.optionalFieldOf("source", EnumSet.allOf(Source.class)).forGetter(Cfg::source),
        ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(Cfg::message)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    /** Set by {@code SetSpawnCommandMixin} while {@code /spawnpoint} runs. Server thread only. */
    public static boolean inCommand;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerSetSpawnEvent.class, event -> {
            if (event.getNewSpawn() == null || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (!PowerLookup.hasActive(player, ID)) return;
            Source from = source(player, event);
            List<Cfg> active = PowerLookup.active(player, ID, Cfg.class);
            for (Cfg cfg : active) {
                if (!cfg.blocks(from)) continue;
                event.setCanceled(true);
                cfg.message().ifPresent(m -> player.displayClientMessage(m, true));
                return;
            }
        });
    }

    /** Beds set a non-forced spawn and anchors a forced one; the block is only checked where it is already loaded. */
    private static Source source(ServerPlayer player, PlayerSetSpawnEvent event) {
        if (inCommand) return Source.COMMAND;
        ServerLevel level = player.server.getLevel(event.getSpawnLevel());
        BlockPos pos = event.getNewSpawn();
        if (level == null || !level.isLoaded(pos)) return Source.OTHER;
        BlockState state = level.getBlockState(pos);
        if (!event.isForced()) return state.is(BlockTags.BEDS) ? Source.BED : Source.OTHER;
        return state.getBlock() instanceof RespawnAnchorBlock ? Source.RESPAWN_ANCHOR : Source.OTHER;
    }
}
