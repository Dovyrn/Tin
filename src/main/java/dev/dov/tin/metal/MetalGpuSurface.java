package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.device.CAMetalDrawable;
import dev.dov.metalj.device.CAMetalLayer;
import dev.dov.metalj.device.NSWindow;
import dev.dov.metalj.objc.CGSize;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineDescriptor;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.pipelines.shaders.MTLCompileOptions;
import dev.dov.metalj.resources.samplers.MTLSamplerDescriptor;
import dev.dov.metalj.resources.samplers.MTLSamplerMinMagFilter;
import dev.dov.metalj.resources.samplers.MTLSamplerState;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import dev.dov.tin.metal.command.MetalCommandEncoder;
import dev.dov.tin.metal.resource.MetalGpuTextureView;
import dev.dov.tin.metal.shader.MetalShaders;
import java.lang.foreign.Arena;
import java.util.Collection;
import java.util.List;
//? if >= 26.3 {
/*import java.util.function.BooleanSupplier;
*///?}
//? if >= 26.3 {
/*import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
*///?} else {
import org.lwjgl.glfw.GLFWNativeCocoa;
//?}

public class MetalGpuSurface implements GpuSurfaceBackend {
    private final MetalDevice device;
    private final NSWindow cocoa;
    private final CAMetalLayer layer;
    private final MTLRenderPipelineState blit;
    private final MTLSamplerState sampler;
    //? if >= 26.3 {
    /*private final BooleanSupplier iconified;
    *///?}
    private CAMetalDrawable drawable;
    private boolean suboptimal;
    private int width;
    private int height;

    //? if >= 26.3 {
    /*public MetalGpuSurface(MetalDevice device, long window, BooleanSupplier iconified) {
        this.iconified = iconified;
    *///?} else {
    public MetalGpuSurface(MetalDevice device, long window) {
    //?}
        this.device = device;
        //? if >= 26.3 {
        /*cocoa = NSWindow.of(SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(window),
                SDLVideo.SDL_PROP_WINDOW_COCOA_WINDOW_POINTER, 0L));
        *///?} else {
        cocoa = NSWindow.of(GLFWNativeCocoa.glfwGetCocoaWindow(window));
        //?}
        layer = CAMetalLayer.layer();
        layer.setDevice(device.getDevice());
        layer.setPixelFormat(MTLPixelFormat.MTLPixelFormatBGRA8Unorm);
        layer.setFramebufferOnly(true);
        layer.setContentsScale(cocoa.backingScaleFactor());
        var view = cocoa.contentView();
        view.setWantsLayer(true);
        view.setLayer(layer);
        blit = blitPipeline();
        sampler = blitSampler();
    }

    private MTLRenderPipelineState blitPipeline() {
        var options = MTLCompileOptions.new_();
        var source = NSString.stringWithUTF8String(MetalShaders.source("/tin/blit.metal"));
        var library = device.getDevice().newLibraryWithSource(source, options);
        source.release();
        options.release();
        var vertexName = NSString.stringWithUTF8String("blit_vertex");
        var fragmentName = NSString.stringWithUTF8String("blit_fragment");
        var vertex = library.newFunctionWithName(vertexName);
        var fragment = library.newFunctionWithName(fragmentName);
        vertexName.release();
        fragmentName.release();
        library.release();
        var descriptor = MTLRenderPipelineDescriptor.new_();
        descriptor.setVertexFunction(vertex);
        descriptor.setFragmentFunction(fragment);
        descriptor.colorAttachments()
                .objectAtIndexedSubscript(0)
                .setPixelFormat(MTLPixelFormat.MTLPixelFormatBGRA8Unorm);
        var state = device.getDevice().newRenderPipelineStateWithDescriptor(descriptor);
        descriptor.release();
        vertex.release();
        fragment.release();
        return state;
    }

    private MTLSamplerState blitSampler() {
        var descriptor = MTLSamplerDescriptor.new_();
        descriptor.setMinFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterNearest);
        descriptor.setMagFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterNearest);
        var state = device.getDevice().newSamplerStateWithDescriptor(descriptor);
        descriptor.release();
        return state;
    }

    @Override
    public void configure(GpuSurface.Configuration config) {
        width = config.width();
        height = config.height();
        layer.setContentsScale(cocoa.backingScaleFactor());
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
        //? if >= 26.3 {
        /*if (iconified.getAsBoolean()) {
            throw new SurfaceException("Cannot acquire minimized window");
        }
        *///?}
        var next = layer.nextDrawable();
        if (next.isNull()) {
            drawable = null;
            suboptimal = true;
            throw new SurfaceException("No drawable available");
        }
        drawable = next;
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        if (drawable == null) {
            throw new IllegalStateException("No drawable acquired");
        }
        blit(commandEncoder, textureView);
    }

    private void blit(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        var source = ((MetalGpuTextureView) textureView).getView();
        int copyWidth = Math.min(width, textureView.getWidth(0));
        int copyHeight = Math.min(height, textureView.getHeight(0));
        var cmd = ((MetalCommandEncoder) commandEncoder).commandBuffer();
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        var color = pass.colorAttachments().objectAtIndexedSubscript(0);
        color.setTexture(drawable.texture());
        color.setLoadAction(MTLLoadAction.MTLLoadActionDontCare);
        color.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        var encoder = cmd.renderCommandEncoderWithDescriptor(pass);
        pass.release();
        encoder.setRenderPipelineState(blit);
        encoder.setFragmentTexture(source, 0);
        encoder.setFragmentSamplerState(sampler, 0);
        try (var arena = Arena.ofConfined()) {
            var region = arena.allocate(16);
            region.setAtIndex(ObjC.FLOAT, 0, (float) copyWidth / width);
            region.setAtIndex(ObjC.FLOAT, 1, (float) copyHeight / height);
            region.setAtIndex(ObjC.FLOAT, 2, (float) copyWidth / textureView.getWidth(0));
            region.setAtIndex(ObjC.FLOAT, 3, (float) copyHeight / textureView.getHeight(0));
            encoder.setVertexBytes(region, 16, 0);
            encoder.drawPrimitives(MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle, 0, 3);
        }
        encoder.endEncoding();
        encoder.release();
        cmd.presentDrawable(drawable);
    }

    @Override
    public void present() {
        if (drawable != null) {
            drawable.release();
            drawable = null;
        }
    }

    @Override
    public void close() {
        present();
        blit.release();
        sampler.release();
        layer.release();
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.IMMEDIATE, GpuSurface.PresentMode.FIFO);
    }
}
