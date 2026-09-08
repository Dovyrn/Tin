package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.IndexType;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;

public class MetalRenderPass implements RenderPassBackend {
    @Override
    public void pushDebugGroup(@NotNull Supplier<String> label) {
    }

    @Override
    public void popDebugGroup() {
    }

    @Override
    public void setPipeline(@NotNull RenderPipeline pipeline) {
    }

    @Override
    public void bindTexture(@NotNull String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
    }

    @Override
    public void setUniform(@NotNull String name, @NotNull GpuBuffer value) {
    }

    @Override
    public void setUniform(@NotNull String name, @NotNull GpuBufferSlice value) {
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
    }

    @Override
    public void disableScissor() {
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
    }

    @Override
    public void setIndexBuffer(@NotNull GpuBuffer indexBuffer, @NotNull IndexType indexType) {
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
    }

    @Override
    public void multiDrawIndexed(@NotNull IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
    }

    @Override
    public void multiDrawIndexed(@NotNull PointerBuffer firstIndexOffsets, @NotNull IntBuffer indexCounts, @NotNull IntBuffer vertexOffsets,
                                 int drawCount) {
    }

    @Override
    public void drawIndexedIndirect(@NotNull GpuBufferSlice commands, int drawCount) {
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
    }

    @Override
    public void multiDraw(@NotNull IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
    }

    @Override
    public void multiDraw(@NotNull IntBuffer firstVertices, @NotNull IntBuffer vertexCounts, int drawCount) {
    }

    @Override
    public void drawIndirect(@NotNull GpuBufferSlice commands, int drawCount) {
    }

    @Override
    public <T> void drawMultipleIndexed(@NotNull Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
                                        @Nullable IndexType defaultIndexType, @NotNull Collection<String> dynamicUniforms, T uniformArgument) {
    }

    @Override
    public void writeTimestamp(@NotNull GpuQueryPool pool, int index) {
    }
}
