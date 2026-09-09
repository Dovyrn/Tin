package dev.dov.tin.metal;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.encoders.MTLScissorRect;
import dev.dov.metalj.commands.encoders.MTLViewport;
import dev.dov.metalj.objc.NSString;
import java.lang.foreign.Arena;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;

public class MetalRenderPass implements RenderPassBackend {
    private final MetalCommandEncoder encoder;
    private final MTLRenderCommandEncoder pass;
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final Map<String, GpuTextureView> views = new HashMap<>();
    private final Map<String, GpuSampler> samplers = new HashMap<>();
    private final int width;
    private final int height;
    private final boolean depth;
    private final RenderPass.@Nullable RenderArea area;
    private MetalRenderPipeline pipeline;
    private GpuBuffer indexBuffer;
    private IndexType indexType;
    private boolean dirty = true;

    public MetalRenderPass(MetalCommandEncoder encoder, RenderPassDescriptor descriptor) {
        this.encoder = encoder;
        this.area = descriptor.renderArea;
        var info = MTLRenderPassDescriptor.renderPassDescriptor();
        int size = 0;
        int rows = 0;
        for (int i = 0; i < descriptor.colorAttachments.size(); i++) {
            var color = descriptor.colorAttachments.get(i);
            if (color == null) {
                continue;
            }
            size = color.textureView().getWidth(0);
            rows = color.textureView().getHeight(0);
            var attachment = info.colorAttachments().objectAtIndexedSubscript(i);
            attachment.setTexture(((MetalGpuTextureView) color.textureView()).getView());
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            if (color.clearValue().isPresent()) {
                var value = color.clearValue().get();
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
                try (var arena = Arena.ofConfined()) {
                    attachment.setClearColor(MTLClearColor.of(arena, value.x(), value.y(), value.z(), value.w()));
                }
            } else {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            }
        }
        this.depth = descriptor.depthAttachment != null;
        if (depth) {
            var view = descriptor.depthAttachment.textureView();
            size = view.getWidth(0);
            rows = view.getHeight(0);
            var attachment = info.depthAttachment();
            attachment.setTexture(((MetalGpuTextureView) view).getView());
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            var clear = descriptor.depthAttachment.clearValue();
            if (clear.isPresent()) {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
                attachment.setClearDepth(clear.getAsDouble());
            } else {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            }
        }
        this.width = size;
        this.height = rows;
        pass = encoder.commandBuffer().renderCommandEncoderWithDescriptor(info);
        pass.setLabel(NSString.stringWithUTF8String(descriptor.label().get()));
        try (var arena = Arena.ofConfined()) {
            pass.setViewport(MTLViewport.of(arena, 0, 0, width, height, 0, 1));
        }
        pass.setFrontFacingWinding(MTLRenderCommandEncoder.MTLWindingCounterClockwise);
        disableScissor();
    }

    public void end() {
        pass.endEncoding();
    }

    @Override
    public void pushDebugGroup(Supplier<String> label) {
        pass.pushDebugGroup(NSString.stringWithUTF8String(label.get()));
    }

    @Override
    public void popDebugGroup() {
        pass.popDebugGroup();
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        this.pipeline = (MetalRenderPipeline) encoder.getDevice().compiled(pipeline);
        this.pipeline.bind(pass, depth);
        dirty = true;
    }

    @Override
    public void bindTexture(String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
        if (textureView == null) {
            views.remove(name);
            samplers.remove(name);
        } else {
            views.put(name, textureView);
            samplers.put(name, sampler);
        }
        dirty = true;
    }

    @Override
    public void setUniform(String name, GpuBuffer value) {
        setUniform(name, value.slice(0, value.size()));
    }

