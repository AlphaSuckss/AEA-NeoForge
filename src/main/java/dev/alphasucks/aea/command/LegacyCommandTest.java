package dev.alphasucks.aea.command;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.legacy.ApoliCommandUpgrader;
import dev.alphasucks.aea.legacy.CommandUpgrader;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.function.Predicate;

/** {@code /aea selftest legacy}: runs Apoli 2.9 / Origins 1.10.2 command syntax through the upgrader on a fake player and checks 2.9 results. */
final class LegacyCommandTest {
    private LegacyCommandTest() {}

    private static int pass, fail;

    static int run(CommandSourceStack src) {
        pass = 0; fail = 0;
        MinecraftServer server = src.getServer();
        ServerLevel level = src.getLevel();
        net.neoforged.neoforge.common.util.FakePlayer fp = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("aea_legacy_cmd".getBytes()), "AEA_Legacy"));
        fp.moveTo(src.getPosition());
        CommandSourceStack as = fp.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        PowerContainer pc = dev.overgrown.apoli.PowerContainerAttachment.getOrCreate(fp);
        ResourceLocation wb = rl("origins:water_breathing"), fi = rl("origins:fall_immunity"), wv = rl("origins:water_vision"),
            lw = rl("origins:like_water"), res = rl("aea_audit:t/power__apoli_resource"), cmd = rl("apoli:command"), s = rl("aea_audit:src");

        try { server.getCommands().getDispatcher().execute("scoreboard objectives add aea_n dummy", as); } catch (Throwable ignored) {}
        exec(server, as, "power grant @s origins:water_breathing", r -> r == 1);
        check("grant without source uses apoli:command", pc.sourcesOf(wb).equals(Set.of(cmd)));
        exec(server, as, "power grant @s origins:fall_immunity aea_audit:src", r -> r == 1);
        exec(server, as, "power sources @s origins:water_breathing", r -> r == 1);
        exec(server, as, "power list @s", r -> r >= 2);
        exec(server, as, "power list @s true", r -> r >= 2);
        exec(server, as, "execute store result score @s aea_n run power list @s", r -> true);
        exec(server, as, "power has @s origins:fall_immunity", r -> r == 1);
        exec(server, as, "power revoke @s origins:water_breathing", r -> r == 1);
        check("revoke without source removes apoli:command", !pc.hasPower(wb));
        exec(server, as, "power revoke @s origins:fall_immunity aea_audit:src", r -> r == 1);
        check("revoke with source", !pc.hasPower(fi));
        exec(server, as, "power grant @s origins:fall_immunity", r -> r == 1);
        exec(server, as, "power grant @s origins:fall_immunity aea_audit:src", r -> r == 1);
        exec(server, as, "power sources @s origins:fall_immunity", r -> r == 2);
        exec(server, as, "power remove @s origins:fall_immunity", r -> r == 1);
        check("remove clears every source", !pc.hasPower(fi));
        exec(server, as, "power grant @s origins:water_vision aea_audit:src", r -> r == 1);
        check("water_vision expands to night_vision + toggle", pc.allPowers().size() >= 2);
        exec(server, as, "power revokeall @s aea_audit:src", r -> r >= 1);
        check("revokeall", !pc.hasPower(wv));
        exec(server, as, "power grant @s origins:like_water", r -> r == 1);
        exec(server, as, "power grant @s " + res, r -> r == 1);
        exec(server, as, "resource set @s " + res + " 3", r -> true);
        exec(server, as, "resource get @s " + res, r -> r == 3);
        exec(server, as, "resource change @s " + res + " 1", r -> true);
        exec(server, as, "resource get @s " + res, r -> r == 4);
        exec(server, as, "resource has @s " + res, r -> r >= 1);
        exec(server, as, "scoreboard players set @s aea_n 7", r -> true);
        exec(server, as, "resource operation @s " + res + " = @s aea_n", r -> true);
        exec(server, as, "resource get @s " + res, r -> r == 7);
        exec(server, as, "power clear @s", r -> r == 1);
        check("clear", pc.allPowers().isEmpty());
        exec(server, as, "origin set @s origins:origin aea_audit:legacy_upgrade", r -> r >= 1);
        exec(server, as, "origin has @s origins:origin aea_audit:legacy_upgrade", r -> r == 1);
        exec(server, as, "origin get @s origins:origin", r -> true);
        check("legacy origin's old built-in powers resolve", pc.hasPower(wb) && pc.hasPower(lw) && pc.hasPower(wv));
        dev.overgrown.origins.origin.Origin o = dev.overgrown.origins.origin.OriginRegistry.get(rl("aea_audit:legacy_upgrade"));
        check("origin upgrades decoded", o != null && o.upgrades().size() == 1
            && o.upgrades().get(0).origin().equals(rl("origins:merling"))
            && o.upgrades().get(0).announcement().map(c -> c.getStyle().getColor() != null).orElse(false));
        exec(server, as, "origin random @s origins:origin", r -> true);

        // Apoli 2.9 damage_over_time with protection_enchantment.
        ResourceLocation dot = rl("aea_audit:t/power__apoli_damage_over_time");
        dev.overgrown.apoli.power.Power dp = dev.overgrown.apoli.power.ApoliPowers.get(dot);
        AEA.LOGGER.info("[AEA legacy] damage_over_time decoded as {}", dp == null ? null : dp.typeId());
        net.minecraft.world.entity.Mob dz = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (dz != null) {
            dz.moveTo(src.getPosition()); dz.setNoAi(true); level.addFreshEntity(dz);
            PowerContainer zc = dev.overgrown.apoli.PowerContainerAttachment.getOrCreate(dz);
            zc.addPower(dot, s);
            float before = dz.getHealth();
            for (int i = 0; i < 25; i++) {
                dz.invulnerableTime = 0;
                if (zc instanceof dev.overgrown.apoli.power.PowerContainerImpl impl) impl.tickActive();
            }
            check("legacy damage_over_time deals damage after onset (" + before + " -> " + dz.getHealth() + ")", dz.getHealth() < before);
            dz.discard();
        }

        // 2.9 condition semantics on the upgraded JSON.
        fp.moveTo(100, 64, 0);
        cond(fp, null, "{\"type\":\"apoli:distance_from_coordinates\",\"reference\":\"world_origin\",\"comparison\":\"<\",\"compare_to\":150}", true);
        cond(fp, null, "{\"type\":\"apoli:distance_from_coordinates\",\"reference\":\"world_origin\",\"ignore_x\":true,\"comparison\":\"<\",\"compare_to\":80}", true);
        cond(fp, null, "{\"type\":\"apoli:distance_from_coordinates\",\"coordinates\":{\"x\":100,\"y\":64,\"z\":0},\"comparison\":\"<=\",\"compare_to\":0.5}", true);
        cond(fp, null, "{\"type\":\"apoli:distance_from_coordinates\",\"shape\":\"cube\",\"reference\":\"world_origin\",\"comparison\":\"==\",\"compare_to\":100}", true);
        cond(fp, null, "{\"type\":\"apoli:distance_from_coordinates\",\"reference\":\"world_origin\",\"comparison\":\">\",\"compare_to\":150}", false);
        cond(fp, null, "{\"type\":\"apoli:distance_from_spawn\",\"comparison\":\">=\",\"compare_to\":0}", true);
        net.minecraft.world.entity.Mob z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (z != null) {
            z.moveTo(100, 64, 2);
            String targetIsZombie = "{\"type\":\"apoli:target_condition\",\"condition\":{\"type\":\"apoli:entity_type\",\"entity_type\":\"minecraft:zombie\"}}";
            cond(fp, z, targetIsZombie, true);
            cond(z, fp, targetIsZombie, false);
            cond(z, fp, "{\"type\":\"apoli:undirected\",\"condition\":" + targetIsZombie + "}", true);
        }

        int p = pass, f = fail;
        src.sendSuccess(() -> Component.literal("[AEA selftest] legacy commands: " + p + " passed, " + f + " failed"), false);
        return p;
    }

    private static ResourceLocation rl(String s) { return ResourceLocation.parse(s); }

    private static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        AEA.LOGGER.info("[AEA legacy] {} {}", ok ? "PASS" : "FAIL", what);
    }

    private static void cond(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b, String json, boolean expect) {
        boolean got;
        String err = null;
        try {
            com.google.gson.JsonElement up = dev.alphasucks.aea.legacy.ApoliJsonUpgrader.upgrade(com.google.gson.JsonParser.parseString(json), 3465);
            if (b == null) {
                got = dev.overgrown.apoli.condition.EntityCondition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, up).getOrThrow()
                    .test(new dev.overgrown.apoli.condition.context.EntityCtx(a, a.level()));
            } else {
                got = dev.overgrown.apoli.condition.BiEntityCondition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, up).getOrThrow()
                    .test(new dev.overgrown.apoli.condition.context.BiEntityCtx(a, b, a.level()));
            }
        } catch (Throwable t) {
            got = !expect;
            err = t.toString();
        }
        check("condition " + json + " == " + expect + (err == null ? "" : " (" + err + ")"), got == expect);
    }

    private static void exec(MinecraftServer server, CommandSourceStack as, String legacy, Predicate<Integer> expect) {
        String up = CommandUpgrader.upgrade(ApoliCommandUpgrader.upgrade(legacy), 3465);
        int r;
        String err = null;
        try {
            r = server.getCommands().getDispatcher().execute(up, as);
        } catch (Throwable t) {
            r = Integer.MIN_VALUE;
            err = t.getMessage();
        }
        boolean ok = err == null && expect.test(r);
        if (ok) pass++; else fail++;
        AEA.LOGGER.info("[AEA legacy] {} '{}' -> '{}' = {}{}", ok ? "PASS" : "FAIL", legacy, up, r, err == null ? "" : " (" + err + ")");
    }
}
