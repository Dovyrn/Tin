package dev.dov.tin.metal;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.resources.textures.MTLTexture;
import lombok.Getter;

public class MetalGpuTextureView extends GpuTextureView {
    @Getter
    private final MTLTexture view;
    private final boolean owned;
    private boolean closed;

    public MetalGpuTextureView(MTLTexture view, GpuTexture texture, int baseMipLevel, int mipLevels, boolean owned) {
        super(texture, baseMipLevel, mipLevels);
        this.view = view;
        this.owned = owned;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (owned) {
                view.release();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
