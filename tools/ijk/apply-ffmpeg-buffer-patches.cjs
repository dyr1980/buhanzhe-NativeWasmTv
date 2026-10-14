// LGPL-2.1-or-later. Preserve already-read samples while enlarging network AVIO.
const fs = require('fs');
const path = require('path');
function apply(root) {
    const target = path.join(root, 'libavformat/utils.c');
    const source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    const before = '        ffio_set_buf_size(s->pb, pos_delta);';
    const after = `        /* nTv: retain unread fMP4/HLS samples when growing the input buffer. */
        AVIOContext *pb = s->pb;
        uint8_t *buffer = av_malloc(pos_delta);
        if (buffer) {
            ptrdiff_t ptr = pb->buf_ptr - pb->buffer;
            ptrdiff_t end = pb->buf_end - pb->buffer;
            ptrdiff_t maximum = pb->buf_ptr_max - pb->buffer;
            memcpy(buffer, pb->buffer, pb->buffer_size);
            av_free(pb->buffer);
            pb->buffer = buffer;
            pb->buf_ptr = buffer + ptr;
            pb->buf_end = buffer + end;
            pb->buf_ptr_max = buffer + maximum;
            pb->buffer_size = pb->orig_buffer_size = pos_delta;
        }`;
    if (source.includes(after)) return;
    const previous = after.replace('nTv: retain unread fMP4/HLS samples when growing the input buffer.',
            'Growing a nonseekable fMP4/HLS buffer must retain unread sample bytes.');
    if (source.includes(previous)) {
        fs.writeFileSync(target, source.replace(previous, after));
        return;
    }
    if (source.split(before).length !== 2) throw new Error('Unexpected FFmpeg buffer configuration: ' + target);
    fs.writeFileSync(target, source.replace(before, after));
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
