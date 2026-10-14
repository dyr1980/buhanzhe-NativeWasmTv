// LGPL-2.1-or-later. Bound software frame allocation on low-RAM devices.
const fs = require('fs');
const path = require('path');
function apply(root) {
    function edit(file,before,after) {
        const target=path.join(root,file);
        let source=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
        if(file.endsWith('ffpipeline_android.c'))source=source.replace('ffp_notify_msg2(ffp, FFP_MSG_ERROR, -20002);','ffp->last_error = -20002;');
        if(file.endsWith('ff_ffplay_def.h') || file.endsWith('ff_ffplay_options.h')) {
            const marker=after.split('\n')[0];
            if(source.includes(marker)) {
                let seen=false;
                source=source.split('\n').filter(line=>line!==marker || (!seen && (seen=true))).join('\n');
                fs.writeFileSync(target,source);return;
            }
        }
        if(source.includes(after)){fs.writeFileSync(target,source);return;}
        if(source.split(before).length!==2)throw new Error('Unexpected software memory hook: '+file);
        fs.writeFileSync(target,source.replace(before,after));
    }
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    int ntv_frame_sync;',
        '    int ntv_max_software_pixels; /* zero leaves software resolution unlimited */\n    int ntv_frame_sync;');
    edit('ijkmedia/ijkplayer/ff_ffplay_options.h','    { "ntv-frame-sync",',
        '    { "ntv-max-software-pixels", "low-RAM software frame allocation limit", OPTION_OFFSET(ntv_max_software_pixels), OPTION_INT(0, 0, 2147483647) },\n    { "ntv-frame-sync",');
    edit('ijkmedia/ijkplayer/android/pipeline/ffpipeline_android.c',
        '    if (!node) {\n        node = ffpipenode_create_video_decoder_from_ffplay(ffp);',
        `    if (!node) {
        const AVCodecContext *avctx = ffp->is->viddec.avctx;
        if (ffp->ntv_max_software_pixels > 0 && avctx && avctx->width > 0 && avctx->height > 0
                && (int64_t)avctx->width * avctx->height > ffp->ntv_max_software_pixels) {
            ALOGW("nTv: software resolution %dx%d exceeds low-RAM frame limit", avctx->width, avctx->height);
            ffp->last_error = -20002;
            return NULL;
        }
        node = ffpipenode_create_video_decoder_from_ffplay(ffp);`);
    const pipeline=path.join(root,'ijkmedia/ijkplayer/android/pipeline/ffpipeline_android.c');
    let source=fs.readFileSync(pipeline,'utf8');
    source=source.replace('ffp_notify_msg2(ffp, FFP_MSG_ERROR, -20002);','ffp->last_error = -20002;');
    source=source.replace('ffp_notify_msg2(ffp, FFP_MSG_ERROR, -20001);','ffp->last_error = -20001;');
    fs.writeFileSync(pipeline,source);
    const file=path.join(root,'ijkmedia/ijkplayer/ff_ffplay.c');
    source=fs.readFileSync(file,'utf8');
    if(!source.includes('/* The pipeline rejected avctx before decoder ownership transferred. */')) {
        const pattern=/if \(!ffp->node_vdec\)\n\s+goto fail;/g;
        if([...source.matchAll(pattern)].length!==2)throw new Error('Unexpected video decoder failure hooks');
        source=source.replace(pattern,`if (!ffp->node_vdec) {
                    /* The pipeline rejected avctx before decoder ownership transferred. */
                    is->viddec.avctx = NULL;
                    is->video_stream = -1;
                    is->video_st = NULL;
                    ret = ffp->last_error ? ffp->last_error : AVERROR(ENOMEM);
                    goto fail;
                }`);
        fs.writeFileSync(file,source);
    }
    edit('ijkmedia/ijkplayer/ff_ffplay.c',
        '        ret = stream_component_open(ffp, st_index[AVMEDIA_TYPE_VIDEO]);',
        `        ret = stream_component_open(ffp, st_index[AVMEDIA_TYPE_VIDEO]);
        if (ret == -20001 || ret == -20002) {
            last_error = ret;
            goto fail;
        }`);
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
