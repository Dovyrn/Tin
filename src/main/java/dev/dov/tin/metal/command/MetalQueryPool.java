package dev.dov.tin.metal.command;

import com.mojang.blaze3d.systems.GpuQueryPool;
import dev.dov.metalj.metal4.MTL4CommandBuffer;
import dev.dov.metalj.metal4.counters.MTL4CounterHeap;
import dev.dov.metalj.metal4.counters.MTL4CounterHeapDescriptor;
import dev.dov.metalj.metal4.counters.MTL4CounterHeapType;
import dev.dov.metalj.metal4.counters.MTL4TimestampHeapEntry;
import dev.dov.metalj.objc.NSObject;
import dev.dov.metalj.objc.NSRange;
import dev.dov.tin.metal.MetalDevice;
import java.lang.foreign.Arena;
import java.util.Arrays;
import java.util.OptionalLong;
import lombok.Getter;

public class MetalQueryPool implements GpuQueryPool {
    private final int size;
    @Getter
    private final MTL4CounterHeap heap;
    private final long[] written;
    private final MetalDevice device;

    public MetalQueryPool(MetalDevice device, int size) {
        this.size = size;
        this.device = device;
        this.written = new long[size];
        Arrays.fill(written, Long.MIN_VALUE);
        heap = heap(device, size);
    }

    private static MTL4CounterHeap heap(MetalDevice device, int size) {
        var descriptor = MTL4CounterHeapDescriptor.new_();
        descriptor.setType(MTL4CounterHeapType.MTL4CounterHeapTypeTimestamp);
        descriptor.setCount(size);
        try {
            var heap = device.getDevice().newCounterHeapWithDescriptor(descriptor);
            return heap.isNull() ? null : heap;
        } catch (IllegalStateException e) {
            return null;
        } finally {
            descriptor.release();
        }
    }

    public void write(MTL4CommandBuffer cmd, int index) {
        if (heap == null) {
            return;
        }
        cmd.writeTimestampIntoHeap(heap, index);
        record(index);
    }

    public void record(int index) {
        written[index] = device.getEncoder().getSubmits();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public OptionalLong getValue(int index) {
        if (heap == null || written[index] == Long.MIN_VALUE) {
            return OptionalLong.empty();
        }
        if (!device.getEncoder().awaitSubmit(written[index], 0)) {
            return OptionalLong.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var range = NSRange.of(arena, index, 1);
            long value = NSObject.drained(() -> MTL4TimestampHeapEntry.timestamp(
                    heap.resolveCounterRange(range).bytes(), 0));
            return OptionalLong.of(value);
        }
    }

    @Override
    public OptionalLong[] getValues(int index, int count) {
        var values = new OptionalLong[count];
        for (int i = 0; i < count; i++) {
            values[i] = getValue(index + i);
        }
        return values;
    }

    @Override
    public void close() {
        if (heap != null) {
            device.retire(heap::release);
        }
    }
}
