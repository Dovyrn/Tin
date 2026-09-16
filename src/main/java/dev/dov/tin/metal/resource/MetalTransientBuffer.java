package dev.dov.tin.metal.resource;

import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >= 26.3 {
/*import com.mojang.renderpearl.backend.common.BaseGpuBuffer;
*///?}
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import java.util.function.LongSupplier;
import lombok.Getter;

//? if >= 26.3 {
/*public class MetalTransientBuffer extends BaseGpuBuffer {
*///?} else {
public class MetalTransientBuffer extends GpuBuffer {
//?}
    @Getter
    private final MTLBuffer buffer;
    private final LongSupplier submits;
    @Getter
    private long index;
    private boolean closed;

    public MetalTransientBuffer(MTLBuffer buffer, int usage, long size, LongSupplier submits) {
        super(usage, size);
        this.buffer = buffer;
        this.submits = submits;
        this.index = submits.getAsLong();
    }

    public void reuse() {
        index = submits.getAsLong();
        closed = false;
    }

    public GpuBuffer view(int usage) {
        return new MetalTransientView(this, usage);
    }

    @Override
    public boolean isClosed() {
        if (closed) {
            return true;
        }
        closed = index < submits.getAsLong();
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        throw new IllegalStateException("Cannot map transient buffer");
    }

    @Override
    public GpuBufferSlice slice(long offset, long length) {
        throw new IllegalStateException("Cannot slice transient buffer");
    }

    @Override
    public GpuBufferSlice slice() {
        throw new IllegalStateException("Cannot slice transient buffer");
    }
}
