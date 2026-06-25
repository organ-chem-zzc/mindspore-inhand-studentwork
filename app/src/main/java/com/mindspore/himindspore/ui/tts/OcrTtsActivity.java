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
package com.mindspore.himindspore.ui.tts;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.mlsdk.MLAnalyzerFactory;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.text.MLLocalTextSetting;
import com.huawei.hms.mlsdk.text.MLText;
import com.huawei.hms.mlsdk.text.MLTextAnalyzer;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;
import com.mindspore.himindspore.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * OCR + TTS 语音朗读主界面
 *
 * 功能：
 * 1. 拍照/选图 → HMS ML Kit OCR 识别文字
 * 2. 识别结果可编辑，实时字数统计
 * 3. 选择音色、调整语速音调
 * 4. 调用 MiMo TTS API 合成语音
 * 5. 播放预览 / 停止 / 导出 MP3
 * 6. 修改文本或参数后自动清除缓存，确保重新合成
 */
@Route(path = "/tts/OcrTtsActivity")
public class OcrTtsActivity extends AppCompatActivity {

    private static final String TAG = "OcrTtsActivity";
    private static final int RC_CHOOSE_PHOTO = 1;
    private static final int RC_CHOOSE_CAMERA = 2;

    // UI
    private EditText etText;
    private TextView tvWordCount;
    private Spinner spinnerVoice;
    private TextView tvVoiceDesc;
    private SeekBar seekbarSpeed, seekbarPitch, seekbarProgress;
    private TextView tvSpeedValue, tvPitchValue, tvProgressTime;
    private Button btnPlay, btnStop, btnExport;
    private ProgressBar progressBar;
    private TextView tvStatus;

    // 数据
    private Uri imageUri;
    private MLTextAnalyzer ocrAnalyzer;
    private TtsExecutor ttsExecutor;
    private MediaPlayer mediaPlayer;
    private byte[] currentAudioData;
    private boolean isSynthesizing = false;
    private boolean isPlaying = false;

    // 音频进度更新
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    // 记录上次合成参数，用于检测变化
    private String lastSynthText = "";
    private String lastSynthVoice = "";
    private float lastSynthSpeed = 1.0f;
    private int lastSynthPitch = 0;

