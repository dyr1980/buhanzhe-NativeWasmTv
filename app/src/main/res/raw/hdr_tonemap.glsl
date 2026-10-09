// Shared by the hardware SurfaceTexture path and IJK's software YUV renderer.
// Input: nonlinear BT.2020 RGB. Output: SDR BT.709 / sRGB.
uniform float ntvTransfer;
uniform float ntvPeak;
// Hardware BT.709 override: undo the decoder's reported YUV matrix in the
// encoded domain, then reinterpret the original YUV as BT.709 (no tone mapping).
// Native software output already uses BT.709 YUV directly; this uniform stays 0.
uniform float ntvBt709Source;
vec3 ntvToSdr(vec3 encoded) {
    if (ntvBt709Source > 0.5) {
        if (ntvBt709Source < 1.5) return clamp(encoded, 0.0, 1.0);
        float kr = ntvBt709Source > 5.5 ? 0.2627 : 0.299;
        float kb = ntvBt709Source > 5.5 ? 0.0593 : 0.114;
        float y = dot(encoded, vec3(kr, 1.0 - kr - kb, kb));
        float u = (encoded.b - y) / (2.0 * (1.0 - kb));
        float v = (encoded.r - y) / (2.0 * (1.0 - kr));
        float r = y + 1.5748 * v;
        float b = y + 1.8556 * u;
        return clamp(vec3(r, (y - 0.2126 * r - 0.0722 * b) / 0.7152, b), 0.0, 1.0);
    }
    if (ntvTransfer < 0.5) return encoded;
    vec3 v = clamp(encoded, 0.0, 1.0);
    vec3 linear;
    if (ntvTransfer < 6.5) {
        vec3 p = pow(v, vec3(1.0 / 78.84375));
        linear = 10000.0 * pow(max(p - 0.8359375, 0.0)
                / max(18.8515625 - 18.6875 * p, 0.000001), vec3(1.0 / 0.1593017578125));
    } else {
        vec3 low = v * v / 3.0;
        vec3 high = (exp((v - 0.55991073) / 0.17883277) + 0.28466892) / 12.0;
        vec3 scene = mix(low, high, step(vec3(0.5), v));
        float sceneLuma = max(dot(scene, vec3(0.2627, 0.6780, 0.0593)), 0.000001);
        linear = scene * pow(sceneLuma, 0.2) * ntvPeak;
    }
    float luma = max(dot(linear, vec3(0.2627, 0.6780, 0.0593)), 0.000001);
    float x = luma / 100.0;
    float white = max(ntvPeak / 100.0, 1.0);
    float mapped = clamp(x * (1.0 + x / (white * white)) / (1.0 + x), 0.0, 1.0);
    // BT.2020 -> BT.709 in linear light, before SDR encoding.
    vec3 rgb = mat3(1.660491, -0.124550, -0.018151,
                    -0.587641, 1.132900, -0.100579,
                    -0.072850, -0.008349, 1.118730) * linear * (mapped / luma);
    // Compress out-of-gamut chroma toward the mapped neutral, preserving luma.
    vec3 chroma = rgb - vec3(mapped);
    float scale = 1.0;
    if (min(min(rgb.r, rgb.g), rgb.b) < 0.0)
        scale = min(scale, mapped / max(-min(min(chroma.r, chroma.g), chroma.b), 0.000001));
    if (max(max(rgb.r, rgb.g), rgb.b) > 1.0)
        scale = min(scale, (1.0 - mapped) / max(max(max(chroma.r, chroma.g), chroma.b), 0.000001));
    rgb = clamp(vec3(mapped) + chroma * scale, 0.0, 1.0);
    return mix(12.92 * rgb, 1.055 * pow(rgb, vec3(1.0 / 2.4)) - 0.055,
            step(vec3(0.0031308), rgb));
}
