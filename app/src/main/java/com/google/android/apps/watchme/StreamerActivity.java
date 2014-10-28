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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ToggleButton;

import com.google.android.apps.watchme.util.Utils;
import com.google.android.apps.watchme.util.YouTubeApi;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         StreamerActivity class which previews the camera and streams via StreamerService.
 */
public class StreamerActivity extends Activity {
    // CONSTANTS
    // TODO: Stop hardcoding this and read values from the camera's supported sizes.
    public static final int CAMERA_WIDTH = 640;
    public static final int CAMERA_HEIGHT = 480;

    // Member variables
    private StreamerService streamerService;
    private ServiceConnection streamerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(MainActivity.APP_NAME, "onServiceConnected");

            streamerService = ((StreamerService.LocalBinder) service).getService();

            restoreStateFromService();
            startStreaming();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.e(MainActivity.APP_NAME, "onServiceDisconnected");

            // This should never happen, because our service runs in the same process.
            streamerService = null;
        }
    };
    private PowerManager.WakeLock wakeLock;
    private Preview preview;
    private String rtmpUrl;
    private String broadcastId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(MainActivity.APP_NAME, "onCreate");
        super.onCreate(savedInstanceState);

        broadcastId = getIntent().getStringExtra(YouTubeApi.BROADCAST_ID_KEY);
        //Log.v(MainActivity.APP_NAME, broadcastId);

        rtmpUrl = getIntent().getStringExtra(YouTubeApi.RTMP_URL_KEY);

        if (rtmpUrl == null) {
            Log.w(MainActivity.APP_NAME, "No RTMP URL was passed in; bailing.");
            finish();
        }
        Log.i(MainActivity.APP_NAME, String.format("Got RTMP URL '%s' from calling activity.", rtmpUrl));

        setContentView(R.layout.streamer);
        preview = (Preview) findViewById(R.id.surfaceViewPreview);

        if (!bindService(new Intent(this, StreamerService.class), streamerConnection,
                BIND_AUTO_CREATE | BIND_DEBUG_UNBIND)) {
            Log.e(MainActivity.APP_NAME, "Failed to bind StreamerService!");
        }

        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleBroadcasting);
        toggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleButton.isChecked()) {
                    streamerService.startStreaming(rtmpUrl);
                } else {
                    streamerService.stopStreaming();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        Log.d(MainActivity.APP_NAME, "onResume");

        super.onResume();

        if (streamerService != null) {
            restoreStateFromService();
        }
    }

    @Override
    protected void onPause() {
        Log.d(MainActivity.APP_NAME, "onPause");

        super.onPause();

        if (preview != null) {
            preview.setCamera(null);
        }

        if (streamerService != null) {
            streamerService.releaseCamera();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(MainActivity.APP_NAME, "onDestroy");

        super.onDestroy();

        if (streamerConnection != null) {
            unbindService(streamerConnection);
        }

        stopStreaming();

        if (streamerService != null) {
            streamerService.releaseCamera();
        }
    }

    private void restoreStateFromService() {
        preview.setCamera(Utils.getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT));
    }

    private void startStreaming() {
        Log.d(MainActivity.APP_NAME, "startStreaming");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, this.getClass().getName());
        wakeLock.acquire();

        if (!streamerService.isStreaming()) {
            streamerService.startStreaming(rtmpUrl);
        }
    }

    private void stopStreaming() {
        Log.d(MainActivity.APP_NAME, "stopStreaming");

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        if (streamerService.isStreaming()) {
            streamerService.stopStreaming();
        }
    }

    public void endEvent(View view) {
        Intent data = new Intent();
        data.putExtra(YouTubeApi.BROADCAST_ID_KEY, broadcastId);
        if (getParent() == null) {
            setResult(Activity.RESULT_OK, data);
        } else {
            getParent().setResult(Activity.RESULT_OK, data);
        }
        finish();
    }

}
