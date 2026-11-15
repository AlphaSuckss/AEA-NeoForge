package dev.alphasucks.aea;

import com.mojang.logging.LogUtils;
import dev.alphasucks.aea.assets.PackAssets;
import dev.alphasucks.aea.client.AEAClient;
import dev.alphasucks.aea.command.AEACommands;
import dev.alphasucks.aea.compat.AEAHooks;
import dev.alphasucks.aea.compat.AEALoot;
import dev.alphasucks.aea.compat.AEATypes;
import dev.alphasucks.aea.content.TemporaryCobwebBlock;
import dev.alphasucks.aea.gui.AeaGui;
import dev.alphasucks.aea.hitbox.HitboxParts;
import dev.alphasucks.aea.textures.PackTextures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
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
    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            var reg = event.getRegistry(Registries.ENTITY_TYPE);
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "minion"), ResourceLocation.fromNamespaceAndPath("apoli", "minion"));
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "player_clone"), ResourceLocation.fromNamespaceAndPath("apoli", "clone"));
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("sync", "custom_projectile"), ResourceLocation.fromNamespaceAndPath("apoli", "custom_projectile"));
            // Origins 1.10.2's Enderian pearl was a damage-free ender pearl.
            reg.addAlias(ResourceLocation.fromNamespaceAndPath("origins", "enderian_pearl"), ResourceLocation.withDefaultNamespace("ender_pearl"));
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            ResourceLocation web = ResourceLocation.fromNamespaceAndPath("origins", "temporary_cobweb");
            if (!BuiltInRegistries.BLOCK.containsKey(web)) {
                event.register(Registries.BLOCK, web, () -> new TemporaryCobwebBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.COBWEB)
                        .noCollission().noLootTable()));
            }
        }
    }

    public AEA(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AEAConfig.SPEC);
        AEALoot.register(modBus);
        NeoForge.EVENT_BUS.register(new AEAHooks());
        NeoForge.EVENT_BUS.register(new AEACommands());
        modBus.addListener(RegisterEvent.class, AEA::onRegister);
        AeaGui.register(modBus);
        HitboxParts.register(modBus);
        PackTextures.register(modBus);
        PackAssets.register(modBus);
        if (FMLEnvironment.dist.isClient()) {
            AEAClient.init(modBus);
        }
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> {
            AEATypes.registerAll();
            LOGGER.info("[AEA] Ready: legacy Origins 1.10.2 / Apoli 2.9 / Sync 1.11.8 support is active.");
        }));
    }
}
