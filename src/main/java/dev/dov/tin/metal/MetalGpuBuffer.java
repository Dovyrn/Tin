package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.jetbrains.annotations.NotNull;

public class MetalGpuBuffer extends GpuBuffer {
    private boolean closed;

    public MetalGpuBuffer(int usage, long size) {
        super(usage, size);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public GpuBufferSlice.@NotNull MappedView map(long offset, long length, boolean read, boolean write) {
        return null;
    }
}
