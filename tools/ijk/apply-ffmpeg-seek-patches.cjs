// LGPL-2.1-or-later. Adapt HLS position-reset handling to the pinned FFmpeg 3.4 MOV demuxer.
// Background: https://ffmpeg.org/pipermail/ffmpeg-devel/2024-November/335634.html
const fs = require('fs');
const path = require('path');
function apply(root) {
    const target = path.join(root, 'libavformat/mov.c');
    let source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    if (source.includes('nTv: HLS reset its nonseekable fragment stream.')) return;
    const before = '    if (avio_seek(s->pb, target, SEEK_SET) != target) {';
    if (source.split(before).length !== 2) throw new Error('Unexpected MOV root seek');
    source = source.replace(before, '    if (target >= 0 && avio_seek(s->pb, target, SEEK_SET) != target) {');
    const anchor = '    mov->fc = s;\n retry:\n    sample = mov_find_next_sample(s, &st);';
    const replacement = `    mov->fc = s;
 retry:
    /* nTv: HLS reset its nonseekable fragment stream. Old byte indexes refer
     * to a different segment; keep track headers but rebuild sample offsets. */
    if (!(s->pb->seekable & AVIO_SEEKABLE_NORMAL) && s->pb->pos == 0
        && mov->trex_count > 0) {
        int i;
        for (i = 0; i < mov->fragment_index_count; i++) {
            av_freep(&mov->fragment_index_data[i]->items);
            av_freep(&mov->fragment_index_data[i]);
        }
        av_freep(&mov->fragment_index_data);
        mov->fragment_index_count = 0;
        mov->fragment_index_complete = 0;
        for (i = 0; i < s->nb_streams; i++) {
            AVStream *stream = s->streams[i];
            MOVStreamContext *context = stream->priv_data;
            mov_current_sample_set(context, 0);
            context->ctts_index = context->ctts_sample = 0;
            av_freep(&context->ctts_data);
            context->ctts_count = context->ctts_allocated_size = 0;
            av_freep(&stream->index_entries);
            stream->nb_index_entries = stream->index_entries_allocated_size = 0;
        }
        if ((ret = mov_switch_root(s, -1)) < 0)
            return ret;
    }
    sample = mov_find_next_sample(s, &st);`;
    if (source.split(anchor).length !== 2) throw new Error('Unexpected MOV packet reader');
    fs.writeFileSync(target, source.replace(anchor, replacement));
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
