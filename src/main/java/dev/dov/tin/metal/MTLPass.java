package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

@RequiredArgsConstructor
public class MTLPass implements RenderPassBackend {
    @Getter
    private final long handle;
    private final MTLDevice device;

    @Override
    public void pushDebugGroup(Supplier<String> label) {
        Native.nPassPush(handle, label.get());
    }

    @Override
    public void popDebugGroup() {
        Native.nPassPop(handle);
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        var compiled = (MTLPipeline) device.precompilePipeline(pipeline, null);
        Native.nPassPipeline(handle, compiled.getHandle());
    }

    @Override
    public void bindTexture(String name, GpuTextureView view, GpuSampler sampler) {
        long v = view == null ? 0 : ((MTLView) view).getHandle();
        long s = sampler == null ? 0 : ((MTLSampler) sampler).getHandle();
        Native.nPassTexture(handle, name, v, s);
    }

    @Override
    public void setUniform(String name, GpuBuffer buffer) {
        Native.nPassUniform(handle, name, handle(buffer), 0, buffer.size());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice slice) {
        Native.nPassUniform(handle, name, handle(slice.buffer()), slice.offset(), slice.length());
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        Native.nPassScissor(handle, x, y, width, height);
    }

    @Override
    public void disableScissor() {
        Native.nPassNoScissor(handle);
    }

    @Override
    public void setVertexBuffer(int slot, GpuBufferSlice slice) {
        if (slice == null) {
            Native.nPassVertex(handle, slot, 0, 0, 0);
            return;
        }
        Native.nPassVertex(handle, slot, handle(slice.buffer()), slice.offset(), slice.length());
    }

    @Override
    public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
        Native.nPassIndex(handle, handle(buffer), type.ordinal());
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset,
            int firstInstance) {
        Native.nPassDrawIndexed(handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Override
    public void multiDrawIndexed(IntBuffer params, int instanceCount, int firstInstance, int drawCount) {
        Native.nPassMultiDrawIndexed(handle, MemoryUtil.memAddress(params), instanceCount, firstInstance,
                drawCount);
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets,
            int drawCount) {
        Native.nPassMultiDrawIndexedSeparate(handle, MemoryUtil.memAddress(firstIndexOffsets),
                MemoryUtil.memAddress(indexCounts), MemoryUtil.memAddress(vertexOffsets), drawCount);
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        Native.nPassDrawIndexedIndirect(handle, handle(commands.buffer()), commands.offset(), commands.length(),
                drawCount);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer defaultIndex,
            IndexType defaultType, Collection<String> dynamicUniforms, T argument) {
        for (var draw : draws) {
            var uploader = draw.uniformUploaderConsumer();
            if (uploader != null) {
                uploader.accept(argument, this::setUniform);
            }
            var index = draw.indexBuffer() == null ? defaultIndex : draw.indexBuffer();
            var type = draw.indexType() == null ? defaultType : draw.indexType();
            Native.nPassDrawOne(handle, draw.slot(), handle(draw.vertexBuffer()), handle(index), type.ordinal(),
                    draw.firstIndex(), draw.indexCount(), draw.baseVertex());
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        Native.nPassDraw(handle, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void multiDraw(IntBuffer params, int instanceCount, int firstInstance, int drawCount) {
        Native.nPassMultiDraw(handle, MemoryUtil.memAddress(params), instanceCount, firstInstance, drawCount);
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
        Native.nPassMultiDrawSeparate(handle, MemoryUtil.memAddress(firstVertices),
                MemoryUtil.memAddress(vertexCounts), drawCount);
    }

    @Override
    public void drawIndirect(GpuBufferSlice commands, int drawCount) {
        Native.nPassDrawIndirect(handle, handle(commands.buffer()), commands.offset(), commands.length(),
                drawCount);
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        Native.nPassTimestamp(handle, ((MTLQueries) pool).getHandle(), index);
    }

    private long handle(GpuBuffer buffer) {
        return ((MTLBuffer) buffer).getHandle();
    }
}
