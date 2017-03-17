package com.hzitoun.camera2SecretPictureTaker.services;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import com.hzitoun.camera2SecretPictureTaker.listeners.OnPictureCapturedListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

/**
 * The aim of this service is to secretly take pictures (without preview or opening device's camera app)
 * from all available cameras.
 * @author hzitoun (zitoun.hamed@gmail.com)
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP) //camera 2 api was added in API level 21
public class PictureService {

    private static final String TAG = PictureService.class.getSimpleName();
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Activity context;
    private WindowManager windowManager;
    private CameraManager manager;
    private TreeMap<String, byte[]> picturesTaken;
    private OnPictureCapturedListener capturedListener;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private String currentCameraId;
    private Queue<String> cameraIds;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public void startCapturing(final Activity activity,
                               final OnPictureCapturedListener capturedListener) {
        this.picturesTaken = new TreeMap<>();
        this.context = activity;
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.windowManager = context.getWindowManager();
        this.capturedListener = capturedListener;
        this.cameraIds = new LinkedList<>();
        try {
            final String[] cameraIdList = manager.getCameraIdList();
            if (cameraIdList != null && cameraIdList.length > 0) {
                for (final String cameraId : cameraIdList) {
                    this.cameraIds.add(cameraId);
                }
                this.currentCameraId = this.cameraIds.poll();
                openCameraAndTakePicture();
            } else {
                capturedListener.onDoneCapturingAllPhotos(picturesTaken);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Exception occurred while accessing the list of cameras", e);
        }
    }

    private void openCameraAndTakePicture() {
        startBackgroundThread();
        Log.d(TAG, "opening camera " + currentCameraId);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(currentCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, " exception occurred while opening camera " + currentCameraId, e);
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "camera " + camera.getId() + " opened");
            cameraDevice = camera;
            Log.i(TAG, "Taking picture from camera " + camera.getId());
            takePicture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, " camera " + camera.getId() + " disconnected");
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.d(TAG, "camera " + camera.getId() + " closed");
            stopBackgroundThread();
            if (!cameraIds.isEmpty()) {
                new Handler().postDelayed(() ->
                                takeAnotherPicture()
                        , 100);
            } else {
                capturedListener.onDoneCapturingAllPhotos(picturesTaken);
            }
        }


        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "camera in error, int code " + error);
            if (cameraDevice != null) {
                cameraDevice.close();
                return;
            }
        }
    };


    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                if (characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) != null) {
                    jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.JPEG);
                }
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            final List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            final int rotation = this.windowManager.getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            ImageReader.OnImageAvailableListener readerListener = (ImageReader readerL) -> {
                final Image image = readerL.acquireLatestImage();
                final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                final byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                saveImageToDisk(bytes);
                if (image != null) {
                    image.close();
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                       Log.e(TAG, " exception occurred while accessing " + currentCameraId, e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
           Log.e(TAG, " exception occurred while accessing " + currentCameraId, e);
        }
    }

    private void saveImageToDisk(final byte[] bytes) {
        final File file = new File(Environment.getExternalStorageDirectory() + "/" + this.cameraDevice.getId() + "_pic.jpg");
        try (final OutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            this.picturesTaken.put(file.getPath(), bytes);
        } catch (IOException e) {
            Log.e(TAG, "Exception occurred while saving picture to external storage ", e);
        }
    }


    private void startBackgroundThread() {
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("Camera Background" + currentCameraId);
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "exception occurred while stoping BackgroundThread ", e);
        }
    }


    private void takeAnotherPicture() {
        startBackgroundThread();
        this.currentCameraId = this.cameraIds.poll();
        openCameraAndTakePicture();
    }

    private void closeCamera() {
        Log.d(TAG, "closing camera " + cameraDevice.getId());
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }


    final private CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (picturesTaken.lastEntry() != null) {
                capturedListener.onCaptureDone(picturesTaken.lastEntry().getKey(), picturesTaken.lastEntry().getValue());
                Log.i(TAG, "done taking picture from camera " + cameraDevice.getId());
            }
            closeCamera();
        }
    };
}
