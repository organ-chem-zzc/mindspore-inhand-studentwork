/**
 * Copyright 2021 Huawei Technologies Co., Ltd
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
package com.mindspore.himindspore.ui.superresolution;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;
import com.mindspore.himindspore.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 超分辨率主界面
 *
 * 功能流程：
 * 1. 选择/拍摄图片
 * 2. 加载模型（默认 assets 或自定义路径）
 * 3. 一键超分，显示耗时
 * 4. 左右滑动对比原图与超分结果
 * 5. 保存超分结果到相册
 */
@Route(path = "/superresolution/SuperResolutionActivity")
public class SuperResolutionActivity extends AppCompatActivity {

    private static final String TAG = "SRActivity";
    private static final int RC_CHOOSE_PHOTO = 1;
    private static final int RC_CHOOSE_CAMERA = 2;
    private static final int RC_PICK_MODEL = 3;

    // UI
    private SuperResolutionResultView resultView;
    private TextView tvPlaceholder;
    private ProgressBar progressBar;
    private TextView tvModelStatus;
    private LinearLayout panelInfo;
    private TextView tvBenchmark;
    private Button btnUpscale;

    // 数据
    private Uri imageUri;
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private SuperResolutionExecutor executor;
    private boolean isRunning = false;

    private NoticeDialog noticeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_super_resolution);
        init();
    }

    private void init() {
        // Toolbar
        Toolbar toolbar = findViewById(R.id.sr_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // UI 绑定
        resultView = findViewById(R.id.sr_result_view);
        tvPlaceholder = findViewById(R.id.tv_sr_placeholder);
        progressBar = findViewById(R.id.sr_progress);
        tvModelStatus = findViewById(R.id.tv_sr_model_status);
        panelInfo = findViewById(R.id.panel_sr_info);
        tvBenchmark = findViewById(R.id.tv_sr_benchmark);
        btnUpscale = findViewById(R.id.btn_sr_upscale);

        // 按钮点击
        findViewById(R.id.btn_sr_select_photo).setOnClickListener(v -> openGallery());
        findViewById(R.id.btn_sr_take_photo).setOnClickListener(v -> openCamera());
        findViewById(R.id.btn_sr_select_model).setOnClickListener(v -> openModelPicker());
        btnUpscale.setOnClickListener(v -> runUpscale());
        findViewById(R.id.btn_sr_reset).setOnClickListener(v -> resetView());
        findViewById(R.id.btn_sr_save).setOnClickListener(v -> saveResult());

        // 初始化推理引擎
        executor = new SuperResolutionExecutor(this);
        boolean loaded = executor.loadModel();
        updateModelStatus(loaded);
    }

    // ==================== 菜单 ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setting_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_help) {
            showHelpDialog();
        } else if (item.getItemId() == R.id.item_more) {
            Utils.openBrowser(this, "https://mindspore.cn/");
        }
        return super.onOptionsItemSelected(item);
    }

    private void showHelpDialog() {
        noticeDialog = new NoticeDialog(this);
        noticeDialog.setTitleString(getString(R.string.explain_title));
        noticeDialog.setContentString(getString(R.string.explain_sr));
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    // ==================== 图片选择 ====================

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, RC_CHOOSE_PHOTO);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (photoDir != null && !photoDir.exists()) {
            photoDir.mkdirs();
        }
        String photoPath = new File(photoDir, "sr_photo.jpeg").getAbsolutePath();
        imageUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider",
                new File(photoPath));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, RC_CHOOSE_CAMERA);
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, RC_PICK_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || (data == null && requestCode != RC_CHOOSE_CAMERA)) {
            return;
        }

        switch (requestCode) {
            case RC_CHOOSE_PHOTO:
                if (data.getData() != null) {
                    imageUri = data.getData();
                    loadOriginalImage();
                }
                break;

            case RC_CHOOSE_CAMERA:
                loadOriginalImage();
                break;

            case RC_PICK_MODEL:
                if (data.getData() != null) {
                    File dst = copyUriToFile(data.getData(), "custom_sr_model.ms");
                    if (dst != null) {
                        boolean ok = executor.loadModelFromPath(dst.getAbsolutePath());
                        updateModelStatus(ok);
                        Toast.makeText(this,
                                ok ? R.string.sr_model_load_ok : R.string.sr_model_load_fail,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private void loadOriginalImage() {
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            originalBitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (originalBitmap != null) {
                tvPlaceholder.setVisibility(View.GONE);
                resultView.setImages(originalBitmap, originalBitmap);
                resultBitmap = null;
                panelInfo.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadOriginalImage error: " + e.getMessage());
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 超分推理 ====================

    private void runUpscale() {
        if (originalBitmap == null) {
            Toast.makeText(this, R.string.sr_no_image, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!executor.isModelLoaded()) {
            Toast.makeText(this, R.string.sr_model_not_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isRunning) {
            Toast.makeText(this, R.string.sr_running, Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        progressBar.setVisibility(View.VISIBLE);
        btnUpscale.setEnabled(false);

        new Thread(() -> {
            SuperResolutionExecutor.SRResult srResult = executor.execute(originalBitmap);
            runOnUiThread(() -> {
                isRunning = false;
                progressBar.setVisibility(View.GONE);
                btnUpscale.setEnabled(true);

                if (srResult != null && srResult.getResultBitmap() != null) {
                    resultBitmap = srResult.getResultBitmap();
                    resultView.setImages(originalBitmap, resultBitmap);

                    // 显示耗时信息
                    tvBenchmark.setText(srResult.getFormattedLog());
                    panelInfo.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this, R.string.sr_inference_fail, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // ==================== 其他操作 ====================

    private void resetView() {
        if (originalBitmap != null && resultBitmap != null) {
            resultView.setImages(originalBitmap, resultBitmap);
            resultView.resetSplit();
        }
    }

    private void saveResult() {
        if (resultBitmap == null) {
            Toast.makeText(this, R.string.sr_no_result, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 保存到 Pictures 目录
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (!dir.exists()) dir.mkdirs();
            String fileName = "SR_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            // 通知相册刷新
            Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScan.setData(Uri.fromFile(file));
            sendBroadcast(mediaScan);

            Toast.makeText(this, getString(R.string.sr_save_ok) + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "saveResult error: " + e.getMessage());
            Toast.makeText(this, R.string.sr_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 工具方法 ====================

    private void updateModelStatus(boolean loaded) {
        tvModelStatus.setText(loaded ? R.string.sr_model_loaded : R.string.sr_model_not_loaded);
        tvModelStatus.setTextColor(loaded ? 0xFF4CAF50 : 0xFFF44336);
    }

    private File copyUriToFile(Uri uri, String fileName) {
        File dst = new File(getFilesDir(), fileName);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return dst;
        } catch (Exception e) {
            Log.e(TAG, "copyUriToFile failed", e);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.release();
        if (originalBitmap != null && !originalBitmap.isRecycled()) originalBitmap.recycle();
        if (resultBitmap != null && !resultBitmap.isRecycled()) resultBitmap.recycle();
    }
}
