package dev.alphasucks.aea.gamerule;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Data pack gamerules: {@code data/<namespace>/gamerules/<name>.json} defines the rule {@code <namespace>:<name>}.
 * Players set it with {@code /gamerule}, and packs read it with the {@code aea:gamerule} condition or the
 * {@code gamerule(...)} expression function. Values are saved with the world and sent to players, so conditions on
 * the player's game see them too.
 */
public final class DataGameRules {
    private DataGameRules() {}

    private static final String FOLDER = "gamerules";

    public enum Type implements StringRepresentable {
        BOOLEAN, INTEGER;

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A rule's definition. Booleans are stored as 1 and 0. */
    public record Rule(Type type, int defaultValue, int min, int max) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.<Raw>create(i -> i.group(
            Type.CODEC.fieldOf("type").forGetter(Raw::type),
            Codec.either(Codec.BOOL, Codec.INT).optionalFieldOf("default").forGetter(Raw::def),
            Codec.INT.optionalFieldOf("min", Integer.MIN_VALUE).forGetter(Raw::min),
            Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(Raw::max)
        ).apply(i, Raw::new)).comapFlatMap(Raw::toRule, Raw::of);

        int clamp(int value) {
            return type == Type.BOOLEAN ? (value != 0 ? 1 : 0) : Mth.clamp(value, min, max);
        }

        String display(int value) {
            return type == Type.BOOLEAN ? String.valueOf(value != 0) : String.valueOf(value);
        }
    }

    private record Raw(Type type, java.util.Optional<com.mojang.datafixers.util.Either<Boolean, Integer>> def, int min, int max) {
        com.mojang.serialization.DataResult<Rule> toRule() {
            if (min > max) return com.mojang.serialization.DataResult.error(() -> "min is larger than max");
            int value = def.map(e -> e.map(b -> b ? 1 : 0, n -> n)).orElse(0);
            if (type == Type.BOOLEAN) return com.mojang.serialization.DataResult.success(new Rule(type, value != 0 ? 1 : 0, 0, 1));
            if (def.isPresent() && def.get().left().isPresent()) return com.mojang.serialization.DataResult.error(() -> "an integer rule needs a number as its default");
            return com.mojang.serialization.DataResult.success(new Rule(type, Mth.clamp(value, min, max), min, max));
        }

        static Raw of(Rule rule) {
            com.mojang.datafixers.util.Either<Boolean, Integer> def = rule.type() == Type.BOOLEAN
                ? com.mojang.datafixers.util.Either.left(rule.defaultValue() != 0) : com.mojang.datafixers.util.Either.right(rule.defaultValue());
            return new Raw(rule.type(), java.util.Optional.of(def), rule.min(), rule.max());
        }
    }

