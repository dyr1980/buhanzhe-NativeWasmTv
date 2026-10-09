LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := ijkffmpeg
LOCAL_SRC_FILES := ../../app/src/main/libs/$(TARGET_ARCH_ABI)/libijkffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)
include $(CLEAR_VARS)
LOCAL_MODULE := ntvframe
LOCAL_SRC_FILES := bridge.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/vendor
LOCAL_CFLAGS := -O2 -std=c99 -fvisibility=hidden
LOCAL_SHARED_LIBRARIES := ijkffmpeg
LOCAL_LDLIBS := -ljnigraphics
include $(BUILD_SHARED_LIBRARY)
