package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MTLSampler extends GpuSampler {
    @Getter
    private final long handle;
    private final AddressMode u;
    private final AddressMode v;
    private final FilterMode min;
    private final FilterMode mag;
    private final int anisotropy;
    private final OptionalDouble maxLod;

    @Override
    public AddressMode getAddressModeU() {
        return u;
    }

    @Override
    public AddressMode getAddressModeV() {
        return v;
    }

    @Override
    public FilterMode getMinFilter() {
        return min;
    }

    @Override
    public FilterMode getMagFilter() {
        return mag;
    }

    @Override
    public int getMaxAnisotropy() {
        return anisotropy;
    }

    @Override
    public OptionalDouble getMaxLod() {
        return maxLod;
    }

    @Override
    public void close() {
        Native.nSamplerClose(handle);
    }
}
