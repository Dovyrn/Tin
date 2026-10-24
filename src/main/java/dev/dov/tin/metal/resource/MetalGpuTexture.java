package dev.dov.tin.metal.resource;

import com.mojang.blaze3d.GpuFormat;
//? if >= 26.3 {
/*import com.mojang.renderpearl.backend.common.BaseGpuTexture;
*///?} else {
import com.mojang.blaze3d.textures.GpuTexture;
//?}
import dev.dov.metalj.resources.textures.MTLTexture;
import dev.dov.tin.metal.MetalDevice;
import lombok.Getter;

//? if >= 26.3 {
/*public class MetalGpuTexture extends BaseGpuTexture {
*///?} else {
public class MetalGpuTexture extends GpuTexture {
//?}
    private final MetalDevice device;
    @Getter
    private final MTLTexture texture;
    @Getter
    private boolean closed;

    public MetalGpuTexture(MetalDevice device, MTLTexture texture, int usage, String label, GpuFormat format,
            int width, int height, int depthOrLayers, int mipLevels) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.device = device;
        this.texture = texture;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            device.free(texture);
        }
    }
}
