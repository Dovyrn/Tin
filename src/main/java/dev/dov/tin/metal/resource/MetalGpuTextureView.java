package dev.dov.tin.metal.resource;

import com.mojang.blaze3d.textures.GpuTexture;
//? if >= 26.3 {
/*import com.mojang.renderpearl.backend.common.BaseGpuTextureView;
*///?} else {
import com.mojang.blaze3d.textures.GpuTextureView;
//?}
import dev.dov.metalj.resources.textures.MTLTexture;
import dev.dov.tin.metal.MetalDevice;
import lombok.Getter;

//? if >= 26.3 {
/*public class MetalGpuTextureView extends BaseGpuTextureView {
*///?} else {
public class MetalGpuTextureView extends GpuTextureView {
//?}
    private final MetalDevice device;
    @Getter
    private final MTLTexture view;
    @Getter
    private boolean closed;

    public MetalGpuTextureView(MetalDevice device, MTLTexture view, GpuTexture texture, int baseMipLevel,
            int mipLevels) {
        super(texture, baseMipLevel, mipLevels);
        this.device = device;
        this.view = view;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            device.retire(view::release);
        }
    }
}
