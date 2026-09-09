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
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLRenderPassDescriptor;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.objc.Block;
import dev.dov.metalj.resources.MTLOrigin;
import dev.dov.metalj.resources.MTLResourceOptions;
import dev.dov.metalj.resources.MTLSize;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.RequiredArgsConstructor;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

@RequiredArgsConstructor
public class MetalCommandEncoder implements CommandEncoderBackend {
    private static final int IN_FLIGHT = 2;

    private final MetalDevice device;
    private final MetalTransientMemory memory = new MetalTransientMemory();
    private final Deque<MTLCommandBuffer> submitted = new ArrayDeque<>();
    private MTLCommandBuffer cmd;

    public MTLCommandBuffer commandBuffer() {
        if (cmd == null) {
            cmd = device.getQueue().commandBuffer();
        }
        return cmd;
    }

    @Override
    public void submit() {
        if (cmd != null) {
            cmd.commit();
            submitted.addLast(cmd);
            cmd = null;
        }
        while (submitted.size() > IN_FLIGHT) {
            submitted.removeFirst().waitUntilCompleted();
        }
    }

    @Override
    public TransientMemory transientMemory() {
        return memory;
    }

    @Override
    public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
        return new MetalRenderPass(this, descriptor);
    }

    @Override
    public void submitRenderPass() {
    }

    @Override
    public void clearColorTexture(GpuTexture colorTexture, Vector4fc clearColor) {
        clear(colorTexture, clearColor, null, 0);
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
            double clearDepth) {
        clear(colorTexture, clearColor, depthTexture, clearDepth);
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
            double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight) {
        boolean whole = regionX == 0 && regionY == 0 && regionWidth == colorTexture.getWidth(0)
                && regionHeight == colorTexture.getHeight(0);
        if (!whole) {
            throw new UnsupportedOperationException("scissored clear");
        }
        clear(colorTexture, clearColor, depthTexture, clearDepth);
    }

    @Override
    public void clearDepthTexture(GpuTexture depthTexture, double clearDepth) {
        clear(null, null, depthTexture, clearDepth);
    }

    private void clear(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth) {
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        if (color != null) {
            var attachment = pass.colorAttachments().objectAtIndexedSubscript(0);
            attachment.setTexture(((MetalGpuTexture) color).getTexture());
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            try (var arena = Arena.ofConfined()) {
                attachment.setClearColor(MTLClearColor.of(arena, clearColor.x(), clearColor.y(), clearColor.z(),
                        clearColor.w()));
            }
        }
        if (depth != null) {
            var attachment = pass.depthAttachment();
            attachment.setTexture(((MetalGpuTexture) depth).getTexture());
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            attachment.setClearDepth(clearDepth);
        }
        commandBuffer().renderCommandEncoderWithDescriptor(pass).endEncoding();
    }

    private MTLBuffer staging(ByteBuffer data) {
        long size = data.remaining();
        var bytes = MemorySegment.ofAddress(MemoryUtil.memAddress(data)).reinterpret(size);
        return device.getDevice().newBufferWithBytes(bytes, size, MTLResourceOptions.MTLResourceStorageModeShared);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
        var source = staging(data);
        var blit = commandBuffer().blitCommandEncoder();
        blit.copyFromBuffer(source, 0, buffer(destination), destination.offset(), data.remaining());
        blit.endEncoding();
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        var blit = commandBuffer().blitCommandEncoder();
        blit.copyFromBuffer(buffer(source), source.offset(), buffer(target), target.offset(), source.length());
        blit.endEncoding();
    }

    private static MTLBuffer buffer(GpuBufferSlice slice) {
        return ((MetalGpuBuffer) slice.buffer()).getBuffer();
    }

    @Override
    public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int destX,
            int destY, int width, int height) {
        long row = (long) width * destination.getFormat().blockSize();
        copyToTexture(staging(source), 0, row, destination, mipLevel, depthOrLayer, destX, destY, width, height);
    }

    @Override
    public void copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth,
            int sourceHeight, GpuTexture destination, int destinationX, int destinationY, int copyWidth,
            int copyHeight, int mipLevel, int arrayLayer) {
        long pixel = destination.getFormat().blockSize();
        long row = sourceWidth * pixel;
        long start = source.offset() + sourceY * row + sourceX * pixel;
        copyToTexture(buffer(source), start, row, destination, mipLevel, arrayLayer, destinationX, destinationY,
                copyWidth, copyHeight);
    }

    private void copyToTexture(MTLBuffer source, long offset, long row, GpuTexture destination, int mipLevel,
            int layer, int x, int y, int width, int height) {
        var blit = commandBuffer().blitCommandEncoder();
        try (var arena = Arena.ofConfined()) {
            blit.copyFromBuffer(source, offset, row, row * height, MTLSize.of(arena, width, height, 1),
                    ((MetalGpuTexture) destination).getTexture(), layer, mipLevel,
                    MTLOrigin.of(arena, x, y, 0));
        }
        blit.endEncoding();
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback,
            int mipLevel) {
        copyTextureToBuffer(source, destination, offset, callback, mipLevel, 0, 0, source.getWidth(mipLevel),
                source.getHeight(mipLevel));
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback,
            int mipLevel, int x, int y, int width, int height) {
        long row = (long) width * source.getFormat().blockSize();
        var blit = commandBuffer().blitCommandEncoder();
        try (var arena = Arena.ofConfined()) {
            blit.copyFromTexture(((MetalGpuTexture) source).getTexture(), 0, mipLevel, MTLOrigin.of(arena, x, y, 0),
                    MTLSize.of(arena, width, height, 1), ((MetalGpuBuffer) destination).getBuffer(), offset, row,
                    row * height);
        }
        blit.endEncoding();
        commandBuffer().addCompletedHandler(Block.once(callback));
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destX, int destY,
            int sourceX, int sourceY, int width, int height) {
        var blit = commandBuffer().blitCommandEncoder();
        try (var arena = Arena.ofConfined()) {
            blit.copyFromTexture(((MetalGpuTexture) source).getTexture(), 0, mipLevel,
                    MTLOrigin.of(arena, sourceX, sourceY, 0), MTLSize.of(arena, width, height, 1),
                    ((MetalGpuTexture) destination).getTexture(), 0, mipLevel, MTLOrigin.of(arena, destX, destY, 0));
        }
        blit.endEncoding();
    }

    @Override
    public GpuFence createFence() {
        return new MetalFence(commandBuffer());
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        ((MetalQueryPool) pool).write(commandBuffer(), index);
    }
}
