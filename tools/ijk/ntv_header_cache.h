/* LGPL-2.1-or-later. Exact, bounded cache for immutable codec extradata only. */
#ifndef NTV_HEADER_CACHE_H
#define NTV_HEADER_CACHE_H
#include "ntv_hevc_color.h"
typedef int (*NtvHeaderParser)(const uint8_t *, unsigned, NtvHevcColor *);
typedef struct NtvHeaderCache {
    uint8_t bytes[1024];
    unsigned size;
    int initialized, codec, result;
    NtvHevcColor color;
} NtvHeaderCache;

/* Owned by the demux thread. In-band SPS/SEI and decoded frames are never cached. */
static int ntv_cached_header(NtvHeaderCache *cache, int codec,
        const uint8_t *data, unsigned size, NtvHeaderParser parse, NtvHevcColor *out)
{
    if (!cache || !data || size > sizeof(cache->bytes)) return parse(data, size, out);
    if (!cache->initialized || cache->codec != codec || cache->size != size
            || memcmp(cache->bytes, data, size)) {
        NtvHevcColor color = {0};
        cache->result = parse(data, size, &color);
        cache->color = color;
        memcpy(cache->bytes, data, size);
        cache->size = size;
        cache->codec = codec;
        cache->initialized = 1;
    }
    if (cache->result) *out = cache->color;
    return cache->result;
}
#endif
