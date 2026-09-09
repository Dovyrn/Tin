#include <metal_stdlib>
using namespace metal;

struct Clear {
    float4 color;
    float depth;
};

struct Fragment {
    float4 position [[position]];
};

vertex Fragment clear_vertex(uint id [[vertex_id]], constant Clear &clear [[buffer(0)]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    Fragment out;
    out.position = float4(p * 2 - 1, clear.depth, 1);
    return out;
}

fragment float4 clear_fragment(constant Clear &clear [[buffer(0)]]) {
    return clear.color;
}
