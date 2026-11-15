package dev.alphasucks.aea.mixin.legacy;

import dev.alphasucks.aea.legacy.LegacyResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {
    @Inject(method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/IoSupplier;",
        at = @At("HEAD"), cancellable = true)
    private void aea$legacyGetResource(PackType type, ResourceLocation location, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        IoSupplier<InputStream> result = LegacyResources.getResource((PackResources) (Object) this, type, location);
        if (result != LegacyResources.PASS) cir.setReturnValue(result);
    }

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    private void aea$legacyListResources(PackType type, String namespace, String path, PackResources.ResourceOutput output, CallbackInfo ci) {
        if (LegacyResources.listResources((PackResources) (Object) this, type, namespace, path, output)) ci.cancel();
    }
}
