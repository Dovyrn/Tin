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
import dev.dov.metalj.resources.MTLOrigin;
import dev.dov.metalj.resources.MTLSize;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.joml.Vector4fc;

public class MetalCommandEncoder implements CommandEncoderBackend {
    private static final int IN_FLIGHT = 2;

    @lombok.Getter
    private final MetalDevice device;
    private final MetalTransientMemory memory;
    private final List<Runnable> pending = new ArrayList<>();
    private final Deque<List<Runnable>> retired = new ArrayDeque<>();
    private final NavigableMap<Long, MTLCommandBuffer> batches = new TreeMap<>();
    @lombok.Getter
    private long submits;
    @lombok.Getter
    private long completed = -1;
    private MTLCommandBuffer cmd;
    private MetalRenderPass pass;

    public MetalCommandEncoder(MetalDevice device) {
        this.device = device;
        memory = new MetalTransientMemory(device);
    }

    public MTLCommandBuffer commandBuffer() {
        if (pass != null) {
            throw new IllegalStateException("Cannot start command buffer while inside RenderPass");
        }
        if (cmd == null) {
            cmd = device.getQueue().commandBuffer();
            cmd.retain();
        }
        return cmd;
    }

    @Override
    public void submit() {
        if (pass != null) {
            throw new IllegalStateException("Cannot submit while inside a render pass");
        }
        memory.endSubmit();
        retired.addLast(List.copyOf(pending));
        pending.clear();
        if (cmd != null) {
            cmd.commit();
            batches.put(submits, cmd);
            cmd = null;
        }
        submits++;
        while (batches.size() > IN_FLIGHT) {
            finish(batches.firstKey());
        }
        while (retired.size() > IN_FLIGHT) {
            for (var callback : retired.removeFirst()) {
                callback.run();
            }
        }
    }

    private void finish(long index) {
        var buffer = batches.remove(index);
        buffer.waitUntilCompleted();
        buffer.release();
        completed = Math.max(completed, index);
    }

    @Override
    public TransientMemory transientMemory() {
        return memory;
    }

    @Override
    public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
        if (pass != null) {
            throw new IllegalStateException("Cannot start a render pass while one is already open");
        }
        pass = new MetalRenderPass(this, descriptor);
        return pass;
    }

    @Override
    public void submitRenderPass() {
        if (pass == null) {
            throw new IllegalStateException("Cannot submit a renderpass if one hasn't been started!");
        }
        var ended = pass;
        pass = null;
        ended.end();
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
        if (whole) {
            clear(colorTexture, clearColor, depthTexture, clearDepth);
            return;
        }
        device.getClears().region(commandBuffer(), colorTexture, clearColor, depthTexture, clearDepth, regionX,
                regionY, regionWidth, regionHeight);
    }

    @Override
    public void clearDepthTexture(GpuTexture depthTexture, double clearDepth) {
        clear(null, null, depthTexture, clearDepth);
    }

    private void clear(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth) {
        int levels = Math.max(color == null ? 0 : color.getMipLevels(), depth == null ? 0 : depth.getMipLevels());
        for (int level = 0; level < levels; level++) {
            clearLevel(color, clearColor, depth, clearDepth, level);
        }
    }

    private void clearLevel(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth, int level) {
        var pass = MTLRenderPassDescriptor.renderPassDescriptor();
        if (color != null) {
            var attachment = pass.colorAttachments().objectAtIndexedSubscript(0);
            attachment.setTexture(((MetalGpuTexture) color).getTexture());
            attachment.setLevel(level);
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
            attachment.setLevel(level);
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            attachment.setClearDepth(clearDepth);
        }
        commandBuffer().renderCommandEncoderWithDescriptor(pass).endEncoding();
    }

    private GpuBufferSlice staging(ByteBuffer data) {
        return memory.uploadStaging(data, 1, GpuBuffer.USAGE_COPY_SRC);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
        copyToBuffer(staging(data), destination);
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        var blit = commandBuffer().blitCommandEncoder();
        blit.copyFromBuffer(buffer(source), source.offset(), buffer(target), target.offset(), source.length());
        blit.endEncoding();
    }

    public static MTLBuffer buffer(GpuBufferSlice slice) {
        return buffer(slice.buffer());
    }

    public static MTLBuffer buffer(GpuBuffer owner) {
        if (owner instanceof MetalTransientView view) {
            return view.block().getBuffer();
        }
        return owner instanceof MetalTransientBuffer block
                ? block.getBuffer()
                : ((MetalGpuBuffer) owner).getBuffer();
    }

    @Override
    public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int destX,
            int destY, int width, int height) {
        long pixel = destination.getFormat().blockSize();
        long row = width * pixel;
        var slice = memory.uploadStaging(source, Math.max(4, pixel), GpuBuffer.USAGE_COPY_SRC);
        copyToTexture(buffer(slice), slice.offset(), row, destination, mipLevel, depthOrLayer, destX, destY, width,
                height);
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
        pending.addLast(callback);
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

    public void waitIdle() {
        while (!batches.isEmpty()) {
            finish(batches.firstKey());
        }
        if (submits > 0) {
            completed = submits - 1;
        }
    }

    public void close() {
        waitIdle();
        for (var batch : retired) {
            for (var callback : batch) {
                callback.run();
            }
        }
        retired.clear();
        for (var callback : pending) {
            callback.run();
        }
        pending.clear();
        memory.close();
    }

    public void retire(Runnable release) {
        pending.add(release);
    }

    @Override
    public GpuFence createFence() {
        return new MetalFence(this, submits);
    }

    public boolean awaitSubmit(long index, long timeoutNs) {
        if (completed >= index) {
            return true;
        }
        if (index == submits) {
            if (timeoutNs == 0) {
                return false;
            }
            throw new IllegalStateException("Cannot wait on a fence for the current submit");
        }
        var buffer = batches.get(index);
        if (buffer == null) {
            return true;
        }
        if (timeoutNs == 0 && buffer.status() != MTLCommandBuffer.MTLCommandBufferStatusCompleted) {
            return false;
        }
        while (!batches.isEmpty() && batches.firstKey() <= index) {
            finish(batches.firstKey());
        }
        return true;
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        ((MetalQueryPool) pool).write(commandBuffer(), index);
    }
}
