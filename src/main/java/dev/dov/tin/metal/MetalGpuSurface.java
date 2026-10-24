package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.device.CAMetalDrawable;
import dev.dov.metalj.device.CAMetalLayer;
import dev.dov.metalj.device.NSWindow;
import dev.dov.metalj.metal4.MTL4RenderPassDescriptor;
import dev.dov.metalj.metal4.pipelines.MTL4RenderPipelineDescriptor;
import dev.dov.metalj.objc.CGSize;
import dev.dov.metalj.objc.NSObject;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.pipelines.render.MTLRenderPipelineState;
import dev.dov.metalj.residency.MTLResidencySet;
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
    private static final long RESIDENCY_SET = ObjC.sel("residencySet");

    private final MetalDevice device;
    private final NSWindow cocoa;
    private final CAMetalLayer layer;
    private final MTLResidencySet drawables;
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
        drawables = MTLResidencySet.of(NSObject.sendPtr(layer.getId(), RESIDENCY_SET));
        device.getQueue().addResidencySet(drawables);
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
        var source = MetalShaders.source("/tin/blit.metal");
        var vertex = device.function(source, "blit_vertex");
        var fragment = device.function(source, "blit_fragment");
        var descriptor = MTL4RenderPipelineDescriptor.new_();
        descriptor.setVertexFunctionDescriptor(vertex);
        descriptor.setFragmentFunctionDescriptor(fragment);
        descriptor.colorAttachments()
                .objectAtIndexedSubscript(0)
                .setPixelFormat(MTLPixelFormat.MTLPixelFormatBGRA8Unorm);
        var state = device.pipeline(descriptor);
        descriptor.release();
        vertex.release();
        fragment.release();
        return state;
    }

    private MTLSamplerState blitSampler() {
        var descriptor = MTLSamplerDescriptor.new_();
        descriptor.setMinFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterNearest);
        descriptor.setMagFilter(MTLSamplerMinMagFilter.MTLSamplerMinMagFilterNearest);
        descriptor.setSupportArgumentBuffers(true);
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
        var commands = (MetalCommandEncoder) commandEncoder;
        var pass = MTL4RenderPassDescriptor.new_();
        var color = pass.colorAttachments().objectAtIndexedSubscript(0);
        color.setTexture(drawable.texture());
        color.setLoadAction(MTLLoadAction.MTLLoadActionDontCare);
        color.setStoreAction(MTLStoreAction.MTLStoreActionStore);
        var encoder = commands.render(pass);
        pass.release();
        encoder.setRenderPipelineState(blit);
        var table = commands.getTable();
        table.setTexture(source.gpuResourceID(), 0);
        table.setSamplerState(sampler.gpuResourceID(), 0);
        table.setAddress(commands.upload((float) copyWidth / width, (float) copyHeight / height,
                (float) copyWidth / textureView.getWidth(0), (float) copyHeight / textureView.getHeight(0)), 0);
        encoder.drawPrimitives(MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle, 0, 3);
        MetalCommandEncoder.end(encoder);
        commands.present(drawable);
    }

    @Override
    public void present() {
        if (drawable != null) {
            drawable.present();
            drawable.release();
            drawable = null;
        }
    }

    @Override
    public void close() {
        if (drawable != null) {
            drawable.release();
            drawable = null;
        }
        device.getQueue().removeResidencySet(drawables);
        device.retire(blit::release);
        device.retire(sampler::release);
        device.retire(layer::release);
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.IMMEDIATE, GpuSurface.PresentMode.FIFO);
    }
}
