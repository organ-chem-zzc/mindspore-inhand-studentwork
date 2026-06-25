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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;
import com.mindspore.himindspore.R;
import com.mindspore.himindspore.ui.tts.TtsExecutor;

import java.io.File;
import java.io.IOException;

/**
 * AI 变声器主界面
 *
 * 流程：录制原声 → ASR 识别文字 → 选择目标音色 → TTS 合成变声 → 对比播放 → 导出
 */
@Route(path = "/voice/VoiceChangerActivity")
public class VoiceChangerActivity extends AppCompatActivity {

    private static final String TAG = "VoiceChangerActivity";

    // UI
    private Button btnRecord;
    private TextView tvRecordStatus, tvDuration;
    private LinearLayout panelOriginal;
    private Button btnPlayOriginal;
    private TextView tvOriginalInfo;
    private EditText etText;
    private Spinner spinnerVoice;
    private TextView tvVoiceDesc;
    private SeekBar seekbarSpeed, seekbarPitch;
    private TextView tvSpeedValue, tvPitchValue;
    private Button btnChange;
    private LinearLayout panelResult;
    private Button btnPlayResult;
    private TextView tvResultInfo;
    private Button btnExport;
    private ProgressBar progressBar;
    private TextView tvStatus;

    // 数据
    private AudioRecorderHelper recorderHelper;
    private VoiceChangerExecutor voiceChangerExecutor;
    private MediaPlayer mediaPlayer;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private byte[] originalAudioData;
    private byte[] resultAudioData;
    private boolean isProcessing = false;

