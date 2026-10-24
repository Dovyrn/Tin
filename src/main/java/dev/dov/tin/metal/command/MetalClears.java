package dev.dov.tin.metal.command;

import com.mojang.blaze3d.textures.GpuTexture;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.encoders.MTLScissorRect;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.metal4.MTL4RenderPassDescriptor;
import dev.dov.metalj.metal4.pipelines.MTL4RenderPipelineDescriptor;
import dev.dov.metalj.pipelines.depth.MTLCompareFunction;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilDescriptor;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilState;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import dev.dov.tin.metal.MetalConst;
import dev.dov.tin.metal.MetalDevice;
import dev.dov.tin.metal.resource.MetalGpuTexture;
import dev.dov.tin.metal.shader.MetalShaders;
import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Map;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public class MetalClears {
    private final MetalDevice device;
    private final Map<Long, MTLRenderPipelineState> pipelines = new HashMap<>();
    private final MTLDepthStencilState depthState;
    private final MTLDepthStencilState colorState;

    public MetalClears(MetalDevice device) {
        this.device = device;
        var descriptor = MTLDepthStencilDescriptor.new_();
        descriptor.setDepthCompareFunction(MTLCompareFunction.MTLCompareFunctionAlways);
        descriptor.setDepthWriteEnabled(true);
        depthState = device.getDevice().newDepthStencilStateWithDescriptor(descriptor);
        descriptor.setDepthWriteEnabled(false);
        colorState = device.getDevice().newDepthStencilStateWithDescriptor(descriptor);
        descriptor.release();
    }

    public void close() {
        for (var pipeline : pipelines.values()) {
            pipeline.release();
        }
        depthState.release();
        colorState.release();
    }

    public void region(MetalCommandEncoder commands, @Nullable GpuTexture color, @Nullable Vector4fc clearColor,
            @Nullable GpuTexture depth, double clearDepth, int x, int y, int width, int height, int level) {
        clear(commands, color, clearColor, depth, clearDepth, x, y, width, height, level);
    }

    private void clear(MetalCommandEncoder commands, @Nullable GpuTexture color, @Nullable Vector4fc clearColor,
            @Nullable GpuTexture depth, double clearDepth, int x, int y, int width, int height, int level) {
        var pass = MTL4RenderPassDescriptor.new_();
        long format = MTLPixelFormat.MTLPixelFormatInvalid;
        if (color != null) {
            format = MetalConst.pixelFormat(color.getFormat());
            var attachment = pass.colorAttachments().objectAtIndexedSubscript(0);
            attachment.setTexture(((MetalGpuTexture) color).getTexture());
            attachment.setLevel(level);
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        }
        if (depth != null) {
            var attachment = pass.depthAttachment();
            attachment.setTexture(((MetalGpuTexture) depth).getTexture());
            attachment.setLevel(level);
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        }
        var encoder = commands.render(pass);
        pass.release();
        encoder.setRenderPipelineState(pipelines.computeIfAbsent(format, this::build));
        encoder.setDepthStencilState(depth == null ? colorState : depthState);
        long constants = commands.upload(clearColor == null ? 0 : clearColor.x(),
                clearColor == null ? 0 : clearColor.y(), clearColor == null ? 0 : clearColor.z(),
                clearColor == null ? 0 : clearColor.w(), (float) clearDepth);
        commands.getTable().setAddress(constants, 0);
        try (var arena = Arena.ofConfined()) {
            encoder.setScissorRect(MTLScissorRect.of(arena, x, y, width, height));
        }
        encoder.drawPrimitives(MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle, 0, 3);
        MetalCommandEncoder.end(encoder);
    }

    private MTLRenderPipelineState build(long format) {
        var source = MetalShaders.source("/tin/clear.metal");
        var vertex = device.function(source, "clear_vertex");
        var fragment = device.function(source, "clear_fragment");
        var descriptor = MTL4RenderPipelineDescriptor.new_();
        descriptor.setVertexFunctionDescriptor(vertex);
        descriptor.setFragmentFunctionDescriptor(fragment);
        if (format != MTLPixelFormat.MTLPixelFormatInvalid) {
            descriptor.colorAttachments()
                    .objectAtIndexedSubscript(0)
                    .setPixelFormat(format);
        }
        var state = device.pipeline(descriptor);
        descriptor.release();
        vertex.release();
        fragment.release();
        return state;
    }
}
