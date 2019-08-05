<img align="right" width="200" height="200" src="https://user-images.githubusercontent.com/34313493/44617202-8d051180-a880-11e8-8788-b52580aea56e.jpg">

[![Build Status](https://travis-ci.org/botyourbusiness/android-camera2-secret-picture-taker.svg?branch=master)](https://travis-ci.org/botyourbusiness/android-camera2-secret-picture-taker)

# 📸 Android Camera2 Secret Picture Taker (AC2SPT) 
Take pictures secretly (without preview or launching device's camera app) from all available cameras using Android CAMERA2 API.
The <a href="https://developer.android.com/reference/android/hardware/camera2/package-summary.html">Camera2 API</a> replaces the deprecated Camera class.

## How can I support this project?
- If you have enjoyed the project and it helped you creating a project, building an app, starting a business. You could encourage and support me on patreon https://www.patreon.com/hzitoun 🤗 !  
- Star this GitHub repo :star:
- Create pull requests, submit bugs, suggest new features or documentation updates :wrench:

## Usage

1. Implement the interface <a href="https://github.com/hzitoun/android-camera2-secret-picture-taker/blob/master/app/src/main/java/com/hzitoun/camera2SecretPictureTaker/listeners/PictureCapturingListener.java">```PictureCapturingListener```</a> (your capture listener) and override the following methods:
    -  **void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken)** which is called when we've done taking pictures from ALL available cameras OR when NO camera was detected on the device;
    -  **void onCaptureDone(String pictureUrl, byte[] pictureData)** to get a couple (picture Url, picture Data). Use this method if you don't want to wait for ALL pictures to be ready;
2. Create a new instance of <a href="https://github.com/hzitoun/android-camera2-secret-picture-taker/blob/master/app/src/main/java/com/hzitoun/camera2SecretPictureTaker/services/APictureCapturingService.java">```APictureCapturingService``` </a> using <a href="https://github.com/hzitoun/android-camera2-secret-picture-taker/blob/master/app/src/main/java/com/hzitoun/camera2SecretPictureTaker/services/PictureCapturingServiceImpl.java">```PictureCapturingServiceImpl#getInstance()```</a> method;
3. **Start capture** by calling the method ```APictureCapturingService#startCapturing(PictureCapturingListener listener) ``` and pass the listener you've just implemented (**step 1**)

## Sample

Here, I've chosen to just  display the two pictures taken within a vertical linear layout. Here is a code snippet of how to use the service:

```java
public class MainActivity extends AppCompatActivity implements PictureCapturingListener, ActivityCompat.OnRequestPermissionsResultCallback {
     private APictureCapturingService pictureService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //check for camera and external storage permissions
        checkPermissions();
        final Button btn = (Button) findViewById(R.id.startCaptureBtn);
        pictureService = PictureCapturingServiceImpl.getInstance(this);
        //start capturing when clicking on the button
        btn.setOnClickListener(v ->
                pictureService.startCapturing(this)
        );
    }

    @Override
    public void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken) {
        if (picturesTaken != null && !picturesTaken.isEmpty()) {
            picturesTaken.forEach((pictureUrl, pictureData) -> {
               //convert the byte array 'pictureData' to a bitmap (no need to read the file from the external storage) but in case you
               //You can also use 'pictureUrl' which stores the picture's location on the device
                final Bitmap bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length);
            });
            showToast("Done capturing all photos!");
            return;
        }
        showToast("No camera detected!");
    }

    @Override
    public void onCaptureDone(String pictureUrl, byte[] pictureData) {
        if (pictureData != null && pictureUrl != null) {
            runOnUiThread(() -> {
                //convert byte array 'pictureData' to a bitmap (no need to read the file from the external storage)
                final Bitmap bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length);
                //scale image to avoid POTENTIAL "Bitmap too large to be uploaded into a texture" when displaying into an ImageView
                final int nh = (int) (bitmap.getHeight() * (512.0 / bitmap.getWidth()));
                final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 512, nh, true);
               //do whatever you want with the bitmap or the scaled one...
            });
            showToast("Picture saved to " + pictureUrl);
        }
    }
    
    private void showToast(final String text) {
        runOnUiThread(() ->
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show()
        );
    }

}
```

Thanks to https://github.com/maaudrana for the logo :)

## Contributors

Hamed ZITOUN <zitoun.hamed@gmail.com>

## Help

If you run into issues, please don't hesitate to find help on the GitHub project.

## License

The android-camera2-secret-picture-taker is covered by the MIT License.

The MIT License (MIT)

Copyright (c) 2019 Hamed ZITOUN and contributors to the android-camera2-secret-picture-taker project.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

