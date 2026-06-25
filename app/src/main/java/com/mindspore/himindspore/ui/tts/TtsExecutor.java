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

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MiMo-v2.5-TTS-VoiceDesign 语音合成引擎
 *
 * 调用小米 MiMo TTS API 将文本转换为 MP3 音频。
 * 支持 9 种音色、语速/音调控制。
 *
 * API 端点: POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
 * 响应格式: response.choices[0].message.audio.data (base64 编码的 MP3)
 */
public class TtsExecutor {

    private static final String TAG = "TtsExecutor";
    private static final String API_URL = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions";
    private static final String MODEL_NAME = "mimo-v2.5-tts-voicedesign";
    private static final int TIMEOUT_MS = 120000;
    private static final int MAX_RETRIES = 3;

    // API Key（从 BuildConfig 注入，构建时从 local.properties 读取）
    private String apiKey = com.mindspore.himindspore.BuildConfig.MIMO_API_KEY;

    // 音色列表
    public static final String[] VOICES = {
            "mimo_default", "冰糖", "茉莉", "苏打", "白桦",
            "Mia", "Chloe", "Milo", "Dean"
    };

    public static final String[] VOICE_DESCS = {
            "默认音色", "中文女声-甜美", "中文女声-温柔", "中文-活力", "中文-稳重",
            "英文女声", "英文女声", "英文男声", "英文男声"
    };

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public void setApiKey(String key) {
        this.apiKey = key;
    }

    /**
     * 合成语音（异步）
     */
    public void synthesizeAsync(String text, String voice, float speed, int pitch,
                                 TtsCallback callback) {
        executor.execute(() -> {
            byte[] audioData = synthesize(text, voice, speed, pitch);
            if (audioData != null) {
                callback.onSuccess(audioData);
            } else {
                callback.onError("语音合成失败");
            }
        });
    }

    /**
     * 合成语音（同步，需在子线程调用）
     */
    public byte[] synthesize(String text, String voice, float speed, int pitch) {
        if (text == null || text.trim().isEmpty()) {
            Log.e(TAG, "Text is empty");
            return null;
        }

        // 文本分段（每段不超过 500 字）
        String[] segments = splitText(text, 500);
        if (segments.length == 0) return null;

        if (segments.length == 1) {
            return synthesizeSegment(segments[0], voice, speed, pitch);
        }

        // 多段合并
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        for (int i = 0; i < segments.length; i++) {
            byte[] part = synthesizeSegment(segments[i], voice, speed, pitch);
            if (part == null) {
                Log.e(TAG, "Segment " + (i + 1) + " failed");
                return null;
            }
            try {
                merged.write(part);
            } catch (IOException e) {
                Log.e(TAG, "Merge write error: " + e.getMessage());
                return null;
            }
            // 段间间隔
            if (i < segments.length - 1) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return merged.toByteArray();
    }

    /**
     * 合成单段语音
     */
    private byte[] synthesizeSegment(String text, String voice, float speed, int pitch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", MODEL_NAME);

                JSONArray messages = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", "请朗读以下文字");
                messages.put(userMsg);

                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", text);
                messages.put(assistantMsg);

                payload.put("messages", messages);
                payload.put("modalities", new JSONArray().put("text").put("audio"));

                JSONObject audioConfig = new JSONObject();
                audioConfig.put("format", "mp3");
                audioConfig.put("speed", speed);
                audioConfig.put("pitch", pitch);
                payload.put("audio", audioConfig);

                if (voice != null && !voice.equals("mimo_default")) {
                    payload.put("voice", voice);
                }

                String response = httpPost(API_URL, payload.toString());
                if (response == null) continue;

                JSONObject result = new JSONObject(response);
                String audioData = result.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getJSONObject("audio")
                        .getString("data");

                return android.util.Base64.decode(audioData, android.util.Base64.DEFAULT);

            } catch (Exception e) {
                Log.e(TAG, "synthesize attempt " + (attempt + 1) + " error: " + e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }
        return null;
    }

    /**
     * 保存音频到文件（静态方法，不依赖实例）
     */
    public static boolean saveToFileStatic(byte[] audioData, String filePath) {
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(audioData);
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            Log.e("TtsExecutor", "Save error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存音频到文件
     */
    public boolean saveToFile(byte[] audioData, String filePath) {
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(audioData);
            fos.flush();
            fos.close();
            Log.i(TAG, "Audio saved: " + filePath + " (" + audioData.length + " bytes)");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Save error: " + e.getMessage());
            return false;
        }
    }

    /**
     * HTTP POST 请求
     */
    private String httpPost(String urlStr, String jsonBody) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                return readStream(conn.getInputStream());
            } else if (code == 429) {
                Log.w(TAG, "Rate limited, waiting 10s...");
                Thread.sleep(10000);
                return null;
            } else {
                InputStream errStream = conn.getErrorStream();
                String errBody = errStream != null ? readStream(errStream) : "no error body";
                Log.e(TAG, "HTTP " + code + ": " + errBody);
                return null;
            }
        } catch (InterruptedException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    /**
     * 文本分段
     */
    private String[] splitText(String text, int maxChars) {
        if (text.length() <= maxChars) return new String[]{text};

        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (current.length() + line.length() + 1 > maxChars) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                }
                // 超长行按句子切分
                if (line.length() > maxChars) {
                    String[] subParts = splitBySentence(line, maxChars);
                    for (String sp : subParts) parts.add(sp);
                    current.setLength(0);
                } else {
                    current = new StringBuilder(line);
                }
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(line);
            }
        }
        if (current.length() > 0) parts.add(current.toString());

        return parts.toArray(new String[0]);
    }

    private String[] splitBySentence(String text, int maxChars) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String delimiters = "。！？；.!?;";
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int best = end;
                for (int i = end - 1; i > start + maxChars / 2; i--) {
                    if (delimiters.indexOf(text.charAt(i)) >= 0) {
                        best = i + 1;
                        break;
                    }
                }
                end = best;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts.toArray(new String[0]);
    }

    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public interface TtsCallback {
        void onSuccess(byte[] audioData);
        void onError(String message);
    }
}
