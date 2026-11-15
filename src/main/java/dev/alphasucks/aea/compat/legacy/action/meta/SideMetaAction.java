package dev.alphasucks.aea.compat.legacy.action.meta;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** {@code apoli:side} (Apoli 2.9): runs the action only on the client or only on the server. */
public final class SideMetaAction<CTX, ACTION> implements ActionType<CTX, SideMetaAction.Cfg<ACTION>> {
    private final Codec<ACTION> actionCodec;
    private final BiConsumer<ACTION, CTX> actionRunner;
    private final Function<CTX, Level> level;

    public SideMetaAction(Codec<ACTION> actionCodec, BiConsumer<ACTION, CTX> actionRunner, Function<CTX, Level> level) {
        this.actionCodec = actionCodec;
        this.actionRunner = actionRunner;
        this.level = level;
    }

    public record Cfg<ACTION>(boolean client, ACTION action) {}

    @Override
    public MapCodec<Cfg<ACTION>> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("side").forGetter(c -> c.client() ? "client" : "server"),
            actionCodec.fieldOf("action").forGetter(Cfg<ACTION>::action)
        ).apply(i, (s, a) -> new Cfg<>(s.toLowerCase(Locale.ROOT).equals("client"), a)));
    }

    @Override
    public void run(Cfg<ACTION> cfg, CTX ctx) {
        if (cfg.client == level.apply(ctx).isClientSide()) actionRunner.accept(cfg.action, ctx);
    }
}
