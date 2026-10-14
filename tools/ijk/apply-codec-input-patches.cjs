// LGPL-2.1-or-later. Keep failed JNI writes out of MediaCodec's unsigned sizes.
const fs=require('fs');
const path=require('path');
function apply(root) {
    function edit(file,before,after,count=1) {
        const target=path.join(root,file),source=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
        if(source.includes(after))return;
        if(source.split(before).length-1!==count)throw new Error('Unexpected codec input hook: '+file);
        fs.writeFileSync(target,source.split(before).join(after));
    }
    const feed='ijkmedia/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c';
    edit(feed,'            if (!copy_size) {','            if (copy_size <= 0) {',2);
    const file='ijkmedia/ijksdl/android/ijksdl_codec_android_mediacodec_java.c';
    const target=path.join(root,file);
    let source=fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n');
    const guarded=`        // A failed/non-direct buffer must never reach memcpy or queueInputBuffer.
        if (J4A_ExceptionCheck__catchAll(env) || !buf_ptr || !data || buf_size <= 0)
            goto fail;
        write_ret = size < (size_t)buf_size ? size : (size_t)buf_size;
        memcpy(buf_ptr, data, write_ret);`;
    if(!source.includes(guarded)) {
        const pattern=/        write_ret = (?:FFMIN\(\(int\)size, \(int\)buf_size\)|size < buf_size \? size : buf_size);\n        memcpy\(buf_ptr, data, write_ret\);/g;
        if([...source.matchAll(pattern)].length!==1)throw new Error('Unexpected JNI direct-buffer writer');
        source=source.replace(pattern,guarded);fs.writeFileSync(target,source);
    }
    const signature='sdl_amedia_status_t SDL_AMediaCodecJava_queueInputBuffer';
    const start=source.indexOf(signature);
    if(start<0)throw new Error('Missing JNI queue function');
    const before='    jobject android_media_codec = opaque->android_media_codec;';
    const after=`    // JNI accepts signed 32-bit offset/length/index. Do not truncate size_t.
    if (idx > 0x7fffffffU || offset < 0 || offset > 0x7fffffffLL ||
        size > 0x7fffffffU || (uint64_t)offset + size > 0x7fffffffU)
        return SDL_AMEDIA_ERROR_UNKNOWN;
    jobject android_media_codec = opaque->android_media_codec;`;
    const tail=source.slice(start);
    if(!tail.includes(after)) {
        const pos=tail.indexOf(before);
        if(pos<0)throw new Error('Missing JNI queue arguments');
        source=source.slice(0,start)+tail.slice(0,pos)+after+tail.slice(pos+before.length);
        fs.writeFileSync(target,source);
    }
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
