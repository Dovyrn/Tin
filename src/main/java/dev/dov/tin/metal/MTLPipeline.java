package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MTLPipeline implements CompiledRenderPipeline {
    @Getter
    private final long handle;

    @Override
    public boolean isValid() {
        return Native.nPipelineValid(handle);
    }
}
