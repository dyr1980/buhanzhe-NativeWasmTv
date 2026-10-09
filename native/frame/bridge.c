#include <jni.h>
#include <android/bitmap.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <string.h>

JNIEXPORT jboolean JNICALL
Java_xiao_bu_tv_NativeVideoFrame_initialize(JNIEnv *env, jclass type) {
    // AVCodecContext/AVFrame must match the FFmpeg already shipped by IJK.
    if (avcodec_version() != LIBAVCODEC_VERSION_INT) return JNI_FALSE;
    av_register_all();
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_xiao_bu_tv_NativeVideoFrame_decode(JNIEnv *env, jclass type, jstring filename,
        jint max_width, jint max_height, jboolean preview) {
    const char *path = NULL;
    AVFormatContext *input = NULL;
    AVCodecContext *decoder = NULL;
    AVFrame *frame = NULL;
    struct SwsContext *scaler = NULL;
    AVPacket packet;
    jobject bitmap = NULL;
    void *pixels = NULL;
    uint8_t *rgba = NULL;
    int video = -1, got = 0, result = -1;
    av_init_packet(&packet); packet.data = NULL; packet.size = 0;
    path = (*env)->GetStringUTFChars(env, filename, NULL);
    if (!path || avformat_open_input(&input, path, NULL, NULL) < 0) goto cleanup;
    // The local MP4 contains one sync frame; its moov already describes the track.
    // No stream-info probing, network, audio decoding or full GOP is needed.
    for (unsigned i = 0; i < input->nb_streams; i++) {
        if (input->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) { video = i; break; }
    }
    if (video < 0) goto cleanup;
    AVCodec *codec = avcodec_find_decoder(input->streams[video]->codecpar->codec_id);
    if (!codec) goto cleanup;
    decoder = avcodec_alloc_context3(codec);
    if (!decoder || avcodec_parameters_to_context(decoder, input->streams[video]->codecpar) < 0)
        goto cleanup;
    decoder->thread_count = 1;
    decoder->thread_type = FF_THREAD_SLICE;
    if (preview) {
        decoder->skip_loop_filter = AVDISCARD_ALL;
        decoder->flags2 |= AV_CODEC_FLAG2_FAST;
    }
    if (avcodec_open2(decoder, codec, NULL) < 0) goto cleanup;
    frame = av_frame_alloc();
    if (!frame) goto cleanup;
    for (int i = 0; i < 16 && !got; i++) {
        if (av_read_frame(input, &packet) < 0) break;
        if (packet.stream_index == video) result = avcodec_decode_video2(decoder, frame, &got, &packet);
        av_packet_unref(&packet);
        if (result < 0) goto cleanup;
    }
    for (int i = 0; i < 16 && !got; i++) {
        packet.data = NULL; packet.size = 0;
        if (avcodec_decode_video2(decoder, frame, &got, &packet) < 0) goto cleanup;
    }
    if (!got || frame->width < 1 || frame->height < 1
            || (int64_t)frame->width * frame->height > 16 * 1024 * 1024) goto cleanup;
    result = -1;
    int width = frame->width, height = frame->height;
    if (preview && max_width > 0 && max_height > 0) {
        double scale = FFMIN(1.0, FFMIN((double)max_width / width, (double)max_height / height));
        width = FFMAX(1, (int)(width * scale + 0.5));
        height = FFMAX(1, (int)(height * scale + 0.5));
    }
    jclass bitmap_class = (*env)->FindClass(env, "android/graphics/Bitmap");
    jclass config_class = (*env)->FindClass(env, "android/graphics/Bitmap$Config");
    if (!bitmap_class || !config_class) goto cleanup;
    jfieldID config_field = (*env)->GetStaticFieldID(env, config_class, "ARGB_8888",
            "Landroid/graphics/Bitmap$Config;");
    jmethodID create = (*env)->GetStaticMethodID(env, bitmap_class, "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!config_field || !create) goto cleanup;
    jobject config = (*env)->GetStaticObjectField(env, config_class, config_field);
    bitmap = (*env)->CallStaticObjectMethod(env, bitmap_class, create, width, height, config);
    if (!bitmap || (*env)->ExceptionCheck(env)) goto cleanup;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
            || AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
        goto cleanup;
    scaler = sws_getContext(frame->width, frame->height, frame->format,
            width, height, AV_PIX_FMT_RGBA, SWS_BILINEAR, NULL, NULL, NULL);
    if (!scaler) goto cleanup;
    int colorspace = frame->colorspace == AVCOL_SPC_BT709 ? SWS_CS_ITU709
            : frame->colorspace == AVCOL_SPC_SMPTE240M ? SWS_CS_SMPTE240M : SWS_CS_ITU601;
    const int *coefficients = sws_getCoefficients(colorspace);
    sws_setColorspaceDetails(scaler, coefficients, frame->color_range == AVCOL_RANGE_JPEG,
            coefficients, 1, 0, 1 << 16, 1 << 16);
    const uint8_t *source[4] = {frame->data[0], frame->data[1], frame->data[2], frame->data[3]};
    // Old Dalvik bitmaps may be only 8-byte aligned. IJK's ARM NEON colour
    // converter requires 16-byte aligned output; never write directly to them.
    int rgba_stride = (width * 4 + 31) & ~31;
    rgba = av_malloc((size_t)rgba_stride * height + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!rgba) goto cleanup;
    uint8_t *destination[4] = {rgba, NULL, NULL, NULL};
    int strides[4] = {rgba_stride, 0, 0, 0};
    result = sws_scale(scaler, source, frame->linesize, 0, frame->height, destination, strides);
    if (result != height) result = -1;
    else for (int row = 0; row < height; row++)
        memcpy((uint8_t *)pixels + (size_t)info.stride * row,
                rgba + (size_t)rgba_stride * row, (size_t)width * 4);
cleanup:
    if (pixels) AndroidBitmap_unlockPixels(env, bitmap);
    av_free(rgba);
    sws_freeContext(scaler);
    av_frame_free(&frame);
    av_packet_unref(&packet);
    avcodec_free_context(&decoder);
    avformat_close_input(&input);
    if (path) (*env)->ReleaseStringUTFChars(env, filename, path);
    if (bitmap && result <= 0 && !(*env)->ExceptionCheck(env)) {
        jclass bitmap_type = (*env)->GetObjectClass(env, bitmap);
        jmethodID recycle = (*env)->GetMethodID(env, bitmap_type, "recycle", "()V");
        if (recycle) (*env)->CallVoidMethod(env, bitmap, recycle);
    }
    return result > 0 && !(*env)->ExceptionCheck(env) ? bitmap : NULL;
}
