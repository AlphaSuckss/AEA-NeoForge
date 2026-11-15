package dev.alphasucks.aea.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.legacy.LegacyItemMerge;
import dev.alphasucks.aea.compat.legacy.condition.item.LegacyNbtCondition;
import dev.alphasucks.aea.legacy.Diagnostics;
import dev.alphasucks.aea.legacy.NbtUpgrader;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerResources;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.PowerTypes;
import dev.overgrown.apoli.power.builtin.ActionOnKeyPressPower;
import dev.overgrown.apoli.power.builtin.TogglePower;
import dev.overgrown.origins.origin.Origin;
import dev.overgrown.origins.origin.OriginLayer;
import dev.overgrown.origins.origin.OriginLayers;
import dev.overgrown.origins.origin.OriginManager;
import dev.overgrown.origins.origin.OriginRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Debug-only smoke test: grants every loaded power to a test mob and runs every function, logging any crash. */
final class SelfTest {
    private SelfTest() {}

    static final ResourceLocation SOURCE = AEA.id("selftest");
    static Mob subject;

    static int grant(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Mob mob = EntityType.ZOMBIE.create(level);
        if (mob == null) return 0;
        mob.moveTo(src.getPosition());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.addTag("aea_test");
        level.addFreshEntity(mob);
        subject = mob;
        PowerContainer c = PowerContainerAttachment.getOrCreate(mob);
        int ok = 0, fail = 0;
        Set<String> errors = new LinkedHashSet<>();
        for (ResourceLocation id : new ArrayList<>(ApoliPowers.view().keySet())) {
            if (ApoliPowers.isSubPower(id)) continue;
            try {
                c.addPower(id, SOURCE);
                ok++;
            } catch (Throwable t) {
                fail++;
                if (errors.add(t.toString())) AEA.LOGGER.error("[AEA selftest] granting {} crashed", id, t);
            }
        }
        int o = ok, f = fail;
        src.sendSuccess(() -> Component.literal("[AEA selftest] granted " + o + " powers, " + f + " crashed"), false);
        return ok;
    }

    /** Gives a fake player every origin in turn, ticks it and fires its key-press powers. */
    static int player(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        FakePlayer fp = FakePlayerFactory.get(level,
            new GameProfile(UUID.nameUUIDFromBytes("aea_selftest".getBytes()), "AEA_Selftest"));
        fp.moveTo(src.getPosition());
        int origins = 0, fired = 0, fail = 0;
        Set<String> errors = new LinkedHashSet<>();
        ResourceLocation keyPress = PowerTypeRegistry.resolveId(ResourceLocation.fromNamespaceAndPath("apoli", "action_on_key_press"));
        for (OriginLayer layer : OriginLayers.enabledOrdered()) {
            for (Origin origin : new ArrayList<>(OriginRegistry.all())) {
                try {
                    OriginManager.chooseOrigin(fp, layer.id(), origin.id(), false);
                    for (int i = 0; i < 5; i++) fp.doTick();
                    PowerContainer c = PowerContainer.of(fp);
                    if (c != null) {
                        for (ResourceLocation pid : new ArrayList<>(c.allPowers())) {
                            Power p = ApoliPowers.get(pid);
                            if (p == null || !keyPress.equals(PowerTypeRegistry.resolveId(p.typeId()))) continue;
                            if (p.config() instanceof ActionOnKeyPressPower.Config cfg) {
                                try {
                                    PowerTypes.ACTION_ON_KEY_PRESS.tryActivate(pid, cfg, c);
                                    fired++;
                                } catch (Throwable t) {
                                    fail++;
                                    if (errors.add(t.toString())) AEA.LOGGER.error("[AEA selftest] key power {} of {} crashed", pid, origin.id(), t);
                                }
                            }
                        }
                    }
                    for (int i = 0; i < 5; i++) fp.doTick();
                    origins++;
                } catch (Throwable t) {
                    fail++;
                    if (errors.add(t.toString())) AEA.LOGGER.error("[AEA selftest] origin {} crashed", origin.id(), t);
                }
            }
        }
        int o = origins, k = fired, f = fail;
        src.sendSuccess(() -> Component.literal("[AEA selftest] " + o + " origin applications, " + k + " key powers fired, " + f + " crashed"), false);
        return origins;
    }

