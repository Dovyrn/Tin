package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.List;

public class MetalTransientMemory implements TransientMemory {
    @Override
    public @NotNull ByteBuffer allocateCpu(long size, long alignment, long minimumAllocation, long elementSize) {
        return null;
    }

    @Override
    public GpuBufferSlice.@NotNull MappedView allocateStaging(long size, long alignment, int usage, long minimumAllocation,
                                                              long elementSize) {
        return null;
    }

    @Override
    public @NotNull GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimumAllocation,
                                               long elementSize) {
        return null;
    }

    @Override
    public GpuBufferSlice.@NotNull MappedView allocateGpuMapped(long size, long alignment, int usage, long minimumAllocation,
                                                                long elementSize) {
        return null;
    }

    @Override
    public @NotNull GpuBufferSlice uploadStaging(@NotNull List<ByteBuffer> data, long alignment, int usage, long minimumAllocation,
                                                 long elementSize) {
        return null;
    }

    @Override
    public @NotNull GpuBufferSlice uploadGpu(@NotNull List<ByteBuffer> data, long alignment, int usage, long minimumAllocation,
                                             long elementSize) {
        return null;
    }

    @Override
    public @NotNull List<GpuBufferSlice> multiUploadStaging(@NotNull List<ByteBuffer> data, long alignment, int usage) {
        return List.of();
    }

    @Override
    public @NotNull List<GpuBufferSlice> multiUploadGpu(@NotNull List<ByteBuffer> data, long alignment, int usage) {
        return List.of();
    }
}
