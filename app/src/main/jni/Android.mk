#
# Copyright (c) 2014 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

WORKING_DIR := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PATH := $(WORKING_DIR)/../third_party/lame/libmp3lame
LOCAL_MODULE    := lame
LOCAL_C_INCLUDES := $(WORKING_DIR)/../third_party/lame/libmp3lame \
                    $(WORKING_DIR)/../third_party/lame/include
LOCAL_CFLAGS := -DSTDC_HEADERS -std=c99
LOCAL_ARM_MODE := arm
APP_OPTIM := release

LOCAL_SRC_FILES := VbrTag.c \
                   bitstream.c \
                   encoder.c \
                   fft.c \
                   gain_analysis.c \
                   id3tag.c \
                   lame.c \
                   newmdct.c \
                   presets.c \
                   psymodel.c \
                   quantize.c \
                   quantize_pvt.c \
                   reservoir.c \
                   set_get.c \
                   tables.c \
                   takehiro.c \
                   util.c \
                   vbrquantize.c \
                   version.c

include $(BUILD_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH := $(WORKING_DIR)
LOCAL_MODULE    := ffmpeg
LOCAL_CFLAGS := -DHAVE_AV_CONFIG_H -std=c99 -D__STDC_CONSTANT_MACROS -DSTDC_HEADERS
LOCAL_SRC_FILES := ffmpeg-jni.c
LOCAL_C_INCLUDES := $(WORKING_DIR)/../third_party/include
LOCAL_STATIC_LIBRARIES := lame
LOCAL_LDLIBS := -llog -lm -lz $(WORKING_DIR)/../third_party/lib/libavformat.a $(WORKING_DIR)/../third_party/lib/libavcodec.a $(WORKING_DIR)/../third_party/lib/libavfilter.a $(WORKING_DIR)/../third_party/lib/libavresample.a $(WORKING_DIR)/../third_party/lib/libswscale.a $(WORKING_DIR)/../third_party/lib/libavutil.a $(WORKING_DIR)/../third_party/lib/libx264.a $(WORKING_DIR)/../third_party/lib/libpostproc.a $(WORKING_DIR)/../third_party/lib/libswresample.a $(WORKING_DIR)/../third_party/lib/libfdk-aac.a
APP_OPTIM := release
include $(BUILD_SHARED_LIBRARY)
