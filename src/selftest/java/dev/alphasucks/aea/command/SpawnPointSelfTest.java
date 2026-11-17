package dev.alphasucks.aea.command;

import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /aea selftest spawn [power]}: places a bed and a respawn anchor near 0 0, then tries to set a fake player's
 * spawn point from a bed, an anchor, {@code /spawnpoint} and a plain forced call, reporting where it ended up.
 */
final class SpawnPointSelfTest {
    private SpawnPointSelfTest() {}

    static int run(CommandSourceStack src, ResourceLocation power) {
        ServerLevel level = src.getLevel();
        BlockPos bed = new BlockPos(0, 100, 0), anchor = new BlockPos(3, 100, 0), plain = new BlockPos(6, 100, 0);
        level.setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        level.setBlockAndUpdate(bed.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState());

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        PowerContainer container = PowerContainerAttachment.getOrCreate(player);
        ResourceLocation source = AEA.id("selftest");
        if (power != null) container.addPower(power, source);
        try {
            String name = power == null ? "none" : power.toString();
            report(src, name, "bed", player, () -> player.setRespawnPosition(level.dimension(), bed, 0, false, false));
            report(src, name, "anchor", player, () -> player.setRespawnPosition(level.dimension(), anchor, 0, true, false));
            report(src, name, "other", player, () -> player.setRespawnPosition(level.dimension(), plain, 0, true, false));
            // A command run from inside this one would be queued until after it, so call /spawnpoint's method itself.
            report(src, name, "command", player, () -> {
                try {
                    java.lang.reflect.Method set = net.minecraft.server.commands.SetSpawnCommand.class.getDeclaredMethod("setSpawn",
                        CommandSourceStack.class, java.util.Collection.class, BlockPos.class, float.class);
                    set.setAccessible(true);
                    set.invoke(null, src.withSuppressedOutput(), java.util.List.of(player), new BlockPos(9, 100, 0), 0F);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            return 4;
        } finally {
            if (power != null) container.removePower(power, source);
            player.setRespawnPosition(level.dimension(), null, 0, false, false);
        }
    }

    private static void report(CommandSourceStack src, String power, String how, FakePlayer player, Runnable attempt) {
        player.setRespawnPosition(player.level().dimension(), null, 0, false, false);
        attempt.run();
        BlockPos at = player.getRespawnPosition();
        String where = at == null ? "unset" : at.getX() + "," + at.getY() + "," + at.getZ();
        src.sendSuccess(() -> Component.literal("[AEA selftest] spawn " + power + " | " + how + " -> " + where), false);
    }
}
