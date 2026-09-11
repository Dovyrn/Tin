package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.GpuOutOfMemoryException;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
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
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
import com.mojang.logging.LogUtils;
import dev.dov.metalj.debug.MTLCounterSamplingPoint;
import dev.dov.metalj.device.MTLCommandQueue;
import dev.dov.metalj.device.MTLDevice;
import dev.dov.metalj.device.Metal;
import dev.dov.metalj.objc.NSRange;
import dev.dov.metalj.objc.NSString;
import dev.dov.metalj.objc.ObjC;
import dev.dov.metalj.pipelines.shaders.MTLCompileOptions;
import dev.dov.metalj.pipelines.shaders.MTLFunction;
import dev.dov.metalj.pipelines.shaders.MTLLanguageVersion;
import dev.dov.metalj.resources.MTLResourceOptions;
import dev.dov.metalj.resources.MTLStorageMode;
import dev.dov.metalj.resources.samplers.MTLSamplerDescriptor;
import dev.dov.metalj.resources.samplers.MTLSamplerMipFilter;
import dev.dov.metalj.resources.textures.MTLTextureDescriptor;
import dev.dov.metalj.resources.textures.MTLTextureType;
import dev.dov.metalj.resources.textures.MTLTextureUsage;
import dev.dov.tin.metal.command.MetalClears;
import dev.dov.tin.metal.command.MetalCommandEncoder;
import dev.dov.tin.metal.command.MetalQueryPool;
import dev.dov.tin.metal.resource.MetalGpuBuffer;
import dev.dov.tin.metal.resource.MetalGpuSampler;
import dev.dov.tin.metal.resource.MetalGpuTexture;
import dev.dov.tin.metal.resource.MetalGpuTextureView;
import dev.dov.tin.metal.resource.MetalTransientBuffer;
import dev.dov.tin.metal.shader.MetalRenderPipeline;
import dev.dov.tin.metal.shader.MetalShaderKey;
import dev.dov.tin.metal.shader.MetalShaders;
import dev.dov.tin.metal.shader.Request;
import dev.dov.tin.metal.shader.Texel;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

public class MetalDevice implements GpuDeviceBackend {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ANISOTROPY = 16;

    private static final int MAX_TEXTURE = 16384;
    private static final int MAX_ATTACHMENTS = 8;
    private static final float NO_LOD = 1000;
    private static final float MIP_THRESHOLD = 0.25f;

    @Getter
    private final MTLDevice device;
    @Getter
    private final MTLCommandQueue queue;
    private final Map<RenderPipeline, MetalRenderPipeline> pipelines = new IdentityHashMap<>();
    private final Map<MetalShaderKey, IntermediaryShaderModule> modules = new HashMap<>();
    private final GlslCompiler compiler = new GlslCompiler();
    @Getter
    private final MetalClears clears;
    private MetalCommandEncoder encoder;
    private final List<String> messages = new ArrayList<>();
    private final DeviceInfo info;
    @Getter
    private final boolean drawSampling;
    @Getter
    private final boolean stageSampling;
    @Getter
    private final boolean blitSampling;
    private final ShaderSource shaders;
    private final GpuDebugOptions debug;

    public MetalDevice(MTLDevice device, ShaderSource shaders, GpuDebugOptions debug) {
        this.device = device;
        this.queue = device.newCommandQueue();
        MetalConst.depth24 = device.isDepth24Stencil8PixelFormatSupported();
        this.clears = new MetalClears(this);
        this.shaders = shaders;
        this.debug = debug;
        this.drawSampling = device.supportsCounterSampling(MTLCounterSamplingPoint.MTLCounterSamplingPointAtDrawBoundary);
        this.stageSampling = device.supportsCounterSampling(
                MTLCounterSamplingPoint.MTLCounterSamplingPointAtStageBoundary);
        this.blitSampling = device.supportsCounterSampling(MTLCounterSamplingPoint.MTLCounterSamplingPointAtBlitBoundary);
        this.info = info();
    }

