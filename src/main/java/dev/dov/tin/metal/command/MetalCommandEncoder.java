package dev.dov.tin.metal.command;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.TransientMemory;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.dov.metalj.commands.encoders.MTLRenderStages;
import dev.dov.metalj.commands.encoders.MTLStages;
import dev.dov.metalj.commands.passes.MTLClearColor;
import dev.dov.metalj.commands.passes.MTLLoadAction;
import dev.dov.metalj.commands.passes.MTLStoreAction;
import dev.dov.metalj.device.CAMetalDrawable;
import dev.dov.metalj.metal4.MTL4ArgumentTable;
import dev.dov.metalj.metal4.MTL4ArgumentTableDescriptor;
import dev.dov.metalj.metal4.MTL4CommandAllocator;
import dev.dov.metalj.metal4.MTL4CommandBuffer;
import dev.dov.metalj.metal4.MTL4RenderPassDescriptor;
import dev.dov.metalj.metal4.encoders.MTL4CommandEncoder;
import dev.dov.metalj.metal4.encoders.MTL4ComputeCommandEncoder;
import dev.dov.metalj.metal4.encoders.MTL4RenderCommandEncoder;
import dev.dov.metalj.metal4.encoders.MTL4VisibilityOptions;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.resources.MTLOrigin;
import dev.dov.metalj.resources.MTLSize;
import dev.dov.metalj.resources.buffers.MTLBuffer;
import dev.dov.metalj.sync.MTLSharedEvent;
import dev.dov.tin.metal.MetalDevice;
import dev.dov.tin.metal.resource.MetalGpuBuffer;
import dev.dov.tin.metal.resource.MetalGpuTexture;
import dev.dov.tin.metal.resource.MetalTransientBuffer;
import dev.dov.tin.metal.resource.MetalTransientMemory;
import dev.dov.tin.metal.resource.MetalTransientView;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public class MetalCommandEncoder implements CommandEncoderBackend {
    public static final int IN_FLIGHT = 2;
    private static final long RENDER = MTLStages.MTLStageVertex | MTLStages.MTLStageFragment;
    private static final long STAGES = RENDER | MTLStages.MTLStageBlit;
    private static final long SHADERS = MTLRenderStages.MTLRenderStageVertex | MTLRenderStages.MTLRenderStageFragment;
    private static final int BUFFERS = 31;
    private static final int TEXTURES = 31;
    private static final int SAMPLERS = 16;

    @Getter
    private final MetalDevice device;
    private final MetalTransientMemory memory;
    private final List<Runnable> pending = new ArrayList<>();
    private final Deque<List<Runnable>> retired = new ArrayDeque<>();
    private final Map<GpuTexture, Vector4fc> colorClears = new IdentityHashMap<>();
    private final Map<GpuTexture, Double> depthClears = new IdentityHashMap<>();
    private final List<Span> reads = new ArrayList<>();
    private final List<Span> writes = new ArrayList<>();
    private final MTL4CommandAllocator[] allocators = new MTL4CommandAllocator[IN_FLIGHT];
    private final MTL4CommandBuffer commands;
    private final MemorySegment committed = Arena.ofAuto().allocate(ObjC.PTR);
    private final MTLSharedEvent event;
    @Getter
    private final MTL4ArgumentTable table;
    @Getter
    private long submits;
    private boolean recording;
    private MTL4ComputeCommandEncoder blit;
    private CAMetalDrawable drawable;
    @Getter
    private MetalRenderPass pass;

    public MetalCommandEncoder(MetalDevice device) {
        this.device = device;
        memory = new MetalTransientMemory(device);
        var metal = device.getDevice();
        for (int i = 0; i < IN_FLIGHT; i++) {
            allocators[i] = metal.newCommandAllocator();
        }
        commands = metal.newCommandBuffer();
        committed.set(ObjC.PTR, 0, commands.getId());
        event = metal.newSharedEvent();
        var descriptor = MTL4ArgumentTableDescriptor.new_();
        descriptor.setMaxBufferBindCount(BUFFERS);
        descriptor.setMaxTextureBindCount(TEXTURES);
        descriptor.setMaxSamplerStateBindCount(SAMPLERS);
        table = metal.newArgumentTableWithDescriptor(descriptor);
        descriptor.release();
    }

    public MTL4CommandBuffer commandBuffer() {
        endBlit();
        open();
        flushClears();
        return commands;
    }

    private void open() {
        if (pass != null) {
            throw new IllegalStateException("Cannot start command buffer while inside RenderPass");
        }
        if (!recording) {
            finish(submits - IN_FLIGHT);
            var allocator = allocators[(int) (submits % IN_FLIGHT)];
            allocator.reset();
            commands.beginCommandBufferWithAllocator(allocator);
            recording = true;
        }
    }

    public MTL4RenderCommandEncoder render(MTL4RenderPassDescriptor descriptor) {
        var encoder = commandBuffer().renderCommandEncoderWithDescriptor(descriptor);
        encoder.setArgumentTable(table, SHADERS);
        return encoder;
    }

    public static void end(MTL4RenderCommandEncoder encoder) {
        end(encoder, RENDER);
    }

    private static void end(MTL4CommandEncoder encoder, long stages) {
        encoder.barrierAfterStages(stages, STAGES, MTL4VisibilityOptions.MTL4VisibilityOptionDevice);
        encoder.endEncoding();
        encoder.release();
    }

    private MTL4ComputeCommandEncoder blit(Span source, Span target) {
        open();
        if (!colorClears.isEmpty() || !depthClears.isEmpty()) {
            endBlit();
            flushClears();
        }
        if (blit == null) {
            blit = commands.computeCommandEncoder();
        } else if (overlaps(writes, source) || overlaps(writes, target) || overlaps(reads, target)) {
            blit.barrierAfterEncoderStages(MTLStages.MTLStageBlit, MTLStages.MTLStageBlit,
                    MTL4VisibilityOptions.MTL4VisibilityOptionDevice);
            reads.clear();
            writes.clear();
        }
        reads.add(source);
        writes.add(target);
        return blit;
    }

    private static boolean overlaps(List<Span> spans, Span span) {
        for (var other : spans) {
            if (other.overlaps(span)) {
                return true;
            }
        }
        return false;
    }

    private void endBlit() {
        if (blit != null) {
            end(blit, MTLStages.MTLStageBlit);
            blit = null;
            reads.clear();
            writes.clear();
        }
    }

    public long upload(float... values) {
        var view = memory.allocateGpuMapped((long) values.length * Float.BYTES, 16, GpuBuffer.USAGE_UNIFORM);
        for (float value : values) {
            view.data().putFloat(value);
        }
        view.close();
        return address(view.slice());
    }

    public void present(CAMetalDrawable drawable) {
        drawable.retain();
        this.drawable = drawable;
    }

    private void flushClears() {
        if (colorClears.isEmpty() && depthClears.isEmpty()) {
            return;
        }
        var colors = new IdentityHashMap<>(colorClears);
        var depths = new IdentityHashMap<>(depthClears);
        colorClears.clear();
        depthClears.clear();
        for (var entry : colors.entrySet()) {
            if (!entry.getKey().isClosed()) {
                clearLevel(entry.getKey(), entry.getValue(), null, 0, 0);
            }
        }
        for (var entry : depths.entrySet()) {
            if (!entry.getKey().isClosed()) {
                clearLevel(null, null, entry.getKey(), entry.getValue(), 0);
            }
        }
    }

    public Vector4fc takeColorClear(GpuTexture texture) {
        return colorClears.remove(texture);
    }

    public Double takeDepthClear(GpuTexture texture) {
        return depthClears.remove(texture);
    }

    @Override
    public void submit() {
        if (pass != null) {
            throw new IllegalStateException("Cannot submit while inside a render pass");
        }
        memory.endSubmit();
        retired.addLast(List.copyOf(pending));
        pending.clear();
        commandBuffer().endCommandBuffer();
        recording = false;
        device.flush();
        var queue = device.getQueue();
        if (drawable != null) {
            queue.waitForDrawable(drawable);
        }
        queue.commit(committed, 1);
        if (drawable != null) {
            queue.signalDrawable(drawable);
            drawable.release();
            drawable = null;
        }
        submits++;
        queue.signalEvent(event, submits);
        finish(submits - IN_FLIGHT);
        while (retired.size() > IN_FLIGHT) {
            for (var callback : retired.removeFirst()) {
                callback.run();
            }
        }
    }

    private void finish(long index) {
        while (event.signaledValue() <= index) {
            event.waitUntilSignaledValue(index + 1, 1000);
        }
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

    //? if >= 26.3 {
    /*@Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
            double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight, int mipLevel) {
        boolean whole = regionX == 0 && regionY == 0 && regionWidth == colorTexture.getWidth(mipLevel)
                && regionHeight == colorTexture.getHeight(mipLevel);
        if (whole && mipLevel == 0) {
            clear(colorTexture, clearColor, depthTexture, clearDepth);
            return;
        }
        if (whole) {
            clearNow(colorTexture, clearColor, depthTexture, clearDepth, mipLevel);
            return;
        }
        device.getClears().region(this, colorTexture, clearColor, depthTexture, clearDepth, regionX,
                regionY, regionWidth, regionHeight, mipLevel);
    }
    *///?} else {
    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
            double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight) {
        boolean whole = regionX == 0 && regionY == 0 && regionWidth == colorTexture.getWidth(0)
                && regionHeight == colorTexture.getHeight(0);
        if (whole) {
            clear(colorTexture, clearColor, depthTexture, clearDepth);
            return;
        }
        device.getClears().region(this, colorTexture, clearColor, depthTexture, clearDepth, regionX,
                regionY, regionWidth, regionHeight, 0);
    }
    //?}

    @Override
    public void clearDepthTexture(GpuTexture depthTexture, double clearDepth) {
        clear(null, null, depthTexture, clearDepth);
    }

    private void clear(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth) {
        boolean flat = (color == null || color.getMipLevels() == 1) && (depth == null || depth.getMipLevels() == 1);
        if (flat) {
            if (color != null) {
                colorClears.put(color, new Vector4f(clearColor));
            }
            if (depth != null) {
                depthClears.put(depth, clearDepth);
            }
            return;
        }
        int levels = Math.max(color == null ? 0 : color.getMipLevels(), depth == null ? 0 : depth.getMipLevels());
        for (int level = 0; level < levels; level++) {
            clearLevel(color, clearColor, depth, clearDepth, level);
        }
    }

    private void clearLevel(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth, int level) {
        clearNow(color, clearColor, depth, clearDepth, level);
    }

    private void clearNow(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth, int level) {
        var pass = MTL4RenderPassDescriptor.new_();
        if (color != null && level < color.getMipLevels()) {
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
        if (depth != null && level < depth.getMipLevels()) {
            var attachment = pass.depthAttachment();
            attachment.setTexture(((MetalGpuTexture) depth).getTexture());
            attachment.setLevel(level);
            attachment.setLoadAction(MTLLoadAction.MTLLoadActionClear);
            attachment.setStoreAction(MTLStoreAction.MTLStoreActionStore);
            attachment.setClearDepth(clearDepth);
        }
        end(render(pass));
        pass.release();
    }

    private GpuBufferSlice staging(ByteBuffer data) {
        return memory.uploadStaging(data, 4, GpuBuffer.USAGE_COPY_SRC);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
        copyToBuffer(staging(data), destination);
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        var from = buffer(source);
        var to = buffer(target);
        blit(new Span(from, source.offset(), source.length()), new Span(to, target.offset(), source.length()))
                .copyFromBuffer(from, source.offset(), to, target.offset(), source.length());
    }

    public static MTLBuffer buffer(GpuBufferSlice slice) {
        return buffer(slice.buffer());
    }

    public static long address(GpuBufferSlice slice) {
        return address(slice.buffer()) + slice.offset();
    }

    public static long address(GpuBuffer owner) {
        if (owner instanceof MetalTransientView view) {
            return view.block().getAddress();
        }
        return owner instanceof MetalTransientBuffer block
                ? block.getAddress()
                : ((MetalGpuBuffer) owner).getAddress();
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
        var texture = ((MetalGpuTexture) destination).getTexture();
        try (var arena = Arena.ofConfined()) {
            blit(new Span(source, offset, row * height), Span.whole(texture)).copyFromBuffer(source, offset, row,
                    row * height, MTLSize.of(arena, width, height, 1), texture, layer, mipLevel,
                    MTLOrigin.of(arena, x, y, 0));
        }
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
        var texture = ((MetalGpuTexture) source).getTexture();
        var buffer = ((MetalGpuBuffer) destination).getBuffer();
        try (var arena = Arena.ofConfined()) {
            blit(Span.whole(texture), new Span(buffer, offset, row * height)).copyFromTexture(texture, 0, mipLevel,
                    MTLOrigin.of(arena, x, y, 0), MTLSize.of(arena, width, height, 1), buffer, offset, row,
                    row * height);
        }
        pending.add(callback);
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destX, int destY,
            int sourceX, int sourceY, int width, int height) {
        var from = ((MetalGpuTexture) source).getTexture();
        var to = ((MetalGpuTexture) destination).getTexture();
        try (var arena = Arena.ofConfined()) {
            blit(Span.whole(from), Span.whole(to)).copyFromTexture(from, 0, mipLevel,
                    MTLOrigin.of(arena, sourceX, sourceY, 0), MTLSize.of(arena, width, height, 1), to, 0, mipLevel,
                    MTLOrigin.of(arena, destX, destY, 0));
        }
    }

    public void waitIdle() {
        finish(submits - 1);
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
        if (recording) {
            endBlit();
            commands.endCommandBuffer();
        }
        if (drawable != null) {
            drawable.release();
        }
        table.release();
        event.release();
        commands.release();
        for (var allocator : allocators) {
            allocator.release();
        }
    }

    public void retire(Runnable release) {
        pending.add(release);
    }

    @Override
    public GpuFence createFence() {
        return new MetalFence(this, submits);
    }

    public boolean awaitSubmit(long index, long timeoutNs) {
        if (event.signaledValue() > index) {
            return true;
        }
        if (index == submits) {
            if (timeoutNs == 0) {
                return false;
            }
            throw new IllegalStateException("Cannot wait on a fence for the current submit");
        }
        if (timeoutNs == 0) {
            return false;
        }
        return event.waitUntilSignaledValue(index + 1, Math.max(timeoutNs / 1_000_000, 1));
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        ((MetalQueryPool) pool).write(commandBuffer(), index);
    }
}
