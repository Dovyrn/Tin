package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.lwjgl.system.MemoryUtil;

@RequiredArgsConstructor
public class MTLMemory implements TransientMemory {
    @Getter
    private final long handle;

    @Override
    public ByteBuffer allocateCpu(long size, long alignment, long minimum, long element) {
        return MemoryUtil.memByteBuffer(Native.nMemoryCpu(handle, size, alignment, minimum, element), (int) size);
    }

    @Override
    public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, int usage, long minimum,
            long element) {
        return mapped(Native.nMemoryStaging(handle, size, alignment, usage, minimum, element), usage);
    }

    @Override
    public GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimum, long element) {
        return slice(Native.nMemoryGpu(handle, size, alignment, usage, minimum, element), 0, usage);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, int usage, long minimum,
            long element) {
        return mapped(Native.nMemoryGpuMapped(handle, size, alignment, usage, minimum, element), usage);
    }

    @Override
    public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, int usage, long minimum,
            long element) {
        long[] out = Native.nMemoryUploadStaging(handle, addresses(data), sizes(data), alignment, usage, minimum,
                element);
        return slice(out, 0, usage);
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimum, long element) {
        long[] out = Native.nMemoryUploadGpu(handle, addresses(data), sizes(data), alignment, usage, minimum,
                element);
        return slice(out, 0, usage);
    }

    @Override
    public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, int usage) {
        return slices(Native.nMemoryMultiStaging(handle, addresses(data), sizes(data), alignment, usage), usage);
    }

    @Override
    public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, int usage) {
        return slices(Native.nMemoryMultiGpu(handle, addresses(data), sizes(data), alignment, usage), usage);
    }

    private long[] addresses(List<ByteBuffer> data) {
        long[] out = new long[data.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = MemoryUtil.memAddress(data.get(i));
        }
        return out;
    }

    private int[] sizes(List<ByteBuffer> data) {
        int[] out = new int[data.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = data.get(i).remaining();
        }
        return out;
    }

    private GpuBufferSlice slice(long[] out, int at, int usage) {
        return new GpuBufferSlice(new MTLBuffer(out[at], usage, out[at + 2]), out[at + 1], out[at + 2]);
    }

    private List<GpuBufferSlice> slices(long[] out, int usage) {
        var list = new ArrayList<GpuBufferSlice>();
        for (int i = 0; i < out.length; i += 3) {
            list.add(slice(out, i, usage));
        }
        return list;
    }

    private GpuBufferSlice.MappedView mapped(long[] out, int usage) {
        var data = MemoryUtil.memByteBuffer(out[3], (int) out[2]);
        return new GpuBufferSlice.MappedView(slice(out, 0, usage), data, () -> {
        });
    }
}
