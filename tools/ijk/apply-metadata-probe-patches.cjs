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
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    int ntv_metadata_only;',
        '    int ntv_adaptive_fps_probe; /* verify codec clock against DTS before early completion */\n    int ntv_metadata_only;');
    edit('ijkmedia/ijkplayer/ff_ffplay_options.h','    { "ntv-metadata-only",',
        '    { "ntv-adaptive-fps-probe", "use verified codec/DTS timing for initial discovery", OPTION_OFFSET(ntv_adaptive_fps_probe), OPTION_INT(0, 0, 1) },\n    { "ntv-metadata-only",');
    edit('ijkmedia/ijkplayer/ff_ffplay.c','    av_format_inject_global_side_data(ic);',
        '    if (ffp->ntv_adaptive_fps_probe)\n        av_dict_set(&ic->metadata, "ntv-adaptive-fps-probe", "1", 0);\n    av_format_inject_global_side_data(ic);');
    // A per-player, demux-thread-only cache. Compare the complete extradata;
    // pointer/size alone miss in-place updates and resolution/HDR changes.
    fs.copyFileSync(path.join(__dirname,'ntv_header_cache.h'),
            path.join(root,'ijkmedia/ijkplayer/ntv_header_cache.h'));
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','#include "ntv_stream_timing.h"',
            '#include "ntv_header_cache.h"\n#include "ntv_stream_timing.h"');
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    int ntv_source_avc_header;',
            '    NtvHeaderCache ntv_source_header_cache; /* demux-thread extradata only */\n    int ntv_source_avc_header;');
    edit('ijkmedia/ijkplayer/ff_ffplay_def.h','    NtvHeaderCache ntv_source_header_cache;',
            '    int ntv_source_avc_scan; /* newest in-band SPS; 0 unknown, 1 frame-only, 2 field-capable */\n    NtvHeaderCache ntv_source_header_cache;');
    // A frame-only SPS guarantees one picture per demuxed access unit. Reuse
    // its classification and packet timestamps instead of parsing each slice
    // twice. Field-capable/unknown AVC still needs the PAFF picture parser.
    const sourceProbe='ijkmedia/ijkplayer/ntv_stream_probe.h';
    edit(sourceProbe,
            '                || ffp->ntv_source_context->codec_id != st->codecpar->codec_id) {',
            `                || ffp->ntv_source_context->codec_id != st->codecpar->codec_id
                || ffp->ntv_source_context->extradata_size != st->codecpar->extradata_size
                || (st->codecpar->extradata_size > 0 && memcmp(ffp->ntv_source_context->extradata,
                            st->codecpar->extradata, st->codecpar->extradata_size))) {`);
    edit(sourceProbe,
            '            ffp->ntv_source_parser = h264 ? av_parser_init(AV_CODEC_ID_H264) : NULL;',
            '            ffp->ntv_source_parser = NULL;\n            ffp->ntv_source_avc_scan = 0;');
    edit(sourceProbe,
            '                    if (h264) ffp->ntv_source_avc_header = 1;',
            '                    if (h264) {\n                        ffp->ntv_source_avc_header = 1;\n                        ffp->ntv_source_avc_scan = color.scan;\n                    }');
    edit(sourceProbe,
            '        if (ffp->ntv_source_parser && ffp->ntv_source_context) {',
            `        int scan = ffp->ntv_source_avc_scan;
        if (h264 && !scan) {
            NtvHevcColor header;
            if (ntv_cached_header(&ffp->ntv_source_header_cache, st->codecpar->codec_id,
                        st->codecpar->extradata, st->codecpar->extradata_size,
                        ntv_h264_extradata_color, &header)) scan = header.scan;
        }
        if (h264 && scan == 1 && ffp->ntv_source_context) {
            structure = AV_PICTURE_STRUCTURE_FRAME;
            ffp->ntv_source_context->field_order = AV_FIELD_PROGRESSIVE;
        } else if (h264 && ffp->ntv_source_context) {
            if (!ffp->ntv_source_parser) {
                ffp->ntv_source_parser = av_parser_init(AV_CODEC_ID_H264);
                if (ffp->ntv_source_parser) ffp->ntv_source_parser->flags |= PARSER_FLAG_COMPLETE_FRAMES;
            }
            /* Do not retain the old progressive classification after an SPS change. */
            ffp->ntv_source_context->field_order = AV_FIELD_UNKNOWN;
        }
        if (h264 && scan != 1 && ffp->ntv_source_parser && ffp->ntv_source_context) {`);
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            'static uint32_t ntv_describe_video_header(AVStream *st, AVCodecContext *ctx, AVFrame *frame, int packet_header)',
            'static uint32_t ntv_describe_video_header_cached(AVStream *st, AVCodecContext *ctx, AVFrame *frame, int packet_header, NtvHeaderCache *cache)');
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            'ntv_hevc_extradata_color(par->extradata, par->extradata_size, &signal);',
            'ntv_cached_header(cache, par->codec_id, par->extradata, par->extradata_size, ntv_hevc_extradata_color, &signal);');
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            'ntv_h264_extradata_color(par->extradata,par->extradata_size,&signal));',
            'ntv_cached_header(cache, par->codec_id, par->extradata,par->extradata_size, ntv_h264_extradata_color, &signal));');
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            'static uint32_t ntv_describe_video(AVStream *st, AVCodecContext *ctx, AVFrame *frame)',
            `static uint32_t ntv_describe_video_header(AVStream *st, AVCodecContext *ctx, AVFrame *frame, int packet_header)
{
    return ntv_describe_video_header_cached(st, ctx, frame, packet_header, NULL);
}
static uint32_t ntv_describe_video(AVStream *st, AVCodecContext *ctx, AVFrame *frame)`);
    if (!fs.readFileSync(path.join(root,'ijkmedia/ijkplayer/ntv_video_description.h'),'utf8')
            .includes('if (old & NTV_DESCRIPTION_FRAME) return;'))
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            '    uint32_t value = ntv_describe_video_header(st,ctx,NULL,ffp->ntv_source_avc_header);',
            `    if (old & NTV_DESCRIPTION_FRAME) return;
    uint32_t value = ntv_describe_video_header_cached(st,ctx,NULL,ffp->ntv_source_avc_header,
            &ffp->ntv_source_header_cache);`);
    edit('ijkmedia/ijkplayer/ntv_video_description.h',
            '            &ffp->ntv_source_header_cache);',
            '            ffp->first_video_frame_rendered ? &ffp->ntv_source_header_cache : NULL);');
    // FFmpeg 3.4 HEVC does not honor skip_frame=all. Deny only its probe
    // decoder; stream parsers and the bounded SPS reader still fill dimensions.
    if (!fs.readFileSync(play,'utf8').includes('av_dict_set(&opts[n], "codec_whitelist", "ntv_header_only", 0);'))
    edit('ijkmedia/ijkplayer/ff_ffplay.c','        int orig_nb_streams = ic->nb_streams;\n\n        do {',
        `        int orig_nb_streams = ic->nb_streams;
        if (ffp->ntv_metadata_only) {
            for (int n = 0; n < orig_nb_streams; ++n)
                if (ic->streams[n]->codecpar->codec_id == AV_CODEC_ID_HEVC)
                    av_dict_set(&opts[n], "codec_whitelist", "ntv_header_only", 0);
        }

        do {`);
    // These temporary decoder frames are discarded after format discovery.
    // Loop filtering cannot improve their metadata. Playback creates a separate
    // decoder with the original options, including normal loop filtering.
    edit('ijkmedia/ijkplayer/ff_ffplay.c','        do {\n            if (av_stristart(is->filename, "data:", NULL) && orig_nb_streams > 0) {',
        `        for (int n = 0; n < orig_nb_streams; ++n)
            if (ic->streams[n]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO
                    && (ic->streams[n]->codecpar->codec_id == AV_CODEC_ID_H264
                        || ic->streams[n]->codecpar->codec_id == AV_CODEC_ID_HEVC))
                av_dict_set(&opts[n], "skip_loop_filter", "all", 0);

        do {
            if (av_stristart(is->filename, "data:", NULL) && orig_nb_streams > 0) {`);
    if(!fs.readFileSync(play,'utf8').includes('return 0; /* stream_close owns ic; the probe caller releases the player */'))
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
    const target=path.join(root,'ijkmedia/ijkplayer/ff_ffplay.c');
    let code=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
    const before='        ntv_probe_dimensions(ic, st_index[AVMEDIA_TYPE_VIDEO]);\n        ijkmeta_set_avformat_context_l(ffp->meta, ic);';
    const after=`        ntv_probe_dimensions(ic, st_index[AVMEDIA_TYPE_VIDEO]);
        /* Auxiliary SPS metadata, without opening a playback decoder or Surface. */
        int probe_index = st_index[AVMEDIA_TYPE_VIDEO];
        if (probe_index >= 0 && probe_index < ic->nb_streams) {
            AVStream *probe_video = ic->streams[probe_index];
            uint32_t description = ntv_describe_video(probe_video, NULL, NULL);
            NtvHevcColor signal = {0};
            if (((description >> 27) & 7) == AV_FIELD_UNKNOWN
                    && probe_video->codecpar->codec_id == AV_CODEC_ID_H264
                    && ntv_h264_extradata_color(probe_video->codecpar->extradata,
                            probe_video->codecpar->extradata_size, &signal) && signal.scan)
                description |= (signal.scan == 1 ? AV_FIELD_PROGRESSIVE : 6U) << 27;
            __atomic_store_n(&ffp->ntv_video_description, description, __ATOMIC_RELAXED);
        }
        ijkmeta_set_avformat_context_l(ffp->meta, ic);`;
    // Remove only the exact older duplicate block created by a previous overlay rerun.
    if(code.includes(after)) {
        const duplicate = /    if \(ffp->ntv_metadata_only\) \{\n        ntv_probe_dimensions\(ic, st_index\[AVMEDIA_TYPE_VIDEO\]\);\n        ijkmeta_set_avformat_context_l\(ffp->meta, ic\);[\s\S]*?return 0; \/\* stream_close owns ic; the probe caller releases the player \*\/\n    \}\n/g;
        const clean=code.replace(duplicate,'');
        if(clean!==code){code=clean;fs.writeFileSync(target,code);}
    }
    if(!code.includes(after)) {
        if(code.split(before).length!==2)throw new Error('Unexpected metadata description hook');
        fs.writeFileSync(target,code.replace(before,after));
    }

}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
