// LGPL-2.1-or-later. Publish probe metadata without opening playback decoders.
const fs = require('fs');
const path = require('path');
function apply(root) {
    const play=path.join(root,'ijkmedia/ijkplayer/ff_ffplay.c');
    let original=fs.readFileSync(play,'utf8').replace(/\r\n/g,'\n');
    const old='    if (ffp->ntv_metadata_only) {\n        ijkmeta_set_avformat_context_l(ffp->meta, ic);';
    if(original.includes(old)) {
        if(original.split(old).length!==2)throw new Error('Duplicate metadata probe block');
        original=original.replace(old,'    if (ffp->ntv_metadata_only) {\n        ntv_probe_dimensions(ic, st_index[AVMEDIA_TYPE_VIDEO]);\n        ijkmeta_set_avformat_context_l(ffp->meta, ic);');
        fs.writeFileSync(play,original);
    }
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
        if(source.split(before).length!==2)throw new Error('Unexpected metadata probe hook: '+file);
        fs.writeFileSync(target,source.replace(before,after));
    }
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    int ntv_frame_sync;',
        '    int ntv_metadata_only; /* one-shot discovery; never opens audio/video output */\n    int ntv_frame_sync;');
    edit('ijkmedia/ijkplayer/ff_ffplay_options.h','    { "ntv-frame-sync",',
        '    { "ntv-metadata-only", "publish discovered tracks without playback decoders", OPTION_OFFSET(ntv_metadata_only), OPTION_INT(0, 0, 1) },\n    { "ntv-frame-sync",');
    // FFmpeg 3.4 HEVC does not honor skip_frame=all. Deny only its probe
    // decoder; stream parsers and the bounded SPS reader still fill dimensions.
    edit('ijkmedia/ijkplayer/ff_ffplay.c','        int orig_nb_streams = ic->nb_streams;\n\n        do {',
        `        int orig_nb_streams = ic->nb_streams;
        if (ffp->ntv_metadata_only) {
            for (int n = 0; n < orig_nb_streams; ++n)
                if (ic->streams[n]->codecpar->codec_id == AV_CODEC_ID_HEVC)
                    av_dict_set(&opts[n], "codec_whitelist", "ntv_header_only", 0);
        }

        do {`);
    edit('ijkmedia/ijkplayer/ff_ffplay.c','    /* open the streams */',
        `    if (ffp->ntv_metadata_only) {
        ntv_probe_dimensions(ic, st_index[AVMEDIA_TYPE_VIDEO]);
        ijkmeta_set_avformat_context_l(ffp->meta, ic);
        ijkmeta_set_int64_l(ffp->meta, IJKM_KEY_VIDEO_STREAM, st_index[AVMEDIA_TYPE_VIDEO]);
        ijkmeta_set_int64_l(ffp->meta, IJKM_KEY_AUDIO_STREAM, st_index[AVMEDIA_TYPE_AUDIO]);
        ffp->prepared = true;
        ffp_notify_msg1(ffp, FFP_MSG_PREPARED);
        ntv_hevc_probe_clear(&ntv_hevc_probe);
        SDL_DestroyMutex(wait_mutex);
        return 0; /* stream_close owns ic; the probe caller releases the player */
    }
    /* open the streams */`);
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
