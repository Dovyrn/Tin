package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.buffers.GpuFence;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MTLFence implements GpuFence {
    @Getter
    private final long handle;

    @Override
    public boolean awaitCompletion(long timeoutNs) {
        return Native.nFenceAwait(handle, timeoutNs);
    }

    @Override
    public void close() {
        Native.nFenceClose(handle);
    }
}
