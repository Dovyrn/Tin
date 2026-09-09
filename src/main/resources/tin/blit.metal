#include <metal_stdlib>
using namespace metal;

struct Fragment {
    float4 position [[position]];
    float2 uv;
};

vertex Fragment blit_vertex(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    Fragment out;
    out.position = float4(p * 2 - 1, 0, 1);
    out.uv = float2(p.x, 1 - p.y);
    return out;
}

fragment float4 blit_fragment(Fragment in [[stage_in]], texture2d<float> source [[texture(0)]],
                              sampler linearSampler [[sampler(0)]]) {
    return source.sample(linearSampler, in.uv);
}
