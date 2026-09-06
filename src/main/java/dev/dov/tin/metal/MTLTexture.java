package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import lombok.Getter;

public class MTLTexture extends GpuTexture {
    @Getter
    private final long handle;
    private boolean closed;

    public MTLTexture(long handle, int usage, String label, GpuFormat format, int width, int height,
            int depthOrLayers, int mips) {
        super(usage, label, format, width, height, depthOrLayers, mips);
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            Native.nTextureClose(handle);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
