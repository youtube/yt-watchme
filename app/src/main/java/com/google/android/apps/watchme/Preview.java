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

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.watchme.util.Utils;

import java.io.IOException;
import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Preview class which previews the camera.
 */
class Preview extends ViewGroup implements SurfaceHolder.Callback {
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Size previewSize;
    List<Size> supportedPreviewSizes;
    Camera camera;

    public Preview(Context context, AttributeSet attributes) {
        super(context, attributes);
        surfaceView = new SurfaceView(context);
        addView(surfaceView);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera camera) {
        this.camera = camera;

        if (camera != null) {
            supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            for (Size s : supportedPreviewSizes) {
                Log.d(MainActivity.APP_NAME, String.format("Supported size: %dw x %dh", s.width, s.height));
            }
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // TODO: This shouldn't be hardcoded.
        final int width = StreamerActivity.CAMERA_WIDTH; // = resolveSize(getSuggestedMinimumWidth(), StreamerActivity.CAMERA_WIDTH);
        final int height = StreamerActivity.CAMERA_HEIGHT; // = resolveSize(getSuggestedMinimumHeight(), StreamerActivity.CAMERA_HEIGHT);

        setMeasuredDimension(width, height);

        if (supportedPreviewSizes != null) {
            previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (previewSize != null) {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2, width,
                        (height + scaledChildHeight) / 2);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            setCamera(Utils.getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT));
            if (camera != null) {
                camera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(MainActivity.APP_NAME, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (camera != null) {
            try {
                camera.setPreviewDisplay(null);
            } catch (IOException e) {
                Log.e(MainActivity.APP_NAME, "Caught IOException", e);
            }
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null)
            return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (camera != null) {
            // Now that the size is known, set up the camera parameters and begin
            // the preview.
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(StreamerActivity.CAMERA_WIDTH, StreamerActivity.CAMERA_HEIGHT);
            requestLayout();

            camera.setParameters(parameters);
            try {
                camera.setPreviewDisplay(holder);
            } catch (IOException e) {
                Log.e(MainActivity.APP_NAME, "", e);
            }
            camera.startPreview();
        }
    }
}