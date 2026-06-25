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

import android.util.Log;

import com.mindspore.himindspore.ui.tts.TtsExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 变声器引擎
 *
 * 流程：原声音频 → ASR 识别文字 → TTS 用目标音色合成 → 变声音频
 */
public class VoiceChangerExecutor {

    private static final String TAG = "VoiceChangerExecutor";

    private AsrExecutor asrExecutor;
    private TtsExecutor ttsExecutor;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public VoiceChangerExecutor(String apiKey) {
        asrExecutor = new AsrExecutor(apiKey);
        ttsExecutor = new TtsExecutor();
        ttsExecutor.setApiKey(apiKey);
    }

    /**
     * 执行变声
     *
     * @param originalAudio 原声音频数据
     * @param targetVoice   目标音色
     * @param speed         语速 (0.5~2.0)
     * @param pitch         音调 (-10~+10)
     * @param callback      结果回调
     */
    public void voiceChange(byte[] originalAudio, String targetVoice,
                             float speed, int pitch, VoiceChangeCallback callback) {
        executor.execute(() -> {
            // 步骤1: ASR 识别
            Log.i(TAG, "Step 1: ASR recognition...");
            String recognizedText = asrExecutor.recognize(originalAudio);
            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                callback.onError("语音识别失败，无法获取文字内容");
                return;
            }
            Log.i(TAG, "ASR result: " + recognizedText.substring(0, Math.min(50, recognizedText.length())));
            callback.onAsrResult(recognizedText);

            // 步骤2: TTS 合成
            Log.i(TAG, "Step 2: TTS synthesis with voice=" + targetVoice
                    + ", speed=" + speed + ", pitch=" + pitch);
            byte[] resultAudio = ttsExecutor.synthesize(recognizedText, targetVoice, speed, pitch);
            if (resultAudio == null) {
                callback.onError("语音合成失败");
                return;
            }
            Log.i(TAG, "TTS result: " + resultAudio.length + " bytes");

            callback.onSuccess(resultAudio, recognizedText);
        });
    }

    public void release() {
        if (asrExecutor != null) asrExecutor.release();
        if (ttsExecutor != null) ttsExecutor.release();
        if (executor != null && !executor.isShutdown()) executor.shutdown();
    }

    public interface VoiceChangeCallback {
        void onAsrResult(String text);
        void onSuccess(byte[] audioData, String text);
        void onError(String message);
    }
}
