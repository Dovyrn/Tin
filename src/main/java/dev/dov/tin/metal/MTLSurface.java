package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.Collection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MTLSurface implements GpuSurfaceBackend {
    @Getter
    private final long handle;

    @Override
    public void configure(GpuSurface.Configuration config) {
        Native.nSurfaceConfigure(handle, config.width(), config.height(), config.presentMode().ordinal());
    }

    @Override
    public boolean isSuboptimal() {
        return Native.nSurfaceSuboptimal(handle);
    }

    @Override
    public void acquireNextTexture() {
        Native.nSurfaceAcquire(handle);
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend encoder, GpuTextureView view) {
        Native.nSurfaceBlit(handle, ((MTLEncoder) encoder).getHandle(), ((MTLView) view).getHandle());
    }

    @Override
    public void present() {
        Native.nSurfacePresent(handle);
    }

    @Override
    public void close() {
        Native.nSurfaceClose(handle);
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        var modes = new ArrayList<GpuSurface.PresentMode>();
        for (int i : Native.nSurfaceModes(handle)) {
            modes.add(GpuSurface.PresentMode.values()[i]);
        }
        return modes;
    }
}
