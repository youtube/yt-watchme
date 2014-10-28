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

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.Surface;


public class VideoStreamingConnection implements VideoStreamingInterface {
    // CONSTANTS.
    private static final int AUDIO_SAMPLE_RATE = 44100;

    // Member variables.
    private VideoFrameGrabber videoFrameGrabber;
    private AudioFrameGrabber audioFrameGrabber;
    private Object frame_mutex = new Object();
    private boolean encoding;

    @Override
    public void open(String url, Camera camera, Surface previewSurface) {
        Log.d(MainActivity.APP_NAME, "open");

        videoFrameGrabber = new VideoFrameGrabber();
        videoFrameGrabber.setFrameCallback(new VideoFrameGrabber.FrameCallback() {
            @Override
            public void handleFrame(byte[] yuv_image) {
                if (encoding) {
                    synchronized (frame_mutex) {
                        int encoded_size = Ffmpeg.encodeVideoFrame(yuv_image);

                        // Logging.Verbose("Encoded video! Size = " + encoded_size);
                    }
                }
            }
        });

        audioFrameGrabber = new AudioFrameGrabber();
        audioFrameGrabber.setFrameCallback(new AudioFrameGrabber.FrameCallback() {
            @Override
            public void handleFrame(short[] audioData, int length) {
                if (encoding) {
                    synchronized (frame_mutex) {
                        int encoded_size = Ffmpeg.encodeAudioFrame(audioData, length);

                        // Logging.Verbose("Encoded audio! Size = " + encoded_size);
                    }
                }
            }
        });

        synchronized (frame_mutex) {
            Size previewSize = videoFrameGrabber.start(camera);
            audioFrameGrabber.start(AUDIO_SAMPLE_RATE);

            int width = previewSize.width;
            int height = previewSize.height;
            encoding = Ffmpeg.init(width, height, AUDIO_SAMPLE_RATE, url);

            Log.i(MainActivity.APP_NAME, "Ffmpeg.init() returned " + encoding);
        }
    }

    @Override
    public void close() {
        Log.i(MainActivity.APP_NAME, "close");

        videoFrameGrabber.stop();
        audioFrameGrabber.stop();

        encoding = false;
        if (encoding) {
            Ffmpeg.shutdown();
        }
    }
}
