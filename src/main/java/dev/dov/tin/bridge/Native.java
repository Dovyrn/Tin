package dev.dov.tin.bridge;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Native {
    static {
        NativeLibrary.load();
    }

    public native long nDeviceCreate(long window, int logLevel, boolean syncLogs, boolean labels, boolean validation);

    public native long nDeviceSurface(long device, long window);

    public native long nDeviceEncoder(long device);

    public native long nDeviceSampler(long device, int u, int v, int min, int mag, int anisotropy, boolean hasLod,
            double maxLod);

    public native long nDeviceTexture(long device, String label, int usage, int format, int width, int height,
            int depthOrLayers, int mips);

    public native long nDeviceView(long device, long texture, int baseMip, int mips);

    public native long nDeviceBuffer(long device, String label, int usage, long size);

    public native long nDeviceBufferData(long device, String label, int usage, long address, int length);

    public native String[] nDeviceMessages(long device);

    public native boolean nDeviceDebugging(long device);

    public native long nDevicePipeline(long device, String location, long vertex, int vertexSize, long fragment,
            int fragmentSize, String[] inputs, String[] texels, int[] formats, int[] state);

    public native void nDeviceClearPipelines(long device);

    public native void nDeviceClose(long device);

    public native long nDeviceQueries(long device, int size);

    public native long nDeviceTimestamp(long device);

    public native long[] nDeviceInfoNumbers(long device);

    public native String[] nDeviceInfoStrings(long device);

    public native boolean nPipelineValid(long pipeline);

    public native void nEncoderSubmit(long encoder);

    public native long nEncoderMemory(long encoder);

    public native long nEncoderPass(long encoder, String label, long[] views, float[] clears, int[] area);

    public native void nEncoderSubmitPass(long encoder);

    public native void nEncoderClearColor(long encoder, long texture, float r, float g, float b, float a);

    public native void nEncoderClearColorDepth(long encoder, long color, float r, float g, float b, float a, long depth,
            double value);

    public native void nEncoderClearColorDepthRegion(long encoder, long color, float r, float g, float b, float a,
            long depth, double value, int x, int y, int width, int height);

    public native void nEncoderClearDepth(long encoder, long texture, double value);

    public native void nEncoderWriteBuffer(long encoder, long buffer, long offset, long length, long address, int size);

    public native void nEncoderCopyBuffer(long encoder, long source, long sourceOffset, long sourceLength, long target,
            long targetOffset, long targetLength);

    public native void nEncoderWriteTexture(long encoder, long texture, long address, int size, int mip, int layer,
            int x, int y, int width, int height);

    public native void nEncoderCopyBufferTexture(long encoder, long buffer, long offset, long length, int sourceX,
            int sourceY, int sourceWidth, int sourceHeight, long texture, int x, int y, int width, int height,
            int mip, int layer);

    public native void nEncoderCopyTextureBuffer(long encoder, long texture, long buffer, long offset, Runnable callback,
            int mip);

    public native void nEncoderCopyTextureBufferRegion(long encoder, long texture, long buffer, long offset,
            Runnable callback, int mip, int x, int y, int width, int height);

    public native void nEncoderCopyTexture(long encoder, long source, long target, int mip, int x, int y, int sourceX,
            int sourceY, int width, int height);

    public native long nEncoderFence(long encoder);

    public native void nEncoderTimestamp(long encoder, long queries, int index);

    public native boolean nFenceAwait(long fence, long timeoutNs);

    public native void nFenceClose(long fence);

    public native void nPassPush(long pass, String label);

    public native void nPassPop(long pass);

    public native void nPassPipeline(long pass, long pipeline);

    public native void nPassTexture(long pass, String name, long view, long sampler);

    public native void nPassUniform(long pass, String name, long buffer, long offset, long length);

    public native void nPassScissor(long pass, int x, int y, int width, int height);

    public native void nPassNoScissor(long pass);

    public native void nPassVertex(long pass, int slot, long buffer, long offset, long length);

    public native void nPassIndex(long pass, long buffer, int type);

    public native void nPassDrawIndexed(long pass, int indexCount, int instanceCount, int firstIndex, int vertexOffset,
            int firstInstance);

    public native void nPassMultiDrawIndexed(long pass, long params, int instanceCount, int firstInstance, int drawCount);

    public native void nPassMultiDrawIndexedSeparate(long pass, long firstIndexOffsets, long indexCounts,
            long vertexOffsets, int drawCount);

    public native void nPassDrawIndexedIndirect(long pass, long buffer, long offset, long length, int drawCount);

    public native void nPassDrawOne(long pass, int slot, long vertex, long index, int type, int firstIndex, int indexCount,
            int baseVertex);

    public native void nPassDraw(long pass, int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    public native void nPassMultiDraw(long pass, long params, int instanceCount, int firstInstance, int drawCount);

    public native void nPassMultiDrawSeparate(long pass, long firstVertices, long vertexCounts, int drawCount);

    public native void nPassDrawIndirect(long pass, long buffer, long offset, long length, int drawCount);

    public native void nPassTimestamp(long pass, long queries, int index);

    public native void nSurfaceConfigure(long surface, int width, int height, int mode);

    public native boolean nSurfaceSuboptimal(long surface);

    public native void nSurfaceAcquire(long surface);

    public native void nSurfaceBlit(long surface, long encoder, long view);

    public native void nSurfacePresent(long surface, long device);

    public native void nSurfaceClose(long surface);

    public native int[] nSurfaceModes(long surface);

    public native long nMemoryCpu(long memory, long size, long alignment, long minimum, long element);

    public native long[] nMemoryStaging(long memory, long size, long alignment, int usage, long minimum, long element);

    public native long[] nMemoryGpu(long memory, long size, long alignment, int usage, long minimum, long element);

    public native long[] nMemoryGpuMapped(long memory, long size, long alignment, int usage, long minimum, long element);

    public native long[] nMemoryUploadStaging(long memory, long[] addresses, int[] sizes, long alignment, int usage,
            long minimum, long element);

    public native long[] nMemoryUploadGpu(long memory, long[] addresses, int[] sizes, long alignment, int usage,
            long minimum, long element);

    public native long[] nMemoryMultiStaging(long memory, long[] addresses, int[] sizes, long alignment, int usage);

    public native long[] nMemoryMultiGpu(long memory, long[] addresses, int[] sizes, long alignment, int usage);


    public native void nBufferClose(long buffer);

    public native long nBufferMap(long buffer, long offset, long length, boolean read, boolean write);

    public native void nBufferUnmap(long buffer);


    public native void nTextureClose(long texture);


    public native void nViewClose(long view);

    public native void nSamplerClose(long sampler);

    public native long[] nQueriesValues(long queries, int index, int count);

    public native void nQueriesClose(long queries);
}
