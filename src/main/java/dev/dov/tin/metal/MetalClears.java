package dev.dov.tin.metal;

import com.mojang.blaze3d.textures.GpuTexture;
import dev.dov.metalj.commands.MTLCommandBuffer;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.encoders.MTLScissorRect;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.pipelines.depth.MTLCompareFunction;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilDescriptor;
import dev.dov.metalj.pipelines.depth.MTLDepthStencilState;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineDescriptor;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.pipelines.shaders.MTLCompileOptions;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.joml.Vector4fc;

public class MetalClears {
    private final MetalDevice device;
    private final Map<Long, MTLRenderPipelineState> pipelines = new HashMap<>();
    private final MTLDepthStencilState depthState;
    private final MTLDepthStencilState colorState;

    public MetalClears(MetalDevice device) {
        this.device = device;
        var writes = MTLDepthStencilDescriptor.new_();
        writes.setDepthCompareFunction(MTLCompareFunction.MTLCompareFunctionAlways);
        writes.setDepthWriteEnabled(true);
        depthState = device.getDevice().newDepthStencilStateWithDescriptor(writes);
        var keeps = MTLDepthStencilDescriptor.new_();
        keeps.setDepthCompareFunction(MTLCompareFunction.MTLCompareFunctionAlways);
        keeps.setDepthWriteEnabled(false);
        colorState = device.getDevice().newDepthStencilStateWithDescriptor(keeps);
    }

    public void region(MTLCommandBuffer cmd, @Nullable GpuTexture color, @Nullable Vector4fc clearColor,
            @Nullable GpuTexture depth, double clearDepth, int x, int y, int width, int height) {
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        long format = MTLPixelFormat.MTLPixelFormatInvalid;
        if (color != null) {
            format = MetalConst.pixelFormat(color.getFormat());
            var attachment = pass.colorAttachments().objectAtIndexedSubscript(0);
            attachment.setTexture(((MetalGpuTexture) color).getTexture());
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        }
        if (depth != null) {
            var attachment = pass.depthAttachment();
            attachment.setTexture(((MetalGpuTexture) depth).getTexture());
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionLoad);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        }
        var encoder = cmd.renderCommandEncoderWithDescriptor(pass);
        encoder.setRenderPipelineState(pipeline(format, depth != null));
        encoder.setDepthStencilState(depth == null ? colorState : depthState);
        try (var arena = Arena.ofConfined()) {
            encoder.setScissorRect(MTLScissorRect.of(arena, x, y, width, height));
            var constants = arena.allocate(20);
            constants.setAtIndex(ObjC.FLOAT, 0, clearColor == null ? 0 : clearColor.x());
            constants.setAtIndex(ObjC.FLOAT, 1, clearColor == null ? 0 : clearColor.y());
            constants.setAtIndex(ObjC.FLOAT, 2, clearColor == null ? 0 : clearColor.z());
            constants.setAtIndex(ObjC.FLOAT, 3, clearColor == null ? 0 : clearColor.w());
            constants.setAtIndex(ObjC.FLOAT, 4, (float) clearDepth);
            encoder.setVertexBytes(constants, 20, 0);
            encoder.setFragmentBytes(constants, 20, 0);
            encoder.drawPrimitives(MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle, 0, 3);
        }
        encoder.endEncoding();
    }

    private MTLRenderPipelineState pipeline(long format, boolean depth) {
        long key = format * 2 + (depth ? 1 : 0);
        return pipelines.computeIfAbsent(key, ignored -> build(format, depth));
    }

    private MTLRenderPipelineState build(long format, boolean depth) {
        var library = device.getDevice().newLibraryWithSource(NSString.stringWithUTF8String(source()),
                MTLCompileOptions.new_());
        var descriptor = MTLRenderPipelineDescriptor.new_();
        descriptor.setVertexFunction(library.newFunctionWithName(NSString.stringWithUTF8String("clear_vertex")));
        descriptor.setFragmentFunction(library.newFunctionWithName(NSString.stringWithUTF8String("clear_fragment")));
        if (format != MTLPixelFormat.MTLPixelFormatInvalid) {
            descriptor.colorAttachments()
                    .objectAtIndexedSubscript(0)
                    .setPixelFormat(format);
        }
        if (depth) {
            descriptor.setDepthAttachmentPixelFormat(MTLPixelFormat.MTLPixelFormatDepth32Float);
        }
        return device.getDevice().newRenderPipelineStateWithDescriptor(descriptor);
    }

    @SneakyThrows
    private static String source() {
        try (var stream = MetalClears.class.getResourceAsStream("/tin/clear.metal")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
