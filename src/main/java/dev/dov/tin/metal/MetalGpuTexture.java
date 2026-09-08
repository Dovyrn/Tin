package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

public class MetalGpuTexture extends GpuTexture {
    private boolean closed;

    public MetalGpuTexture(int usage, String label, GpuFormat format, int width, int height, int depthOrLayers,
            int mipLevels) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
