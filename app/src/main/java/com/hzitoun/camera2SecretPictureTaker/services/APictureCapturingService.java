package com.hzitoun.camera2SecretPictureTaker.services;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import com.hzitoun.camera2SecretPictureTaker.listeners.PictureCapturingListener;

/**
 * Abstract Picture Taking Service.
 *
 * @author hzitoun (zitoun.hamed@gmail.com)
 */
public abstract class APictureCapturingService {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    final Context context;
    final CameraManager manager;

    /***
     * constructor.
     *
     * @param context the context used to get display manager and the application context
     */
    APictureCapturingService(final Context context) {
        this.context = context.getApplicationContext();
        this.manager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
    }

    /***
     * @return orientation
     */
    int getOrientation() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            final int rotation = windowManager.getDefaultDisplay().getRotation();
            return ORIENTATIONS.get(rotation);
        } else {
            return ORIENTATIONS.get(/*default*/0);
        }
    }


    /**
     * starts pictures capturing process.
     *
     * @param listener picture capturing listener
     */
    public abstract void startCapturing(final PictureCapturingListener listener);
}
