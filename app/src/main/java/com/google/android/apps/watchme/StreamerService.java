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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.google.android.apps.watchme.util.Utils;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         StreamerService class which streams the video from camera.
 */
public class StreamerService extends Service {
    private static int STREAMER_NOTIFICATION_ID = 1001;
    private final IBinder binder = new LocalBinder();
    // Member variables.
    private VideoStreamingConnection connection;
    private Camera camera;

    @Override
    public void onCreate() {
        Log.d(MainActivity.APP_NAME, "onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(MainActivity.APP_NAME, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        camera = Utils.getCamera(CameraInfo.CAMERA_FACING_FRONT);
        Log.d(MainActivity.APP_NAME, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(MainActivity.APP_NAME, "onUnbind");

        return false;
    }

    public void startStreaming(String streamUrl) {
        Log.d(MainActivity.APP_NAME, "startStreaming");
        showForegroundNotification();
        connection = new VideoStreamingConnection();
        // TODO Pass an actual preview surface.
        connection.open(streamUrl, camera, null);
    }

    public void stopStreaming() {
        Log.d(MainActivity.APP_NAME, "stopStreaming");

        if (connection != null) {
            connection.close();
        }
        connection = null;
        stopForeground(true);
    }

    public boolean isStreaming() {
        return (connection != null);
    }

    public void releaseCamera() {
        Log.d(MainActivity.APP_NAME, "releaseCamera");
        if (!isStreaming() && camera != null) {
            Utils.releaseCamera();
            Log.d(MainActivity.APP_NAME, "Camera was released.");
            camera = null;
        } else {
            Log.d(MainActivity.APP_NAME, "Camera was not released.");
        }
    }

    private void showForegroundNotification() {
        final NotificationManager notifyManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Intent to call our activity from background.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        // The PendingIntent to launch our activity if the user selects this notification.
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle(getText(R.string.activeStreamingLabel))
                .setContentText(getText(R.string.activeStreamingStatus))
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .build();

        notifyManager.notify(STREAMER_NOTIFICATION_ID, notification);
    }

    public class LocalBinder extends Binder {
        StreamerService getService() {
            return StreamerService.this;
        }
    }

}
