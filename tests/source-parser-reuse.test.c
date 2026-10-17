/* Run against the generated native overlay and its matching libijkffmpeg. */
#include <assert.h>
#include <stdio.h>
#include "libavformat/avformat.h"
#include "libavutil/time.h"
#include "ntv_header_cache.h"
#include "ntv_stream_timing.h"
typedef struct FFPlayer {
    AVCodecParserContext *ntv_source_parser;
    AVCodecContext *ntv_source_context;
    int ntv_source_parser_serial, ntv_source_avc_scan, ntv_source_avc_header;
    int ntv_source_preferred_transfer, first_video_frame_rendered;
    uint32_t ntv_video_description;
    NtvHeaderCache ntv_source_header_cache;
    NtvStreamTiming ntv_timing;
} FFPlayer;
#include "ntv_stream_probe.h"
#ifdef NDEBUG
#error This regression requires assertions
#endif
int main(int argc, char **argv)
{
    av_register_all(); av_log_set_level(AV_LOG_ERROR);
    assert(argc > 1);
    FFPlayer transitions = {0}; transitions.first_video_frame_rendered = 1;
    for (int file = 1; file < argc; ++file) {
        AVFormatContext *ic = NULL;
        assert(avformat_open_input(&ic, argv[file], NULL, NULL) >= 0);
        assert(avformat_find_stream_info(ic, NULL) >= 0);
        int video = av_find_best_stream(ic, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
        assert(video >= 0);
        AVPacket packets[2048] = {{0}}; unsigned count = 0;
        while (count < 2048 && av_read_frame(ic, &packets[count]) >= 0) {
            if (packets[count].stream_index == video) ++count;
            else av_packet_unref(&packets[count]);
        }
        assert(count > 50);
        int64_t elapsed = av_gettime_relative();
        float fps = 0; unsigned order = 0; int parser = 0;
        for (unsigned repeat = 0; repeat < 100; ++repeat) {
            FFPlayer ffp = {0}; ffp.first_video_frame_rendered = 1;
            for (unsigned n = 0; n < count; ++n)
                ntv_measure_source(&ffp, ic->streams[video], &packets[n], 1);
            fps = ffp.ntv_timing.source_fps;
            order = (ffp.ntv_video_description >> 27) & 7;
            parser = ffp.ntv_source_parser != NULL;
            assert(fps > 23 && fps < 31);
            assert(order != AV_FIELD_UNKNOWN);
#ifndef NTV_BASELINE
            if (order == AV_FIELD_PROGRESSIVE) assert(!parser);
            else assert(parser);
#endif
            if (ffp.ntv_source_parser) av_parser_close(ffp.ntv_source_parser);
            avcodec_free_context(&ffp.ntv_source_context);
        }
        elapsed = av_gettime_relative() - elapsed;
        printf("%s packets=%u repeats=100 us=%lld fps=%.3f order=%u auxiliary_parser=%d\n",
                argv[file], count, (long long)elapsed, fps, order, parser);
#ifndef NTV_BASELINE
        /* Format updates can replace extradata or deliver SPS in-band, without a seek. */
        for (unsigned n = 0; n < count; ++n)
            ntv_measure_source(&transitions, ic->streams[video], &packets[n], 7);
        assert(((transitions.ntv_video_description >> 27) & 7) == order);
#endif
        for (unsigned n = 0; n < count; ++n) av_packet_unref(&packets[n]);
        avformat_close_input(&ic);
    }
    if (transitions.ntv_source_parser) av_parser_close(transitions.ntv_source_parser);
    avcodec_free_context(&transitions.ntv_source_context);
    return 0;
}
