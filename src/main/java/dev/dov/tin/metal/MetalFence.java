package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuFence;

public class MetalFence implements GpuFence {
    @Override
    public boolean awaitCompletion(long timeoutNs) {
        return true;
    }

    @Override
    public void close() {
    }
}
