package com.hzitoun.camera2SecretPictureTaker.listeners;

import java.util.TreeMap;

/**
 * @author hzitoun (hamed.zitoun@gmail.com)
 *         Photo capturing listener
 */
public interface OnPictureCapturedListener {

    void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken);

    void onCaptureDone(String pictureUrl, byte[] pictureData);
}