    @Override
    public void setUniform(String name, GpuBufferSlice value) {
        uniforms.put(name, value);
        dirty = true;
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        int left = Math.max(x, 0);
        int top = Math.max(y, 0);
        int columns = Math.min(Math.max(width, 0), this.width - Math.min(left, this.width));
        int rows = Math.min(Math.max(height, 0), this.height - Math.min(top, this.height));
        try (var arena = Arena.ofConfined()) {
            pass.setScissorRect(MTLScissorRect.of(arena, left, top, columns, rows));
        }
    }

    @Override
    public void disableScissor() {
        if (area != null && area.width() > 0 && area.height() > 0) {
            enableScissor(area.x(), area.y(), area.width(), area.height());
        } else {
            enableScissor(0, 0, width, height);
        }
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
        if (vertexBuffer == null) {
            return;
        }
        pass.setVertexBuffer(((MetalGpuBuffer) vertexBuffer.buffer()).getBuffer(), vertexBuffer.offset(), slot);
    }

    @Override
    public void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType) {
        this.indexBuffer = indexBuffer;
        this.indexType = indexType;
    }

    private void bind() {
        if (!dirty) {
            return;
        }
        pipeline.bindResources(pass, uniforms, views, samplers);
        dirty = false;
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        bind();
        pass.drawIndexedPrimitives(pipeline.topology(), indexCount, MetalConst.indexType(indexType),
                ((MetalGpuBuffer) indexBuffer).getBuffer(), (long) firstIndex * indexType.bytes, instanceCount,
                vertexOffset, firstInstance);
    }

    @Override
    public void multiDrawIndexed(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        for (int i = 0; i < drawCount; i++) {
            int first = drawParameters.get(i * 3);
            int count = drawParameters.get(i * 3 + 1);
            int vertexOffset = drawParameters.get(i * 3 + 2);
            drawIndexed(count, instanceCount, first, vertexOffset, firstInstance);
        }
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets,
            int drawCount) {
        for (int i = 0; i < drawCount; i++) {
            int first = (int) (firstIndexOffsets.get(i) / indexType.bytes);
            drawIndexed(indexCounts.get(i), 1, first, vertexOffsets == null ? 0 : vertexOffsets.get(i), 0);
        }
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        bind();
        var buffer = ((MetalGpuBuffer) commands.buffer()).getBuffer();
        for (int i = 0; i < drawCount; i++) {
            pass.drawIndexedPrimitives(pipeline.topology(), MetalConst.indexType(indexType),
                    ((MetalGpuBuffer) indexBuffer).getBuffer(), 0, buffer,
                    commands.offset() + (long) i * MetalConst.INDEXED_INDIRECT_STRIDE);
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        bind();
        pass.drawPrimitives(pipeline.topology(), firstVertex, vertexCount, instanceCount, firstInstance);
    }

    @Override
    public void multiDraw(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        for (int i = 0; i < drawCount; i++) {
            draw(drawParameters.get(i * 2 + 1), instanceCount, drawParameters.get(i * 2), firstInstance);
        }
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
        for (int i = 0; i < drawCount; i++) {
            draw(vertexCounts.get(i), 1, firstVertices.get(i), 0);
        }
    }

    @Override
    public void drawIndirect(GpuBufferSlice commands, int drawCount) {
        bind();
        var buffer = ((MetalGpuBuffer) commands.buffer()).getBuffer();
        for (int i = 0; i < drawCount; i++) {
            pass.drawPrimitives(pipeline.topology(), buffer,
                    commands.offset() + (long) i * MetalConst.INDIRECT_STRIDE);
        }
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
            @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument) {
        for (var draw : draws) {
            var indices = draw.indexBuffer() == null ? defaultIndexBuffer : draw.indexBuffer();
            var type = draw.indexType() == null ? defaultIndexType : draw.indexType();
            setIndexBuffer(indices, type);
            setVertexBuffer(draw.slot(), draw.vertexBuffer().slice(0, draw.vertexBuffer().size()));
            drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        ((MetalQueryPool) pool).write(encoder.commandBuffer(), index);
    }
}
