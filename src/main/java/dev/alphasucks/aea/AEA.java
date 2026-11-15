package dev.alphasucks.aea;

import com.mojang.logging.LogUtils;
import dev.alphasucks.aea.compat.AEATypes;
import dev.alphasucks.aea.compat.AEALoot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * AEA (Alpha's Everything Addon) for Overgrown's Origins — NeoForge 1.21.1.
 * Lets Origins 1.10.2 / Apoli 2.9 / Sync 1.11 content run on Overgrown's Apoli + Origins.
 */
@Mod(AEA.MOD_ID)
public final class AEA {
    public static final String MOD_ID = "aea";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Sync's entity ids → Overgrown's equivalents, so selectors and NBT that name them still resolve. */
    private static void onRegister(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ENTITY_TYPE)) {
            var reg = event.getRegistry(net.minecraft.core.registries.Registries.ENTITY_TYPE);
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "minion"), ResourceLocation.fromNamespaceAndPath("apoli", "minion"));
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "player_clone"), ResourceLocation.fromNamespaceAndPath("apoli", "clone"));
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "custom_projectile"), ResourceLocation.fromNamespaceAndPath("apoli", "custom_projectile"));
            // Origins 1.10.2's Enderian pearl was a damage-free ender pearl.
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("origins", "enderian_pearl"), ResourceLocation.withDefaultNamespace("ender_pearl"));
        }
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) {
            ResourceLocation web = ResourceLocation.fromNamespaceAndPath("origins", "temporary_cobweb");
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(web)) {
                event.register(net.minecraft.core.registries.Registries.BLOCK, web, () -> new dev.alphasucks.aea.content.TemporaryCobwebBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COBWEB)
                        .noCollission().noLootTable()));
            }
        }
    }

    public AEA(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AEAConfig.SPEC);
        AEALoot.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new dev.alphasucks.aea.compat.AEAHooks());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new dev.alphasucks.aea.command.AEACommands());
        modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class, AEA::onRegister);
        dev.alphasucks.aea.gui.AeaGui.register(modBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            dev.alphasucks.aea.client.AEAClient.init(modBus);
        }
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> {
            AEATypes.registerAll();
            LOGGER.info("[AEA] Ready: legacy Origins 1.10.2 / Apoli 2.9 / Sync 1.11.8 support is active.");
        }));
    }
}
