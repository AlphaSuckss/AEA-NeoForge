package dev.alphasucks.aea.compat.addon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alphasucks.aea.AEA;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Power tags: {@code data/<namespace>/tags/power/<path>.json} lists powers in the vanilla tag format, and
 * {@code #<namespace>:<path>} refers to the list. Tags are merged across data packs unless one sets {@code replace}.
 * The server sends them to players, because rendering checks them on the player's game.
 */
public final class PowerTags {
    private PowerTags() {}

    private static final String FOLDER = "tags/power";
    private static final int MAX_TAGS = 4096;
    private static final int MAX_ENTRIES = 4096;

    private static volatile Map<ResourceLocation, Set<ResourceLocation>> server = Map.of();
    private static volatile Map<ResourceLocation, Set<ResourceLocation>> client = Map.of();

    /** The powers in a tag, with nested tags already expanded. Empty when the tag does not exist. */
    public static Set<ResourceLocation> get(ResourceLocation tag, boolean clientSide) {
        return (clientSide ? client : server).getOrDefault(tag, Set.of());
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").optional()
            .playToClient(SyncPayload.TYPE, SyncPayload.CODEC, (payload, ctx) -> client = payload.tags()));
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener((ResourceManagerReloadListener) PowerTags::load));
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, e -> {
            SyncPayload payload = new SyncPayload(server);
            e.getRelevantPlayers().forEach(player -> {
                if (player.connection != null && player.connection.hasChannel(SyncPayload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
            });
        });
    }

    private static void load(ResourceManager manager) {
        // Each tag's direct entries, in pack order; an entry starting with # is another tag.
        Map<ResourceLocation, List<String>> raw = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> file : manager.listResourceStacks(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            ResourceLocation tag = ResourceLocation.fromNamespaceAndPath(file.getKey().getNamespace(),
                path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
            List<String> entries = raw.computeIfAbsent(tag, k -> new ArrayList<>());
            for (Resource resource : file.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("replace") && json.get("replace").getAsBoolean()) entries.clear();
                    if (!json.has("values")) continue;
                    for (JsonElement value : json.getAsJsonArray("values")) {
                        String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                        if (entries.size() < MAX_ENTRIES) entries.add(entry);
                    }
                } catch (Exception e) {
                    AEA.LOGGER.warn("[AEA] Could not read power tag {} from {}: {}", tag, resource.sourcePackId(), e.toString());
                }
            }
            if (raw.size() >= MAX_TAGS) break;
        }
        Map<ResourceLocation, Set<ResourceLocation>> resolved = new HashMap<>();
        for (ResourceLocation tag : raw.keySet()) resolved.put(tag, Set.copyOf(expand(tag, raw, new HashSet<>())));
        server = Map.copyOf(resolved);
        if (!resolved.isEmpty()) AEA.LOGGER.info("[AEA] Loaded {} power tag(s).", resolved.size());
    }

    private static Set<ResourceLocation> expand(ResourceLocation tag, Map<ResourceLocation, List<String>> raw, Set<ResourceLocation> visiting) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        if (!visiting.add(tag)) {
            AEA.LOGGER.warn("[AEA] Power tag #{} includes itself.", tag);
            return out;
        }
        for (String entry : raw.getOrDefault(tag, List.of())) {
            boolean nested = entry.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(nested ? entry.substring(1) : entry);
            if (id == null) {
                AEA.LOGGER.warn("[AEA] Power tag #{} has an entry that is not an ID: \"{}\".", tag, entry);
            } else if (nested) {
                out.addAll(expand(id, raw, visiting));
            } else {
                out.add(id);
            }
        }
        visiting.remove(tag);
        return out;
    }

    /** Server → client: every power tag with its expanded list of powers. */
    public record SyncPayload(Map<ResourceLocation, Set<ResourceLocation>> tags) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(AEA.id("power_tags"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.tags.size());
            p.tags.forEach((tag, powers) -> {
                buf.writeResourceLocation(tag);
                buf.writeVarInt(powers.size());
                for (ResourceLocation power : powers) buf.writeResourceLocation(power);
            });
        }, buf -> {
            int count = Math.min(buf.readVarInt(), MAX_TAGS);
            Map<ResourceLocation, Set<ResourceLocation>> tags = new HashMap<>();
            for (int i = 0; i < count; i++) {
                ResourceLocation tag = buf.readResourceLocation();
                int size = Math.min(buf.readVarInt(), MAX_ENTRIES);
                Set<ResourceLocation> powers = new LinkedHashSet<>();
                for (int j = 0; j < size; j++) powers.add(buf.readResourceLocation());
                tags.put(tag, Set.copyOf(powers));
            }
            return new SyncPayload(Map.copyOf(tags));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
