/**
 * Copyright 2020 Huawei Technologies Co., Ltd
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mindspore.imagefusion;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    // Save the picture to the system album and refresh it.
    public static boolean saveToAlbum(final Context context, Bitmap bitmap) {
        Log.d(TAG, "saveToAlbum called, bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
        
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, cannot save");
            return false;
        }
        
        File file = null;
        String fileName = "imagefusion_" + System.currentTimeMillis() + ".jpg";
        
        // 使用公共相册目录 DCIM/Camera
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
            // 尝试备用目录
            dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ImageFusion");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory: " + dir.getAbsolutePath());
                return false;
            }
        }
        
        file = new File(dir, fileName);
        Log.d(TAG, "Saving to: " + file.getAbsolutePath());
        
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
            boolean compressSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            if (!compressSuccess) {
                Log.e(TAG, "Bitmap compress failed");
                return false;
            }
            os.flush();
            Log.d(TAG, "Image saved successfully to: " + file.getAbsolutePath());

        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing stream: " + e.getMessage());
            }
        }
        
        if (file == null || !file.exists()) {
            Log.e(TAG, "File does not exist after save");
            return false;
        }
        
        // Gallery refresh.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String path = null;
            try {
                path = file.getCanonicalPath();
            } catch (IOException e) {
                Log.e(TAG, "Error getting canonical path: " + e.getMessage());
            }
            final String finalPath = path;
            MediaScannerConnection.scanFile(context, new String[]{path}, null,
                    (path1, uri) -> {
                        Log.d(TAG, "Media scan completed: " + path1 + ", uri: " + uri);
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(uri);
                        context.sendBroadcast(mediaScanIntent);
                    });
        } else {
            String relationDir = file.getParent();
            File file1 = new File(relationDir);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file1.getAbsoluteFile())));
        }
        
        return true;
    }
}
