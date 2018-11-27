package com.hzitoun.camera2SecretPictureTaker.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.hzitoun.camera2SecretPictureTaker.R;
import com.hzitoun.camera2SecretPictureTaker.listeners.PictureCapturingListener;
import com.hzitoun.camera2SecretPictureTaker.services.APictureCapturingService;
import com.hzitoun.camera2SecretPictureTaker.services.PictureCapturingServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;


/**
 * App's Main Activity showing a simple usage of the picture taking service.
 * @author hzitoun (zitoun.hamed@gmail.com)
 */
public class MainActivity extends AppCompatActivity implements PictureCapturingListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String[] requiredPermissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
    };
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_CODE = 1;

    private ImageView uploadBackPhoto;
    private ImageView uploadFrontPhoto;
    
     //The capture service          
    private APictureCapturingService pictureService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        uploadBackPhoto = (ImageView) findViewById(R.id.backIV);
        uploadFrontPhoto = (ImageView) findViewById(R.id.frontIV);
        final Button btn = (Button) findViewById(R.id.startCaptureBtn);
        // getting instance of the Service from PictureCapturingServiceImpl
        pictureService = PictureCapturingServiceImpl.getInstance(this);
        btn.setOnClickListener(v -> {
               showToast("Starting capture!");
               pictureService.startCapturing(this);
        });
    }
    
    private void showToast(final String text) {
        runOnUiThread(() ->
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show()
        );
    }

    /**
    * We've finished taking pictures from all phone's cameras
    */    
    @Override
    public void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken) {
        if (picturesTaken != null && !picturesTaken.isEmpty()) {
            showToast("Done capturing all photos!");
            return;
        }
        showToast("No camera detected!");
    }

    /**
    * Displaying the pictures taken.
    */             
    @Override
    public void onCaptureDone(String pictureUrl, byte[] pictureData) {
        if (pictureData != null && pictureUrl != null) {
            runOnUiThread(() -> {
                final Bitmap bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length);
                final int nh = (int) (bitmap.getHeight() * (512.0 / bitmap.getWidth()));
                final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 512, nh, true);
                if (pictureUrl.contains("0_pic.jpg")) {
                    uploadBackPhoto.setImageBitmap(scaled);
                } else if (pictureUrl.contains("1_pic.jpg")) {
                    uploadFrontPhoto.setImageBitmap(scaled);
                }
            });
            showToast("Picture saved to " + pictureUrl);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_CODE: {
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    checkPermissions();
                }
            }
        }
    }

    /**
     * checking  permissions at Runtime.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermissions() {
        final List<String> neededPermissions = new ArrayList<>();
        for (final String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        if (!neededPermissions.isEmpty()) {
            requestPermissions(neededPermissions.toArray(new String[]{}),
                    MY_PERMISSIONS_REQUEST_ACCESS_CODE);
        }
    }
}

