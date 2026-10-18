/* Inspect actual packets returned by the packaged FFmpeg, including parse2. */
#include <stdio.h>
#include <stdlib.h>
#include "libavformat/avformat.h"
int main(int argc, char **argv)
{
    AVFormatContext *ic = NULL;
    AVPacket pkt;
    int video = 0, corrupt = 0;
    if (argc != 3) return 2;
    av_register_all();
    if (avformat_open_input(&ic, argv[1], NULL, NULL) < 0 ||
            avformat_find_stream_info(ic, NULL) < 0) return 3;
    av_init_packet(&pkt);
    while (av_read_frame(ic, &pkt) >= 0) {
        if (ic->streams[pkt.stream_index]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            ++video;
            if (pkt.flags & AV_PKT_FLAG_CORRUPT) ++corrupt;
        }
        av_packet_unref(&pkt);
    }
    avformat_close_input(&ic);
    printf("video=%d corrupt=%d expectedCorrupt=%d\n", video, corrupt, atoi(argv[2]));
    return video > 0 && (atoi(argv[2]) ? corrupt > 0 : corrupt == 0) ? 0 : 4;
}
