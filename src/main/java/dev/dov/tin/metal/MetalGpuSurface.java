package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@lombok.RequiredArgsConstructor
public class MetalGpuSurface implements GpuSurfaceBackend {
    private final MetalDevice device;
    private final long window;

    @Override
    public void configure(GpuSurface.@NotNull Configuration config) {
    }

    @Override
    public boolean isSuboptimal() {
        return false;
    }

    @Override
    public void acquireNextTexture() {
    }

    @Override
    public void blitFromTexture(@NotNull CommandEncoderBackend commandEncoder, @NotNull GpuTextureView textureView) {
    }

    @Override
    public void present() {
    }

    @Override
    public void close() {
    }

    @Override
    public @NotNull Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.IMMEDIATE);
    }
}
