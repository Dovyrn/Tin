package dev.dov.tin.metal.command;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
//? if >= 26.3 {
/*import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import java.nio.ByteBuffer;
*///?} else {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.encoders.MTLScissorRect;
import dev.dov.metalj.commands.encoders.MTLViewport;
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.resources.MTLResourceOptions;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import dev.dov.metalj.resources.textures.MTLTextureDescriptor;
import dev.dov.metalj.resources.textures.MTLTextureUsage;
import dev.dov.tin.metal.MetalConst;
import dev.dov.tin.metal.resource.MetalGpuSampler;
import dev.dov.tin.metal.resource.MetalGpuTextureView;
import dev.dov.tin.metal.shader.Binding;
import dev.dov.tin.metal.shader.MetalRenderPipeline;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;

public class MetalRenderPass implements RenderPassBackend {
    private final MetalCommandEncoder encoder;
    @Getter
    private final MTLRenderCommandEncoder pass;
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final Map<String, GpuTextureView> views = new HashMap<>();
    private final Map<String, GpuSampler> samplers = new HashMap<>();
    private final Map<String, GpuBufferSlice> bound = new HashMap<>();
    private final GpuBufferSlice[] vertices = new GpuBufferSlice[16];
    private final Set<String> dirtyUniforms = new HashSet<>();
    private final Set<String> dirtyTextures = new HashSet<>();
    private int width;
    private int height;
    private boolean depth;
    private final RenderPass.@Nullable RenderArea area;
    private MetalRenderPipeline pipeline;
    private GpuBuffer indexBuffer;
    private IndexType indexType;
    private int groups;
    //? if >= 26.3 {
    /*private GpuBufferSlice pushConstants;
    private boolean pushConstantsDirty;
    *///?}
    private final List<Runnable> deferred = new ArrayList<>();

    public MetalRenderPass(MetalCommandEncoder encoder, RenderPassDescriptor descriptor) {
        this.encoder = encoder;
        //? if >= 26.3 {
        /*this.area = descriptor.renderArea();
        *///?} else {
        this.area = descriptor.renderArea;
        //?}
        pass = open(descriptor);
        try (var arena = Arena.ofConfined()) {
            pass.setViewport(MTLViewport.of(arena, 0, 0, width, height, 0, 1));
        }
        pass.setFrontFacingWinding(MTLRenderCommandEncoder.MTLWindingClockwise);
        disableScissor();
    }

