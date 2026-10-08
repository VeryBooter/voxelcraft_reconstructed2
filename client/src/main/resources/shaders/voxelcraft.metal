#include <metal_stdlib>
using namespace metal;

// Exactly matches ChunkMesher's 24-byte vertex layout.
struct Vertex { packed_float3 position; packed_float2 uv; uint packed; };
struct Uniforms {
    float4 eyeAmbient;
    float4 rotation; // cos(yaw), sin(yaw), cos(-pitch), sin(-pitch)
    float4 projection; // x/y scale, depth scale, depth offset; Metal depth is 0..1
    float4 atlas; // columns, rows, tile pixel size, reserved
    float4 special; // portal block ID, missile block ID, reserved
};
struct Varyings {
    float4 position [[position]];
    float3 world;
    float2 uv;
    uint material [[flat]];
    float brightness;
    float alpha;
};

vertex Varyings worldVertex(uint id [[vertex_id]], device const Vertex* vertices [[buffer(0)]],
                           constant Uniforms& u [[buffer(1)]]) {
    Vertex v = vertices[id];
    float3 d = float3(v.position) - u.eyeAmbient.xyz;
    float x = d.x * u.rotation.x - d.z * u.rotation.y;
    float z = d.x * u.rotation.y + d.z * u.rotation.x;
    float y = d.y * u.rotation.z - z * u.rotation.w;
    z = d.y * u.rotation.w + z * u.rotation.z;
    Varyings out;
    out.position = float4(x * u.projection.x, y * u.projection.y,
                          z * u.projection.z + u.projection.w, z);
    out.world = float3(v.position);
    out.uv = float2(v.uv);
    out.material = v.packed & 65535u;
    out.brightness = float((v.packed >> 16) & 255u) / 255.0;
    out.alpha = float(v.packed >> 24) / 255.0;
    return out;
}

float noise3(float3 p) { return fract(sin(dot(p, float3(12.9898, 78.233, 37.719))) * 43758.5453123); }
float3 tile(texture2d<float> atlas, float index, float2 uv, constant Uniforms& u) {
    constexpr sampler nearest(coord::normalized, address::clamp_to_edge, filter::nearest);
    float2 cell = float2(fmod(index, u.atlas.x), floor(index / u.atlas.x));
    return atlas.sample(nearest, (cell + uv) / u.atlas.xy).rgb;
}

fragment float4 worldFragment(Varyings v [[stage_in]], constant Uniforms& u [[buffer(1)]],
                              texture2d<float> atlas [[texture(0)]], texture2d<float> lut [[texture(1)]],
                              texture2d<float> portal [[texture(2)]], texture2d<float> missile [[texture(3)]]) {
    if (v.alpha < 0.5) discard_fragment();
    uint id = v.material & 4095u;
    uint face = min(v.material >> 12, 5u);
    float2 uv = (floor(fract(v.uv) * u.atlas.z) + 0.5) / u.atlas.z;
    constexpr sampler nearest(coord::normalized, address::clamp_to_edge, filter::nearest);
    float3 color;
    if (id == uint(u.special.x)) {
        color = portal.sample(nearest, float2(uv.x, 1.0 - uv.y)).rgb;
    } else if (id == uint(u.special.y)) {
        color = missile.sample(nearest, float2(uv.x, 1.0 - uv.y)).rgb;
    } else {
        uint4 meta = uint4(round(lut.read(uint2(id, face)) * 255.0));
        color = tile(atlas, float(meta.x + meta.y * 256u), uv, u);
        if (meta.w > 0) color = mix(color, tile(atlas, float(meta.w), uv, u), 0.35);
        float climate = clamp(0.5 + 0.5 * sin(v.world.x * 0.009 + v.world.z * 0.013), 0.0, 1.0);
        if (meta.z == 1) color *= mix(float3(0.76, 0.86, 0.62), float3(0.53, 0.83, 0.41), climate);
        else if (meta.z == 2) color *= mix(float3(0.70, 0.84, 0.58), float3(0.41, 0.74, 0.37), climate);
        else if (meta.z == 3) color *= float3(0.62, 0.76, 1.0);
        else if (meta.z == 4) color *= 0.92 + noise3(floor(v.world * 0.25)) * 0.16;
        color *= 0.94 + noise3(floor(v.world * 0.25)) * 0.12;
    }
    return float4(clamp(color * v.brightness * u.eyeAmbient.w, 0.0, 1.0), v.alpha);
}

struct HudVaryings { float4 position [[position]]; float2 uv; };
vertex HudVaryings hudVertex(uint id [[vertex_id]]) {
    float2 uv = float2((id << 1) & 2, id & 2);
    return {float4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, 0.0, 1.0), uv};
}
fragment float4 hudFragment(HudVaryings v [[stage_in]], device const uint* pixels [[buffer(0)]],
                            constant uint2& dimensions [[buffer(1)]]) {
    uint2 p = min(uint2(v.uv * float2(dimensions)), dimensions - 1u);
    uint argb = pixels[p.y * dimensions.x + p.x];
    return float4(float((argb >> 16) & 255u), float((argb >> 8) & 255u),
                  float(argb & 255u), float(argb >> 24)) / 255.0;
}
