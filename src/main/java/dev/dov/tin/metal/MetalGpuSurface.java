package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.device.CAMetalDrawable;
import dev.dov.metalj.device.CAMetalLayer;
import dev.dov.metalj.device.NSWindow;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.objc.CGSize;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineDescriptor;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.pipelines.shaders.MTLCompileOptions;
import dev.dov.metalj.resources.samplers.MTLSamplerDescriptor;
import dev.dov.metalj.resources.samplers.MTLSamplerMinMagFilter;
import dev.dov.metalj.resources.samplers.MTLSamplerState;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import org.lwjgl.glfw.GLFWNativeCocoa;

public class MetalGpuSurface implements GpuSurfaceBackend {
    private final MetalDevice device;
    private final CAMetalLayer layer;
    private final MTLRenderPipelineState blit;
    private final MTLSamplerState sampler;
    private CAMetalDrawable drawable;
    private boolean suboptimal;
    private int width;
    private int height;

    public MetalGpuSurface(MetalDevice device, long window) {
        this.device = device;
        var cocoa = NSWindow.of(GLFWNativeCocoa.glfwGetCocoaWindow(window));
        layer = CAMetalLayer.layer();
        layer.setDevice(device.getDevice());
        layer.setPixelFormat(MTLPixelFormat.MTLPixelFormatBGRA8Unorm);
        layer.setFramebufferOnly(false);
        layer.setContentsScale(cocoa.backingScaleFactor());
        var view = cocoa.contentView();
        view.setWantsLayer(true);
        view.setLayer(layer);
        blit = blitPipeline();
        sampler = blitSampler();
    }

    private MTLRenderPipelineState blitPipeline() {
        var library = device.getDevice().newLibraryWithSource(NSString.stringWithUTF8String(shader()),
                MTLCompileOptions.new_());
        var descriptor = MTLRenderPipelineDescriptor.new_();
        descriptor.setVertexFunction(library.newFunctionWithName(NSString.stringWithUTF8String("blit_vertex")));
        descriptor.setFragmentFunction(library.newFunctionWithName(NSString.stringWithUTF8String("blit_fragment")));
        descriptor.colorAttachments()
                .objectAtIndexedSubscript(0)
                .setPixelFormat(MTLPixelFormat.MTLPixelFormatBGRA8Unorm);
        return device.getDevice().newRenderPipelineStateWithDescriptor(descriptor);
    }

    private MTLSamplerState blitSampler() {
        var descriptor = MTLSamplerDescriptor.new_();
        descriptor.setMinFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterLinear);
        descriptor.setMagFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterLinear);
        return device.getDevice().newSamplerStateWithDescriptor(descriptor);
    }

    @SneakyThrows
    private static String shader() {
        try (var source = MetalGpuSurface.class.getResourceAsStream("/tin/blit.metal")) {
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void configure(GpuSurface.Configuration config) {
        width = config.width();
        height = config.height();
        try (var arena = Arena.ofConfined()) {
            layer.setDrawableSize(CGSize.of(arena, width, height));
        }
        layer.setDisplaySyncEnabled(config.presentMode() == GpuSurface.PresentMode.FIFO);
        suboptimal = false;
    }

    @Override
    public boolean isSuboptimal() {
        return suboptimal;
    }

    @Override
    public void acquireNextTexture() throws SurfaceException {
        drawable = layer.nextDrawable();
        if (drawable.isNull()) {
            drawable = null;
            suboptimal = true;
            throw new SurfaceException("No drawable available");
        }
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        if (drawable == null || drawable.isNull()) {
            return;
        }
        var cmd = ((MetalCommandEncoder) commandEncoder).commandBuffer();
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        var color = pass.colorAttachments().objectAtIndexedSubscript(0);
        color.setTexture(drawable.texture());
        color.setLoadAction(MTLLoadAction.MTLLoadActionDontCare);
        color.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        var encoder = cmd.renderCommandEncoderWithDescriptor(pass);
        encoder.setRenderPipelineState(blit);
        encoder.setFragmentTexture(((MetalGpuTextureView) textureView).getView(), 0);
        encoder.setFragmentSamplerState(sampler, 0);
        encoder.drawPrimitives(MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle, 0, 3);
        encoder.endEncoding();
        cmd.presentDrawable(drawable);
        drawable = null;
    }

    @Override
    public void present() {
        if (drawable == null || drawable.isNull()) {
            return;
        }
        var cmd = device.getQueue().commandBuffer();
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        var color = pass.colorAttachments().objectAtIndexedSubscript(0);
        color.setTexture(drawable.texture());
        color.setLoadAction(MTLLoadAction.MTLLoadActionClear);
        color.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        try (var arena = Arena.ofConfined()) {
            color.setClearColor(MTLClearColor.of(arena, 0, 0, 0, 1));
        }
        cmd.renderCommandEncoderWithDescriptor(pass).endEncoding();
        cmd.presentDrawable(drawable);
        cmd.commit();
        drawable = null;
    }

    @Override
    public void close() {
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.IMMEDIATE, GpuSurface.PresentMode.FIFO);
    }
}
