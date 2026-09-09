package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilDescriptor;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilState;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineDescriptor;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.pipelines.shaders.MTLFunction;
import dev.dov.metalj.pipelines.vertex.MTLVertexDescriptor;
import dev.dov.metalj.pipelines.vertex.MTLVertexStepFunction;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import java.util.Map;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

public class MetalRenderPipeline implements CompiledRenderPipeline {
    private final MetalDevice device;
    @Getter
    private final Translation translation;
    @Getter
    private final Map<String, GpuFormat> texels;
    private final long fill;
    @Getter
    private final boolean fan;
    private final MTLDepthStencilState depthState;
    private final long topology;
    private final long cull;
    private final float biasScale;
    private final float biasConstant;
    private final @Nullable MTLRenderPipelineState state;
    private final @Nullable MTLRenderPipelineState noDepth;

    public MetalRenderPipeline(MetalDevice device, RenderPipeline pipeline, MTLFunction vertex,
            MTLFunction fragment, Translation translation, Map<String, GpuFormat> texels) {
        this.device = device;
        this.translation = translation;
        this.texels = texels;
        fan = pipeline.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN;
        fill = pipeline.getPolygonMode() == PolygonMode.WIREFRAME
                ? MTLRenderCommandEncoder.MTLTriangleFillModeLines
                : MTLRenderCommandEncoder.MTLTriangleFillModeFill;
        var depth = pipeline.getDepthStencilState();
        var depthDescriptor = MTLDepthStencilDescriptor.new_();
        depthDescriptor.setDepthCompareFunction(depth == null
                ? MetalConst.compareFunction(CompareOp.ALWAYS_PASS)
                : MetalConst.compareFunction(depth.depthTest()));
        depthDescriptor.setDepthWriteEnabled(depth != null && depth.writeDepth());
        depthState = device.getDevice().newDepthStencilStateWithDescriptor(depthDescriptor);
        depthDescriptor.release();
        biasScale = depth == null ? 0 : depth.depthBiasScaleFactor();
        biasConstant = depth == null ? 0 : depth.depthBiasConstant();
        topology = MetalConst.primitiveType(pipeline.getPrimitiveTopology());
        cull = pipeline.isCull()
                ? MTLRenderCommandEncoder.MTLCullModeBack
                : MTLRenderCommandEncoder.MTLCullModeNone;
        if (vertex == null || translation == null) {
            state = null;
            noDepth = null;
            return;
        }
        var descriptor = MTLRenderPipelineDescriptor.new_();
        if (device.useLabels()) {
            descriptor.setLabel(NSString.stringWithUTF8String(pipeline.getLocation().toString()));
        }
        descriptor.setVertexFunction(vertex);
        descriptor.setFragmentFunction(fragment);
        descriptor.setInputPrimitiveTopology(MetalConst.topologyClass(pipeline.getPrimitiveTopology()));
        var targets = pipeline.getColorTargetStates();
        for (int i = 0; i < targets.length; i++) {
            var target = targets[i];
            if (target == null) {
                continue;
            }
            var attachment = descriptor.colorAttachments().objectAtIndexedSubscript(i);
            attachment.setPixelFormat(MetalConst.pixelFormat(target.format()));
            attachment.setWriteMask(MetalConst.writeMask(target.writeMask()));
            if (target.blendFunction().isPresent()) {
                var blend = target.blendFunction().get();
                attachment.setBlendingEnabled(true);
                attachment.setSourceRGBBlendFactor(MetalConst.blendFactor(blend.color().sourceFactor()));
                attachment.setDestinationRGBBlendFactor(MetalConst.blendFactor(blend.color().destFactor()));
                attachment.setRgbBlendOperation(MetalConst.blendOp(blend.color().op()));
                attachment.setSourceAlphaBlendFactor(MetalConst.blendFactor(blend.alpha().sourceFactor()));
                attachment.setDestinationAlphaBlendFactor(MetalConst.blendFactor(blend.alpha().destFactor()));
                attachment.setAlphaBlendOperation(MetalConst.blendOp(blend.alpha().op()));
            }
        }
        descriptor.setVertexDescriptor(layout(pipeline));
        descriptor.setDepthAttachmentPixelFormat(MTLPixelFormat.MTLPixelFormatDepth32Float);
        MTLRenderPipelineState compiled = null;
        MTLRenderPipelineState compiledNoDepth = null;
        try {
            compiled = device.getDevice().newRenderPipelineStateWithDescriptor(descriptor);
            if (depth == null) {
                descriptor.setDepthAttachmentPixelFormat(MTLPixelFormat.MTLPixelFormatInvalid);
                compiledNoDepth = device.getDevice().newRenderPipelineStateWithDescriptor(descriptor);
            }
        } catch (IllegalStateException e) {
            device.message("Couldn't compile pipeline " + pipeline.getLocation() + ": " + e.getMessage());
            compiled = null;
            compiledNoDepth = null;
        }
        descriptor.release();
        state = compiled;
        noDepth = compiledNoDepth;
    }

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

    @Override
    public boolean isValid() {
        return state != null;
    }

    public void close() {
        if (state != null) {
            state.release();
        }
        if (noDepth != null) {
            noDepth.release();
        }
        depthState.release();
    }

    public long topology() {
        return topology;
    }

    public void bind(MTLRenderCommandEncoder pass, boolean depth) {
        var chosen = depth || noDepth == null ? state : noDepth;
        if (chosen == null) {
            throw new IllegalStateException("pipeline does not fit this pass");
        }
        pass.setRenderPipelineState(chosen);
        pass.setDepthStencilState(depthState);
        pass.setCullMode(cull);
        pass.setTriangleFillMode(fill);
        pass.setDepthBias(biasConstant, biasScale, 0);
    }
}
