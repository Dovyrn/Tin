package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.device.CAMetalDrawable;
import dev.dov.metalj.device.CAMetalLayer;
import dev.dov.metalj.device.NSWindow;
import dev.dov.metalj.objc.CGSize;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import java.lang.foreign.Arena;
import java.util.Collection;
import java.util.List;
import org.lwjgl.glfw.GLFWNativeCocoa;

public class MetalGpuSurface implements GpuSurfaceBackend {
    private final MetalDevice device;
    private final CAMetalLayer layer;
    private CAMetalDrawable drawable;
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
    }

    @Override
    public void configure(GpuSurface.Configuration config) {
        width = config.width();
        height = config.height();
        try (var arena = Arena.ofConfined()) {
            layer.setDrawableSize(CGSize.of(arena, width, height));
        }
        layer.setDisplaySyncEnabled(config.presentMode() == GpuSurface.PresentMode.FIFO);
    }

    @Override
    public boolean isSuboptimal() {
        return false;
    }

    @Override
    public void acquireNextTexture() {
        drawable = layer.nextDrawable();
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        if (drawable == null || drawable.isNull()) {
            return;
        }
        var cmd = ((MetalCommandEncoder) commandEncoder).commandBuffer();
        var blit = cmd.blitCommandEncoder();
        blit.copyFromTexture(((MetalGpuTextureView) textureView).getView(), drawable.texture());
        blit.endEncoding();
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
