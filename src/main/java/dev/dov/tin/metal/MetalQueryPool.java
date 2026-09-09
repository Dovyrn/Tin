package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.GpuQueryPool;
import dev.dov.metalj.commands.MTLCommandBuffer;
import dev.dov.metalj.commands.passes.MTLComputePassDescriptor;
import dev.dov.metalj.debug.MTLCounterResultTimestamp;
import dev.dov.metalj.debug.MTLCounterSampleBuffer;
import dev.dov.metalj.debug.MTLCounterSampleBufferDescriptor;
import dev.dov.metalj.debug.MTLCommonCounter;
import dev.dov.metalj.debug.MTLCounterSet;
import dev.dov.metalj.resources.MTLStorageMode;
import java.util.Arrays;
import java.util.OptionalLong;
import lombok.Getter;

public class MetalQueryPool implements GpuQueryPool {
    private final int size;
    @Getter
    private final MTLCounterSampleBuffer samples;
    private final long[] written;
    private final MetalDevice device;

    public MetalQueryPool(MetalDevice device, int size) {
        this.size = size;
        this.device = device;
        this.written = new long[size];
        Arrays.fill(written, Long.MIN_VALUE);
        samples = AutoreleasePool.get(() -> samples(device, size));
    }

    private static MTLCounterSampleBuffer samples(MetalDevice device, int size) {
        var set = timestamps(device);
        if (set == null) {
            return null;
        }
        var descriptor = MTLCounterSampleBufferDescriptor.new_();
        descriptor.setCounterSet(set);
        descriptor.setStorageMode(MTLStorageMode.MTLStorageModeShared);
        descriptor.setSampleCount(size);
        var buffer = device.getDevice().newCounterSampleBufferWithDescriptor(descriptor);
        descriptor.release();
        return buffer;
    }

    private static MTLCounterSet timestamps(MetalDevice device) {
        var sets = device.getDevice().counterSets();
        var wanted = MTLCommonCounter.MTLCommonCounterSetTimestamp().UTF8String();
        for (long i = 0; i < sets.count(); i++) {
            var set = MTLCounterSet.of(sets.objectAtIndex(i));
            if (wanted.equals(set.name().UTF8String())) {
                return set;
            }
        }
        return null;
    }

    public void write(MTLCommandBuffer cmd, int index) {
        if (samples == null) {
            return;
        }
        var pass = MTLComputePassDescriptor.computePassDescriptor();
        var attachment = pass.sampleBufferAttachments().objectAtIndexedSubscript(0);
        attachment.setSampleBuffer(samples);
        attachment.setStartOfEncoderSampleIndex(index);
        cmd.computeCommandEncoderWithDescriptor(pass).endEncoding();
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
        if (samples == null || written[index] == Long.MIN_VALUE) {
            return OptionalLong.empty();
        }
        if (device.getEncoder().getCompleted() < written[index]) {
            return OptionalLong.empty();
        }
        long value = AutoreleasePool.get(
                () -> MTLCounterResultTimestamp.timestamp(samples.resolveCounterRange(index, 1).bytes(), 0));
        if (value == -1) {
            return OptionalLong.empty();
        }
        written[index] = Long.MIN_VALUE;
        return OptionalLong.of(value);
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
        if (samples != null) {
            samples.release();
        }
    }
}
