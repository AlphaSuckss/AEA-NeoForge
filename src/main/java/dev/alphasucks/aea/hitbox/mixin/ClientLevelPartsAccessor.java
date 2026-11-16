package dev.alphasucks.aea.hitbox.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client level's map of multipart boxes, which the crosshair is checked against. */
@OnlyIn(Dist.CLIENT)
@Mixin(ClientLevel.class)
public interface ClientLevelPartsAccessor {
    @Accessor(value = "partEntities", remap = false)
    Int2ObjectMap<PartEntity<?>> aea$parts();
}
