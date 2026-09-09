package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.dov.metalj.commands.encoders.MTLRenderCommandEncoder;
import dev.dov.metalj.pipelines.depth.MTLCompareFunction;
import dev.dov.metalj.pipelines.render.MTLBlendFactor;
import dev.dov.metalj.pipelines.render.MTLBlendOperation;
import dev.dov.metalj.pipelines.render.MTLColorWriteMask;
import dev.dov.metalj.pipelines.render.MTLPrimitiveTopologyClass;
import dev.dov.metalj.pipelines.vertex.MTLVertexFormat;
import dev.dov.metalj.resources.samplers.MTLSamplerAddressMode;
import dev.dov.metalj.resources.samplers.MTLSamplerMinMagFilter;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MetalConst {
    public final long INDIRECT_STRIDE = 16;
    public final long INDEXED_INDIRECT_STRIDE = 20;

    public long indexType(IndexType type) {
        return type == IndexType.SHORT
                ? MTLRenderCommandEncoder.MTLIndexTypeUInt16
                : MTLRenderCommandEncoder.MTLIndexTypeUInt32;
    }

    public long pixelFormat(GpuFormat format) {
        return switch (format) {
            case R8_UNORM -> MTLPixelFormat.MTLPixelFormatR8Unorm;
            case R8_SNORM -> MTLPixelFormat.MTLPixelFormatR8Snorm;
            case RG8_UNORM -> MTLPixelFormat.MTLPixelFormatRG8Unorm;
            case RG8_SNORM -> MTLPixelFormat.MTLPixelFormatRG8Snorm;
            case RGBA8_UNORM -> MTLPixelFormat.MTLPixelFormatRGBA8Unorm;
            case RGBA8_SNORM -> MTLPixelFormat.MTLPixelFormatRGBA8Snorm;
            case R16_UNORM -> MTLPixelFormat.MTLPixelFormatR16Unorm;
            case R16_SNORM -> MTLPixelFormat.MTLPixelFormatR16Snorm;
            case RG16_UNORM -> MTLPixelFormat.MTLPixelFormatRG16Unorm;
            case RG16_SNORM -> MTLPixelFormat.MTLPixelFormatRG16Snorm;
            case RGBA16_UNORM -> MTLPixelFormat.MTLPixelFormatRGBA16Unorm;
            case RGBA16_SNORM -> MTLPixelFormat.MTLPixelFormatRGBA16Snorm;
            case R8_UINT -> MTLPixelFormat.MTLPixelFormatR8Uint;
            case R8_SINT -> MTLPixelFormat.MTLPixelFormatR8Sint;
            case RG8_UINT -> MTLPixelFormat.MTLPixelFormatRG8Uint;
            case RG8_SINT -> MTLPixelFormat.MTLPixelFormatRG8Sint;
            case RGBA8_UINT -> MTLPixelFormat.MTLPixelFormatRGBA8Uint;
            case RGBA8_SINT -> MTLPixelFormat.MTLPixelFormatRGBA8Sint;
            case R16_UINT -> MTLPixelFormat.MTLPixelFormatR16Uint;
            case R16_SINT -> MTLPixelFormat.MTLPixelFormatR16Sint;
            case RG16_UINT -> MTLPixelFormat.MTLPixelFormatRG16Uint;
            case RG16_SINT -> MTLPixelFormat.MTLPixelFormatRG16Sint;
            case RGBA16_UINT -> MTLPixelFormat.MTLPixelFormatRGBA16Uint;
            case RGBA16_SINT -> MTLPixelFormat.MTLPixelFormatRGBA16Sint;
            case R32_UINT -> MTLPixelFormat.MTLPixelFormatR32Uint;
            case R32_SINT -> MTLPixelFormat.MTLPixelFormatR32Sint;
            case RG32_UINT -> MTLPixelFormat.MTLPixelFormatRG32Uint;
            case RG32_SINT -> MTLPixelFormat.MTLPixelFormatRG32Sint;
            case RGBA32_UINT -> MTLPixelFormat.MTLPixelFormatRGBA32Uint;
            case RGBA32_SINT -> MTLPixelFormat.MTLPixelFormatRGBA32Sint;
            case R16_FLOAT -> MTLPixelFormat.MTLPixelFormatR16Float;
            case RG16_FLOAT -> MTLPixelFormat.MTLPixelFormatRG16Float;
            case RGBA16_FLOAT -> MTLPixelFormat.MTLPixelFormatRGBA16Float;
            case R32_FLOAT -> MTLPixelFormat.MTLPixelFormatR32Float;
            case RG32_FLOAT -> MTLPixelFormat.MTLPixelFormatRG32Float;
            case RGBA32_FLOAT -> MTLPixelFormat.MTLPixelFormatRGBA32Float;
            case RGB10A2_UNORM -> MTLPixelFormat.MTLPixelFormatRGB10A2Unorm;
            case RGB10A2_UINT -> MTLPixelFormat.MTLPixelFormatRGB10A2Uint;
            case RG11B10_FLOAT -> MTLPixelFormat.MTLPixelFormatRG11B10Float;
            case D32_FLOAT -> MTLPixelFormat.MTLPixelFormatDepth32Float;
            case D32_FLOAT_S8_UINT -> MTLPixelFormat.MTLPixelFormatDepth32Float_Stencil8;
            case D24_UNORM_S8_UINT -> MTLPixelFormat.MTLPixelFormatDepth24Unorm_Stencil8;
            case D16_UNORM -> MTLPixelFormat.MTLPixelFormatDepth16Unorm;
            case S8_UINT -> MTLPixelFormat.MTLPixelFormatStencil8;
            default -> throw new IllegalArgumentException("no metal format for " + format);
        };
    }

    public long addressMode(AddressMode mode) {
        return mode == AddressMode.REPEAT
                ? MTLSamplerAddressMode.MTLSamplerAddressModeRepeat
                : MTLSamplerAddressMode.MTLSamplerAddressModeClampToEdge;
    }

    public long filterMode(FilterMode mode) {
        return mode == FilterMode.LINEAR
                ? MTLSamplerMinMagFilter.MTLSamplerMinMagFilterLinear
                : MTLSamplerMinMagFilter.MTLSamplerMinMagFilterNearest;
    }

    public long compareFunction(CompareOp op) {
        return switch (op) {
            case ALWAYS_PASS -> MTLCompareFunction.MTLCompareFunctionAlways;
            case LESS_THAN -> MTLCompareFunction.MTLCompareFunctionLess;
            case LESS_THAN_OR_EQUAL -> MTLCompareFunction.MTLCompareFunctionLessEqual;
            case EQUAL -> MTLCompareFunction.MTLCompareFunctionEqual;
            case NOT_EQUAL -> MTLCompareFunction.MTLCompareFunctionNotEqual;
            case GREATER_THAN_OR_EQUAL -> MTLCompareFunction.MTLCompareFunctionGreaterEqual;
            case GREATER_THAN -> MTLCompareFunction.MTLCompareFunctionGreater;
            case NEVER_PASS -> MTLCompareFunction.MTLCompareFunctionNever;
        };
    }

    public long primitiveType(PrimitiveTopology topology) {
        return switch (topology) {
            case LINES, DEBUG_LINES -> MTLRenderCommandEncoder.MTLPrimitiveTypeLine;
            case DEBUG_LINE_STRIP -> MTLRenderCommandEncoder.MTLPrimitiveTypeLineStrip;
            case POINTS -> MTLRenderCommandEncoder.MTLPrimitiveTypePoint;
            case TRIANGLE_STRIP -> MTLRenderCommandEncoder.MTLPrimitiveTypeTriangleStrip;
            default -> MTLRenderCommandEncoder.MTLPrimitiveTypeTriangle;
        };
    }

    public long topologyClass(PrimitiveTopology topology) {
        return switch (topology) {
            case LINES, DEBUG_LINES, DEBUG_LINE_STRIP -> MTLPrimitiveTopologyClass.MTLPrimitiveTopologyClassLine;
            case POINTS -> MTLPrimitiveTopologyClass.MTLPrimitiveTopologyClassPoint;
            default -> MTLPrimitiveTopologyClass.MTLPrimitiveTopologyClassTriangle;
        };
    }

    public long writeMask(int bits) {
        long mask = MTLColorWriteMask.MTLColorWriteMaskNone;
        if ((bits & 1) != 0) {
            mask |= MTLColorWriteMask.MTLColorWriteMaskRed;
        }
        if ((bits & 2) != 0) {
            mask |= MTLColorWriteMask.MTLColorWriteMaskGreen;
        }
        if ((bits & 4) != 0) {
            mask |= MTLColorWriteMask.MTLColorWriteMaskBlue;
        }
        if ((bits & 8) != 0) {
            mask |= MTLColorWriteMask.MTLColorWriteMaskAlpha;
        }
        return mask;
    }

    public long blendFactor(BlendFactor factor) {
        return switch (factor) {
            case CONSTANT_ALPHA -> MTLBlendFactor.MTLBlendFactorBlendAlpha;
            case CONSTANT_COLOR -> MTLBlendFactor.MTLBlendFactorBlendColor;
            case DST_ALPHA -> MTLBlendFactor.MTLBlendFactorDestinationAlpha;
            case DST_COLOR -> MTLBlendFactor.MTLBlendFactorDestinationColor;
            case ONE -> MTLBlendFactor.MTLBlendFactorOne;
            case ONE_MINUS_CONSTANT_ALPHA -> MTLBlendFactor.MTLBlendFactorOneMinusBlendAlpha;
            case ONE_MINUS_CONSTANT_COLOR -> MTLBlendFactor.MTLBlendFactorOneMinusBlendColor;
            case ONE_MINUS_DST_ALPHA -> MTLBlendFactor.MTLBlendFactorOneMinusDestinationAlpha;
            case ONE_MINUS_DST_COLOR -> MTLBlendFactor.MTLBlendFactorOneMinusDestinationColor;
            case ONE_MINUS_SRC_ALPHA -> MTLBlendFactor.MTLBlendFactorOneMinusSourceAlpha;
            case ONE_MINUS_SRC_COLOR -> MTLBlendFactor.MTLBlendFactorOneMinusSourceColor;
            case SRC_ALPHA -> MTLBlendFactor.MTLBlendFactorSourceAlpha;
            case SRC_ALPHA_SATURATE -> MTLBlendFactor.MTLBlendFactorSourceAlphaSaturated;
            case SRC_COLOR -> MTLBlendFactor.MTLBlendFactorSourceColor;
            case ZERO -> MTLBlendFactor.MTLBlendFactorZero;
        };
    }

    public long blendOp(BlendOp op) {
        return switch (op) {
            case ADD -> MTLBlendOperation.MTLBlendOperationAdd;
            case SUBTRACT -> MTLBlendOperation.MTLBlendOperationSubtract;
            case REVERSE_SUBTRACT -> MTLBlendOperation.MTLBlendOperationReverseSubtract;
            case MIN -> MTLBlendOperation.MTLBlendOperationMin;
            case MAX -> MTLBlendOperation.MTLBlendOperationMax;
        };
    }

    public long vertexFormat(GpuFormat format) {
        return switch (format) {
            case R8_UNORM -> MTLVertexFormat.MTLVertexFormatUCharNormalized;
            case R8_SNORM -> MTLVertexFormat.MTLVertexFormatCharNormalized;
            case RG8_UNORM -> MTLVertexFormat.MTLVertexFormatUChar2Normalized;
            case RG8_SNORM -> MTLVertexFormat.MTLVertexFormatChar2Normalized;
            case RGB8_UNORM -> MTLVertexFormat.MTLVertexFormatUChar3Normalized;
            case RGB8_SNORM -> MTLVertexFormat.MTLVertexFormatChar3Normalized;
            case RGBA8_UNORM -> MTLVertexFormat.MTLVertexFormatUChar4Normalized;
            case RGBA8_SNORM -> MTLVertexFormat.MTLVertexFormatChar4Normalized;
            case R16_UNORM -> MTLVertexFormat.MTLVertexFormatUShortNormalized;
            case R16_SNORM -> MTLVertexFormat.MTLVertexFormatShortNormalized;
            case RG16_UNORM -> MTLVertexFormat.MTLVertexFormatUShort2Normalized;
            case RG16_SNORM -> MTLVertexFormat.MTLVertexFormatShort2Normalized;
            case RGB16_UNORM -> MTLVertexFormat.MTLVertexFormatUShort3Normalized;
            case RGB16_SNORM -> MTLVertexFormat.MTLVertexFormatShort3Normalized;
            case RGBA16_UNORM -> MTLVertexFormat.MTLVertexFormatUShort4Normalized;
            case RGBA16_SNORM -> MTLVertexFormat.MTLVertexFormatShort4Normalized;
            case R8_UINT -> MTLVertexFormat.MTLVertexFormatUChar;
            case R8_SINT -> MTLVertexFormat.MTLVertexFormatChar;
            case RG8_UINT -> MTLVertexFormat.MTLVertexFormatUChar2;
            case RG8_SINT -> MTLVertexFormat.MTLVertexFormatChar2;
            case RGB8_UINT -> MTLVertexFormat.MTLVertexFormatUChar3;
            case RGB8_SINT -> MTLVertexFormat.MTLVertexFormatChar3;
            case RGBA8_UINT -> MTLVertexFormat.MTLVertexFormatUChar4;
            case RGBA8_SINT -> MTLVertexFormat.MTLVertexFormatChar4;
            case R16_UINT -> MTLVertexFormat.MTLVertexFormatUShort;
            case R16_SINT -> MTLVertexFormat.MTLVertexFormatShort;
            case RG16_UINT -> MTLVertexFormat.MTLVertexFormatUShort2;
            case RG16_SINT -> MTLVertexFormat.MTLVertexFormatShort2;
            case RGB16_UINT -> MTLVertexFormat.MTLVertexFormatUShort3;
            case RGB16_SINT -> MTLVertexFormat.MTLVertexFormatShort3;
            case RGBA16_UINT -> MTLVertexFormat.MTLVertexFormatUShort4;
            case RGBA16_SINT -> MTLVertexFormat.MTLVertexFormatShort4;
            case R32_UINT -> MTLVertexFormat.MTLVertexFormatUInt;
            case R32_SINT -> MTLVertexFormat.MTLVertexFormatInt;
            case RG32_UINT -> MTLVertexFormat.MTLVertexFormatUInt2;
            case RG32_SINT -> MTLVertexFormat.MTLVertexFormatInt2;
            case RGB32_UINT -> MTLVertexFormat.MTLVertexFormatUInt3;
            case RGB32_SINT -> MTLVertexFormat.MTLVertexFormatInt3;
            case RGBA32_UINT -> MTLVertexFormat.MTLVertexFormatUInt4;
            case RGBA32_SINT -> MTLVertexFormat.MTLVertexFormatInt4;
            case R16_FLOAT -> MTLVertexFormat.MTLVertexFormatHalf;
            case RG16_FLOAT -> MTLVertexFormat.MTLVertexFormatHalf2;
            case RGB16_FLOAT -> MTLVertexFormat.MTLVertexFormatHalf3;
            case RGBA16_FLOAT -> MTLVertexFormat.MTLVertexFormatHalf4;
            case R32_FLOAT -> MTLVertexFormat.MTLVertexFormatFloat;
            case RG32_FLOAT -> MTLVertexFormat.MTLVertexFormatFloat2;
            case RGB32_FLOAT -> MTLVertexFormat.MTLVertexFormatFloat3;
            case RGBA32_FLOAT -> MTLVertexFormat.MTLVertexFormatFloat4;
            case RGB10A2_UNORM -> MTLVertexFormat.MTLVertexFormatUInt1010102Normalized;
            case RG11B10_FLOAT -> MTLVertexFormat.MTLVertexFormatFloatRG11B10;
            default -> throw new IllegalArgumentException("no metal vertex format for " + format);
        };
    }
}
