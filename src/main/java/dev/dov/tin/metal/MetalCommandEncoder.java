package dev.dov.tin.metal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.TransientMemory;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.dov.metalj.commands.MTLCommandBuffer;
import java.nio.ByteBuffer;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector4fc;

@lombok.RequiredArgsConstructor
public class MetalCommandEncoder implements CommandEncoderBackend {
    private final MetalDevice device;
    private MTLCommandBuffer cmd;

    public MTLCommandBuffer commandBuffer() {
        if (cmd == null) {
            cmd = device.getQueue().commandBuffer();
        }
        return cmd;
    }

    private final MetalTransientMemory memory = new MetalTransientMemory();

    @Override
    public void submit() {
        if (cmd != null) {
            cmd.commit();
            cmd = null;
        }
    }

    @Override
    public @NotNull TransientMemory transientMemory() {
        return memory;
    }

    @Override
    public @NotNull RenderPassBackend createRenderPass(@NotNull RenderPassDescriptor descriptor) {
        return new MetalRenderPass();
    }

    @Override
    public void submitRenderPass() {
    }

    @Override
    public void clearColorTexture(@NotNull GpuTexture colorTexture, @NotNull Vector4fc clearColor) {
    }

    @Override
    public void clearColorAndDepthTextures(@NotNull GpuTexture colorTexture, @NotNull Vector4fc clearColor, @NotNull GpuTexture depthTexture,
                                           double clearDepth) {
    }

    @Override
    public void clearColorAndDepthTextures(@NotNull GpuTexture colorTexture, @NotNull Vector4fc clearColor, @NotNull GpuTexture depthTexture,
                                           double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight) {
    }

    @Override
    public void clearDepthTexture(@NotNull GpuTexture depthTexture, double clearDepth) {
    }

    @Override
    public void writeToBuffer(@NotNull GpuBufferSlice destination, @NotNull ByteBuffer data) {
    }

    @Override
    public void copyToBuffer(@NotNull GpuBufferSlice source, @NotNull GpuBufferSlice target) {
    }

    @Override
    public void writeToTexture(@NotNull GpuTexture destination, @NotNull ByteBuffer source, int mipLevel, int depthOrLayer, int destX,
                               int destY, int width, int height) {
    }

    @Override
    public void copyBufferToTexture(@NotNull GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth,
                                    int sourceHeight, @NotNull GpuTexture destination, int destinationX, int destinationY, int copyWidth,
                                    int copyHeight, int mipLevel, int arrayLayer) {
    }

    @Override
    public void copyTextureToBuffer(@NotNull GpuTexture source, @NotNull GpuBuffer destination, long offset, Runnable callback,
                                    int mipLevel) {
        callback.run();
    }

    @Override
    public void copyTextureToBuffer(@NotNull GpuTexture source, @NotNull GpuBuffer destination, long offset, Runnable callback,
                                    int mipLevel, int x, int y, int width, int height) {
        callback.run();
    }

    @Override
    public void copyTextureToTexture(@NotNull GpuTexture source, @NotNull GpuTexture destination, int mipLevel, int destX, int destY,
                                     int sourceX, int sourceY, int width, int height) {
    }

    @Override
    public @NotNull GpuFence createFence() {
        return new MetalFence();
    }

    @Override
    public void writeTimestamp(@NotNull GpuQueryPool pool, int index) {
    }
}
