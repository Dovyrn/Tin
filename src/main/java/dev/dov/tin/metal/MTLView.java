package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import lombok.Getter;

public class MTLView extends GpuTextureView {
    @Getter
    private final long handle;
    private boolean closed;

    public MTLView(long handle, GpuTexture texture, int baseMip, int mips) {
        super(texture, baseMip, mips);
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            Native.nViewClose(handle);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
