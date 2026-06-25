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

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.bumptech.glide.Glide;
import com.mindspore.common.base.grid.MSGridSpacingItemDecoration;
import com.mindspore.common.config.MSLinkUtils;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;

/**
 * 图片融合主界面
 */
@Route(path = "/imagefusion/ImageFusionMainActivity")
public class ImageFusionMainActivity extends AppCompatActivity implements PresetImageAdapter.OnPresetImageSelectedListener {

    private static final String TAG = "ImageFusionMainActivity";

    // 预设图片资源ID数组
    private static final int[] PRESET_IMAGES = {
            R.drawable.preset0, R.drawable.preset1, R.drawable.preset2,
            R.drawable.preset3, R.drawable.preset4, R.drawable.preset5,
            R.drawable.preset6, R.drawable.preset7, R.drawable.preset8,
            R.drawable.preset9
    };

    private static final int RC_CHOOSE_PHOTO = 1;

    private ImageFusionExecutor fusionExecutor;
    private OnnxFusionExecutor onnxFusionExecutor;  // ONNX Runtime 推理器
    private boolean useOnnxRuntime = true;  // 使用 ONNX Runtime
    private boolean isRunningModel = false;

    // UI组件
    private ImageView imgResult;
    private ImageView imgUserPreview;
    private TextView tvHint;
    private TextView tvUserHint;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private Button btnFusion;
    private Button btnSave;

    // 图片数据
    private Bitmap userBitmap;
    private Bitmap presetBitmap;
    private Bitmap resultBitmap;

    private Integer maxWidthOfImage;
    private Integer maxHeightOfImage;
    private boolean isLandScape;

