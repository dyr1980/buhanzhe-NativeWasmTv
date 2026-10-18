LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := codec-input-test
LOCAL_SRC_FILES := test-codec-input.c
LOCAL_CFLAGS := -std=c99 -Wall -Werror -fPIE
LOCAL_LDFLAGS := -fPIE -pie
include $(BUILD_EXECUTABLE)
