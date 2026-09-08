package dev.dov.tin.metal;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetalRenderPipeline implements CompiledRenderPipeline {
    private final RenderPipeline pipeline;

    @Override
    public boolean isValid() {
        return true;
    }
}