    private MTLRenderCommandEncoder open(RenderPassDescriptor descriptor) {
        var info = MTLRenderPassDescriptor.renderPassDescriptor();
        int size = 0;
        int rows = 0;
        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            var color = descriptor.colorAttachments().get(i);
            if (color == null) {
                continue;
            }
            var view = color.textureView();
            size = view.getWidth(0);
            rows = view.getHeight(0);
            var attachment = info.colorAttachments().objectAtIndexedSubscript(i);
            attachment.setTexture(((MetalGpuTextureView) view).getView());
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            var pending = view.baseMipLevel() == 0 ? encoder.takeColorClear(view.texture()) : null;
            var value = color.clearValue().orElse(pending);
            if (value == null) {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            } else {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
                try (var arena = Arena.ofConfined()) {
                    attachment.setClearColor(MTLClearColor.of(arena, value.x(), value.y(), value.z(), value.w()));
                }
            }
        }
        this.depth = descriptor.depthAttachment() != null;
        if (depth) {
            var view = descriptor.depthAttachment().textureView();
            size = view.getWidth(0);
            rows = view.getHeight(0);
            var attachment = info.depthAttachment();
            attachment.setTexture(((MetalGpuTextureView) view).getView());
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            var pending = view.baseMipLevel() == 0 ? encoder.takeDepthClear(view.texture()) : null;
            var clear = descriptor.depthAttachment().clearValue();
            if (clear.isPresent()) {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
                attachment.setClearDepth(clear.getAsDouble());
            } else if (pending != null) {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
                attachment.setClearDepth(pending);
            } else {
                attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            }
        }
        this.width = size;
        this.height = rows;
        var opened = encoder.commandBuffer().renderCommandEncoderWithDescriptor(info);
        info.release();
        if (encoder.getDevice().useLabels()) {
            var label = NSString.stringWithUTF8String(descriptor.label().get());
            opened.setLabel(label);
            label.release();
        }
        return opened;
    }

    public void end() {
        pass.endEncoding();
        pass.release();
        for (var sample : deferred) {
            sample.run();
        }
    }

    @Override
    public void pushDebugGroup(Supplier<String> label) {
        groups++;
        var text = NSString.stringWithUTF8String(label.get());
        pass.pushDebugGroup(text);
        text.release();
    }

    @Override
    public void popDebugGroup() {
        if (groups == 0) {
            throw new IllegalStateException("Cannot pop more debug groups than were pushed");
        }
        groups--;
        pass.popDebugGroup();
    }

    //? if >= 26.3 {
    /*@Override
    public void setPipeline(BackendRenderPipeline pipeline) {
        this.pipeline = (MetalRenderPipeline) pipeline;
        pushConstantsDirty = true;
        if (!this.pipeline.isValid()) {
            throw new IllegalStateException("Pipeline is not valid (may contain invalid shaders?)");
        }
        this.pipeline.bind(pass, depth);
        bound.clear();
        dirtyUniforms.addAll(uniforms.keySet());
        dirtyTextures.addAll(views.keySet());
    }
    *///?} else {
    @Override
    public void setPipeline(RenderPipeline pipeline) {
        this.pipeline = encoder.getDevice().compiled(pipeline);
        if (!this.pipeline.isValid()) {
            throw new IllegalStateException("Pipeline is not valid (may contain invalid shaders?)");
        }
        this.pipeline.bind(pass, depth);
        bound.clear();
        dirtyUniforms.addAll(uniforms.keySet());
        dirtyTextures.addAll(views.keySet());
    }
    //?}

    public void bindTexture(String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
        if (textureView == null != (sampler == null)) {
            throw new IllegalArgumentException("texture " + name + " needs a view and a sampler together");
        }
        if (textureView == null) {
            views.remove(name);
            samplers.remove(name);
        } else {
            views.put(name, textureView);
            samplers.put(name, sampler);
        }
        dirtyTextures.add(name);
    }

    public void setUniform(String name, GpuBuffer value) {
        setUniform(name, value.slice(0, value.size()));
    }

    public void setUniform(String name, GpuBufferSlice value) {
        if (!value.equals(uniforms.put(name, value))) {
            dirtyUniforms.add(name);
        }
    }

    //? if >= 26.3 {
    /*@Override
    public void setUniform(int index, @Nullable Object value) {
        var name = pipeline.uniformName(index);
        switch (value) {
            case null -> {
                uniforms.remove(name);
                views.remove(name);
                samplers.remove(name);
                dirtyUniforms.add(name);
                dirtyTextures.add(name);
            }
            case GpuBufferSlice slice -> setUniform(name, slice);
            case GpuBuffer buffer -> setUniform(name, buffer);
            case TextureViewAndSampler pair -> bindTexture(name, pair.view(), pair.sampler());
            default -> throw new IllegalArgumentException("unsupported uniform value for " + name);
        }
    }

    @Override
    public void pushConstants(ByteBuffer value) {
        pushConstants = encoder.transientMemory().uploadGpu(value,
                encoder.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
        pushConstantsDirty = true;
    }
    *///?}

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
        if (area != null) {
            enableScissor(area.x(), area.y(), area.width(), area.height());
        } else {
            enableScissor(0, 0, width, height);
        }
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
        var previous = vertices[slot];
        vertices[slot] = vertexBuffer;
        if (vertexBuffer == null) {
            pass.setVertexBuffer(MTLBuffer.of(0), 0, slot);
            return;
        }
        var buffer = MetalCommandEncoder.buffer(vertexBuffer);
        if (previous != null && MetalCommandEncoder.buffer(previous) == buffer) {
            if (previous.offset() != vertexBuffer.offset()) {
                pass.setVertexBufferOffset(vertexBuffer.offset(), slot);
            }
            return;
        }
        pass.setVertexBuffer(buffer, vertexBuffer.offset(), slot);
    }

    @Override
    public void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType) {
        this.indexBuffer = indexBuffer;
        this.indexType = indexType;
    }

    private void bind() {
        if (pipeline == null || !pipeline.isValid()) {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
        //? if >= 26.3 {
        /*if (pushConstantsDirty) {
            pushConstantsDirty = false;
            var constants = pipeline.getTranslation().pushConstant();
            if (constants != null && pushConstants != null) {
                var buffer = MetalCommandEncoder.buffer(pushConstants);
                pass.setVertexBuffer(buffer, pushConstants.offset(), constants.index());
                pass.setFragmentBuffer(buffer, pushConstants.offset(), constants.index());
            }
        }
        *///?}
        if (dirtyUniforms.isEmpty() && dirtyTextures.isEmpty()) {
            return;
        }
        for (var binding : pipeline.getTranslation().uniforms()) {
            if (dirtyUniforms.contains(binding.name())) {
                bindUniform(binding);
            }
        }
        for (var binding : pipeline.getTranslation().textures()) {
            if (binding.texel() ? dirtyUniforms.contains(binding.name()) : dirtyTextures.contains(binding.name())) {
                bindTexture(binding);
            }
        }
        dirtyUniforms.clear();
        dirtyTextures.clear();
    }

    private void bindUniform(Binding binding) {
        var value = uniforms.get(binding.name());
        if (value == null) {
            throw new IllegalStateException("missing uniform " + binding.name());
        }
        var previous = bound.put(binding.name(), value);
        var buffer = MetalCommandEncoder.buffer(value);
        if (previous != null && MetalCommandEncoder.buffer(previous) == buffer) {
            pass.setVertexBufferOffset(value.offset(), binding.index());
            pass.setFragmentBufferOffset(value.offset(), binding.index());
            return;
        }
        pass.setVertexBuffer(buffer, value.offset(), binding.index());
        pass.setFragmentBuffer(buffer, value.offset(), binding.index());
    }

    private void bindTexture(Binding binding) {
        if (binding.texel()) {
            bindTexel(binding);
            return;
        }
        var view = views.get(binding.name());
        if (view == null) {
            throw new IllegalStateException("missing texture " + binding.name());
        }
        var texture = ((MetalGpuTextureView) view).getView();
        var sampler = ((MetalGpuSampler) samplers.get(binding.name())).getSampler();
        pass.setVertexTexture(texture, binding.index());
        pass.setVertexSamplerState(sampler, binding.index());
        pass.setFragmentTexture(texture, binding.index());
        pass.setFragmentSamplerState(sampler, binding.index());
    }

    private void bindTexel(Binding binding) {
        var slice = uniforms.get(binding.name());
        if (slice == null) {
            throw new IllegalStateException("missing texel buffer " + binding.name());
        }
        var format = pipeline.getTexels().get(binding.name());
        long pixel = format.blockSize();
        long width = slice.length() / pixel;
        long align = encoder.getDevice().texelAlign(format);
        if (slice.offset() % align != 0) {
            var copy = encoder.transientMemory()
                    .allocateGpuMapped(slice.length(), align, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER);
            var source = MetalCommandEncoder.buffer(slice).contents().asSlice(slice.offset(), slice.length());
            MemorySegment.ofBuffer(copy.data()).copyFrom(source);
            copy.close();
            slice = copy.slice();
        }
        var aligned = slice;
        var descriptor = MTLTextureDescriptor.textureBufferDescriptorWithPixelFormat(
                MetalConst.pixelFormat(format), width, MTLResourceOptions.MTLResourceStorageModeShared,
                MTLTextureUsage.MTLTextureUsageShaderRead);
        var view = MetalCommandEncoder.buffer(aligned).newTextureWithDescriptor(descriptor, aligned.offset(),
                width * pixel);
        descriptor.release();
        pass.setVertexTexture(view, binding.index());
        pass.setFragmentTexture(view, binding.index());
        encoder.retire(view::release);
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        bind();
        if (pipeline.isFan()) {
            drawFan(indexCount, instanceCount, vertexOffset, firstInstance, indices(firstIndex));
            return;
        }
        pass.drawIndexedPrimitives(pipeline.topology(), indexCount, MetalConst.indexType(indexType),
                MetalCommandEncoder.buffer(indexBuffer), (long) firstIndex * indexType.bytes, instanceCount,
                vertexOffset, firstInstance);
    }

    private void drawFan(int count, int instanceCount, int vertexOffset, int firstInstance, IntUnaryOperator at) {
        if (count < 3) {
            return;
        }
        int triangles = count - 2;
        var expanded = encoder.transientMemory()
                .allocateGpuMapped(triangles * 3L * Integer.BYTES, 4, GpuBuffer.USAGE_INDEX);
        var data = expanded.data();
        for (int i = 0; i < triangles; i++) {
            data.putInt(at.applyAsInt(0));
            data.putInt(at.applyAsInt(i + 1));
            data.putInt(at.applyAsInt(i + 2));
        }
        data.flip();
        expanded.close();
        var slice = expanded.slice();
        pass.drawIndexedPrimitives(pipeline.topology(), triangles * 3L,
                MTLRenderCommandEncoder.MTLIndexTypeUInt32, MetalCommandEncoder.buffer(slice), slice.offset(),
                instanceCount, vertexOffset, firstInstance);
    }

    private IntUnaryOperator indices(int firstIndex) {
        var source = MetalCommandEncoder.buffer(indexBuffer).contents();
        long base = (long) firstIndex * indexType.bytes;
        return i -> {
            long offset = base + (long) i * indexType.bytes;
            return indexType == IndexType.SHORT
                    ? Short.toUnsignedInt(source.get(ValueLayout.JAVA_SHORT, offset))
                    : source.get(ValueLayout.JAVA_INT, offset);
        };
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
        var buffer = MetalCommandEncoder.buffer(commands);
        for (int i = 0; i < drawCount; i++) {
            pass.drawIndexedPrimitives(pipeline.topology(), MetalConst.indexType(indexType),
                    MetalCommandEncoder.buffer(indexBuffer), 0, buffer,
                    commands.offset() + (long) i * MetalConst.INDEXED_INDIRECT_STRIDE);
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        if (pipeline == null || !pipeline.isValid()) {
            return;
        }
        bind();
        if (pipeline.isFan()) {
            drawFan(vertexCount, instanceCount, firstVertex, firstInstance, i -> i);
            return;
        }
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
        var buffer = MetalCommandEncoder.buffer(commands);
        for (int i = 0; i < drawCount; i++) {
            pass.drawPrimitives(pipeline.topology(), buffer,
                    commands.offset() + (long) i * MetalConst.INDIRECT_STRIDE);
        }
    }

//? if < 26.3 {
    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
            @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument) {
        for (var draw : draws) {
            var uploader = draw.uniformUploaderConsumer();
            if (uploader != null) {
                uploader.accept(uniformArgument, this::setUniform);
            }
            var indices = draw.indexBuffer() == null ? defaultIndexBuffer : draw.indexBuffer();
            var type = draw.indexType() == null ? defaultIndexType : draw.indexType();
            if (indices == null || type == null) {
                throw new IllegalStateException("draw has no index buffer and no default was given");
            }
            setIndexBuffer(indices, type);
            setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }

//?}

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        var queries = (MetalQueryPool) pool;
        if (queries.getSamples() == null) {
            return;
        }
        if (!encoder.getDevice().isDrawSampling()) {
            deferred.add(() -> encoder.writeTimestamp(pool, index));
            return;
        }
        pass.sampleCountersInBuffer(queries.getSamples(), index, true);
        queries.record(index);
    }
}
