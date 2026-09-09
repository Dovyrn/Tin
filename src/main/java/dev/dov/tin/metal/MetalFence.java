package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuFence;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetalFence implements GpuFence {
    private final MetalCommandEncoder encoder;
    private final long index;
    private boolean completed;

    @Override
    public boolean awaitCompletion(long timeoutNs) {
        if (!completed) {
            completed = encoder.awaitSubmit(index, timeoutNs);
        }
        return completed;
    }

    @Override
    public void close() {
    }
}
