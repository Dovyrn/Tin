#include <metal_stdlib>
using namespace metal;

struct Region {
    float2 clip;
    float2 uv;
};

struct Fragment {
    float4 position [[position]];
    float2 uv;
};

vertex Fragment blit_vertex(uint id [[vertex_id]], constant Region &region [[buffer(0)]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    Fragment out;
    out.position = float4(-1 + 2 * region.clip.x * p.x, 1 - 2 * region.clip.y + 2 * region.clip.y * p.y, 0, 1);
    out.uv = region.uv * p;
    return out;
}

fragment float4 blit_fragment(Fragment in [[stage_in]], texture2d<float> source [[texture(0)]],
                              sampler sourceSampler [[sampler(0)]]) {
    return source.sample(sourceSampler, in.uv);
}
