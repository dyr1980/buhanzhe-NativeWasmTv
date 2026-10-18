/* LGPL-2.1-or-later. Submit one complete compressed access unit per buffer.
 * Do not split an access unit into unflagged, timestamp-less pictures. */
#ifndef NTV_CODEC_INPUT_H
#define NTV_CODEC_INPUT_H
#include <stdint.h>
typedef struct NtvCodecInput {
    int requested;
    int wait_key;
    int failed;
} NtvCodecInput;

static int ntv_codec_input_accept(NtvCodecInput *state, int key, int corrupt)
{
    if (corrupt) { state->wait_key = 1; return 0; }
    if (state->wait_key && !key) return 0;
    state->wait_key = 0;
    return 1;
}

/* A bounded retry: some vendor codecs ignore max-input-size. Never loop or
 * allocate from an unchecked packet length. The limit is compressed bytes. */
static int ntv_codec_input_grow(NtvCodecInput *state, int size, int copied, int key)
{
    const int step = 1024 * 1024, limit = 16 * 1024 * 1024;
    if (state->failed) return -1;
    if (size <= 0 || copied <= 0 || copied > size) return -1;
    if (copied == size) return 0;
    if (size > limit || size <= state->requested) {
        state->failed = 1;
        return -1;
    }
    state->requested = (int)(((int64_t)size + step - 1) / step * step);
    state->wait_key = !key;
    return 1;
}
#endif
