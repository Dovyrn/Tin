package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.HintsAndWorkarounds;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.dov.metalj.device.MTLCommandQueue;
import dev.dov.metalj.device.Metal;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.pipelines.shaders.MTLCompileOptions;
import dev.dov.metalj.pipelines.shaders.MTLFunction;
import dev.dov.metalj.resources.MTLResourceOptions;
import dev.dov.metalj.resources.MTLStorageMode;
import dev.dov.metalj.resources.samplers.MTLSamplerDescriptor;
import dev.dov.metalj.resources.samplers.MTLSamplerMipFilter;
import dev.dov.metalj.resources.textures.MTLTextureDescriptor;
import dev.dov.metalj.resources.textures.MTLTextureUsage;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public class MetalDevice implements GpuDeviceBackend {
    private static final int MAX_ANISOTROPY = 16;
    private static final int UNIFORM_ALIGN = 16;
    private static final int MAX_TEXTURE = 16384;
    private static final int MAX_ATTACHMENTS = 8;
    private static final float NO_LOD = 1000;
    private static final float MIP_THRESHOLD = 0.25f;

    @Getter
    private final dev.dov.metalj.device.MTLDevice device = Metal.MTLCreateSystemDefaultDevice();
    @Getter
    private final MTLCommandQueue queue = device.newCommandQueue();
    private final Map<RenderPipeline, MetalRenderPipeline> pipelines = new IdentityHashMap<>();
    private final GlslCompiler compiler = new GlslCompiler();
    private final long window;
    private final ShaderSource shaders;
    private final GpuDebugOptions debug;

    public MetalDevice(long window, ShaderSource shaders, GpuDebugOptions debug) {
        this.window = window;
        this.shaders = shaders;
        this.debug = debug;
    }

    @Override
    public GpuSurfaceBackend createSurface(long windowHandle) {
        return new MetalGpuSurface(this, windowHandle);
    }

    @Override
    public CommandEncoderBackend createCommandEncoder() {
        return new MetalCommandEncoder(this);
    }

    @Override
    public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter,
            FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
        float lod = (float) maxLod.orElse(NO_LOD);
        var descriptor = MTLSamplerDescriptor.new_();
        descriptor.setSAddressMode(MetalConst.addressMode(addressModeU));
        descriptor.setTAddressMode(MetalConst.addressMode(addressModeV));
        descriptor.setMinFilter(MetalConst.filterMode(minFilter));
        descriptor.setMagFilter(MetalConst.filterMode(magFilter));
        descriptor.setMipFilter(lod > MIP_THRESHOLD
                ? MTLSamplerMipFilter.MTLSamplerMipFilterLinear
                : MTLSamplerMipFilter.MTLSamplerMipFilterNearest);
        descriptor.setLodMaxClamp(Math.max(lod, MIP_THRESHOLD));
        descriptor.setMaxAnisotropy(Math.max(maxAnisotropy, 1));
        var sampler = device.newSamplerStateWithDescriptor(descriptor);
        return new MetalGpuSampler(sampler, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(@Nullable Supplier<String> label, int usage, GpuFormat format, int width,
            int height, int depthOrLayers, int mipLevels) {
        return createTexture(label == null ? null : label.get(), usage, format, width, height, depthOrLayers,
                mipLevels);
    }

    @Override
    public GpuTexture createTexture(@Nullable String label, int usage, GpuFormat format, int width, int height,
            int depthOrLayers, int mipLevels) {
        boolean cube = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
        long pixelFormat = MetalConst.pixelFormat(format);
        var descriptor = cube
                ? MTLTextureDescriptor.textureCubeDescriptorWithPixelFormat(pixelFormat, width, mipLevels > 1)
                : MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(pixelFormat, width, height, mipLevels > 1);
        descriptor.setMipmapLevelCount(mipLevels);
        descriptor.setStorageMode(MTLStorageMode.MTLStorageModePrivate);
        long flags = 0;
        if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) {
            flags |= MTLTextureUsage.MTLTextureUsageShaderRead;
        }
        if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            flags |= MTLTextureUsage.MTLTextureUsageRenderTarget;
        }
        descriptor.setUsage(flags);
        var texture = device.newTextureWithDescriptor(descriptor);
        texture.setLabel(NSString.stringWithUTF8String(label == null ? "" : label));
        return new MetalGpuTexture(texture, usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        return createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        return new MetalGpuTextureView(((MetalGpuTexture) texture).getTexture(), texture, baseMipLevel, mipLevels);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, long size) {
        var buffer = device.newBufferWithLength(Math.max(size, 1),
                MTLResourceOptions.MTLResourceStorageModeShared);
        buffer.setLabel(NSString.stringWithUTF8String(label == null ? "" : label.get()));
        return new MetalGpuBuffer(buffer, usage, size);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, ByteBuffer data) {
        long size = data.remaining();
        var bytes = java.lang.foreign.MemorySegment.ofAddress(MemoryUtil.memAddress(data)).reinterpret(size);
        var buffer = device.newBufferWithBytes(bytes, size, MTLResourceOptions.MTLResourceStorageModeShared);
        buffer.setLabel(NSString.stringWithUTF8String(label == null ? "" : label.get()));
        return new MetalGpuBuffer(buffer, usage, size);
    }

    @Override
    public List<String> getLastDebugMessages() {
        return List.of();
    }

    @Override
    public boolean isDebuggingEnabled() {
        return debug.useValidationLayers();
    }

    @Override
    public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, @Nullable ShaderSource shaderSource) {
        return pipelines.computeIfAbsent(pipeline, key -> compile(key, shaderSource == null ? shaders : shaderSource));
    }

    private MetalRenderPipeline compile(RenderPipeline pipeline, ShaderSource source) {
        var vertex = function(pipeline, ShaderType.VERTEX, source);
        var fragment = function(pipeline, ShaderType.FRAGMENT, source);
        return new MetalRenderPipeline(this, pipeline, vertex, fragment);
    }

    @lombok.SneakyThrows
    private MTLFunction function(RenderPipeline pipeline, ShaderType stage, ShaderSource source) {
        var id = stage == ShaderType.VERTEX ? pipeline.getVertexShader() : pipeline.getFragmentShader();
        var text = source.get(id, stage);
        var spirv = compiler.createIntermediary(id.toDebugFileName(),
                GlslPreprocessor.injectDefines(text, pipeline.getShaderDefines()), stage);
        var library = device.newLibraryWithSource(
                NSString.stringWithUTF8String(MetalShaders.translate(spirv.spirv(), stage)),
                MTLCompileOptions.new_());
        return library.newFunctionWithName(NSString.stringWithUTF8String(MetalShaders.entryPoint(stage)));
    }

    public CompiledRenderPipeline compiled(RenderPipeline pipeline) {
        return precompilePipeline(pipeline, null);
    }

    @Override
    public void clearPipelineCache() {
        pipelines.clear();
    }

    @Override
    public void close() {
        compiler.close();
    }

    @Override
    public GpuQueryPool createTimestampQueryPool(int size) {
        return new MetalQueryPool(this, size);
    }

    @Override
    public long getTimestampNow() {
        try (var arena = Arena.ofConfined()) {
            var cpu = arena.allocate(ObjC.LONG);
            var gpu = arena.allocate(ObjC.LONG);
            device.sampleTimestamps(cpu, gpu);
            return gpu.get(ObjC.LONG, 0);
        }
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        var limits = new DeviceLimits(MAX_ANISOTROPY, UNIFORM_ALIGN, MAX_TEXTURE, device.maxBufferLength(), 0,
                MAX_ATTACHMENTS);
        var features = new DeviceFeatures(true, false, false, true, true, true, true);
        var hints = new HintsAndWorkarounds(false, false);
        return new DeviceInfo(device.name().UTF8String(), "Apple", "Metal", true, "Metal", 1, limits, features,
                Set.of(), hints, DeviceType.INTEGRATED);
    }
}