    private NoticeDialog noticeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_voice_changer);
        init();
    }

    private void init() {
        Toolbar toolbar = findViewById(R.id.vc_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 录音
        btnRecord = findViewById(R.id.btn_vc_record);
        tvRecordStatus = findViewById(R.id.tv_vc_record_status);
        tvDuration = findViewById(R.id.tv_vc_duration);
        btnRecord.setOnClickListener(v -> toggleRecording());

        // 原声试听
        panelOriginal = findViewById(R.id.panel_vc_original);
        btnPlayOriginal = findViewById(R.id.btn_vc_play_original);
        tvOriginalInfo = findViewById(R.id.tv_vc_original_info);
        btnPlayOriginal.setOnClickListener(v -> playOriginal());

        // 文字
        etText = findViewById(R.id.et_vc_text);

        // 音色
        spinnerVoice = findViewById(R.id.spinner_vc_voice);
        tvVoiceDesc = findViewById(R.id.tv_vc_voice_desc);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TtsExecutor.VOICES);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);
        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                tvVoiceDesc.setText(TtsExecutor.VOICE_DESCS[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 语速
        seekbarSpeed = findViewById(R.id.seekbar_vc_speed);
        tvSpeedValue = findViewById(R.id.tv_vc_speed);
        seekbarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvSpeedValue.setText(String.format("%.1f", 0.5f + progress * 0.1f));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // 音调
        seekbarPitch = findViewById(R.id.seekbar_vc_pitch);
        tvPitchValue = findViewById(R.id.tv_vc_pitch);
        seekbarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvPitchValue.setText(String.valueOf(progress - 10));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // 变声按钮
        btnChange = findViewById(R.id.btn_vc_change);
        btnChange.setOnClickListener(v -> startVoiceChange());

        // 结果试听
        panelResult = findViewById(R.id.panel_vc_result);
        btnPlayResult = findViewById(R.id.btn_vc_play_result);
        tvResultInfo = findViewById(R.id.tv_vc_result_info);
        btnPlayResult.setOnClickListener(v -> playResult());

        // 导出
        btnExport = findViewById(R.id.btn_vc_export);
        btnExport.setOnClickListener(v -> exportResult());

        // 状态
        progressBar = findViewById(R.id.vc_progress);
        tvStatus = findViewById(R.id.tv_vc_status);

        // 引擎
        recorderHelper = new AudioRecorderHelper();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            btnPlayOriginal.setText(getString(R.string.vc_play_original));
            btnPlayResult.setText(getString(R.string.vc_play_result));
        });

        String apiKey = getApiKey();
        voiceChangerExecutor = new VoiceChangerExecutor(apiKey);
    }

    private String getApiKey() {
        try { return com.mindspore.himindspore.BuildConfig.MIMO_API_KEY; }
        catch (Exception e) { return ""; }
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
        noticeDialog.setContentString(getString(R.string.explain_voice_changer));
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    // ==================== 录音 ====================

    private static final int RC_RECORD_AUDIO = 100;

    private void toggleRecording() {
        if (recorderHelper.isRecording()) {
            stopRecording();
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
                Toast.makeText(this, "需要录音权限才能使用变声器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        String filePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                + "/vc_record_" + System.currentTimeMillis() + ".wav";

        if (!recorderHelper.startRecording(filePath)) {
            Toast.makeText(this, "录音启动失败", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRecord.setText(getString(R.string.vc_record_stop));
        tvRecordStatus.setText(getString(R.string.vc_recording));
        tvDuration.setVisibility(View.VISIBLE);
        panelOriginal.setVisibility(View.GONE);
        panelResult.setVisibility(View.GONE);
        btnExport.setVisibility(View.GONE);
        resultAudioData = null;
        setStatus("录音中...");
        startTimerUpdate();
    }

    private void stopRecording() {
        stopTimerUpdate();
        String filePath = recorderHelper.stopRecording();
        btnRecord.setText(getString(R.string.vc_record_start));
        tvRecordStatus.setText(getString(R.string.vc_record_hint));
        tvDuration.setVisibility(View.GONE);

        if (filePath == null) {
            setStatus("录音失败");
            return;
        }

        originalAudioData = AsrExecutor.readAudioFile(filePath);
        if (originalAudioData == null || originalAudioData.length == 0) {
            setStatus("录音文件为空");
            return;
        }

        panelOriginal.setVisibility(View.VISIBLE);
        long durationMs = recorderHelper.getDurationMs();
        tvOriginalInfo.setText("时长: " + (durationMs / 1000) + "s, 大小: "
                + (originalAudioData.length / 1024) + " KB");
        setStatus("录音完成，请点击「开始变声」");
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

    // ==================== 播放 ====================

    private void playOriginal() {
        if (originalAudioData == null) return;
        stopCurrentPlayback();
        playAudioData(originalAudioData, btnPlayOriginal);
    }

    private void playResult() {
        if (resultAudioData == null) return;
        stopCurrentPlayback();
        playAudioData(resultAudioData, btnPlayResult);
    }

    private void stopCurrentPlayback() {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
        } catch (Exception ignored) {}
        btnPlayOriginal.setText(getString(R.string.vc_play_original));
        btnPlayResult.setText(getString(R.string.vc_play_result));
    }

    private void playAudioData(byte[] data, Button btn) {
        try {
            File tempFile = new File(getCacheDir(), "vc_preview.mp3");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            fos.write(data);
            fos.flush();
            fos.close();

            mediaPlayer.reset();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            btn.setText("■ 播放中");
        } catch (IOException e) {
            Log.e(TAG, "playAudio error: " + e.getMessage());
            setStatus("播放失败");
        }
    }

    // ==================== 变声 ====================

    private void startVoiceChange() {
        if (originalAudioData == null) {
            Toast.makeText(this, "请先录制原声", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isProcessing) {
            Toast.makeText(this, "正在处理中...", Toast.LENGTH_SHORT).show();
            return;
        }

        isProcessing = true;
        btnChange.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        panelResult.setVisibility(View.GONE);
        btnExport.setVisibility(View.GONE);

        String voice = TtsExecutor.VOICES[spinnerVoice.getSelectedItemPosition()];
        float speed = 0.5f + seekbarSpeed.getProgress() * 0.1f;
        int pitch = seekbarPitch.getProgress() - 10;

        voiceChangerExecutor.voiceChange(originalAudioData, voice, speed, pitch,
                new VoiceChangerExecutor.VoiceChangeCallback() {
                    @Override
                    public void onAsrResult(String text) {
                        runOnUiThread(() -> {
                            etText.setText(text);
                            setStatus("识别完成: " + text.substring(0, Math.min(30, text.length())) + "...");
                        });
                    }

                    @Override
                    public void onSuccess(byte[] audioData, String text) {
                        runOnUiThread(() -> {
                            isProcessing = false;
                            resultAudioData = audioData;
                            btnChange.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            panelResult.setVisibility(View.VISIBLE);
                            btnExport.setVisibility(View.VISIBLE);
                            tvResultInfo.setText("大小: " + (audioData.length / 1024) + " KB");
                            setStatus("变声完成！");
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            isProcessing = false;
                            btnChange.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            setStatus("变声失败: " + message);
                        });
                    }
                });
    }

    // ==================== 导出 ====================

    private void exportResult() {
        if (resultAudioData == null) {
            Toast.makeText(this, "没有可导出的音频", Toast.LENGTH_SHORT).show();
            return;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "VoiceChanger_Export");
        if (!dir.exists()) dir.mkdirs();

        String fileName = "VC_" + System.currentTimeMillis() + ".mp3";
        File outFile = new File(dir, fileName);

        if (TtsExecutor.saveToFileStatic(resultAudioData, outFile.getAbsolutePath())) {
            Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScan.setData(Uri.fromFile(outFile));
            sendBroadcast(mediaScan);
            Toast.makeText(this, "已导出: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            setStatus("已导出: " + outFile.getAbsolutePath());
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 工具 ====================

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) uiHandler.removeCallbacks(timerRunnable);
        if (recorderHelper != null && recorderHelper.isRecording()) {
            recorderHelper.cancelRecording();
        }
        if (mediaPlayer != null) mediaPlayer.release();
        if (voiceChangerExecutor != null) voiceChangerExecutor.release();
    }
}