    private static volatile Map<ResourceLocation, Rule> rules = Map.of();
    private static volatile Map<ResourceLocation, Integer> client = Map.of();
    private static Values saved;
    private static MinecraftServer server;
    /** The command tree being built for the reload in progress; rule commands are added once the rules are known. */
    private static CommandDispatcher<CommandSourceStack> dispatcher;

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").optional()
            .playToClient(SyncPayload.TYPE, SyncPayload.CODEC, (payload, ctx) -> client = payload.values()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> dispatcher = e.getDispatcher());
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener((ResourceManagerReloadListener) DataGameRules::load));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> {
            server = e.getServer();
            saved = server.overworld().getDataStorage().computeIfAbsent(Values.FACTORY, "aea_gamerules");
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            server = null;
            saved = null;
        });
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, e -> {
            SyncPayload payload = payload();
            e.getRelevantPlayers().forEach(player -> {
                if (player.connection != null && player.connection.hasChannel(SyncPayload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
            });
        });
    }

    private static void load(ResourceManager manager) {
        Map<ResourceLocation, Rule> found = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getKey().getNamespace(),
                path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
            try (Reader reader = file.getValue().openAsReader()) {
                Rule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .ifSuccess(rule -> found.put(id, rule))
                    .ifError(error -> AEA.LOGGER.warn("[AEA] Could not load gamerule {}: {}", id, error.message()));
            } catch (Exception e) {
                AEA.LOGGER.warn("[AEA] Could not load gamerule {}: {}", id, e.toString());
            }
        }
        rules = Map.copyOf(found);
        if (!found.isEmpty()) AEA.LOGGER.info("[AEA] Loaded {} data pack gamerule(s).", found.size());
        if (dispatcher != null) addCommands(dispatcher);
    }

    /** Adds {@code /gamerule <namespace>:<name> [value]} for every rule, next to the vanilla rules. */
    private static void addCommands(CommandDispatcher<CommandSourceStack> target) {
        CommandNode<CommandSourceStack> root = target.getRoot().getChild("gamerule");
        if (root == null) {
            target.register(Commands.literal("gamerule").requires(s -> s.hasPermission(2)));
            root = target.getRoot().getChild("gamerule");
        }
        for (Map.Entry<ResourceLocation, Rule> entry : rules.entrySet()) {
            ResourceLocation id = entry.getKey();
            Rule rule = entry.getValue();
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(id.toString()).executes(c -> query(c, id));
            if (rule.type() == Type.BOOLEAN) {
                node.then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> set(c, id, BoolArgumentType.getBool(c, "value") ? 1 : 0)));
            } else {
                node.then(Commands.argument("value", IntegerArgumentType.integer(rule.min(), rule.max())).executes(c -> set(c, id, IntegerArgumentType.getInteger(c, "value"))));
            }
            root.addChild(node.build());
        }
    }

    private static int query(CommandContext<CommandSourceStack> c, ResourceLocation id) {
        Rule rule = rules.get(id);
        if (rule == null) {
            c.getSource().sendFailure(Component.literal("The gamerule " + id + " is no longer defined by any data pack."));
            return 0;
        }
        int value = serverValue(id, rule);
        c.getSource().sendSuccess(() -> Component.translatable("commands.gamerule.query", id.toString(), rule.display(value)), false);
        return value;
    }

    private static int set(CommandContext<CommandSourceStack> c, ResourceLocation id, int value) {
        Rule rule = rules.get(id);
        if (rule == null || saved == null) {
            c.getSource().sendFailure(Component.literal("The gamerule " + id + " is no longer defined by any data pack."));
            return 0;
        }
        int clamped = rule.clamp(value);
        saved.values.put(id.toString(), clamped);
        saved.setDirty();
        PacketDistributor.sendToAllPlayers(payload());
        c.getSource().sendSuccess(() -> Component.translatable("commands.gamerule.set", id.toString(), rule.display(clamped)), true);
        return clamped;
    }

    /** Saved values are keyed by the id's string; cached so a condition test doesn't build one each time. */
    private static final Map<ResourceLocation, String> KEYS = new java.util.concurrent.ConcurrentHashMap<>();

    private static int serverValue(ResourceLocation id, Rule rule) {
        Values values = saved;
        Integer stored = values == null ? null : values.values.get(KEYS.computeIfAbsent(id, ResourceLocation::toString));
        return stored == null ? rule.defaultValue() : rule.clamp(stored);
    }

    /** The value of a data pack gamerule as a number (booleans are 1 and 0), or 0 when no pack defines it. */
    public static int value(ResourceLocation id, Level level) {
        if (level != null && level.isClientSide()) return client.getOrDefault(id, 0);
        Rule rule = rules.get(id);
        return rule == null ? 0 : serverValue(id, rule);
    }

    private static Map<String, GameRules.Key<?>> vanillaKeys;

    /**
     * The value of a vanilla gamerule, such as {@code keepInventory}. The player's game only knows a few vanilla rules,
     * so this is only reliable on the server.
     */
    public static int vanillaValue(String name, Level level) {
        if (vanillaKeys == null) {
            Map<String, GameRules.Key<?>> keys = new HashMap<>();
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    keys.put(key.getId(), key);
                }
            });
            vanillaKeys = keys;
        }
        GameRules.Key<?> key = vanillaKeys.get(name);
        if (key == null || level == null) return 0;
        GameRules.Value<?> value = level.getGameRules().getRule(key);
        if (value instanceof GameRules.BooleanValue b) return b.get() ? 1 : 0;
        if (value instanceof GameRules.IntegerValue n) return n.get();
        return 0;
    }

    private static SyncPayload payload() {
        Map<ResourceLocation, Integer> values = new HashMap<>();
        rules.forEach((id, rule) -> values.put(id, serverValue(id, rule)));
        return new SyncPayload(Map.copyOf(values));
    }

    /** The values set with {@code /gamerule}, saved with the world. Values of rules no pack defines are kept. */
    private static final class Values extends SavedData {
        static final SavedData.Factory<Values> FACTORY = new SavedData.Factory<>(Values::new, Values::read, null);

        final Map<String, Integer> values = new HashMap<>();

        static Values read(CompoundTag tag, HolderLookup.Provider registries) {
            Values out = new Values();
            for (String key : tag.getAllKeys()) out.values.put(key, tag.getInt(key));
            return out;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            values.forEach(tag::putInt);
            return tag;
        }
    }

    /** Server → client: the current value of every data pack gamerule. */
    public record SyncPayload(Map<ResourceLocation, Integer> values) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(AEA.id("gamerules"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.values.size());
            p.values.forEach((id, value) -> {
                buf.writeResourceLocation(id);
                buf.writeVarInt(value);
            });
        }, buf -> {
            int count = Math.min(buf.readVarInt(), 4096);
            Map<ResourceLocation, Integer> values = new HashMap<>();
            for (int i = 0; i < count; i++) values.put(buf.readResourceLocation(), buf.readVarInt());
            return new SyncPayload(Map.copyOf(values));
        });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
