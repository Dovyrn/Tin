package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.TransientMemory;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import lombok.Getter;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

public class MTLEncoder implements CommandEncoderBackend {
    @Getter
    private final long handle;
    private final MTLDevice device;
    private final MTLMemory memory;

    public MTLEncoder(long handle, MTLDevice device) {
        this.handle = handle;
        this.device = device;
        memory = new MTLMemory(Native.nEncoderMemory(handle));
    }

    @Override
    public void submit() {
        Native.nEncoderSubmit(handle);
    }

    @Override
    public TransientMemory transientMemory() {
        return memory;
    }

    @Override
    public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
        var colors = descriptor.colorAttachments();
        long[] views = new long[colors.size() + 1];
        float[] clears = new float[views.length * 5];
        for (int i = 0; i < colors.size(); i++) {
            var color = colors.get(i);
            if (color == null) {
                continue;
            }
            views[i] = ((MTLView) color.textureView()).getHandle();
            var clear = color.clearValue();
            if (clear.isPresent()) {
                var c = clear.get();
                clears[i * 5] = 1;
                clears[i * 5 + 1] = c.x();
                clears[i * 5 + 2] = c.y();
                clears[i * 5 + 3] = c.z();
                clears[i * 5 + 4] = c.w();
            }
        }
        var depth = descriptor.depthAttachment();
        int d = colors.size();
        if (depth != null) {
            views[d] = ((MTLView) depth.textureView()).getHandle();
            var clear = depth.clearValue();
            if (clear.isPresent()) {
                clears[d * 5] = 1;
                clears[d * 5 + 1] = (float) clear.getAsDouble();
            }
        }
        var area = descriptor.renderArea;
        int[] rect = area == null ? new int[4] : new int[] {area.x(), area.y(), area.width(), area.height()};
        return new MTLPass(Native.nEncoderPass(handle, descriptor.label().get(), views, clears, rect), device);
    }

    @Override
    public void submitRenderPass() {
        Native.nEncoderSubmitPass(handle);
    }

    @Override
    public void clearColorTexture(GpuTexture texture, Vector4fc color) {
        Native.nEncoderClearColor(handle, handle(texture), color.x(), color.y(), color.z(), color.w());
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture texture, Vector4fc color, GpuTexture depth, double value) {
        Native.nEncoderClearColorDepth(handle, handle(texture), color.x(), color.y(), color.z(), color.w(),
                handle(depth), value);
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture texture, Vector4fc color, GpuTexture depth, double value,
            int x, int y, int width, int height) {
        Native.nEncoderClearColorDepthRegion(handle, handle(texture), color.x(), color.y(), color.z(), color.w(),
                handle(depth), value, x, y, width, height);
    }

    @Override
    public void clearDepthTexture(GpuTexture depth, double value) {
        Native.nEncoderClearDepth(handle, handle(depth), value);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice target, ByteBuffer data) {
        Native.nEncoderWriteBuffer(handle, handle(target.buffer()), target.offset(), target.length(),
                MemoryUtil.memAddress(data), data.remaining());
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        Native.nEncoderCopyBuffer(handle, handle(source.buffer()), source.offset(), source.length(),
                handle(target.buffer()), target.offset(), target.length());
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer data, int mip, int layer, int x, int y, int width,
            int height) {
        Native.nEncoderWriteTexture(handle, handle(texture), MemoryUtil.memAddress(data), data.remaining(), mip,
                layer, x, y, width, height);
    }

    @Override
    public void copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth,
            int sourceHeight, GpuTexture texture, int x, int y, int width, int height, int mip, int layer) {
        Native.nEncoderCopyBufferTexture(handle, handle(source.buffer()), source.offset(), source.length(),
                sourceX, sourceY, sourceWidth, sourceHeight, handle(texture), x, y, width, height, mip, layer);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable callback, int mip) {
        Native.nEncoderCopyTextureBuffer(handle, handle(texture), handle(buffer), offset, callback, mip);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable callback, int mip,
            int x, int y, int width, int height) {
        Native.nEncoderCopyTextureBufferRegion(handle, handle(texture), handle(buffer), offset, callback, mip, x,
                y, width, height);
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture target, int mip, int x, int y, int sourceX,
            int sourceY, int width, int height) {
        Native.nEncoderCopyTexture(handle, handle(source), handle(target), mip, x, y, sourceX, sourceY, width,
                height);
    }

    @Override
    public GpuFence createFence() {
        return new MTLFence(Native.nEncoderFence(handle));
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        Native.nEncoderTimestamp(handle, ((MTLQueries) pool).getHandle(), index);
    }

    private long handle(GpuTexture texture) {
        return ((MTLTexture) texture).getHandle();
    }

    private long handle(GpuBuffer buffer) {
        return ((MTLBuffer) buffer).getHandle();
    }
}
