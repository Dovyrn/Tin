package dev.dov.tin.mixin;

import com.mojang.blaze3d.systems.GpuBackend;
import dev.dov.tin.metal.MetalBackend;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PreferredGraphicsApi.class, remap = false)
public class MixinPreferredGraphicsApi {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void forceMetal(CallbackInfoReturnable<GpuBackend[]> cir) {
        if (Util.getPlatform() == Util.OS.OSX) {
            cir.setReturnValue(new GpuBackend[]{new MetalBackend()});
        }
    }
}
