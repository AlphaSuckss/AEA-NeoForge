package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.addon.AddonTypes;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Unique private static final ThreadLocal<Boolean> AEA$IN_DROPS = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true)
    private static void aea$modifyDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity be, @Nullable Entity entity,
                                        ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (entity == null || AEA$IN_DROPS.get()) return;
        List<AddonTypes.ModifyDropsCfg> cfgs = PowerLookup.active(entity, AddonTypes.MODIFY_DROPS, AddonTypes.ModifyDropsCfg.class);
        if (cfgs.isEmpty()) return;
        ItemStack modified = tool.copy();
        boolean any = false;
        for (AddonTypes.ModifyDropsCfg c : cfgs) {
            if (c.blockCondition().isPresent() && !c.blockCondition().get().test(new BlockCtx(pos, state, level, entity))) continue;
            if (c.itemAction().isPresent()) {
                ItemCtx ctx = new ItemCtx(modified, level, entity instanceof LivingEntity le ? le : null);
                c.itemAction().get().run(ctx);
                modified = ctx.stack();
                any = true;
            }
        }
        if (!any) return;
        AEA$IN_DROPS.set(true);
        try {
            cir.setReturnValue(Block.getDrops(state, level, pos, be, entity, modified));
        } finally {
            AEA$IN_DROPS.set(false);
        }
    }
}
