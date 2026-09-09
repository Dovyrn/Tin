package dev.dov.tin.metal;

import com.mojang.blaze3d.shaders.ShaderType;
import java.nio.ByteBuffer;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MetalShaders {
    public String translate(ByteBuffer spirv, ShaderType stage) {
        throw new UnsupportedOperationException("no SPIR-V to MSL translator");
    }

    public String entryPoint(ShaderType stage) {
        return stage == ShaderType.VERTEX ? "main0" : "main0";
    }
}
