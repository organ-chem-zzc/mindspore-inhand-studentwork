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
package com.mindspore.himindspore.ui.voice;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;
import com.mindspore.himindspore.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 语音转文字 (ASR) 主界面
 *
 * 功能：
 * 1. 麦克风录音 → MiMo ASR API 识别文字
 * 2. 导入音频文件 → 识别文字
 * 3. 识别结果可编辑、复制
 * 4. 一键跳转 TTS 朗读
 */
@Route(path = "/voice/AsrActivity")
public class AsrActivity extends AppCompatActivity {

    private static final String TAG = "AsrActivity";
    private static final int RC_PICK_AUDIO = 1;

    // UI
    private Button btnRecord;
    private TextView tvRecordStatus, tvDuration;
    private ProgressBar progressVolume;
    private EditText etText;
    private TextView tvWordCount;
    private ProgressBar progressBar;
    private TextView tvStatus;

    // 数据
    private AudioRecorderHelper recorderHelper;
    private AsrExecutor asrExecutor;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable volumeRunnable;
    private boolean isRecognizing = false;

    private NoticeDialog noticeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_asr);
        init();
    }

    private void init() {
        // Toolbar
        Toolbar toolbar = findViewById(R.id.asr_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 录音
        btnRecord = findViewById(R.id.btn_asr_record);
        tvRecordStatus = findViewById(R.id.tv_asr_record_status);
        tvDuration = findViewById(R.id.tv_asr_duration);
        progressVolume = findViewById(R.id.progress_asr_volume);

        btnRecord.setOnClickListener(v -> toggleRecording());

        // 文本
        etText = findViewById(R.id.et_asr_text);
        tvWordCount = findViewById(R.id.tv_asr_word_count);
        etText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                tvWordCount.setText(s.length() + " 字");
            }
        });

        // 按钮
        findViewById(R.id.btn_asr_import).setOnClickListener(v -> importAudio());
        findViewById(R.id.btn_asr_copy).setOnClickListener(v -> copyText());
        findViewById(R.id.btn_asr_tts).setOnClickListener(v -> goToTts());
        findViewById(R.id.btn_asr_clear).setOnClickListener(v -> clearText());

        // 状态
        progressBar = findViewById(R.id.asr_progress);
        tvStatus = findViewById(R.id.tv_asr_status);

        // 引擎
        recorderHelper = new AudioRecorderHelper();
        String apiKey = getApiKey();
        asrExecutor = new AsrExecutor(apiKey);
    }

    private String getApiKey() {
        try {
            return com.mindspore.himindspore.BuildConfig.MIMO_API_KEY;
        } catch (Exception e) {
            return "";
        }
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
        noticeDialog.setContentString(getString(R.string.explain_asr));
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    // ==================== 录音 ====================

    private static final int RC_RECORD_AUDIO = 100;

    private void toggleRecording() {
        if (recorderHelper.isRecording()) {
            stopRecordingAndRecognize();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, RC_RECORD_AUDIO);
            } else {
                startRecording();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音识别", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        String filePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                + "/asr_record_" + System.currentTimeMillis() + ".wav";

        if (!recorderHelper.startRecording(filePath)) {
            Toast.makeText(this, "录音启动失败", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRecord.setText(getString(R.string.asr_record_stop));
        tvRecordStatus.setText(getString(R.string.asr_recording));
        tvDuration.setVisibility(View.VISIBLE);
        progressVolume.setVisibility(View.VISIBLE);
        setStatus("录音中...");

        // 计时器
        startTimerUpdate();
        startVolumeUpdate();
    }

    private void stopRecordingAndRecognize() {
        stopTimerUpdate();
        stopVolumeUpdate();

        String filePath = recorderHelper.stopRecording();
        btnRecord.setText(getString(R.string.asr_record_start));
        tvRecordStatus.setText(getString(R.string.asr_ready));
        tvDuration.setVisibility(View.GONE);
        progressVolume.setVisibility(View.GONE);

        if (filePath == null) {
            setStatus("录音失败");
            return;
        }

        // 识别
        byte[] audioData = AsrExecutor.readAudioFile(filePath);
        if (audioData == null || audioData.length == 0) {
            setStatus("录音文件为空");
            return;
        }

        recognizeAudio(audioData);
    }

    private void startTimerUpdate() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (recorderHelper.isRecording()) {
                    long ms = recorderHelper.getDurationMs();
                    int sec = (int) (ms / 1000);
                    tvDuration.setText(String.format("%02d:%02d", sec / 60, sec % 60));
                    uiHandler.postDelayed(this, 200);
                }
            }
        };
        uiHandler.post(timerRunnable);
    }

    private void stopTimerUpdate() {
        if (timerRunnable != null) uiHandler.removeCallbacks(timerRunnable);
    }

    private void startVolumeUpdate() {
        volumeRunnable = new Runnable() {
            @Override
            public void run() {
                if (recorderHelper.isRecording()) {
                    progressVolume.setProgress(recorderHelper.getAmplitude());
                    uiHandler.postDelayed(this, 100);
                }
            }
        };
        uiHandler.post(volumeRunnable);
    }

    private void stopVolumeUpdate() {
        if (volumeRunnable != null) uiHandler.removeCallbacks(volumeRunnable);
    }

    // ==================== 导入音频 ====================

    private void importAudio() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, RC_PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == RC_PICK_AUDIO) {
            Uri uri = data.getData();
            byte[] audioData = readAudioFromUri(uri);
            if (audioData != null) {
                recognizeAudio(audioData);
            } else {
                Toast.makeText(this, "音频加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private byte[] readAudioFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            return bos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "readAudioFromUri error: " + e.getMessage());
            return null;
        }
    }

    // ==================== 语音识别 ====================

    private void recognizeAudio(byte[] audioData) {
        isRecognizing = true;
        btnRecord.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        setStatus("正在识别语音...");

        asrExecutor.recognizeAsync(audioData, new AsrExecutor.AsrCallback() {
            @Override
            public void onSuccess(String text) {
                runOnUiThread(() -> {
                    isRecognizing = false;
                    btnRecord.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    etText.setText(text);
                    setStatus("识别完成，共 " + text.length() + " 字");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    isRecognizing = false;
                    btnRecord.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    setStatus("识别失败: " + message);
                });
            }
        });
    }

    // ==================== 操作 ====================

    private void copyText() {
        String text = etText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可复制的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("ASR", text));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void goToTts() {
        String text = etText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "请先识别或输入文字", Toast.LENGTH_SHORT).show();
            return;
        }
        // 跳转到 OCR TTS 页面，携带文本
        Intent intent = new Intent(this, com.mindspore.himindspore.ui.tts.OcrTtsActivity.class);
        intent.putExtra("tts_text", text);
        startActivity(intent);
    }

    private void clearText() {
        etText.setText("");
        setStatus("已清空");
    }

    // ==================== 工具 ====================

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimerUpdate();
        stopVolumeUpdate();
        if (recorderHelper != null && recorderHelper.isRecording()) {
            recorderHelper.cancelRecording();
        }
        if (asrExecutor != null) asrExecutor.release();
    }
}
