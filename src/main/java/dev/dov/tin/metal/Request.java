package dev.dov.tin.metal;

import java.util.List;

public record Request(List<String> inputs, List<Texel> texels) {
}
