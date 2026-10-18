// LGPL-2.1-or-later. Keep compressed pictures intact on small vendor buffers.
const fs=require('fs'),path=require('path');
function apply(root) {
    const target=path.join(root,'ijkmedia/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c');
    let source=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
    function edit(before,after,count=1) {
        if(source.includes(after))return;
        if(source.split(before).length-1!==count)throw new Error('Unexpected complete-input hook: '+before.slice(0,70));
        source=source.split(before).join(after);
    }
    edit('#include "h264_nal.h"','#include "h264_nal.h"\n#include "../../ntv_codec_input.h"');
    edit('    size_t                    nal_size;','    size_t                    nal_size;\n    NtvCodecInput             ntv_input;');
    const format='    opaque->ntv_performance_rate = 0;';
    edit(format,format+`
    if (opaque->ntv_input.requested > 0)
        SDL_AMediaFormat_setInt32(opaque->input_aformat, "max-input-size", opaque->ntv_input.requested);`);
    const pending='        d->packet_pending = 1;';
    edit(pending,pending+`
        /* Reopening a decoder loses its reference pictures. Resume at an IDR,
         * never feed dependent pictures from the old decoder into the new one. */
        if (d->pkt_temp.data && !ntv_codec_input_accept(&opaque->ntv_input,
                    !!(d->pkt_temp.flags & AV_PKT_FLAG_KEY),
                    !!(d->pkt_temp.flags & AV_PKT_FLAG_CORRUPT))) {
            if (d->pkt_temp.flags & AV_PKT_FLAG_CORRUPT) {
                ALOGW("nTv: discard damaged video packet; flush references and wait for keyframe");
                opaque->acodec_flush_request = true;
            }
            d->packet_pending = 0;
            return 0;
        }`,2);
    const stamp='        time_stamp = d->pkt_temp.pts;';
    edit(stamp,`
        if (input_buffer_index >= 0) {
            int grow = ntv_codec_input_grow(&opaque->ntv_input, d->pkt_temp.size,
                    copy_size, !!(d->pkt_temp.flags & AV_PKT_FLAG_KEY));
            if (grow < 0) {
                ALOGE("nTv: codec cannot accept complete picture: bytes=%d capacity=%d requested=%d",
                        d->pkt_temp.size, (int)copy_size, opaque->ntv_input.requested);
                ffp_notify_msg3(ffp, FFP_MSG_ERROR, 0, AVERROR(EINVAL));
                ret = -1;
                goto fail;
            }
            if (grow > 0) {
                ALOGW("nTv: enlarge codec input: bytes=%d capacity=%d requested=%d key=%d",
                        d->pkt_temp.size, (int)copy_size, opaque->ntv_input.requested,
                        !!(d->pkt_temp.flags & AV_PKT_FLAG_KEY));
                opaque->aformat_need_recreate = true;
                ffpipeline_set_surface_need_reconfigure_l(pipeline, true);
                if (opaque->ntv_input.wait_key) d->packet_pending = 0;
                /* No queue, no cursor advance, no timestamp reset. The whole
                 * key packet is retried after codec stop/configure/start. */
                ret = 0;
                goto fail;
            }
        }
`+stamp,2);
    // The legacy synchronous loop ignored fatal feed failures and kept running.
    edit('        ret = feed_input_buffer2(env, node, AMC_SYNC_INPUT_TIMEOUT_US, &enqueue_count);',
         '        ret = feed_input_buffer2(env, node, AMC_SYNC_INPUT_TIMEOUT_US, &enqueue_count);\n        if (ret < 0) goto fail;');
    fs.writeFileSync(target,source);
    fs.copyFileSync(path.join(__dirname,'ntv_codec_input.h'),path.join(root,'ijkmedia/ijkplayer/ntv_codec_input.h'));
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
