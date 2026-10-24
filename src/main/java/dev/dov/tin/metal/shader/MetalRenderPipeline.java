package dev.dov.tin.metal.shader;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.PrimitiveTopology;
//? if >= 26.3 {
/*import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
*///?} else {
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Arrays;
//?}
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.metal4.compiler.MTL4LibraryFunctionDescriptor;
import dev.dov.metalj.metal4.encoders.MTL4RenderCommandEncoder;
import dev.dov.metalj.metal4.pipelines.MTL4BlendState;
import dev.dov.metalj.metal4.pipelines.MTL4RenderPipelineDescriptor;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilDescriptor;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilState;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.pipelines.vertex.MTLVertexDescriptor;
import dev.dov.metalj.pipelines.vertex.MTLVertexStepFunction;
import dev.dov.tin.metal.MetalConst;
import dev.dov.tin.metal.MetalDevice;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

//? if >= 26.3 {
/*public class MetalRenderPipeline implements BackendRenderPipeline {
*///?} else {
public class MetalRenderPipeline implements CompiledRenderPipeline {
//?}
    private final MetalDevice device;
    @Getter
    private final Translation translation;
    @Getter
    private final Map<String, GpuFormat> texels;
    //? if >= 26.3 {
    /*private final List<String> uniformNames;
    *///?}
    private final long fill;
    @Getter
    private final boolean fan;
    private final MTLDepthStencilState depthState;
    private final long topology;
    private final long cull;
    private final float biasScale;
    private final float biasConstant;
    private final @Nullable MTLRenderPipelineState state;
    @Getter
    private boolean closed;

