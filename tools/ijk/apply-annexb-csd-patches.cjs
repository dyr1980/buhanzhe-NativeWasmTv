// Pass Annex B parameter sets to MediaCodec, including SDP's out-of-band SPS/PPS.
const fs = require('fs');
const path = require('path');
function apply(root) {
    const target = path.join(root, 'ijkmedia/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c');
    const source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    const before = '            // SDL_AMediaFormat_setBuffer(opaque->aformat, "csd-0", opaque->codecpar->extradata, opaque->codecpar->extradata_size);';
    const after = `            /* nTv: SDP may provide parameter sets only out of band. */
            if (opaque->codecpar->codec_id == AV_CODEC_ID_H264
                    || opaque->codecpar->codec_id == AV_CODEC_ID_HEVC)
                SDL_AMediaFormat_setBuffer(opaque->input_aformat, "csd-0",
                        opaque->codecpar->extradata, opaque->codecpar->extradata_size);`;
    if (source.includes(after)) return;
    if (source.split(before).length !== 2) throw new Error('Unexpected IJK Annex B CSD: ' + target);
    fs.writeFileSync(target, source.replace(before, after));
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