    static int functions(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        CommandSourceStack as = subject != null ? subject.createCommandSourceStack().withPermission(4).withSuppressedOutput()
            : src.withSuppressedOutput();
        int ok = 0, fail = 0;
        Set<String> errors = new LinkedHashSet<>();
        List<ResourceLocation> ids = new ArrayList<>();
        server.getFunctions().getFunctionNames().forEach(ids::add);
        for (ResourceLocation id : ids) {
            if (id.getNamespace().equals("minecraft") || id.getPath().contains("debug")) continue;
            try {
                server.getFunctions().get(id).ifPresent(fn -> server.getFunctions().execute(fn, as));
                ok++;
            } catch (Throwable t) {
                fail++;
                if (errors.add(t.toString())) AEA.LOGGER.error("[AEA selftest] function {} crashed", id, t);
            }
        }
        int o = ok, f = fail;
        src.sendSuccess(() -> Component.literal("[AEA selftest] ran " + o + " functions, " + f + " crashed"), false);
        return ok;
    }

    /** Applies every upgraded merge_nbt in the loaded packs to sample stacks, and checks a Sapu's Alchemist chain. */
    public static int legacyItems(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        MapCodec<LegacyItemMerge.Patch> codec = LegacyItemMerge.codec("nbt");
        List<JsonObject> found = new ArrayList<>();
        for (JsonElement e : Diagnostics.POWERS.values()) collectMerges(e, found);
        int ok = 0, bad = 0;
        Item[] bases = {Items.POTION, Items.IRON_SWORD, Items.WRITTEN_BOOK, Items.PAPER};
        for (JsonObject o : found) {
            var parsed = codec.codec().parse(JsonOps.INSTANCE, o);
            if (parsed.result().isEmpty()) { bad++; AEA.LOGGER.error("[AEA selftest] merge_nbt didn't parse: {} {}", parsed.error().get().message(), o); continue; }
            for (Item it : bases) {
                try { parsed.result().get().apply(new ItemStack(it), level.registryAccess()); ok++; }
                catch (Throwable t) { bad++; AEA.LOGGER.error("[AEA selftest] merge_nbt crashed on " + it, t); }
            }
        }
        final int okF = ok, badF = bad;
        src.sendSuccess(() -> Component.literal("[AEA selftest] legacy merge_nbt: " + found.size() + " actions, " + okF + " ok, " + badF + " failed"), false);
        AEA.LOGGER.info("[AEA selftest] legacy merge_nbt: {} actions, {} applications ok, {} failed", found.size(), ok, bad);

        // Sapu's Alchemist: water bottle + coal -> Simple Solvent, then the conditions later steps check.
        String solvent = "{display:{Name:'{\"text\":\"Simple Solvent\",\"italic\":false}',Lore:['{\"text\":\"Part of a greater whole.\",\"color\":\"dark_purple\",\"italic\":true}']},pouchStorable:1b,isSolvent:1b,solventLevel:1,CustomPotionEffects:[{Id:9,Amplifier:0b,Duration:160,ShowParticles:0b},{Id:19,Amplifier:0b,Duration:160,ShowParticles:0b}],Potion:\"minecraft:mundane\",CustomPotionColor:12582911}";
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        boolean isWater = matchesLegacy("{Potion:\"minecraft:water\"}", water, level);
        new LegacyItemMerge.Patch(NbtUpgrader.parse(solvent), 3465).apply(water, level.registryAccess());
        AEA.LOGGER.info("[AEA selftest] solvent stack: {}", water.getComponentsPatch());
        String[] checks = {"{isSolvent:1b}", "{solventLevel:1}", "{Potion:\"minecraft:mundane\"}", "{display:{Name:'{\"text\":\"Simple Solvent\",\"italic\":false}'}}", "{CustomPotionColor:12582911}"};
        StringBuilder sb = new StringBuilder("water=" + isWater);
        for (String c : checks) sb.append(", ").append(c).append('=').append(matchesLegacy(c, water, level));
        AEA.LOGGER.info("[AEA selftest] solvent checks: {}", sb);
        // A second merge on top keeps the earlier potion data (1.20.1 merge semantics).
        new LegacyItemMerge.Patch(NbtUpgrader.parse("{isCalcinated:1b,CustomPotionColor:255}"), 3465).apply(water, level.registryAccess());
        AEA.LOGGER.info("[AEA selftest] after second merge: {} | mundane={} calcinated={} solvent={}", water.getComponentsPatch(),
            matchesLegacy("{Potion:\"minecraft:mundane\"}", water, level),
            matchesLegacy("{isCalcinated:1b}", water, level),
            matchesLegacy("{isSolvent:1b}", water, level));
        return 1;
    }

