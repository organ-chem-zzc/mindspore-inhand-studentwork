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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 录音工具类
 *
 * 使用 AudioRecord 录制 PCM 数据并保存为 WAV 格式。
 * 采样率 16kHz，单声道，16bit — 与 MiMo ASR API 兼容。
 *
 * 为什么用 WAV 而非 M4A/AAC：
 * MiMo-v2.5-ASR 仅支持 WAV 和 MP3 格式，M4A 会导致识别失败。
 */
public class AudioRecorderHelper {

    private static final String TAG = "AudioRecorderHelper";

    // 录音参数
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private int bufferSize;
    private String outputPath;
    private boolean isRecording = false;
    private long startTime = 0;
    private volatile int maxAmplitude = 0;
    private Thread recordThread;

    /**
     * 开始录音
     * @param outputFilePath 输出文件路径（.wav）
     * @return true 成功开始
     */
    public boolean startRecording(String outputFilePath) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return false;
        }

        try {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Invalid buffer size");
                return false;
            }

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed");
                audioRecord.release();
                audioRecord = null;
                return false;
            }

            outputPath = outputFilePath;
            File outFile = new File(outputFilePath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            maxAmplitude = 0;
            audioRecord.startRecording();
            isRecording = true;
            startTime = System.currentTimeMillis();

            // 后台线程写入 WAV 文件
            recordThread = new Thread(() -> writeWavFile(outputFilePath), "AudioRecorder");
            recordThread.start();

            Log.i(TAG, "Recording started: " + outputFilePath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "startRecording error: " + e.getMessage());
            release();
            return false;
        }
    }

    /**
     * 停止录音
     * @return 录音文件路径，失败返回 null
     */
    public String stopRecording() {
        if (!isRecording || audioRecord == null) {
            Log.w(TAG, "Not recording");
            return null;
        }

        isRecording = false;

        try {
            audioRecord.stop();
        } catch (Exception e) {
            Log.e(TAG, "stopRecording error: " + e.getMessage());
        }

        // 等待写入线程结束
        if (recordThread != null) {
            try { recordThread.join(2000); } catch (InterruptedException ignored) {}
        }

        release();

        Log.i(TAG, "Recording stopped: " + outputPath + " (" + getDurationMs() + "ms)");
        return outputPath;
    }

    /**
     * 取消录音（删除文件）
     */
    public void cancelRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            release();
        }
        if (recordThread != null) {
            try { recordThread.join(2000); } catch (InterruptedException ignored) {}
        }
        if (outputPath != null) {
            new File(outputPath).delete();
            outputPath = null;
        }
    }

    /**
     * 写入 WAV 文件（PCM → WAV 格式）
     */
    private void writeWavFile(String filePath) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath);

            // 写入占位的 WAV 头（44 字节），后面再回填
            fos.write(new byte[44]);

            byte[] buffer = new byte[bufferSize];
            int totalBytes = 0;

            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    fos.write(buffer, 0, read);
                    totalBytes += read;

                    // 计算最大振幅
                    int max = 0;
                    for (int i = 0; i < read - 1; i += 2) {
                        int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
                        int abs = Math.abs(sample);
                        if (abs > max) max = abs;
                    }
                    maxAmplitude = max;
                }
            }

            fos.flush();

            // 回填 WAV 头
            fos.close();
            fos = null;
            fillWavHeader(filePath, totalBytes);

        } catch (IOException e) {
            Log.e(TAG, "writeWavFile error: " + e.getMessage());
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 回填 WAV 文件头
     */
    private void fillWavHeader(String filePath, int dataBytes) {
        try {
            RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
            int totalBytes = dataBytes + 36;

            // RIFF header
            raf.writeBytes("RIFF");
            raf.write(intToLittleEndian(totalBytes));
            raf.writeBytes("WAVE");

            // fmt chunk
            raf.writeBytes("fmt ");
            raf.write(intToLittleEndian(16));           // chunk size
            raf.write(shortToLittleEndian((short) 1));   // PCM format
            raf.write(shortToLittleEndian((short) 1));   // mono
            raf.write(intToLittleEndian(SAMPLE_RATE));   // sample rate
            raf.write(intToLittleEndian(SAMPLE_RATE * 2)); // byte rate
            raf.write(shortToLittleEndian((short) 2));   // block align
            raf.write(shortToLittleEndian((short) 16));  // bits per sample

            // data chunk
            raf.writeBytes("data");
            raf.write(intToLittleEndian(dataBytes));

            raf.close();
        } catch (IOException e) {
            Log.e(TAG, "fillWavHeader error: " + e.getMessage());
        }
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }

    /**
     * 获取当前录音时长（毫秒）
     */
    public long getDurationMs() {
        if (!isRecording) return 0;
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 获取当前录音音量振幅（0~32767）
     * 用于 UI 显示音量波形
     */
    public int getAmplitude() {
        return maxAmplitude;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public String getOutputPath() {
        return outputPath;
    }

    private void release() {
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }
}
