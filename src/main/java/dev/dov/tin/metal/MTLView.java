package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import lombok.Getter;

public class MTLView extends GpuTextureView {
    @Getter
    private final long handle;

    public MTLView(long handle, GpuTexture texture, int baseMip, int mips) {
        super(texture, baseMip, mips);
        this.handle = handle;
    }

    @Override
    public void close() {
        Native.nViewClose(handle);
    }

    @Override
    public boolean isClosed() {
        return Native.nViewClosed(handle);
    }
}
