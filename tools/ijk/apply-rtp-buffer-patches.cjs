// SDP carries realtime RTP packets, whose duration may be unspecified.
const fs = require('fs');
const path = require('path');
function apply(root) {
    const target = path.join(root, 'ijkmedia/ijkplayer/ff_ffplay.c');
    const source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    const before = '    is->realtime = is_realtime(ic);';
    const after = `${before}
    /* nTv: RTP has no download reserve or backpressure. Packet durations in
     * SDP may be zero; the HTTP byte/time high-water mark then never opens.
     * Retain RTP reordering and normal presentation clocks, without that gate. */
    if (!strcmp(ic->iformat->name, "sdp"))
        ffp->packet_buffering = 0;`;
    if (source.includes(after)) return;
    if (source.split(before).length !== 2) throw new Error('Unexpected IJK realtime detection: ' + target);
    fs.writeFileSync(target, source.replace(before, after));
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
