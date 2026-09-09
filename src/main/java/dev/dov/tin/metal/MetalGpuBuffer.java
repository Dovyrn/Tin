package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import lombok.Getter;
import org.lwjgl.system.MemoryUtil;

public class MetalGpuBuffer extends GpuBuffer {
    @Getter
    private final MTLBuffer buffer;
    private boolean closed;

    public MetalGpuBuffer(MTLBuffer buffer, int usage, long size) {
        super(usage, size);
        this.buffer = buffer;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            buffer.release();
        }
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        var contents = buffer.contents();
        var data = MemoryUtil.memByteBuffer(contents.address() + offset, (int) length);
        return new GpuBufferSlice.MappedView(slice(offset, length), data, () -> {
        });
    }
}
