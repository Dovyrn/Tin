package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.lwjgl.system.MemoryUtil;

@RequiredArgsConstructor
public class MetalTransientMemory implements TransientMemory {
    private static final long BLOCK = 512 * 1024;
    private static final int IN_FLIGHT = 2;
    private static final int CPU_ALIGN = 16;

    private final MetalDevice device;
    private final List<ByteBuffer> cpu = new ArrayList<>();
    private final Deque<List<ByteBuffer>> cpuRetired = new ArrayDeque<>();
    private final List<GpuBuffer> free = new ArrayList<>();
    private final List<GpuBuffer> used = new ArrayList<>();
    private final Deque<List<GpuBuffer>> retired = new ArrayDeque<>();
    private long cpuOffset = BLOCK;
    private long offset = BLOCK;

    private static long round(long value, long align) {
        return (value + align - 1) / align * align;
    }

    @Override
    public ByteBuffer allocateCpu(long size, long alignment, long minimumAllocation, long elementSize) {
        long start = round(cpuOffset, Math.max(alignment, 1));
        ByteBuffer block = cpu.isEmpty() ? null : cpu.get(cpu.size() - 1);
        long room = block == null ? 0 : Math.max(block.capacity() - start, 0);
        if (room < size) {
            block = MemoryUtil.memCalloc((int) Math.max(size, BLOCK));
            cpu.add(block);
            start = 0;
        }
        cpuOffset = start + Math.max(size, CPU_ALIGN);
        return block.slice((int) start, (int) size);
    }

    private GpuBuffer block(long size) {
        for (int i = 0; i < free.size(); i++) {
            if (free.get(i).size() >= size) {
                used.add(free.remove(i));
                offset = 0;
                return used.get(used.size() - 1);
            }
        }
        used.add(device.createBuffer(() -> "transient block", 0, Math.max(size, BLOCK)));
        offset = 0;
        return used.get(used.size() - 1);
    }

    private GpuBufferSlice slice(long size, long alignment, long minimumAllocation, long elementSize) {
        long align = Math.max(alignment, 1);
        long element = Math.max(elementSize, 1);
        if (size > BLOCK) {
            block(size);
            return take(size);
        }
        long start = round(offset, align);
        GpuBuffer last = used.isEmpty() ? null : used.get(used.size() - 1);
        long room = last == null ? 0 : Math.max(last.size() - start, 0);
        if (room >= size) {
            offset = start;
            return take(size);
        }
        if (room >= minimumAllocation) {
            offset = start;
            return take(room / element * element);
        }
        block(BLOCK);
        return take(size);
    }

    private GpuBufferSlice take(long size) {
        var block = used.get(used.size() - 1);
        long start = offset;
        offset = start + size;
        return block.slice(start, size);
    }

    private static ByteBuffer view(GpuBufferSlice slice) {
        var contents = ((MetalGpuBuffer) slice.buffer()).getBuffer().contents();
        return MemoryUtil.memByteBuffer(contents.address() + slice.offset(), (int) slice.length());
    }

    @Override
    public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        return allocateGpuMapped(size, alignment, usage, minimumAllocation, elementSize);
    }

    @Override
    public GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        return slice(size, alignment, minimumAllocation, elementSize);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        var slice = slice(size, alignment, minimumAllocation, elementSize);
        return new GpuBufferSlice.MappedView(slice, view(slice), () -> {
        });
    }

    private GpuBufferSlice upload(List<ByteBuffer> data, long alignment, long minimumAllocation, long elementSize) {
        long align = Math.max(alignment, 1);
        long total = 0;
        for (var part : data) {
            total = round(total + part.remaining(), align);
        }
        var slice = slice(total, align, minimumAllocation, elementSize);
        var target = view(slice);
        long at = 0;
        for (var part : data) {
            if (at >= slice.length()) {
                break;
            }
            int length = (int) Math.min(part.remaining(), slice.length() - at);
            MemoryUtil.memCopy(MemoryUtil.memAddress(part), MemoryUtil.memAddress(target) + at, length);
            at = round(at + part.remaining(), align);
        }
        return slice;
    }

    @Override
    public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        return upload(data, alignment, minimumAllocation, elementSize);
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        return upload(data, alignment, minimumAllocation, elementSize);
    }

    @Override
    public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, int usage) {
        return multi(data, alignment);
    }

    @Override
    public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, int usage) {
        return multi(data, alignment);
    }

    private List<GpuBufferSlice> multi(List<ByteBuffer> data, long alignment) {
        var slices = new ArrayList<GpuBufferSlice>(data.size());
        for (var part : data) {
            slices.add(upload(List.of(part), alignment, part.remaining(), 1));
        }
        return slices;
    }

    public void endSubmit() {
        cpuRetired.addLast(List.copyOf(cpu));
        cpu.clear();
        cpuOffset = BLOCK;
        while (cpuRetired.size() > IN_FLIGHT) {
            for (var block : cpuRetired.removeFirst()) {
                MemoryUtil.memFree(block);
            }
        }
        retired.addLast(List.copyOf(used));
        used.clear();
        offset = BLOCK;
        while (retired.size() > IN_FLIGHT) {
            free.addAll(retired.removeFirst());
        }
    }
}
