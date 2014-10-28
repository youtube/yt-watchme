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

package com.google.android.apps.watchme;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         FFmpeg class which loads ffmpeg library and exposes its methods.
 */
public class Ffmpeg {


    static {
        System.loadLibrary("ffmpeg");
    }

    public static native boolean init(int width, int height, int audio_sample_rate, String rtmpUrl);

    public static native void shutdown();

    // Returns the size of the encoded frame.
    public static native int encodeVideoFrame(byte[] yuv_image);

    public static native int encodeAudioFrame(short[] audio_data, int length);
}
