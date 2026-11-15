package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

/** {@code sync:print}: logs a message, optionally also to the player's chat. */
public final class PrintAction implements ActionType<EntityCtx, PrintAction.Cfg> {
    public record Cfg(String message, boolean showInChat, String logger) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("message").forGetter(Cfg::message),
            Codec.BOOL.optionalFieldOf("show_in_chat", false).forGetter(Cfg::showInChat),
            Codec.STRING.optionalFieldOf("logger_id", "Sync/PrintAction").forGetter(Cfg::logger)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        LoggerFactory.getLogger(cfg.logger).info(cfg.message);
        if (cfg.showInChat && ctx.raw() instanceof ServerPlayer p) p.sendSystemMessage(Component.literal(cfg.message));
    }
}
