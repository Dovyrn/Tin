package dev.dov.tin.metal.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

public record MetalShaderKey(Identifier id, ShaderType type, ShaderDefines defines) {
}
