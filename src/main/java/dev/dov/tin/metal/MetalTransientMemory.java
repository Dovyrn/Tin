package dev.dov.tin.metal;

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
    private static final int CPU_ALIGN = 16;

    private final MetalDevice device;
    private final List<ByteBuffer> cpu = new ArrayList<>();
    private final Deque<List<ByteBuffer>> cpuRetired = new ArrayDeque<>();
    private final List<MetalTransientBuffer> free = new ArrayList<>();
    private final List<MetalTransientBuffer> used = new ArrayList<>();
    private final Deque<List<MetalTransientBuffer>> retired = new ArrayDeque<>();
    private long submits;
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

    private MetalTransientBuffer block(long size) {
        for (int i = 0; i < free.size(); i++) {
            if (free.get(i).size() >= size) {
                var reused = free.remove(i);
                reused.reuse();
                used.add(reused);
                offset = 0;
                return reused;
            }
        }
        used.add(device.createTransientBuffer(Math.max(size, BLOCK), () -> submits));
        offset = 0;
        return used.get(used.size() - 1);
    }

    private GpuBufferSlice slice(long size, long alignment, long minimumAllocation, long elementSize, int usage) {
        long align = Math.max(alignment, 1);
        long element = Math.max(elementSize, 1);
        if (size > BLOCK) {
            block(size);
            return take(size, usage);
        }
        long start = round(offset, align);
        var last = used.isEmpty() ? null : used.get(used.size() - 1);
        long room = last == null ? 0 : Math.max(last.size() - start, 0);
        if (room >= size) {
            offset = start;
            return take(size, usage);
        }
        long partial = room / element * element;
        if (room >= minimumAllocation && partial > 0) {
            offset = start;
            return take(partial, usage);
        }
        block(BLOCK);
        return take(size, usage);
    }

    private GpuBufferSlice take(long size, int usage) {
        var block = used.get(used.size() - 1);
        long start = offset;
        offset = start + size;
        return new GpuBufferSlice(block.view(usage), start, size);
    }

    private static ByteBuffer view(GpuBufferSlice slice) {
        var contents = MetalCommandEncoder.buffer(slice).contents();
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
        return slice(size, alignment, minimumAllocation, elementSize, usage);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        var slice = slice(size, alignment, minimumAllocation, elementSize, usage);
        return new GpuBufferSlice.MappedView(slice, view(slice), () -> {
        });
    }

    private GpuBufferSlice upload(List<ByteBuffer> data, long alignment, long minimumAllocation, long elementSize,
            int usage) {
        long align = Math.max(alignment, 1);
        long total = 0;
        for (var part : data) {
            total = round(total + part.remaining(), align);
        }
        var slice = slice(total, align, minimumAllocation, elementSize, usage);
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
        return upload(data, alignment, minimumAllocation, elementSize, usage);
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation,
            long elementSize) {
        return upload(data, alignment, minimumAllocation, elementSize, usage);
    }

    @Override
    public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, int usage) {
        return multi(data, alignment, usage);
    }

    @Override
    public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, int usage) {
        return multi(data, alignment, usage);
    }

    private List<GpuBufferSlice> multi(List<ByteBuffer> data, long alignment, int usage) {
        var slices = new ArrayList<GpuBufferSlice>(data.size());
        for (var part : data) {
            slices.add(upload(List.of(part), alignment, part.remaining(), 1, usage));
        }
        return slices;
    }

    public void close() {
        for (var block : used) {
            block.getBuffer().release();
        }
        for (var block : free) {
            block.getBuffer().release();
        }
        for (var batch : retired) {
            for (var block : batch) {
                block.getBuffer().release();
            }
        }
        for (var block : cpu) {
            MemoryUtil.memFree(block);
        }
        for (var batch : cpuRetired) {
            for (var block : batch) {
                MemoryUtil.memFree(block);
            }
        }
    }

    public void endSubmit() {
        submits++;
        cpuRetired.addLast(List.copyOf(cpu));
        cpu.clear();
        cpuOffset = BLOCK;
        while (cpuRetired.size() > MetalCommandEncoder.IN_FLIGHT) {
            for (var block : cpuRetired.removeFirst()) {
                MemoryUtil.memFree(block);
            }
        }
        retired.addLast(List.copyOf(used));
        used.clear();
        offset = BLOCK;
        while (retired.size() > MetalCommandEncoder.IN_FLIGHT) {
            for (var block : retired.removeFirst()) {
                if (block.size() > BLOCK && free.stream().anyMatch(kept -> kept.size() > BLOCK)) {
                    block.getBuffer().release();
                } else {
                    free.add(block);
                }
            }
        }
    }
}
