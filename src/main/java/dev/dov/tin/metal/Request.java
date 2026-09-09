package dev.dov.tin.metal;

import java.util.List;

public record Request(List<String> inputs, List<Texel> texels, List<String> uniforms, List<String> samplers) {
}
