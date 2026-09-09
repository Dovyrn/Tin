package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuFence;
import dev.dov.metalj.commands.MTLCommandBuffer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetalFence implements GpuFence {
    private final MTLCommandBuffer cmd;

    @Override
    public boolean awaitCompletion(long timeoutNs) {
        if (cmd.status() == MTLCommandBuffer.MTLCommandBufferStatusCompleted) {
            return true;
        }
        if (timeoutNs == 0) {
            return false;
        }
        cmd.waitUntilCompleted();
        return true;
    }

    @Override
    public void close() {
    }
}
