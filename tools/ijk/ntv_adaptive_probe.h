/* LGPL-2.1-or-later. Verify codec timing against demuxed DTS, never pixel data. */
#ifndef NTV_ADAPTIVE_PROBE_H
#define NTV_ADAPTIVE_PROBE_H
#include <stdint.h>
static int ntv_probe_timing_ready(int intervals, int64_t first, int64_t last,
        int frame_distance, double time_base, double header_fps)
{
    /* Unknown/VFR/discontinuous timing keeps FFmpeg's normal discovery window. */
    if (intervals < 4 || frame_distance < 4 || first == INT64_MIN || last == INT64_MIN
            || last <= first || !(time_base > 0.) || !(header_fps >= 5. && header_fps <= 120.))
        return 0;
    double span = ((double)last - (double)first) * time_base;
    double measured = frame_distance / span;
    return measured >= header_fps * .99 && measured <= header_fps * 1.01;
}
#endif
