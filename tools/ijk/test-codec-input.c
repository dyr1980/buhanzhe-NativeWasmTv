/* Fault cases for the same bounded input policy used by both codec feeders. */
#undef NDEBUG
#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include "ntv_codec_input.h"
int main(void)
{
    NtvCodecInput s = {0};
    assert(ntv_codec_input_grow(&s, 4096, 4096, 0) == 0);
    assert(s.requested == 0 && !s.wait_key);
    assert(ntv_codec_input_grow(&s, 1500000, 1048576, 1) == 1);
    assert(s.requested == 2097152 && !s.wait_key);
    assert(ntv_codec_input_grow(&s, 1500000, 1500000, 1) == 0);
    /* A later, larger non-key picture loses its old references on reopen. */
    assert(ntv_codec_input_grow(&s, 3000000, 2097152, 0) == 1);
    assert(s.requested == 3145728 && s.wait_key);
    assert(!ntv_codec_input_accept(&s, 0, 0) && s.wait_key);
    assert(ntv_codec_input_accept(&s, 1, 0) && !s.wait_key);
    assert(ntv_codec_input_grow(&s, 3000000, 2097152, 0) == -1);
    assert(s.failed); /* Vendor ignored the request: no retry loop. */
    s = (NtvCodecInput){0};
    assert(ntv_codec_input_grow(&s, INT_MAX, 1, 1) == -1 && s.failed);
    s = (NtvCodecInput){0};
    assert(ntv_codec_input_grow(&s, 16777216, 1, 1) == 1);
    assert(s.requested == 16777216);
    s = (NtvCodecInput){0};
    assert(ntv_codec_input_grow(&s, 10, -1, 1) == -1);
    assert(ntv_codec_input_grow(&s, 10, 0, 1) == -1);
    assert(ntv_codec_input_grow(&s, 10, 11, 1) == -1);
    assert(ntv_codec_input_grow(&s, 0, 0, 1) == -1);
    assert(!ntv_codec_input_accept(&s, 1, 1) && s.wait_key);
    assert(!ntv_codec_input_accept(&s, 0, 0) && s.wait_key);
    assert(!ntv_codec_input_accept(&s, 0, 1) && s.wait_key);
    assert(ntv_codec_input_accept(&s, 1, 0) && !s.wait_key);
    assert(ntv_codec_input_accept(&s, 0, 0));
    puts("PASS complete picture / key retry / dependent-picture resync / ignored request / 16MiB bound / invalid capacity");
    return 0;
}
