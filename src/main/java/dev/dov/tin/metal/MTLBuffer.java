package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import lombok.Getter;
import org.lwjgl.system.MemoryUtil;

public class MTLBuffer extends GpuBuffer {
    @Getter
    private final long handle;

    public MTLBuffer(long handle, int usage, long size) {
        super(usage, size);
        this.handle = handle;
    }

    @Override
    public boolean isClosed() {
        return Native.nBufferClosed(handle);
    }

    @Override
    public void close() {
        Native.nBufferClose(handle);
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        long address = Native.nBufferMap(handle, offset, length, read, write);
        var data = MemoryUtil.memByteBuffer(address, (int) length);
        return new GpuBufferSlice.MappedView(slice(offset, length), data, () -> Native.nBufferUnmap(handle));
    }
}
