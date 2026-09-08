package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class MetalDevice implements GpuDeviceBackend {
    private final long window;
    private final ShaderSource shaders;
    private final GpuDebugOptions debug;

    @Override
    public @NotNull GpuSurfaceBackend createSurface(long windowHandle) {
        return new MetalGpuSurface();
    }

    @Override
    public @NotNull CommandEncoderBackend createCommandEncoder() {
        return new MetalCommandEncoder();
    }

    @Override
    public @NotNull GpuSampler createSampler(@NotNull AddressMode addressModeU, @NotNull AddressMode addressModeV, @NotNull FilterMode minFilter,
                                             @NotNull FilterMode magFilter, int maxAnisotropy, @NotNull OptionalDouble maxLod) {
        return new MetalGpuSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    @Override
    public @NotNull GpuTexture createTexture(@Nullable Supplier<String> label, int usage, @NotNull GpuFormat format, int width,
                                             int height, int depthOrLayers, int mipLevels) {
        return createTexture(label == null ? null : label.get(), usage, format, width, height, depthOrLayers,
                mipLevels);
    }

    @Override
    public @NotNull GpuTexture createTexture(@Nullable String label, int usage, @NotNull GpuFormat format, int width, int height,
                                             int depthOrLayers, int mipLevels) {
        return new MetalGpuTexture(usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public @NotNull GpuTextureView createTextureView(@NotNull GpuTexture texture) {
        return createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public @NotNull GpuTextureView createTextureView(@NotNull GpuTexture texture, int baseMipLevel, int mipLevels) {
        return new MetalGpuTextureView(texture, baseMipLevel, mipLevels);
    }

    @Override
    public @NotNull GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, long size) {
        return new MetalGpuBuffer(usage, size);
    }

    @Override
    public @NotNull GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, ByteBuffer data) {
        return new MetalGpuBuffer(usage, data.remaining());
    }

    @Override
    public @NotNull List<String> getLastDebugMessages() {
        return List.of();
    }

    @Override
    public boolean isDebuggingEnabled() {
        return false;
    }

    @Override
    public @NotNull CompiledRenderPipeline precompilePipeline(@NotNull RenderPipeline pipeline, @Nullable ShaderSource shaderSource) {
        return new MetalRenderPipeline(pipeline);
    }

    @Override
    public void clearPipelineCache() {
    }

    @Override
    public void close() {
    }

    @Override
    public @NotNull GpuQueryPool createTimestampQueryPool(int size) {
        return new MetalQueryPool(size);
    }

    @Override
    public long getTimestampNow() {
        return 0;
    }

    @Override
    public @NotNull DeviceInfo getDeviceInfo() {
        return null;
    }
}
