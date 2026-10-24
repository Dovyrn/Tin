package dev.dov.tin.metal.resource;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.dov.metalj.resources.samplers.MTLSamplerState;
import dev.dov.tin.metal.MetalDevice;
import java.util.OptionalDouble;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
//? if >= 26.3 {
/*public class MetalGpuSampler implements GpuSampler {
*///?} else {
public class MetalGpuSampler extends GpuSampler {
//?}
    private final MetalDevice device;
    private final MTLSamplerState sampler;
    private final AddressMode addressModeU;
    private final AddressMode addressModeV;
    private final FilterMode minFilter;
    private final FilterMode magFilter;
    private final int maxAnisotropy;
    private final OptionalDouble maxLod;
    private boolean closed;

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            device.retire(sampler::release);
        }
    }
}
