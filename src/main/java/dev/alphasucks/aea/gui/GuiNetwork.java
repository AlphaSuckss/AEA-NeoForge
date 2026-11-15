package dev.alphasucks.aea.gui;

import dev.alphasucks.aea.AEA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Wire format of the GUI: open data, server → client state deltas, button and slider input. */
public final class GuiNetwork {
    private GuiNetwork() {}

    private static final int MAX_ENTITY_IDS = 16;

    public static void writeOpenData(GuiMenu menu, RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(menu.powerId);
        buf.writeResourceLocation(menu.guiId);
        buf.writeVarInt(menu.slots.size());
        State.full(menu).write(buf);
    }

    public static void sendState(ServerPlayer player, int syncId, State state) {
        if (state.isEmpty() || player.connection == null || player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        if (!player.connection.hasChannel(StatePayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new StatePayload(syncId, state));
    }

    // ------------------------------------------------------------------ payloads

    public record StatePayload(int syncId, State state) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(AEA.id("gui_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.syncId); p.state.write(buf); },
            buf -> new StatePayload(buf.readVarInt(), State.read(buf)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ButtonPayload(int syncId, int element, boolean pressed) implements CustomPacketPayload {
        public static final Type<ButtonPayload> TYPE = new Type<>(AEA.id("gui_button"));
        public static final StreamCodec<FriendlyByteBuf, ButtonPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.syncId); buf.writeVarInt(p.element); buf.writeBoolean(p.pressed); },
            buf -> new ButtonPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SliderPayload(int syncId, int element, double value, boolean release) implements CustomPacketPayload {
        public static final Type<SliderPayload> TYPE = new Type<>(AEA.id("gui_slider"));
        public static final StreamCodec<FriendlyByteBuf, SliderPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.syncId); buf.writeVarInt(p.element); buf.writeDouble(p.value); buf.writeBoolean(p.release); },
            buf -> new SliderPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ state

    public static final class Builder {
        BitSet visible;
        private final List<Integer> progressIdx = new ArrayList<>();
        private final List<Float> progressVal = new ArrayList<>();
        private final List<Integer> sliderIdx = new ArrayList<>();
        private final List<Double> sliderVal = new ArrayList<>();
        private final List<Integer> entityIdx = new ArrayList<>();
        private final List<int[]> entityVal = new ArrayList<>();

        public Builder visible(BitSet v) {
            visible = (BitSet) v.clone();
            return this;
        }

        public Builder progress(int i, float v) {
            progressIdx.add(i);
            progressVal.add(v);
            return this;
        }

        public Builder slider(int i, double v) {
            sliderIdx.add(i);
            sliderVal.add(v);
            return this;
        }

        public Builder entities(int i, int[] ids) {
            entityIdx.add(i);
            entityVal.add(ids.length > MAX_ENTITY_IDS ? Arrays.copyOf(ids, MAX_ENTITY_IDS) : ids);
            return this;
        }

        public State build() {
            return new State(visible, List.copyOf(progressIdx), List.copyOf(progressVal), List.copyOf(sliderIdx),
                List.copyOf(sliderVal), List.copyOf(entityIdx), List.copyOf(entityVal));
        }
    }

    public record State(BitSet visible, List<Integer> progressIdx, List<Float> progressVal, List<Integer> sliderIdx,
                        List<Double> sliderVal, List<Integer> entityIdx, List<int[]> entityVal) {
        public static State full(GuiMenu menu) {
            Builder b = new Builder();
            b.visible = (BitSet) menu.visible.clone();
            for (int i = 0; i < menu.progress.length; i++) {
                if (menu.model.element(i) instanceof GuiElement.Image image && image.progress().isPresent()) b.progress(i, menu.progress[i]);
                if (!Double.isNaN(menu.sliders[i])) b.slider(i, menu.sliders[i]);
                if (menu.entityIds[i].length > 0) b.entities(i, menu.entityIds[i]);
            }
            return b.build();
        }

        public boolean isEmpty() {
            return visible == null && progressIdx.isEmpty() && sliderIdx.isEmpty() && entityIdx.isEmpty();
        }

        public void write(ByteBuf raw) {
            FriendlyByteBuf buf = raw instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(raw);
            int flags = (visible != null ? 1 : 0) | (!progressIdx.isEmpty() ? 2 : 0) | (!sliderIdx.isEmpty() ? 4 : 0) | (!entityIdx.isEmpty() ? 8 : 0);
            buf.writeByte(flags);
            if (visible != null) buf.writeBitSet(visible);
            if ((flags & 2) != 0) {
                buf.writeVarInt(progressIdx.size());
                for (int i = 0; i < progressIdx.size(); i++) {
                    buf.writeVarInt(progressIdx.get(i));
                    buf.writeFloat(progressVal.get(i));
                }
            }
            if ((flags & 4) != 0) {
                buf.writeVarInt(sliderIdx.size());
                for (int i = 0; i < sliderIdx.size(); i++) {
                    buf.writeVarInt(sliderIdx.get(i));
                    buf.writeDouble(sliderVal.get(i));
                }
            }
            if ((flags & 8) != 0) {
                buf.writeVarInt(entityIdx.size());
                for (int i = 0; i < entityIdx.size(); i++) {
                    buf.writeVarInt(entityIdx.get(i));
                    int[] ids = entityVal.get(i);
                    buf.writeVarInt(ids.length);
                    for (int id : ids) buf.writeVarInt(id);
                }
            }
        }

        public static State read(ByteBuf raw) {
            FriendlyByteBuf buf = raw instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(raw);
            Builder b = new Builder();
            byte flags = buf.readByte();
            if ((flags & 1) != 0) b.visible = buf.readBitSet();
            if ((flags & 2) != 0) {
                int count = Math.min(buf.readVarInt(), 4096);
                for (int i = 0; i < count; i++) b.progress(buf.readVarInt(), buf.readFloat());
            }
            if ((flags & 4) != 0) {
                int count = Math.min(buf.readVarInt(), 4096);
                for (int i = 0; i < count; i++) b.slider(buf.readVarInt(), buf.readDouble());
            }
            if ((flags & 8) != 0) {
                int count = Math.min(buf.readVarInt(), 4096);
                for (int i = 0; i < count; i++) {
                    int element = buf.readVarInt();
                    int len = Math.min(buf.readVarInt(), MAX_ENTITY_IDS);
                    int[] ids = new int[len];
                    for (int j = 0; j < len; j++) ids[j] = buf.readVarInt();
                    b.entities(element, ids);
                }
            }
            return b.build();
        }

        public void applyTo(GuiMenu menu) {
            int n = menu.progress.length;
            if (visible != null) {
                menu.visible.clear();
                menu.visible.or(visible.get(0, n));
            }
            for (int i = 0; i < progressIdx.size(); i++) {
                int e = progressIdx.get(i);
                if (e >= 0 && e < n) menu.progress[e] = Math.max(0.0F, Math.min(1.0F, progressVal.get(i)));
            }
            for (int i = 0; i < sliderIdx.size(); i++) {
                int e = sliderIdx.get(i);
                if (e >= 0 && e < n && menu.model.element(e) instanceof GuiElement.Slider slider) menu.sliders[e] = slider.clamp(sliderVal.get(i));
            }
            for (int i = 0; i < entityIdx.size(); i++) {
                int e = entityIdx.get(i);
                if (e >= 0 && e < n) menu.entityIds[e] = entityVal.get(i);
            }
        }
    }
}
