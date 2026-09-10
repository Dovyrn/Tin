package dev.dov.tin.metal.shader;

import java.util.List;

public record Request(List<String> inputs, int buffers, List<Texel> texels, List<String> uniforms,
        List<String> samplers) {
}
