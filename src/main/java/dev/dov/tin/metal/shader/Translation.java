package dev.dov.tin.metal.shader;

import java.util.List;

public record Translation(String error, String vertex, String fragment, String vertexEntry,
        String fragmentEntry, List<Binding> uniforms, List<Binding> textures, Binding pushConstant) {
}