    /** Does {@code stack} match the 1.20.1 tag {@code snbt}, as {@code aea:legacy_nbt} would test it? */
    private static boolean matchesLegacy(String snbt, ItemStack stack, Level level) {
        CompoundTag t = NbtUpgrader.parse(snbt);
        return t != null && new LegacyNbtCondition().test(new LegacyNbtCondition.Cfg(t), new ItemCtx(stack, level, null));
    }

    private static void collectMerges(JsonElement e, List<JsonObject> out) {
        if (e instanceof JsonObject o) {
            if (o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().equals("aea:legacy_merge_nbt")) out.add(o);
            for (var en : o.entrySet()) collectMerges(en.getValue(), out);
        } else if (e instanceof JsonArray a) {
            for (JsonElement x : a) collectMerges(x, out);
        }
    }

    /** Debug: grant a power to a test zombie, toggle its toggles, hit it, and log resources/health. */
    public static int probe(CommandSourceStack src, ResourceLocation powerId) {
        ServerLevel level = src.getLevel();
        Mob mob = EntityType.ZOMBIE.create(level);
        Mob attacker = EntityType.ZOMBIE.create(level);
        if (mob == null || attacker == null) return 0;
        mob.moveTo(src.getPosition()); attacker.moveTo(src.getPosition().add(1, 0, 0));
        mob.setNoAi(true); attacker.setNoAi(true);
        level.addFreshEntity(mob); level.addFreshEntity(attacker);
        PowerContainer c = PowerContainerAttachment.getOrCreate(mob);
        c.addPower(powerId, SOURCE);
        List<ResourceLocation> subs = new ArrayList<>();
        for (ResourceLocation id : c.allPowers()) subs.add(id);
        AEA.LOGGER.info("[AEA probe] {} gave: {}", powerId, subs);
        Supplier<String> state = () -> {
            StringBuilder sb = new StringBuilder("hp=" + mob.getHealth());
            for (ResourceLocation id : c.allPowers()) {
                OptionalInt r = PowerResources.read(c, id);
                if (r.isPresent()) sb.append(' ').append(id.getPath()).append('=').append(r.getAsInt());
                sb.append(' ').append(id.getPath()).append(TogglePower.isActive(mob, id) ? "[toggled]" : "");
            }
            return sb.toString();
        };
        AEA.LOGGER.info("[AEA probe] start: {}", state.get());
        for (ResourceLocation id : new ArrayList<>(c.allPowers())) {
            Power p = ApoliPowers.get(id);
            if (p != null && p.config() instanceof TogglePower.Config tc) {
                boolean r = TogglePower.toggleByKey(c, id, tc);
                AEA.LOGGER.info("[AEA probe] toggleByKey {} -> {}", id, r);
            }
        }
        AEA.LOGGER.info("[AEA probe] after toggle: {}", state.get());
        for (int i = 0; i < 3; i++) {
            mob.invulnerableTime = 0;
            mob.hurt(level.damageSources().mobAttack(attacker), 4.0f);
            AEA.LOGGER.info("[AEA probe] after hit {}: {} vel={}", i + 1, state.get(), mob.getDeltaMovement());
        }
        mob.invulnerableTime = 0;
        mob.hurt(level.damageSources().generic(), 2.0f);
        AEA.LOGGER.info("[AEA probe] after generic: {}", state.get());
        level.getServer().execute(() -> {});
        final int[] t = {0};
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (t[0] < 0) return;
            if (++t[0] == 70) { AEA.LOGGER.info("[AEA probe] after 70 ticks: {}", state.get()); t[0] = -1; }
        });
        return 1;
    }

    /** Debug: writes every upgraded legacy power of a namespace to ./aea-dump/&lt;ns&gt;/ (used to build AEA's own data). */
    public static int dump(CommandSourceStack src, String ns) {
        int n = 0;
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (Map.Entry<ResourceLocation, JsonElement> e : Diagnostics.POWERS.entrySet()) {
            if (!e.getKey().getNamespace().equals(ns)) continue;
            try {
                Path out = Path.of("aea-dump", ns, e.getKey().getPath() + ".json");
                Files.createDirectories(out.getParent());
                Files.writeString(out, gson.toJson(e.getValue()));
                n++;
            } catch (Exception ex) {
                AEA.LOGGER.error("[AEA selftest] dump {} failed", e.getKey(), ex);
            }
        }
        int fn = n;
        src.sendSuccess(() -> Component.literal("[AEA selftest] dumped " + fn + " powers"), false);
        return n;
    }
}