    private NoticeDialog noticeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_ocr_tts);
        init();
    }

    private void init() {
        // Toolbar
        Toolbar toolbar = findViewById(R.id.tts_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // OCR 文本区域 + 字数统计
        etText = findViewById(R.id.et_tts_text);

        tvWordCount = findViewById(R.id.tv_word_count);

        // 接收从 ASR 页面传来的文本
        String incomingText = getIntent().getStringExtra("tts_text");
        if (incomingText != null && !incomingText.isEmpty()) {
            etText.setText(incomingText);
        }

        // 文本变化监听 → 清除缓存 + 更新字数
        etText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                invalidateAudioCache();
                updateWordCount();
            }
        });

        // 按钮
        findViewById(R.id.btn_tts_camera).setOnClickListener(v -> openCamera());
        findViewById(R.id.btn_tts_gallery).setOnClickListener(v -> openGallery());
        findViewById(R.id.btn_tts_clear).setOnClickListener(v -> clearText());
        btnPlay = findViewById(R.id.btn_tts_play);
        btnStop = findViewById(R.id.btn_tts_stop);
        btnExport = findViewById(R.id.btn_tts_export);
        btnPlay.setOnClickListener(v -> onPlay());
        btnStop.setOnClickListener(v -> stopPlayback());
        btnExport.setOnClickListener(v -> onExport());

        // 状态
        progressBar = findViewById(R.id.tts_progress);
        tvStatus = findViewById(R.id.tv_tts_status);

        // 音频播放进度条
        seekbarProgress = findViewById(R.id.seekbar_progress);
        tvProgressTime = findViewById(R.id.tv_progress_time);
        seekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && isPlaying) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // 音色 Spinner
        spinnerVoice = findViewById(R.id.spinner_voice);
        tvVoiceDesc = findViewById(R.id.tv_voice_desc);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TtsExecutor.VOICES);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);
        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                tvVoiceDesc.setText(TtsExecutor.VOICE_DESCS[pos]);
                invalidateAudioCache();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 语速 SeekBar (0.5 ~ 2.0, step 0.1, progress 0~15 → 0.5~2.0)
        seekbarSpeed = findViewById(R.id.seekbar_speed);
        tvSpeedValue = findViewById(R.id.tv_speed_value);
        seekbarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float speed = 0.5f + progress * 0.1f;
                tvSpeedValue.setText(String.format("%.1f", speed));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                invalidateAudioCache();
            }
        });

        // 音调 SeekBar (-10 ~ +10, progress 0~20 → -10~+10)
        seekbarPitch = findViewById(R.id.seekbar_pitch);
        tvPitchValue = findViewById(R.id.tv_pitch_value);
        seekbarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int pitch = progress - 10;
                tvPitchValue.setText(String.valueOf(pitch));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                invalidateAudioCache();
            }
        });

        // 初始化 OCR 引擎
        MLLocalTextSetting setting = new MLLocalTextSetting.Factory()
                .setOCRMode(MLLocalTextSetting.OCR_DETECT_MODE)
                .setLanguage("zh")
                .create();
        ocrAnalyzer = MLAnalyzerFactory.getInstance().getLocalTextAnalyzer(setting);

        // 初始化 TTS 引擎
        ttsExecutor = new TtsExecutor();

        // 初始化 MediaPlayer
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            btnPlay.setText(getString(R.string.tts_play));
            seekbarProgress.setProgress(0);
            stopProgressUpdate();
            setStatus("播放完成");
        });

        // 初始化字数统计
        updateWordCount();
    }

    // ==================== 缓存管理 ====================

    /**
     * 当文本、音色、语速、音调任一变化时，清除已缓存的音频
     * 确保下次点击播放时重新合成
     */
    private void invalidateAudioCache() {
        currentAudioData = null;
        if (isPlaying) {
            stopPlayback();
        }
    }

    /**
     * 检查当前参数是否与上次合成一致
     */
    private boolean isParamsChanged() {
        String text = etText.getText().toString().trim();
        String voice = TtsExecutor.VOICES[spinnerVoice.getSelectedItemPosition()];
        float speed = 0.5f + seekbarSpeed.getProgress() * 0.1f;
        int pitch = seekbarPitch.getProgress() - 10;

        return !text.equals(lastSynthText)
                || !voice.equals(lastSynthVoice)
                || speed != lastSynthSpeed
                || pitch != lastSynthPitch;
    }

    private void saveCurrentParams() {
        lastSynthText = etText.getText().toString().trim();
        lastSynthVoice = TtsExecutor.VOICES[spinnerVoice.getSelectedItemPosition()];
        lastSynthSpeed = 0.5f + seekbarSpeed.getProgress() * 0.1f;
        lastSynthPitch = seekbarPitch.getProgress() - 10;
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
        noticeDialog.setContentString(getString(R.string.explain_ocr_tts));
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    // ==================== 图片选择 + OCR ====================

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, RC_CHOOSE_PHOTO);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (photoDir != null && !photoDir.exists()) photoDir.mkdirs();
        String photoPath = new File(photoDir, "ocr_tts_photo.jpeg").getAbsolutePath();
        imageUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider",
                new File(photoPath));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, RC_CHOOSE_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == RC_CHOOSE_PHOTO && data != null && data.getData() != null) {
            imageUri = data.getData();
        } else if (requestCode == RC_CHOOSE_CAMERA) {
            // imageUri already set
        } else {
            return;
        }
        runOcr();
    }

    private void runOcr() {
        try {
            Bitmap bitmap = loadBitmapFromUri(imageUri);
            if (bitmap == null) {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                return;
            }

            setStatus("正在识别文字...");
            MLFrame frame = MLFrame.fromBitmap(bitmap);
            Task<MLText> task = ocrAnalyzer.asyncAnalyseFrame(frame);
            task.addOnSuccessListener(mlText -> {
                StringBuilder sb = new StringBuilder();
                List<MLText.Block> blocks = mlText.getBlocks();
                for (MLText.Block block : blocks) {
                    for (MLText.TextLine line : block.getContents()) {
                        sb.append(line.getStringValue()).append("\n");
                    }
                }
                String result = sb.toString().trim();
                etText.setText(result);
                // setText 会触发 TextWatcher → invalidateAudioCache + updateWordCount
                setStatus("识别完成，共 " + result.length() + " 字");
            }).addOnFailureListener(e -> {
                Log.e(TAG, "OCR failed: " + e.getMessage());
                setStatus("识别失败: " + e.getMessage());
            });

        } catch (Exception e) {
            Log.e(TAG, "runOcr error: " + e.getMessage());
            setStatus("识别出错");
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "loadBitmap error: " + e.getMessage());
            return null;
        }
    }

    // ==================== 文本操作 ====================

    private void clearText() {
        etText.setText("");
        invalidateAudioCache();
        updateWordCount();
        setStatus("已清空");
    }

    private void updateWordCount() {
        String text = etText.getText().toString();
        int count = text.length();
        tvWordCount.setText(count + " 字");
    }

    // ==================== TTS 播放 ====================

    private void onPlay() {
        String text = etText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.tts_no_text, Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果正在播放，暂停
        if (isPlaying) {
            pausePlayback();
            return;
        }

        // 如果有缓存且参数未变化，直接播放
        if (currentAudioData != null && !isParamsChanged() && !isSynthesizing) {
            playAudio();
            return;
        }

        // 合成语音
        synthesizeAndPlay(text);
    }

    private void synthesizeAndPlay(String text) {
        isSynthesizing = true;
        btnPlay.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        setStatus("正在合成语音...");

        String voice = TtsExecutor.VOICES[spinnerVoice.getSelectedItemPosition()];
        float speed = 0.5f + seekbarSpeed.getProgress() * 0.1f;
        int pitch = seekbarPitch.getProgress() - 10;

        ttsExecutor.synthesizeAsync(text, voice, speed, pitch, new TtsExecutor.TtsCallback() {
            @Override
            public void onSuccess(byte[] audioData) {
                runOnUiThread(() -> {
                    isSynthesizing = false;
                    currentAudioData = audioData;
                    saveCurrentParams();
                    btnPlay.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    setStatus("合成完成，大小: " + (audioData.length / 1024) + " KB");
                    playAudio();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    isSynthesizing = false;
                    btnPlay.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    setStatus("合成失败: " + message);
                });
            }
        });
    }

    private void playAudio() {
        try {
            // 写入临时文件播放
            File tempFile = new File(getCacheDir(), "tts_preview.mp3");
            ttsExecutor.saveToFile(currentAudioData, tempFile.getAbsolutePath());

            mediaPlayer.reset();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            btnPlay.setText(getString(R.string.tts_pause));
            setStatus("播放中...");

            // 设置进度条
            int duration = mediaPlayer.getDuration();
            seekbarProgress.setMax(duration);
            seekbarProgress.setProgress(0);
            startProgressUpdate();

        } catch (IOException e) {
            Log.e(TAG, "playAudio error: " + e.getMessage());
            setStatus("播放失败");
        }
    }

    private void pausePlayback() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlay.setText(getString(R.string.tts_play));
            stopProgressUpdate();
            setStatus("已暂停");
        }
    }

    private void stopPlayback() {
        if (mediaPlayer.isPlaying() || isPlaying) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
        isPlaying = false;
        btnPlay.setText(getString(R.string.tts_play));
        seekbarProgress.setProgress(0);
        tvProgressTime.setText("0:00 / 0:00");
        stopProgressUpdate();
        setStatus("已停止");
    }

    private void startProgressUpdate() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    seekbarProgress.setProgress(pos);
                    tvProgressTime.setText(formatTime(pos) + " / " + formatTime(dur));
                    progressHandler.postDelayed(this, 200);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    private String formatTime(int ms) {
        int sec = ms / 1000;
        int min = sec / 60;
        sec = sec % 60;
        return min + ":" + String.format("%02d", sec);
    }

    // ==================== 导出 MP3 ====================

    private void onExport() {
        if (currentAudioData == null) {
            Toast.makeText(this, R.string.tts_no_audio, Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存到 Pictures/TTS_Export 目录
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "TTS_Export");
        if (!dir.exists()) dir.mkdirs();

        String fileName = "TTS_" + System.currentTimeMillis() + ".mp3";
        File outFile = new File(dir, fileName);

        if (ttsExecutor.saveToFile(currentAudioData, outFile.getAbsolutePath())) {
            // 通知相册刷新
            Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScan.setData(Uri.fromFile(outFile));
            sendBroadcast(mediaScan);

            Toast.makeText(this,
                    getString(R.string.tts_export_ok) + outFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
            setStatus("已导出: " + outFile.getAbsolutePath());
        } else {
            Toast.makeText(this, R.string.tts_export_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 工具 ====================

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        if (ocrAnalyzer != null) {
            try { ocrAnalyzer.stop(); } catch (IOException ignored) {}
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (ttsExecutor != null) {
            ttsExecutor.release();
        }
    }
}
