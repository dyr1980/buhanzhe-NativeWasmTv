// LGPL-2.1-or-later. End only the FPS sampling gate when codec/DTS timing agrees.
const fs = require('fs'), path = require('path');
function apply(root) {
    const target = path.join(root, 'libavformat/utils.c');
    let source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    const before = `            if (st->disposition & AV_DISPOSITION_ATTACHED_PIC)
                fps_analyze_framecount = 0;`;
    const after = `            /* nTv adaptive timing: keep all codec/PMT/audio completeness checks.
             * Trust a parsed AVC/HEVC clock only after four matching DTS intervals.
             * Unknown or inconsistent clocks retain the normal sampling budget. */
            if (ntv_adaptive_probe && ic->fps_probe_size < 0 && st->parser
                    && (st->codecpar->codec_id == AV_CODEC_ID_H264
                        || st->codecpar->codec_id == AV_CODEC_ID_HEVC)
                    && !(ic->iformat->flags & AVFMT_NOTIMESTAMPS)
                    && ntv_probe_timing_ready(st->info->duration_count,
                        st->info->fps_first_dts, st->info->fps_last_dts,
                        st->info->fps_last_dts_idx - st->info->fps_first_dts_idx,
                        av_q2d(st->time_base), av_q2d(st->internal->avctx->framerate))) {
                fps_analyze_framecount = 0;
                if (!ntv_adaptive_logged) {
                    av_log(ic, AV_LOG_INFO, "nTv adaptive probe: verified header/DTS fps=%.3f intervals=%d\\n",
                            av_q2d(st->internal->avctx->framerate), st->info->duration_count);
                    ntv_adaptive_logged = 1;
                }
            }
` + before;
    if (!source.includes(after)) {
        if (source.split(before).length !== 2) throw new Error('Unexpected FFmpeg FPS gate');
        source = source.replace(before, after);
    }
    if (!source.includes('#include "ntv_adaptive_probe.h"'))
        source = '#include "ntv_adaptive_probe.h"\n' + source;
    const declaration = '    int eof_reached = 0;';
    const variables = `    int ntv_adaptive_probe = av_dict_get(ic->metadata, "ntv-adaptive-fps-probe", NULL, 0) != NULL;
    int ntv_adaptive_logged = 0;
` + declaration;
    if (!source.includes(variables)) {
        if (source.split(declaration).length !== 2) throw new Error('Unexpected FFmpeg probe locals');
        source = source.replace(declaration, variables);
    }
    fs.writeFileSync(target, source);
    fs.copyFileSync(path.join(__dirname, 'ntv_adaptive_probe.h'), path.join(root, 'libavformat/ntv_adaptive_probe.h'));
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