    //? if >= 26.3 {
    /*public MetalRenderPipeline(MetalDevice device, BackendRenderPipeline.CreateInfo pipeline,
            MTL4LibraryFunctionDescriptor vertex, MTL4LibraryFunctionDescriptor fragment, Translation translation,
            Map<String, GpuFormat> texels) {
        uniformNames = pipeline.uniforms().stream().map(BindGroupLayout.UniformDescription::name).toList();
        var shape = pipeline.primitiveTopology();
        var mode = pipeline.polygonMode();
        var depth = pipeline.depthStencilState();
        boolean culling = pipeline.cull();
        var name = pipeline.name();
        var targets = pipeline.colorTargetStates();
    *///?} else {
    public MetalRenderPipeline(MetalDevice device, RenderPipeline pipeline, MTL4LibraryFunctionDescriptor vertex,
            MTL4LibraryFunctionDescriptor fragment, Translation translation, Map<String, GpuFormat> texels) {
        var shape = pipeline.getPrimitiveTopology();
        var mode = pipeline.getPolygonMode();
        var depth = pipeline.getDepthStencilState();
        boolean culling = pipeline.isCull();
        var name = pipeline.getLocation().toString();
        var targets = Arrays.asList(pipeline.getColorTargetStates());
    //?}
        this.device = device;
        this.translation = translation;
        this.texels = texels;
        fan = shape == PrimitiveTopology.TRIANGLE_FAN;
        fill = mode == PolygonMode.WIREFRAME
                ? MTLRenderCommandEncoder.MTLTriangleFillModeLines
                : MTLRenderCommandEncoder.MTLTriangleFillModeFill;
        var depthDescriptor = MTLDepthStencilDescriptor.new_();
        depthDescriptor.setDepthCompareFunction(depth == null
                ? MetalConst.compareFunction(CompareOp.ALWAYS_PASS)
                : MetalConst.compareFunction(depth.depthTest()));
        depthDescriptor.setDepthWriteEnabled(depth != null && depth.writeDepth());
        depthState = device.getDevice().newDepthStencilStateWithDescriptor(depthDescriptor);
        depthDescriptor.release();
        biasScale = depth == null ? 0 : depth.depthBiasScaleFactor();
        biasConstant = depth == null ? 0 : depth.depthBiasConstant();
        topology = MetalConst.primitiveType(shape);
        cull = culling
                ? MTLRenderCommandEncoder.MTLCullModeBack
                : MTLRenderCommandEncoder.MTLCullModeNone;
        if (vertex == null || translation == null) {
            state = null;
            return;
        }
        var descriptor = MTL4RenderPipelineDescriptor.new_();
        if (device.useLabels()) {
            var label = NSString.stringWithUTF8String(name);
            descriptor.setLabel(label);
            label.release();
        }
        descriptor.setVertexFunctionDescriptor(vertex);
        descriptor.setFragmentFunctionDescriptor(fragment);
        descriptor.setInputPrimitiveTopology(MetalConst.topologyClass(shape));
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            if (target == null) {
                continue;
            }
            var attachment = descriptor.colorAttachments().objectAtIndexedSubscript(i);
            attachment.setPixelFormat(MetalConst.pixelFormat(target.format()));
            attachment.setWriteMask(MetalConst.writeMask(target.writeMask()));
            if (target.blendFunction().isPresent()) {
                var blend = target.blendFunction().get();
                attachment.setBlendingState(MTL4BlendState.MTL4BlendStateEnabled);
                attachment.setSourceRGBBlendFactor(MetalConst.blendFactor(blend.color().sourceFactor()));
                attachment.setDestinationRGBBlendFactor(MetalConst.blendFactor(blend.color().destFactor()));
                attachment.setRgbBlendOperation(MetalConst.blendOp(blend.color().op()));
                attachment.setSourceAlphaBlendFactor(MetalConst.blendFactor(blend.alpha().sourceFactor()));
                attachment.setDestinationAlphaBlendFactor(MetalConst.blendFactor(blend.alpha().destFactor()));
                attachment.setAlphaBlendOperation(MetalConst.blendOp(blend.alpha().op()));
            }
        }
        var layout = layout(pipeline);
        descriptor.setVertexDescriptor(layout);
        layout.release();
        MTLRenderPipelineState compiled = null;
        try {
            compiled = device.pipeline(descriptor);
        } catch (IllegalStateException e) {
            device.message("Couldn't compile pipeline " + name + ": " + e.getMessage());
        }
        descriptor.release();
        state = compiled;
    }

    //? if >= 26.3 {
    /*public String uniformName(int index) {
        return uniformNames.get(index);
    }

    public int uniformCount() {
        return uniformNames.size();
    }

    private static MTLVertexDescriptor layout(BackendRenderPipeline.CreateInfo pipeline) {
        var layout = MTLVertexDescriptor.vertexDescriptor();
        for (var buffer : pipeline.vertexBuffers()) {
            var target = layout.layouts().objectAtIndexedSubscript(buffer.bufferSlot());
            target.setStride(buffer.stride());
            if (buffer.stepRate() > 0) {
                target.setStepFunction(MTLVertexStepFunction.MTLVertexStepFunctionPerInstance);
                target.setStepRate(buffer.stepRate());
            } else {
                target.setStepFunction(MTLVertexStepFunction.MTLVertexStepFunctionPerVertex);
            }
        }
        for (var binding : pipeline.attribBindings()) {
            var attribute = layout.attributes().objectAtIndexedSubscript(binding.location());
            attribute.setFormat(MetalConst.vertexFormat(binding.format()));
            attribute.setOffset(binding.offset());
            attribute.setBufferIndex(binding.bufferSlot());
        }
        return layout;
    }
    *///?} else {
    private static MTLVertexDescriptor layout(RenderPipeline pipeline) {
        var layout = MTLVertexDescriptor.vertexDescriptor();
        var bindings = pipeline.getVertexFormatBindings();
        int location = 0;
        for (int slot = 0; slot < bindings.length; slot++) {
            var format = bindings[slot];
            if (format == null) {
                continue;
            }
            var buffer = layout.layouts().objectAtIndexedSubscript(slot);
            buffer.setStride(format.getVertexSize());
            if (format.getStepRate() > 0) {
                buffer.setStepFunction(MTLVertexStepFunction.MTLVertexStepFunctionPerInstance);
                buffer.setStepRate(format.getStepRate());
            } else {
                buffer.setStepFunction(MTLVertexStepFunction.MTLVertexStepFunctionPerVertex);
            }
            for (var element : format.getElements()) {
                var attribute = layout.attributes().objectAtIndexedSubscript(location);
                attribute.setFormat(MetalConst.vertexFormat(element.format()));
                attribute.setOffset(element.offset());
                attribute.setBufferIndex(slot);
                location++;
            }
        }
        return layout;
    }
    //?}

    public boolean isValid() {
        return state != null;
    }

    public void close() {
        closed = true;
        if (state != null) {
            device.retire(state::release);
        }
        device.retire(depthState::release);
    }

    public long topology() {
        return topology;
    }

    public void bind(MTL4RenderCommandEncoder pass) {
        pass.setRenderPipelineState(state);
        pass.setDepthStencilState(depthState);
        pass.setCullMode(cull);
        pass.setTriangleFillMode(fill);
        pass.setDepthBias(biasConstant, biasScale, 0);
    }
}
