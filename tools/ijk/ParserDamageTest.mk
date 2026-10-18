LOCAL_PATH := $(call my-dir)
NTV_FFMPEG_INCLUDES := $(LOCAL_PATH)/../../.codex-tmp/ijkplayer-0.8.8/android/contrib/build/ffmpeg-arm64/output/include
include $(CLEAR_VARS)
LOCAL_MODULE := ijkffmpeg
LOCAL_SRC_FILES := ../../app/src/main/libs/$(TARGET_ARCH_ABI)/libijkffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)
include $(CLEAR_VARS)
LOCAL_MODULE := parser-damage-test
LOCAL_SRC_FILES := test-parser-damage.c
LOCAL_C_INCLUDES := $(NTV_FFMPEG_INCLUDES)
LOCAL_SHARED_LIBRARIES := ijkffmpeg
LOCAL_CFLAGS := -std=c99 -fPIE
LOCAL_LDFLAGS := -fPIE -pie
include $(BUILD_EXECUTABLE)
