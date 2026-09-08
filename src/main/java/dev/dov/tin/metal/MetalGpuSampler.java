package dev.dov.tin.metal;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class MetalGpuSampler extends GpuSampler {
    private final AddressMode addressModeU;
    private final AddressMode addressModeV;
    private final FilterMode minFilter;
    private final FilterMode magFilter;
    private final int maxAnisotropy;
    private final OptionalDouble maxLod;

    @Override
    public @NotNull AddressMode getAddressModeU() {
        return addressModeU;
    }

    @Override
    public @NotNull AddressMode getAddressModeV() {
        return addressModeV;
    }

    @Override
    public @NotNull FilterMode getMinFilter() {
        return minFilter;
    }

    @Override
    public @NotNull FilterMode getMagFilter() {
        return magFilter;
    }

    @Override
    public int getMaxAnisotropy() {
        return maxAnisotropy;
    }

    @Override
    public @NotNull OptionalDouble getMaxLod() {
        return maxLod;
    }

    @Override
    public void close() {
    }
}