    private NoticeDialog noticeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_image_fusion);
        this.isLandScape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        init();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "dispatchTouchEvent: x=" + ev.getX() + ", y=" + ev.getY());
        }
        return super.dispatchTouchEvent(ev);
    }

    private void init() {
        // 初始化UI组件
        imgResult = findViewById(R.id.img_result);
        imgUserPreview = findViewById(R.id.img_user_preview);
        tvHint = findViewById(R.id.tv_hint);
        tvUserHint = findViewById(R.id.tv_user_hint);
        progressBar = findViewById(R.id.progress);
        recyclerView = findViewById(R.id.recyclerview_presets);
        btnFusion = findViewById(R.id.btn_fusion);
        btnSave = findViewById(R.id.btn_save);

        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(view -> finish());
            // 设置返回图标
            toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        }

        // 设置RecyclerView
        if (recyclerView != null) {
            GridLayoutManager layoutManager = new GridLayoutManager(this, 5);
            recyclerView.setLayoutManager(layoutManager);
            
            // 使用正确的 ItemDecoration (5列)
            recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
                private final int spacing = 8; // 8dp spacing
                
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    int position = parent.getChildAdapterPosition(view);
                    int spanCount = 5; // 5列
                    
                    // 列间距
                    int column = position % spanCount;
                    outRect.left = column * spacing / spanCount;
                    outRect.right = spacing - (column + 1) * spacing / spanCount;
                    
                    // 行间距
                    if (position >= spanCount) {
                        outRect.top = spacing;
                    }
                }
            });
            
            PresetImageAdapter adapter = new PresetImageAdapter(this, PRESET_IMAGES, this);
            recyclerView.setAdapter(adapter);
            
            Log.d(TAG, "RecyclerView initialized with " + PRESET_IMAGES.length + " preset images");
            Log.d(TAG, "LayoutManager: " + layoutManager.getClass().getSimpleName() + ", spanCount: " + layoutManager.getSpanCount());
            Log.d(TAG, "Adapter item count: " + adapter.getItemCount());
            
            // 添加布局变化监听器
            recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                Log.d(TAG, "RecyclerView layout changed: width=" + (right - left) + ", height=" + (bottom - top));
                Log.d(TAG, "RecyclerView position: left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
                
                // 检查子项数量
                int childCount = recyclerView.getChildCount();
                Log.d(TAG, "RecyclerView child count: " + childCount);
                
                for (int i = 0; i < childCount; i++) {
                    View child = recyclerView.getChildAt(i);
                    Log.d(TAG, "Child " + i + ": left=" + child.getLeft() + ", top=" + child.getTop() + 
                          ", right=" + child.getRight() + ", bottom=" + child.getBottom() +
                          ", width=" + child.getWidth() + ", height=" + child.getHeight());
                }
            });
        } else {
            Log.e(TAG, "RecyclerView is null, cannot initialize");
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置按钮点击事件
        Button btnSelectUserImage = findViewById(R.id.btn_select_user_image);
        if (btnSelectUserImage != null) {
            btnSelectUserImage.setOnClickListener(v -> openGallery());
        }

        if (btnFusion != null) {
            btnFusion.setOnClickListener(v -> startFusion());
        }
        
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveResult());
        }
        
        // 在后台线程初始化模型
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                if (useOnnxRuntime) {
                    onnxFusionExecutor = new OnnxFusionExecutor(this);
                    long initTime = System.currentTimeMillis() - startTime;
                    Log.i(TAG, "ONNX Runtime initialized in " + initTime + "ms");

                    runOnUiThread(() -> {
                        Toast.makeText(this, "模型初始化完成 (" + initTime + "ms)", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    fusionExecutor = new ImageFusionExecutor(this);
                    long initTime = System.currentTimeMillis() - startTime;
                    Log.i(TAG, "MindSpore Lite initialized in " + initTime + "ms");

                    runOnUiThread(() -> {
                        Toast.makeText(this, "模型初始化完成 (" + initTime + "ms)", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Model initialization failed: " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(() -> {
                    Toast.makeText(this, "模型初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setting_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_help) {
            showHelpDialog();
        } else if (itemId == R.id.item_more) {
            Utils.openBrowser(this, MSLinkUtils.HELP_STYLE_TRANSFER);
        }
        return super.onOptionsItemSelected(item);
    }

    private void showHelpDialog() {
        noticeDialog = new NoticeDialog(this);
        noticeDialog.setTitleString(getString(R.string.explain_title));
        noticeDialog.setContentString("选择一张用户图片，再从预设图片中选择一张，点击融合按钮即可生成融合图片。");
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    /**
     * 打开相册选择用户图片
     */
    private void openGallery() {
        Intent intentToPickPic = new Intent(Intent.ACTION_PICK, null);
        intentToPickPic.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intentToPickPic, RC_CHOOSE_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == RC_CHOOSE_PHOTO) {
            if (data != null && data.getData() != null) {
                showUserImage(data.getData());
            }
        }
    }

    /**
     * 显示用户选择的图片
     */
    private void showUserImage(Uri imageUri) {
        Pair<Integer, Integer> targetedSize = getTargetSize();
        int targetWidth = targetedSize.first;
        int maxHeight = targetedSize.second;

        userBitmap = BitmapUtils.loadFromPath(this, imageUri, targetWidth, maxHeight);

        if (userBitmap != null) {
            // 显示用户图片预览，清除缓存确保显示最新图片
            Glide.with(this).clear(imgUserPreview);
            Glide.with(this)
                    .load(userBitmap)
                    .skipMemoryCache(true)
                    .into(imgUserPreview);
            imgUserPreview.setVisibility(View.VISIBLE);
            tvUserHint.setVisibility(View.GONE);

            Log.d(TAG, "User image loaded: " + userBitmap.getWidth() + "x" + userBitmap.getHeight());
        } else {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 预设图片选择回调
     */
    @Override
    public void onPresetImageSelected(int position, Bitmap bitmap) {
        Log.d(TAG, "onPresetImageSelected called: position=" + position + ", bitmap=" + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
        if (bitmap != null) {
            presetBitmap = bitmap;
            Log.d(TAG, "Preset image selected successfully: position " + position);
        } else {
            Log.e(TAG, "Preset image bitmap is null at position " + position);
            Toast.makeText(this, "预设图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 开始融合
     */
    private void startFusion() {
        Log.d(TAG, "startFusion called");
        Log.d(TAG, "fusionExecutor: " + (fusionExecutor != null ? "initialized" : "null"));
        Log.d(TAG, "userBitmap: " + (userBitmap != null ? userBitmap.getWidth() + "x" + userBitmap.getHeight() : "null"));
        Log.d(TAG, "presetBitmap: " + (presetBitmap != null ? presetBitmap.getWidth() + "x" + presetBitmap.getHeight() : "null"));
        Log.d(TAG, "isRunningModel: " + isRunningModel);
        
        // 检查模型是否初始化
        boolean isModelReady;
        if (useOnnxRuntime) {
            isModelReady = onnxFusionExecutor != null;
        } else {
            isModelReady = fusionExecutor != null;
        }

        if (!isModelReady) {
            Toast.makeText(this, "模型正在初始化，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查是否选择了用户图片
        if (userBitmap == null) {
            Toast.makeText(this, "请先选择用户图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否选择了预设图片
        if (presetBitmap == null) {
            Toast.makeText(this, "请选择预设图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否正在运行
        if (isRunningModel) {
            Toast.makeText(this, "模型正在运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 UI 组件
        if (progressBar == null || tvHint == null || imgResult == null) {
            Log.e(TAG, "UI components are null");
            Toast.makeText(this, "界面初始化异常", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunningModel = true;
        progressBar.setVisibility(View.VISIBLE);
        tvHint.setVisibility(View.GONE);

        // 在后台线程执行融合
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting fusion execution...");
                
                Bitmap resultImage;
                if (useOnnxRuntime) {
                    // 使用 ONNX Runtime
                    resultImage = onnxFusionExecutor.execute(userBitmap, presetBitmap);
                } else {
                    // 使用 MindSpore Lite
                    ImageFusionResult result = fusionExecutor.execute(userBitmap, presetBitmap);
                    resultImage = result != null ? result.getFusedImage() : null;
                }
                
                Log.d(TAG, "Fusion execution completed, result: " + (resultImage != null ? "success" : "null"));

                runOnUiThread(() -> {
                    if (resultImage != null) {
                        resultBitmap = resultImage;
                        // 清除Glide缓存并重新加载结果图片
                        Glide.with(this).clear(imgResult);
                        Glide.with(this)
                                .load(resultBitmap)
                                .skipMemoryCache(true)  // 跳过内存缓存
                                .into(imgResult);
                        imgResult.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "融合成功", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Result bitmap size: " + resultBitmap.getWidth() + "x" + resultBitmap.getHeight());
                    } else {
                        Toast.makeText(this, "融合失败", Toast.LENGTH_SHORT).show();
                    }

                    isRunningModel = false;
                    if (progressBar != null) {
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Fusion execution failed: " + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    isRunningModel = false;
                    if (progressBar != null) {
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                    Toast.makeText(this, "融合失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 保存结果图片
     */
    private void saveResult() {
        Log.d(TAG, "saveResult called, resultBitmap: " + (resultBitmap != null ? resultBitmap.getWidth() + "x" + resultBitmap.getHeight() : "null"));
        
        if (resultBitmap == null) {
            Toast.makeText(this, R.string.toast_no_result, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "resultBitmap is null, cannot save");
            return;
        }

        try {
            boolean saveSuccess = ImageUtils.saveToAlbum(getApplicationContext(), resultBitmap);
            if (saveSuccess) {
                Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Image saved successfully");
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Image save failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving image: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 获取图片最大宽度
    private Integer getMaxWidthOfImage() {
        if (this.maxWidthOfImage == null) {
            if (this.isLandScape) {
                this.maxWidthOfImage = ((View) this.imgResult.getParent()).getHeight();
            } else {
                this.maxWidthOfImage = ((View) this.imgResult.getParent()).getWidth();
            }
        }
        return this.maxWidthOfImage;
    }

    // 获取图片最大高度
    private Integer getMaxHeightOfImage() {
        if (this.maxHeightOfImage == null) {
            if (this.isLandScape) {
                this.maxHeightOfImage = ((View) this.imgResult.getParent()).getWidth();
            } else {
                this.maxHeightOfImage = ((View) this.imgResult.getParent()).getHeight();
            }
        }
        return this.maxHeightOfImage;
    }

    // 获取目标尺寸
    private Pair<Integer, Integer> getTargetSize() {
        Integer targetWidth;
        Integer targetHeight;
        Integer maxWidth = this.getMaxWidthOfImage();
        Integer maxHeight = this.getMaxHeightOfImage();
        targetWidth = this.isLandScape ? maxHeight : maxWidth;
        targetHeight = this.isLandScape ? maxWidth : maxHeight;
        return new Pair<>(targetWidth, targetHeight);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusionExecutor != null) {
            fusionExecutor.free();
        }
    }
}
