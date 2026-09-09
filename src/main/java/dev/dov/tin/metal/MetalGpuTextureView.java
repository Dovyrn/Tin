package dev.dov.tin.metal;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.resources.textures.MTLTexture;
import lombok.Getter;

public class MetalGpuTextureView extends GpuTextureView {
    @Getter
    private final MTLTexture view;
    private boolean closed;

    public MetalGpuTextureView(MTLTexture view, GpuTexture texture, int baseMipLevel, int mipLevels) {
        super(texture, baseMipLevel, mipLevels);
        this.view = view;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            view.release();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