    public long texelAlign(GpuFormat format) {
        return device.minimumLinearTextureAlignmentForPixelFormat(MetalConst.pixelFormat(format));
    }

    public boolean useLabels() {
        return debug.useLabels();
    }

    @Override
    public GpuSurfaceBackend createSurface(long window) {
        return new MetalGpuSurface(this, window);
    }

    public MetalCommandEncoder getEncoder() {
        createCommandEncoder();
        return encoder;
    }

    @Override
    public CommandEncoderBackend createCommandEncoder() {
        if (encoder == null) {
            encoder = new MetalCommandEncoder(this);
        }
        return encoder;
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
        descriptor.release();
        if (sampler.isNull()) {
            throw new GpuOutOfMemoryException("Failed to create sampler");
        }
        return new MetalGpuSampler(sampler, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(@Nullable Supplier<String> label, int usage, GpuFormat format, int width,
            int height, int depthOrLayers, int mipLevels) {
        return createTexture(label == null || !debug.useLabels() ? null : label.get(), usage, format, width, height,
                depthOrLayers, mipLevels);
    }

    @Override
    public GpuTexture createTexture(@Nullable String label, int usage, GpuFormat format, int width, int height,
            int depthOrLayers, int mipLevels) {
        return texture(label, usage, format, width, height, depthOrLayers, mipLevels);
    }

    private GpuTexture texture(@Nullable String label, int usage, GpuFormat format, int width, int height,
            int depthOrLayers, int mipLevels) {
        boolean cube = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
        long pixelFormat = MetalConst.pixelFormat(format);
        var descriptor = cube
                ? MTLTextureDescriptor.textureCubeDescriptorWithPixelFormat(pixelFormat, width, mipLevels > 1)
                : MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(pixelFormat, width, height, mipLevels > 1);
        if (!cube && depthOrLayers > 1) {
            descriptor.setTextureType(MTLTextureType.MTLTextureType2DArray);
            descriptor.setArrayLength(depthOrLayers);
        }
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
        descriptor.release();
        if (texture.isNull()) {
            throw new GpuOutOfMemoryException("Failed to create " + format + " texture of " + width + "x" + height);
        }
        if (debug.useLabels() && label != null) {
            var text = NSString.stringWithUTF8String(label);
            texture.setLabel(text);
            text.release();
        }
        return new MetalGpuTexture(texture, usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    public void message(String message) {
        messages.add(message);
        LOGGER.error(message);
    }

    private void label(Consumer<NSString> target, @Nullable Supplier<String> label) {
        if (debug.useLabels() && label != null) {
            var text = NSString.stringWithUTF8String(label.get());
            target.accept(text);
            text.release();
        }
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        return createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        var parent = ((MetalGpuTexture) texture).getTexture();
        if (baseMipLevel == 0 && mipLevels == texture.getMipLevels()) {
            parent.retain();
            return new MetalGpuTextureView(parent, texture, baseMipLevel, mipLevels);
        }
        try (var arena = Arena.ofConfined()) {
            var view = parent.newTextureViewWithPixelFormat(parent.pixelFormat(), parent.textureType(),
                    NSRange.of(arena, baseMipLevel, mipLevels), NSRange.of(arena, 0, texture.getDepthOrLayers()));
            return new MetalGpuTextureView(view, texture, baseMipLevel, mipLevels);
        }
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, long size) {
        var buffer = device.newBufferWithLength(Math.max(size, 1),
                MTLResourceOptions.MTLResourceStorageModeShared);
        if (buffer.isNull()) {
            throw new GpuOutOfMemoryException("Failed to create buffer of " + size + " bytes");
        }
        label(buffer::setLabel, label);
        return new MetalGpuBuffer(buffer, usage, size);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, ByteBuffer data) {
        long size = data.remaining();
        usage |= GpuBuffer.USAGE_COPY_DST;
        var bytes = MemorySegment.ofAddress(MemoryUtil.memAddress(data)).reinterpret(size);
        var buffer = device.newBufferWithBytes(bytes, size, MTLResourceOptions.MTLResourceStorageModeShared);
        if (buffer.isNull()) {
            throw new GpuOutOfMemoryException("Failed to create buffer of " + size + " bytes");
        }
        label(buffer::setLabel, label);
        return new MetalGpuBuffer(buffer, usage, size);
    }

    public MetalTransientBuffer createTransientBuffer(long size, LongSupplier submits) {
        var buffer = device.newBufferWithLength(size, MTLResourceOptions.MTLResourceStorageModeShared);
        int usage = GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX
                | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER;
        return new MetalTransientBuffer(buffer, usage, size, submits);
    }

    @Override
    public List<String> getLastDebugMessages() {
        var out = List.copyOf(messages);
        messages.clear();
        return out;
    }

    @Override
    public boolean isDebuggingEnabled() {
        return debug.useValidationLayers();
    }

    @Override
    public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, @Nullable ShaderSource shaderSource) {
        return pipelines.computeIfAbsent(pipeline,
                key -> compile(key, shaderSource == null ? shaders : shaderSource));
    }

    public MetalRenderPipeline compiled(RenderPipeline pipeline) {
        return (MetalRenderPipeline) precompilePipeline(pipeline, null);
    }

    private MetalRenderPipeline compile(RenderPipeline pipeline, ShaderSource source) {
        var inputs = new ArrayList<String>();
        int buffers = 0;
        var bindings = pipeline.getVertexFormatBindings();
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] == null) {
                continue;
            }
            buffers = i + 1;
            for (var element : bindings[i].getElements()) {
                inputs.add(element.name());
            }
        }
        var texels = new ArrayList<Texel>();
        var formats = new HashMap<String, GpuFormat>();
        var uniforms = new ArrayList<String>();
        for (var uniform : BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts())) {
            uniforms.add(uniform.name());
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                texels.add(new Texel(uniform.name(), true));
                formats.put(uniform.name(), uniform.gpuFormat());
            }
        }
        var samplers = BindGroupLayout.flattenSamplers(pipeline.getBindGroupLayouts());
        var vertex = spirv(pipeline, ShaderType.VERTEX, source);
        var fragment = spirv(pipeline, ShaderType.FRAGMENT, source);
        if (vertex == IntermediaryShaderModule.INVALID || fragment == IntermediaryShaderModule.INVALID) {
            LOGGER.error("Couldn't compile pipeline {}: invalid shaders", pipeline.getLocation());
            return new MetalRenderPipeline(this, pipeline, null, null, null, formats);
        }
        var translation = MetalShaders.translate(vertex.spirv(), fragment.spirv(),
                new Request(inputs, buffers, texels, uniforms, samplers));
        if (translation.error() != null) {
            message("Couldn't compile pipeline " + pipeline.getLocation() + ": " + translation.error());
            return new MetalRenderPipeline(this, pipeline, null, null, translation, formats);
        }
        try {
            var vertexFunction = function(translation.vertex(), translation.vertexEntry());
            var fragmentFunction = function(translation.fragment(), translation.fragmentEntry());
            var compiled = new MetalRenderPipeline(this, pipeline, vertexFunction, fragmentFunction, translation,
                    formats);
            vertexFunction.release();
            fragmentFunction.release();
            return compiled;
        } catch (IllegalStateException e) {
            message("Couldn't compile pipeline " + pipeline.getLocation() + ": " + e.getMessage());
            return new MetalRenderPipeline(this, pipeline, null, null, translation, formats);
        }
    }

    private IntermediaryShaderModule spirv(RenderPipeline pipeline, ShaderType stage, ShaderSource source) {
        var id = stage == ShaderType.VERTEX ? pipeline.getVertexShader() : pipeline.getFragmentShader();
        var key = new MetalShaderKey(id, stage, pipeline.getShaderDefines());
        var cached = modules.get(key);
        if (cached != null) {
            return cached;
        }
        var module = compileShader(pipeline, id, stage, source);
        modules.put(key, module);
        return module;
    }

    private IntermediaryShaderModule compileShader(RenderPipeline pipeline, Identifier id, ShaderType stage,
            ShaderSource source) {
        var text = source.get(id, stage);
        if (text == null) {
            LOGGER.error("Couldn't find source for {} shader ({})", stage, id);
            return IntermediaryShaderModule.INVALID;
        }
        try {
            return compiler.createIntermediary(id.toDebugFileName(),
                    GlslPreprocessor.injectDefines(text, pipeline.getShaderDefines()), stage);
        } catch (ShaderCompileException e) {
            LOGGER.error("Couldn't compile {} shader {}: {}", stage, id, e.getMessage());
            return IntermediaryShaderModule.INVALID;
        }
    }

    private MTLFunction function(String source, String entry) {
        var options = MTLCompileOptions.new_();
        options.setLanguageVersion(MTLLanguageVersion.MTLLanguageVersion3_0);
        try {
            var text = NSString.stringWithUTF8String(source);
            var name = NSString.stringWithUTF8String(entry);
            var library = device.newLibraryWithSource(text, options);
            var function = library.newFunctionWithName(name);
            text.release();
            name.release();
            library.release();
            if (function.isNull()) {
                throw new IllegalStateException("no function " + entry + " in the translated library");
            }
            return function;
        } finally {
            options.release();
        }
    }

    @Override
    public void clearPipelineCache() {
        if (encoder != null) {
            encoder.waitIdle();
        }
        for (var pipeline : pipelines.values()) {
            pipeline.close();
        }
        pipelines.clear();
        for (var module : modules.values()) {
            if (module != IntermediaryShaderModule.INVALID) {
                module.close();
            }
        }
        modules.clear();
    }

    @Override
    public void close() {
        if (encoder != null) {
            encoder.close();
        }
        clearPipelineCache();
        clears.close();
        queue.release();
        compiler.close();
    }

    @Override
    public GpuQueryPool createTimestampQueryPool(int size) {
        return new MetalQueryPool(this, size);
    }

    private int uniformAlign() {
        return device.supportsFamily(MTLDevice.MTLGPUFamilyApple7) ? 32 : 256;
    }

    private static String vendor(String name) {
        if (name.startsWith("Apple")) {
            return "Apple";
        }
        if (name.startsWith("AMD") || name.startsWith("Radeon")) {
            return "AMD";
        }
        return name.startsWith("Intel") ? "Intel" : "Unknown";
    }

    private float period() {
        long[] first = timestamps();
        long until = System.nanoTime() + 2_000_000;
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
        long[] second = timestamps();
        long gpu = second[1] - first[1];
        return gpu <= 0 ? 1 : (float) (second[0] - first[0]) / gpu;
    }

    private long[] timestamps() {
        try (var arena = Arena.ofConfined()) {
            var cpu = arena.allocate(ObjC.LONG);
            var gpu = arena.allocate(ObjC.LONG);
            device.sampleTimestamps(cpu, gpu);
            return new long[] {cpu.get(ObjC.LONG, 0), gpu.get(ObjC.LONG, 0)};
        }
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
        return info;
    }

    private DeviceInfo info() {
        var limits = new DeviceLimits(MAX_ANISOTROPY, uniformAlign(), MAX_TEXTURE,
                device.recommendedMaxWorkingSetSize(), 0, MAX_ATTACHMENTS);
        var features = new DeviceFeatures(true, true, true, true, true, true, true);
        float period = period();
        var hints = new HintsAndWorkarounds(false, false);
        var name = device.name().UTF8String();
        var type = device.hasUnifiedMemory() ? DeviceType.INTEGRATED : DeviceType.DISCRETE;
        return new DeviceInfo(name, vendor(name), "Metal", true, "Metal", period, limits, features, Set.of(), hints,
                type);
    }
}
