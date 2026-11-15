package dev.alphasucks.aea.hitbox.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The level's map of multipart boxes, which attacks and projectiles are checked against. */
@Mixin(ServerLevel.class)
public interface ServerLevelPartsAccessor {
    @Accessor("dragonParts")
    Int2ObjectMap<PartEntity<?>> aea$parts();
}
