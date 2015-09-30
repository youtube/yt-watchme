/*
 * Copyright (c) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

#include <android/log.h>
#include <string.h>
#include <jni.h>

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/opt.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean
        JNICALL Java_com_google_android_apps_watchme_Ffmpeg_init(JNIEnv *env, jobject thiz,
                                                                 jint width, jint height,
                                                                 jint audio_sample_rate,
                                                                 jstring rtmp_url);
        JNIEXPORT void JNICALL Java_com_google_android_apps_watchme_Ffmpeg_shutdown(JNIEnv
*env,
jobject thiz
);
JNIEXPORT jintJNICALL
Java_com_google_android_apps_watchme_Ffmpeg_encodeVideoFrame(JNIEnv
*env,
jobject thiz,
        jbyteArray
yuv_image);
JNIEXPORT jint
        JNICALL Java_com_google_android_apps_watchme_Ffmpeg_encodeAudioFrame(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jshortArray audio_data,
                                                                             jint length);

#ifdef __cplusplus
}
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ffmpeg-jni", __VA_ARGS__)
#define URL_WRONLY 2
        static AVFormatContext *fmt_context;
        static AVStream *video_stream;
        static AVStream *audio_stream;

        static int pts
= 0;
static int last_audio_pts = 0;

// Buffers for UV format conversion
static unsigned char *u_buf;
static unsigned char *v_buf;

static int enable_audio = 1;
static int64_t audio_samples_written = 0;
static int audio_sample_rate = 0;

// Stupid buffer for audio samples. Not even a proper ring buffer
#define AUDIO_MAX_BUF_SIZE 16384  // 2x what we get from Java
static short audio_buf[AUDIO_MAX_BUF_SIZE];
static int audio_buf_size = 0;

void AudioBuffer_Push(const short *audio, int num_samples) {
    if (audio_buf_size >= AUDIO_MAX_BUF_SIZE - num_samples) {
        LOGI("AUDIO BUFFER OVERFLOW: %i + %i > %i", audio_buf_size, num_samples,
             AUDIO_MAX_BUF_SIZE);
        return;
    }
    for (int i = 0; i < num_samples; i++) {
        audio_buf[audio_buf_size++] = audio[i];
    }
}

int AudioBuffer_Size() { return audio_buf_size; }

short *AudioBuffer_Get() { return audio_buf; }

void AudioBuffer_Pop(int num_samples) {
    if (num_samples > audio_buf_size) {
        LOGI("Audio buffer Pop WTF: %i vs %i", num_samples, audio_buf_size);
        return;
    }
    memmove(audio_buf, audio_buf + num_samples, num_samples * sizeof(short));
    audio_buf_size -= num_samples;
}

void AudioBuffer_Clear() {
    memset(audio_buf, 0, sizeof(audio_buf));
    audio_buf_size = 0;
}

static void log_callback(void *ptr, int level, const char *fmt, va_list vl) {
    char x[2048];
    vsnprintf(x, 2048, fmt, vl);
    LOGI(x);
}

JNIEXPORT jboolean
        JNICALL Java_com_google_android_apps_watchme_Ffmpeg_init(JNIEnv *env, jobject thiz,
                                                                 jint width, jint height,
                                                                 jint audio_sample_rate_param,
                                                                 jstring rtmp_url) {
    avcodec_register_all();
    av_register_all();
    av_log_set_callback(log_callback);

    fmt_context = avformat_alloc_context();
    AVOutputFormat *ofmt = av_guess_format("flv", NULL, NULL);
    if (ofmt) {
        LOGI("av_guess_format returned %s", ofmt->long_name);
    } else {
        LOGI("av_guess_format fail");
        return JNI_FALSE;
    }

    fmt_context->oformat = ofmt;
    LOGI("creating video stream");
    video_stream = av_new_stream(fmt_context, 0);

    if (enable_audio) {
        LOGI("creating audio stream");
        audio_stream = av_new_stream(fmt_context, 1);
    }

    // Open Video Codec.
    // ======================
    AVCodec *video_codec = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!video_codec) {
        LOGI("Did not find the video codec");
        return JNI_FALSE;  // leak!
    } else {
        LOGI("Video codec found!");
    }
    AVCodecContext *video_codec_ctx = video_stream->codec;
    video_codec_ctx->codec_id = video_codec->id;
    video_codec_ctx->codec_type = AVMEDIA_TYPE_VIDEO;
    video_codec_ctx->level = 31;

    video_codec_ctx->width = width;
    video_codec_ctx->height = height;
    video_codec_ctx->pix_fmt = PIX_FMT_YUV420P;
    video_codec_ctx->rc_max_rate = 0;
    video_codec_ctx->rc_buffer_size = 0;
    video_codec_ctx->gop_size = 12;
    video_codec_ctx->max_b_frames = 0;
    video_codec_ctx->slices = 8;
    video_codec_ctx->b_frame_strategy = 1;
    video_codec_ctx->coder_type = 0;
    video_codec_ctx->me_cmp = 1;
    video_codec_ctx->me_range = 16;
    video_codec_ctx->qmin = 10;
    video_codec_ctx->qmax = 51;
    video_codec_ctx->keyint_min = 25;
    video_codec_ctx->refs = 3;
    video_codec_ctx->trellis = 0;
    video_codec_ctx->scenechange_threshold = 40;
    video_codec_ctx->flags |= CODEC_FLAG_LOOP_FILTER;
    video_codec_ctx->me_method = ME_HEX;
    video_codec_ctx->me_subpel_quality = 6;
    video_codec_ctx->i_quant_factor = 0.71;
    video_codec_ctx->qcompress = 0.6;
    video_codec_ctx->max_qdiff = 4;
    video_codec_ctx->time_base.den = 10;
    video_codec_ctx->time_base.num = 1;
    video_codec_ctx->bit_rate = 3200 * 1000;
    video_codec_ctx->bit_rate_tolerance = 0;
    video_codec_ctx->flags2 |= 0x00000100;

    fmt_context->bit_rate = 4000 * 1000;

    av_opt_set(video_codec_ctx, "partitions", "i8x8,i4x4,p8x8,b8x8", 0);
    av_opt_set_int(video_codec_ctx, "direct-pred", 1, 0);
    av_opt_set_int(video_codec_ctx, "rc-lookahead", 0, 0);
    av_opt_set_int(video_codec_ctx, "fast-pskip", 1, 0);
    av_opt_set_int(video_codec_ctx, "mixed-refs", 1, 0);
    av_opt_set_int(video_codec_ctx, "8x8dct", 0, 0);
    av_opt_set_int(video_codec_ctx, "weightb", 0, 0);

    if (fmt_context->oformat->flags & AVFMT_GLOBALHEADER)
        video_codec_ctx->flags |= CODEC_FLAG_GLOBAL_HEADER;

    LOGI("Opening video codec");
    AVDictionary *vopts = NULL;
    av_dict_set(&vopts, "profile", "main", 0);
    //av_dict_set(&vopts, "vprofile", "main", 0);
    av_dict_set(&vopts, "rc-lookahead", 0, 0);
    av_dict_set(&vopts, "tune", "film", 0);
    av_dict_set(&vopts, "preset", "ultrafast", 0);
    av_opt_set(video_codec_ctx->priv_data, "tune", "film", 0);
    av_opt_set(video_codec_ctx->priv_data, "preset", "ultrafast", 0);
    av_opt_set(video_codec_ctx->priv_data, "tune", "film", 0);
    int open_res = avcodec_open2(video_codec_ctx, video_codec, &vopts);
    if (open_res < 0) {
        LOGI("Error opening video codec: %i", open_res);
        return JNI_FALSE;   // leak!
    }

    // Open Audio Codec.
    // ======================

    if (enable_audio) {
        AudioBuffer_Clear();
        audio_sample_rate = audio_sample_rate_param;
        AVCodec *audio_codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
        if (!audio_codec) {
            LOGI("Did not find the audio codec");
            return JNI_FALSE;  // leak!
        } else {
            LOGI("Audio codec found!");
        }
        AVCodecContext *audio_codec_ctx = audio_stream->codec;
        audio_codec_ctx->codec_id = audio_codec->id;
        audio_codec_ctx->codec_type = AVMEDIA_TYPE_AUDIO;
        audio_codec_ctx->bit_rate = 128000;
        audio_codec_ctx->bit_rate_tolerance = 16000;
        audio_codec_ctx->channels = 1;
        audio_codec_ctx->profile = FF_PROFILE_AAC_LOW;
        audio_codec_ctx->sample_fmt = AV_SAMPLE_FMT_FLT;
        audio_codec_ctx->sample_rate = 44100;

        LOGI("Opening audio codec");
        AVDictionary *opts = NULL;
        av_dict_set(&opts, "strict", "experimental", 0);
        open_res = avcodec_open2(audio_codec_ctx, audio_codec, &opts);
        LOGI("audio frame size: %i", audio_codec_ctx->frame_size);

        if (open_res < 0) {
            LOGI("Error opening audio codec: %i", open_res);
            return JNI_FALSE;   // leak!
        }
    }

    const jbyte *url = (*env)->GetStringUTFChars(env, rtmp_url, NULL);

    // Point to an output file
    if (!(ofmt->flags & AVFMT_NOFILE)) {
        if (avio_open(&fmt_context->pb, url, URL_WRONLY) < 0) {
            LOGI("ERROR: Could not open file %s", url);
            return JNI_FALSE;  // leak!
        }
    }
    (*env)->ReleaseStringUTFChars(env, rtmp_url, url);

    LOGI("Writing output header.");
    // Write file header
    if (avformat_write_header(fmt_context, NULL) != 0) {
        LOGI("ERROR: av_write_header failed");
        return JNI_FALSE;
    }

    pts = 0;
    last_audio_pts = 0;
    audio_samples_written = 0;

    // Initialize buffers for UV format conversion
    int frame_size = video_codec_ctx->width * video_codec_ctx->height;
    u_buf = (unsigned char *) av_malloc(frame_size / 4);
    v_buf = (unsigned char *) av_malloc(frame_size / 4);

    LOGI("ffmpeg encoding init done");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_google_android_apps_watchme_Ffmpeg_shutdown(JNIEnv
*env,
jobject thiz
) {
av_write_trailer(fmt_context);
avio_close(fmt_context
->pb);
avcodec_close(video_stream
->codec);
if (enable_audio) {
avcodec_close(audio_stream
->codec);
}
av_free(fmt_context);
av_free(u_buf);
av_free(v_buf);

fmt_context = NULL;
u_buf = NULL;
v_buf = NULL;
}

JNIEXPORT jintJNICALL
Java_com_google_android_apps_watchme_Ffmpeg_encodeVideoFrame(JNIEnv
*env,
jobject thiz,
        jbyteArray
yuv_image) {
int yuv_length = (*env)->GetArrayLength(env, yuv_image);
unsigned char *yuv_data = (*env)->GetByteArrayElements(env, yuv_image, 0);

AVCodecContext *video_codec_ctx = video_stream->codec;
//LOGI("Yuv size: %i w: %i h: %i", yuv_length, video_codec_ctx->width, video_codec_ctx->height);

int frame_size = video_codec_ctx->width * video_codec_ctx->height;

const unsigned char *uv = yuv_data + frame_size;

// Convert YUV from NV12 to I420. Y channel is the same so we don't touch it,
// we just have to deinterleave UV.
for (
int i = 0;
i < frame_size / 4; i++) {
v_buf[i] = uv[i * 2];
u_buf[i] = uv[i * 2 + 1];
}

AVFrame source;
memset(&source, 0, sizeof(AVFrame));
source.data[0] =
yuv_data;
source.data[1] =
u_buf;
source.data[2] =
v_buf;
source.linesize[0] = video_codec_ctx->
width;
source.linesize[1] = video_codec_ctx->width / 2;
source.linesize[2] = video_codec_ctx->width / 2;

// only for bitrate regulation. irrelevant for sync.
source.
pts = pts;
pts++;

int out_length = frame_size + (frame_size / 2);
unsigned char *out = (unsigned char *) av_malloc(out_length);
int compressed_length = avcodec_encode_video(video_codec_ctx, out, out_length, &source);

(*env)->
ReleaseByteArrayElements(env, yuv_image, yuv_data,
0);

// Write to file too
if (compressed_length > 0) {
AVPacket pkt;
av_init_packet(&pkt);
pkt.
pts = last_audio_pts;
if (video_codec_ctx->coded_frame && video_codec_ctx->coded_frame->key_frame) {
pkt.flags |= 0x0001;
}
pkt.
stream_index = video_stream->index;
pkt.
data = out;
pkt.
size = compressed_length;
if (
av_interleaved_write_frame(fmt_context,
&pkt) != 0) {
LOGI("Error writing video frame");
}
} else {
LOGI("??? compressed_length <= 0");
}

last_audio_pts++;

av_free(out);
return
compressed_length;
}

JNIEXPORT jintJNICALL
Java_com_google_android_apps_watchme_Ffmpeg_encodeAudioFrame(JNIEnv
*env,
jobject thiz,
        jshortArray
audio_data,
jint length
) {
if (!enable_audio) {
return 0;
}

short *audio = (*env)->GetShortArrayElements(env, audio_data, 0);
//LOGI("java audio buffer size: %i", length);

AVCodecContext *audio_codec_ctx = audio_stream->codec;

unsigned char *out = av_malloc(128000);

AudioBuffer_Push(audio, length
);

int total_compressed = 0;
while (

AudioBuffer_Size()

>= audio_codec_ctx->frame_size) {
AVPacket pkt;
av_init_packet(&pkt);

int compressed_length = avcodec_encode_audio(audio_codec_ctx, out, 128000,
                                             AudioBuffer_Get());

total_compressed +=
compressed_length;
audio_samples_written += audio_codec_ctx->
frame_size;

int new_pts = (audio_samples_written * 1000) / audio_sample_rate;
if (compressed_length > 0) {
pkt.
size = compressed_length;
pkt.
pts = new_pts;
last_audio_pts = new_pts;
//LOGI("audio_samples_written: %i  comp_length: %i   pts: %i", (int)audio_samples_written, (int)compressed_length, (int)new_pts);
pkt.flags |= 0x0001;
pkt.
stream_index = audio_stream->index;
pkt.
data = out;
if (
av_interleaved_write_frame(fmt_context,
&pkt) != 0) {
LOGI("Error writing audio frame");
}
}
AudioBuffer_Pop(audio_codec_ctx
->frame_size);
}

(*env)->
ReleaseShortArrayElements(env, audio_data, audio,
0);

av_free(out);
return
total_compressed;
}
