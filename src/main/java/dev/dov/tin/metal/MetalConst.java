package dev.dov.tin.metal;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.dov.metalj.resources.samplers.MTLSamplerAddressMode;
import dev.dov.metalj.resources.samplers.MTLSamplerMinMagFilter;
import dev.dov.metalj.resources.textures.MTLPixelFormat;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MetalConst {
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
}
