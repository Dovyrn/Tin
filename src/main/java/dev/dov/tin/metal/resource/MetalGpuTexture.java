package dev.dov.tin.metal.resource;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.dov.metalj.resources.textures.MTLTexture;
import lombok.Getter;

public class MetalGpuTexture extends GpuTexture {
    @Getter
    private final MTLTexture texture;
    private boolean closed;

    public MetalGpuTexture(MTLTexture texture, int usage, String label, GpuFormat format, int width, int height,
            int depthOrLayers, int mipLevels) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.texture = texture;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            texture.release();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
