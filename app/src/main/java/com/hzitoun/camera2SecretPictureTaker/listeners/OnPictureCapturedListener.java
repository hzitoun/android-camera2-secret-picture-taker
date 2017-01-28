package com.hzitoun.camera2SecretPictureTaker.listeners;

import java.util.TreeMap;

/**
 * Photo capturing listener
 * @author hzitoun (hamed.zitoun@gmail.com)
 */
public interface OnPictureCapturedListener {

    void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken);

    void onCaptureDone(String pictureUrl, byte[] pictureData);
}
