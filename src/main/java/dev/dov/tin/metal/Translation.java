package dev.dov.tin.metal;

import java.util.List;

public record Translation(String error, String vertex, String fragment, String vertexEntry,
        String fragmentEntry, List<Binding> uniforms, List<Binding> textures) {
}
