// LGPL-2.1-or-later. MT6580 AVC stalls on field-capable sequences; use software.
const fs = require('fs');
const path = require('path');
function apply(root) {
    function edit(file,before,after) {
        const target=path.join(root,file),source=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
        if(file.endsWith('ff_ffplay_def.h') || file.endsWith('ff_ffplay_options.h')) {
            const marker=after.split('\n')[0];
            if(source.includes(marker)) {
                let seen=false;
                fs.writeFileSync(target,source.split('\n').filter(line=>line!==marker || (!seen && (seen=true))).join('\n'));return;
            }
        }
        if(source.includes(after))return;
        if(source.split(before).length!==2)throw new Error('Unexpected MTK field hook: '+file);
        fs.writeFileSync(target,source.replace(before,after));
    }
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    int ntv_frame_sync;',
        '    int ntv_mtk_avc_fields; /* enabled only for the MT6580 device policy */\n    int ntv_frame_sync;');
    edit('ijkmedia/ijkplayer/ff_ffplay_options.h','    { "ntv-frame-sync",',
        '    { "ntv-mtk-avc-fields", "avoid unsupported field-coded AVC on MT6580", OPTION_OFFSET(ntv_mtk_avc_fields), OPTION_INT(0, 0, 1) },\n    { "ntv-frame-sync",');
    edit('ijkmedia/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c',
        '    jsurface = ffpipeline_get_surface_as_global_ref(env, pipeline);\n    ret = reconfigure_codec_l(env, node, jsurface);',
        `    if (ffp->ntv_mtk_avc_fields && opaque->codecpar->codec_id == AV_CODEC_ID_H264
            && !strncasecmp(opaque->mcc.codec_name, "OMX.MTK.", 8)) {
        NtvHevcColor signal;
        if (ntv_h264_extradata_color(opaque->codecpar->extradata,opaque->codecpar->extradata_size,&signal)
                && signal.scan == 2) {
            ALOGW("nTv MT6580: field-capable AVC requires software decoding");
            goto fail;
        }
    }
    jsurface = ffpipeline_get_surface_as_global_ref(env, pipeline);
    ret = reconfigure_codec_l(env, node, jsurface);`);
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
