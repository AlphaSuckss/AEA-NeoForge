package dev.alphasucks.aea.command;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.addon.power.SeeInvisiblePower;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.PowerContainerAttachment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * {@code /aea selftest seeinvis <power>}: gives a fake player the power, then reports for every entity tagged
 * {@code si} whether it is invisible, whether it is invisible to the fake player, and the opacity the power gives it.
 */
final class SeeInvisibleSelfTest {
    private SeeInvisibleSelfTest() {}

    static int run(CommandSourceStack src, ResourceLocation power) {
        ServerLevel level = src.getLevel();
        FakePlayer viewer = FakePlayerFactory.getMinecraft(level);
        // A fake player has no container until something creates one.
        PowerContainer container = PowerContainerAttachment.getOrCreate(viewer);
        ResourceLocation source = AEA.id("selftest");
        container.addPower(power, source);
        try {
            List<String> lines = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (!e.getTags().contains("si")) continue;
                TreeSet<String> labels = new TreeSet<>(e.getTags());
                labels.remove("si");
                float opacity = SeeInvisiblePower.revealOpacity(viewer, e);
                lines.add(String.join(",", labels) + ": invisible=" + e.isInvisible() + " invisibleToViewer=" + e.isInvisibleTo(viewer)
                    + " opacity=" + (Float.isNaN(opacity) ? "-" : opacity));
            }
            lines.sort(null);
            for (String line : lines) src.sendSuccess(() -> Component.literal("[AEA selftest] see " + power + " | " + line), false);
            return lines.size();
        } finally {
            container.removePower(power, source);
        }
    }
}
