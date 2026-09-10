package dev.dov.tin.metal.resource;

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
        if (closed) {
            throw new IllegalStateException("Buffer is closed");
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Mapped range is negative");
        }
        if (read && (usage() & USAGE_MAP_READ) == 0) {
            throw new IllegalStateException("Buffer is not mappable for reading");
        }
        if (write && (usage() & USAGE_MAP_WRITE) == 0) {
            throw new IllegalStateException("Buffer is not mappable for writing");
        }
        if (!read && !write) {
            throw new IllegalArgumentException("Buffer must be mapped for reading or writing");
        }
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cannot map more than 2GB");
        }
        if (offset + length > size()) {
            throw new IllegalArgumentException("Mapped range is outside the buffer");
        }
        var contents = buffer.contents();
        var data = MemoryUtil.memByteBuffer(contents.address() + offset, (int) length);
        return new GpuBufferSlice.MappedView(slice(offset, length), data, () -> {
        });
    }
}
