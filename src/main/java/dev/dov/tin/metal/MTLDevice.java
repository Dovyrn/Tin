package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import org.lwjgl.glfw.GLFWNativeCocoa;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@RequiredArgsConstructor
public class MTLDevice implements GpuDeviceBackend {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Getter
    private final long handle;
    private final ShaderSource shaders;
    private final GlslCompiler compiler = new GlslCompiler();

    @Override
    public GpuSurfaceBackend createSurface(long window) {
        var cocoa = GLFWNativeCocoa.glfwGetCocoaWindow(window);
        return new MTLSurface(Native.nDeviceSurface(handle, cocoa), handle);
    }

    @Override
    public CommandEncoderBackend createCommandEncoder() {
        return new MTLEncoder(Native.nDeviceEncoder(handle), this);
    }

    @Override
    public GpuSampler createSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int anisotropy,
            OptionalDouble maxLod) {
        long sampler = Native.nDeviceSampler(handle, u.ordinal(), v.ordinal(), min.ordinal(), mag.ordinal(),
                anisotropy, maxLod.isPresent(), maxLod.orElse(0));
        return new MTLSampler(sampler, u, v, min, mag, anisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format, int width, int height,
            int depthOrLayers, int mips) {
        return createTexture(label == null ? null : label.get(), usage, format, width, height, depthOrLayers, mips);
    }

    @Override
    public GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height,
            int depthOrLayers, int mips) {
        var name = label == null ? "" : label;
        long texture = Native.nDeviceTexture(handle, name, usage, format.ordinal(), width, height, depthOrLayers,
                mips);
        return new MTLTexture(texture, usage, name, format, width, height, depthOrLayers, mips);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        return createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMip, int mips) {
        long view = Native.nDeviceView(handle, ((MTLTexture) texture).getHandle(), baseMip, mips);
        return new MTLView(view, texture, baseMip, mips);
    }

    @Override
    public GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
        var name = label == null ? "" : label.get();
        return new MTLBuffer(Native.nDeviceBuffer(handle, name, usage, size), usage, size);
    }

    @Override
    public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data) {
        var name = label == null ? "" : label.get();
        long buffer = Native.nDeviceBufferData(handle, name, usage, MemoryUtil.memAddress(data), data.remaining());
        return new MTLBuffer(buffer, usage, data.remaining());
    }

    @Override
    public List<String> getLastDebugMessages() {
        return List.of(Native.nDeviceMessages(handle));
    }

    @Override
    public boolean isDebuggingEnabled() {
        return Native.nDeviceDebugging(handle);
    }

    @Override
    public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, ShaderSource custom) {
        var source = custom == null ? shaders : custom;
        var defines = pipeline.getShaderDefines();
        var inputs = new ArrayList<String>();
        for (var format : pipeline.getVertexFormatBindings()) {
            if (format != null) {
                for (var element : format.getElements()) {
                    inputs.add(element.name());
                }
            }
        }
        var texels = new ArrayList<String>();
        var formats = new ArrayList<Integer>();
        for (var uniform : BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts())) {
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                texels.add(uniform.name());
                formats.add(uniform.gpuFormat().ordinal());
            }
        }
        try (var vertex = spirv(pipeline.getVertexShader(), ShaderType.VERTEX, defines, source);
                var fragment = spirv(pipeline.getFragmentShader(), ShaderType.FRAGMENT, defines, source)) {
            long compiled = Native.nDevicePipeline(handle, pipeline.getLocation().toString(), address(vertex),
                    size(vertex), address(fragment), size(fragment), inputs.toArray(String[]::new),
                    texels.toArray(String[]::new), formats.stream().mapToInt(Integer::intValue).toArray(),
                    state(pipeline));
            return new MTLPipeline(compiled);
        }
    }

    private IntermediaryShaderModule spirv(Identifier id, ShaderType type, ShaderDefines defines,
            ShaderSource source) {
        var text = source.get(id, type);
        if (text == null) {
            LOGGER.error("Couldn't find source for {} shader ({})", type, id);
            return IntermediaryShaderModule.INVALID;
        }
        try {
            return compiler.createIntermediary(id.toDebugFileName(), GlslPreprocessor.injectDefines(text, defines),
                    type);
        } catch (ShaderCompileException e) {
            LOGGER.error("Couldn't compile {} shader {}: {}", type, id, e.getMessage());
            return IntermediaryShaderModule.INVALID;
        }
    }

    private static long address(IntermediaryShaderModule module) {
        return module.spirv() == null ? 0 : MemoryUtil.memAddress(module.spirv());
    }

    private static int size(IntermediaryShaderModule module) {
        return module.spirv() == null ? 0 : module.spirv().remaining();
    }

    private int[] state(RenderPipeline pipeline) {
        var out = new ArrayList<Integer>();
        var depth = pipeline.getDepthStencilState();
        out.add(depth == null ? 0 : 1);
        out.add(depth == null ? 0 : depth.depthTest().ordinal());
        out.add(depth != null && depth.writeDepth() ? 1 : 0);
        out.add(depth == null ? 0 : Float.floatToIntBits(depth.depthBiasScaleFactor()));
        out.add(depth == null ? 0 : Float.floatToIntBits(depth.depthBiasConstant()));
        out.add(pipeline.getPolygonMode().ordinal());
        out.add(pipeline.isCull() ? 1 : 0);
        out.add(pipeline.getPrimitiveTopology().ordinal());
        var targets = pipeline.getColorTargetStates();
        out.add(targets.length);
        for (var target : targets) {
            out.add(target.format().ordinal());
            out.add(target.writeMask());
            var blend = target.blendFunction();
            out.add(blend.isPresent() ? 1 : 0);
            var color = blend.map(b -> b.color()).orElse(null);
            var alpha = blend.map(b -> b.alpha()).orElse(null);
            out.add(color == null ? 0 : color.sourceFactor().ordinal());
            out.add(color == null ? 0 : color.destFactor().ordinal());
            out.add(color == null ? 0 : color.op().ordinal());
            out.add(alpha == null ? 0 : alpha.sourceFactor().ordinal());
            out.add(alpha == null ? 0 : alpha.destFactor().ordinal());
            out.add(alpha == null ? 0 : alpha.op().ordinal());
        }
        var formats = pipeline.getVertexFormatBindings();
        out.add(formats.length);
        for (var format : formats) {
            out.add(format == null ? 0 : 1);
            if (format == null) {
                continue;
            }
            out.add(format.getStepRate());
            out.add(format.getVertexSize());
            var elements = format.getElements();
            out.add(elements.size());
            for (var element : elements) {
                out.add(element.offset());
                out.add(element.format().ordinal());
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void clearPipelineCache() {
        Native.nDeviceClearPipelines(handle);
    }

    @Override
    public void close() {
        compiler.close();
        Native.nDeviceClose(handle);
    }

    @Override
    public GpuQueryPool createTimestampQueryPool(int size) {
        return new MTLQueries(Native.nDeviceQueries(handle, size), size);
    }

    @Override
    public long getTimestampNow() {
        return Native.nDeviceTimestamp(handle);
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        long[] n = Native.nDeviceInfoNumbers(handle);
        var s = Native.nDeviceInfoStrings(handle);
        var limits = new DeviceLimits((int) n[2], (int) n[3], (int) n[4], n[5], (int) n[6], (int) n[7]);
        var features = new DeviceFeatures(n[8] != 0, n[9] != 0, n[10] != 0, n[11] != 0, n[12] != 0, n[13] != 0,
                n[14] != 0);
        var hints = new HintsAndWorkarounds(n[15] != 0, n[16] != 0);
        var extensions = new HashSet<>(Arrays.asList(s).subList(3, s.length));
        return new DeviceInfo(s[0], s[1], s[2], n[0] != 0, "Metal", Float.intBitsToFloat((int) n[1]), limits,
                features, extensions, hints, DeviceType.values()[(int) n[17]]);
    }
}
