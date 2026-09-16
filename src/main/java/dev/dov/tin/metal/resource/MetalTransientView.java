package dev.dov.tin.metal.resource;

//? if >=26.3 {
/*import com.mojang.renderpearl.backend.common.BaseGpuBuffer;
*///?} else {
import com.mojang.blaze3d.buffers.GpuBuffer;
//?}
import com.mojang.blaze3d.buffers.GpuBufferSlice;

//? if >=26.3 {
/*public class MetalTransientView extends BaseGpuBuffer {
*///?} else {
public class MetalTransientView extends GpuBuffer {
//?}
    private final MetalTransientBuffer block;
    private final long index;
    private boolean closed;

    public MetalTransientView(MetalTransientBuffer block, int usage) {
        super(usage, block.size());
        this.block = block;
        this.index = block.getIndex();
    }

    public MetalTransientBuffer block() {
        return block;
    }

    @Override
    public boolean isClosed() {
        return closed || index != block.getIndex() || block.isClosed();
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
