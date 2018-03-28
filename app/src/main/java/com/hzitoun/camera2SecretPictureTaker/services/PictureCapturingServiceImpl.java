package com.hzitoun.camera2SecretPictureTaker.services;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
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
import android.view.Surface;

import com.hzitoun.camera2SecretPictureTaker.listeners.PictureCapturingListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;


/**
 * The aim of this service is to secretly take pictures (without preview or opening device's camera app)
 * from all available cameras using Android Camera 2 API
 *
 * @author hzitoun (zitoun.hamed@gmail.com)
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP) //NOTE: camera 2 api was added in API level 21
public class PictureCapturingServiceImpl extends APictureCapturingService {

    private static final String TAG = PictureCapturingServiceImpl.class.getSimpleName();
    private HandlerThread handlerThread;
    private Handler backgroundHandler;

    /***
     * camera ids queue.
     */
    private Queue<String> cameraIds;

    private String currentCameraId;
    /**
     * stores a sorted map of (pictureUrlOnDisk, PictureData).
     */
    private TreeMap<String, byte[]> picturesTaken;

    private PictureCapturingListener capturingListener;

    /***
     * private constructor, meant to force the use of {@link #getInstance}  method
     */
    private PictureCapturingServiceImpl(final Context context) {
        super(context);
    }

    /**
     * @param context the context used to get the app's context and the display manager
     * @return a new instance
     */
    @NonNull
    public static APictureCapturingService getInstance(@NonNull final Context context) {
        return new PictureCapturingServiceImpl(context);
    }

    /**
     * Starts pictures capturing treatment.
     *
     * @param listener picture capturing listener
     */
    @Override
    public void startCapturing(final PictureCapturingListener listener) {
        if (handlerThread != null) {
            // when last capture meet exceptions, release handlerThread first.
            handlerThread.quitSafely();
        }

        handlerThread = new HandlerThread("CameraHandlerThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        this.picturesTaken = new TreeMap<>();
        this.capturingListener = listener;
        this.cameraIds = new LinkedList<>();
        try {
            final String[] cameraIds = manager.getCameraIdList();
            if (cameraIds.length > 0) {
                this.cameraIds.addAll(Arrays.asList(cameraIds));
                openCamera(this.cameraIds.poll());
            } else {
                //No camera detected!
                capturingListener.onDoneCapturingAllPhotos(picturesTaken);
            }
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception occurred while accessing the list of cameras", e);
        }
    }

    private void openCamera(@NonNull String currentCameraId) {
        this.currentCameraId = currentCameraId;
        Log.d(TAG, "opening camera " + currentCameraId);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(currentCameraId, cameraDeviceStateCallback(), backgroundHandler);
            }
        } catch (final CameraAccessException e) {
            Log.e(TAG, " exception occurred while opening camera " + currentCameraId, e);
        }
    }

    private CameraDevice.StateCallback cameraDeviceStateCallback() {
        return new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.d(TAG, "camera " + camera.getId() + " opened");
                Log.i(TAG, "Taking picture from camera " + camera.getId());

                //Take the picture after some delay. It may resolve getting a black dark photo.
                int delayMillis = 0;// Change this if you do get a black photo.
                backgroundHandler.postDelayed(() -> {
                    try {
                        takePicture(camera);
                    } catch (final CameraAccessException e) {
                        Log.e(TAG, " exception occurred while taking picture from " + currentCameraId, e);
                    }
                }, delayMillis);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                // this device is no longer available, CameraAccessException
                Log.d(TAG, " camera " + camera + " disconnected");
            }

            @Override
            public void onClosed(@NonNull CameraDevice camera) {
                // this device is no longer available, IllegalStateException
                Log.d(TAG, "camera " + camera + " closed");
                //once the current camera has been closed, start taking another picture
                if (!cameraIds.isEmpty()) {
                    takeAnotherPicture();
                } else {
                    capturingListener.onDoneCapturingAllPhotos(picturesTaken);
                }
            }

            private void takeAnotherPicture() {
                openCamera(cameraIds.poll());
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Log.e(TAG, "camera in error, int code " + error);
                camera.close();
            }
        };
    }

    private void takePicture(@NonNull CameraDevice cameraDevice) throws CameraAccessException {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        Size[] jpegSizes = null;
        StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (streamConfigurationMap != null) {
            jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        }
        final boolean jpegSizesNotEmpty = jpegSizes != null && 0 < jpegSizes.length;
        int width = jpegSizesNotEmpty ? jpegSizes[0].getWidth() : 640;
        int height = jpegSizesNotEmpty ? jpegSizes[0].getHeight() : 480;
        final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        final List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(reader.getSurface());

        // The key to achieve non-preview capture:
        // 1. Output into the ImageReader's surface.
        // 2. target the ImageReader's surface, so that you can get Image form listener.
        cameraDevice.createCaptureSession(outputSurfaces, cameraCaptureSessionStateCallback(cameraDevice, reader), backgroundHandler);

        // So if you want to switch between preview and non-preview,
        // just replace the target and output with ImageReader's SurfaceView or anther available SurfaceView
    }

    @NonNull
    private CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback(
            @NonNull final CameraDevice cameraDevice, @NonNull final ImageReader reader) {
        return new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                try {
                    session.capture(request(), captureCallback(), backgroundHandler);
                } catch (final CameraAccessException e) {
                    Log.e(TAG, " exception occurred while accessing " + currentCameraId, e);
                }
            }

            private CaptureRequest request() throws CameraAccessException {
                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(reader.getSurface());
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                builder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation());
                reader.setOnImageAvailableListener(
                        (ImageReader imReader) -> {
                            final Image image = imReader.acquireLatestImage();
                            final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            final byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            saveImageToDisk(bytes, cameraDevice.getId());
                            image.close();
                        },
                        backgroundHandler);
                return builder.build();
            }

            private void saveImageToDisk(@NonNull final byte[] bytes, @NonNull String cameraId) {
                final File file = new File(Environment.getExternalStorageDirectory() + "/" + cameraId + "_pic.jpg");
                try (final OutputStream output = new FileOutputStream(file)) {
                    output.write(bytes);
                    picturesTaken.put(file.getPath(), bytes);
                } catch (final IOException e) {
                    Log.e(TAG, "Exception occurred while saving picture to external storage ", e);
                }
            }

            private CameraCaptureSession.CaptureCallback captureCallback() {
                return new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        // super.onCaptureCompleted(session, request, result);// no-op
                        if (picturesTaken.lastEntry() != null) {
                            // OPTIMIZE: design error: service should notify listeners to handler
                            // image data, not save those data to SD-card then notify listeners.
                            capturingListener.onCaptureDone(picturesTaken.lastEntry().getKey(), picturesTaken.lastEntry().getValue());
                            Log.i(TAG, "done taking picture from camera " + cameraDevice.getId());
                        }
                        closeCamera();
                    }
                };
            }

            private void closeCamera() {
                Log.d(TAG, "closing camera " + cameraDevice.getId());
                cameraDevice.close();
                reader.close();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.d(TAG, "onConfigureFailed() called with: session = [" + session + "]");
            }
        };
    }

}
