// Check both upstream feeder hooks, exact matching, and repeatable rebuilds.
const assert=require('assert'),fs=require('fs'),path=require('path'),os=require('os');
const apply=require('./apply-complete-input-patches.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'ntv-complete-input-'));
const relative='ijkmedia/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c';
const target=path.join(root,relative);
const fixture=`#include "h264_nal.h"
    size_t                    nal_size;
    opaque->ntv_performance_rate = 0;
        d->packet_pending = 1;
        time_stamp = d->pkt_temp.pts;
        d->packet_pending = 1;
        time_stamp = d->pkt_temp.pts;
        ret = feed_input_buffer2(env, node, AMC_SYNC_INPUT_TIMEOUT_US, &enqueue_count);
`;
try {
    fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,fixture);
    apply(root);const first=fs.readFileSync(target,'utf8');apply(root);
    assert.strictEqual(fs.readFileSync(target,'utf8'),first,'Overlay must be idempotent');
    const count=s=>first.split(s).length-1;
    assert.strictEqual(count('int grow = ntv_codec_input_grow'),2);
    assert.strictEqual(count('ntv_codec_input_accept'),2);
    assert.strictEqual(count('AV_PKT_FLAG_CORRUPT'),4);
    assert.strictEqual(count('ffp_notify_msg3(ffp, FFP_MSG_ERROR'),2);
    assert.strictEqual(count('if (ret < 0) goto fail;'),1);
    assert(first.includes('"max-input-size", opaque->ntv_input.requested'));
    assert(fs.readFileSync(path.join(root,'ijkmedia/ijkplayer/ntv_codec_input.h'),'utf8')
        ===fs.readFileSync(path.join(__dirname,'ntv_codec_input.h'),'utf8'));
    fs.writeFileSync(target,fixture.replace('        time_stamp = d->pkt_temp.pts;','        time_stamp = changed;'));
    assert.throws(()=>apply(root),/Unexpected complete-input hook/);
    console.log('PASS both feeders, damaged-packet resync, bounded error path, header deployment, repeat application, unknown-source rejection');
} finally { fs.rmSync(root,{recursive:true,force:true}); }
