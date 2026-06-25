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

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
 * MiMo-v2.5-ASR 语音识别引擎
 *
 * 官方 API 格式（已验证）：
 * - 端点: POST https://api.xiaomimimo.com/v1/chat/completions
 * - 认证: api-key: {API_KEY}
 * - 模型: mimo-v2.5-asr
 * - 输入: messages[0].content[0].input_audio.data = "data:{MIME};base64,{BASE64}"
 * - 参数: asr_options.language = "zh" | "en" | "auto"
 * - 响应: response.choices[0].message.content = 识别文字
 *
 * 支持格式: WAV (audio/wav), MP3 (audio/mpeg)
 * Base64 上限: 10MB
 */
public class AsrExecutor {

    private static final String TAG = "AsrExecutor";
    private static final String API_URL = "https://api.xiaomimimo.com/v1/chat/completions";
    private static final String MODEL_NAME = "mimo-v2.5-asr";
    private static final int TIMEOUT_MS = 120000;

    private String apiKey;
    private String language;  // "zh", "en", "auto"
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public AsrExecutor(String apiKey) {
        this(apiKey, "zh");
    }

    public AsrExecutor(String apiKey, String language) {
        this.apiKey = apiKey;
        this.language = language;
    }

    /**
     * 异步语音识别
     */
    public void recognizeAsync(byte[] audioData, AsrCallback callback) {
        executor.execute(() -> {
            String result = recognize(audioData);
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError("语音识别失败，请检查网络或API配置");
            }
        });
    }

    /**
     * 异步语音识别（指定 MIME 类型）
     */
    public void recognizeAsync(byte[] audioData, String mimeType, AsrCallback callback) {
        executor.execute(() -> {
            String result = recognize(audioData, mimeType);
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError("语音识别失败，请检查网络或API配置");
            }
        });
    }

    /**
     * 同步语音识别（默认 WAV 格式）
     */
    public String recognize(byte[] audioData) {
        return recognize(audioData, "audio/wav");
    }

    /**
     * 同步语音识别（需在子线程调用）
     *
     * @param audioData 音频原始数据
     * @param mimeType  MIME 类型: "audio/wav", "audio/mpeg", "audio/mp3"
     * @return 识别文字，失败返回 null
     */
    public String recognize(byte[] audioData, String mimeType) {
        if (audioData == null || audioData.length == 0) {
            Log.e(TAG, "Audio data is empty");
            return null;
        }

        // 检查 base64 大小限制 (10MB)
        int base64Len = (int) (audioData.length * 4.0 / 3) + 4;
        if (base64Len > 10 * 1024 * 1024) {
            Log.e(TAG, "Base64 size exceeds 10MB limit: " + (base64Len / 1024 / 1024) + "MB");
            return null;
        }

        String base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP);
        String dataUrl = "data:" + mimeType + ";base64," + base64Audio;
        Log.i(TAG, "Audio: " + audioData.length + " bytes, mime: " + mimeType
                + ", dataUrl: " + base64Len + " chars");

        try {
            // 构建请求体（官方格式）
            JSONObject payload = new JSONObject();
            payload.put("model", MODEL_NAME);

            // messages
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");

            JSONArray content = new JSONArray();
            JSONObject audioItem = new JSONObject();
            audioItem.put("type", "input_audio");
            JSONObject audioObj = new JSONObject();
            audioObj.put("data", dataUrl);
            audioItem.put("input_audio", audioObj);
            content.put(audioItem);

            userMsg.put("content", content);
            messages.put(userMsg);
            payload.put("messages", messages);

            // asr_options
            JSONObject asrOptions = new JSONObject();
            asrOptions.put("language", language);
            payload.put("asr_options", asrOptions);

            // 发送请求（使用 api-key header）
            String response = httpPost(payload.toString());
            if (response == null) {
                Log.e(TAG, "HTTP request failed");
                return null;
            }

            // 解析响应
            JSONObject result = new JSONObject(response);
            if (result.has("choices")) {
                String text = result.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                Log.i(TAG, "ASR success: " + text.substring(0, Math.min(80, text.length())));
                return text;
            } else {
                Log.e(TAG, "Unexpected response: " + response.substring(0, Math.min(200, response.length())));
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "recognize error: " + e.getMessage());
            return null;
        }
    }

    /**
     * HTTP POST 请求（使用 api-key 认证）
     */
    private String httpPost(String jsonBody) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("api-key", apiKey);  // 官方认证方式
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
            } else {
                InputStream errStream = conn.getErrorStream();
                String err = errStream != null ? readStream(errStream) : "no error body";
                Log.e(TAG, "HTTP " + code + ": " + err.substring(0, Math.min(300, err.length())));
                return null;
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 读取音频文件为 byte[]
     */
    public static byte[] readAudioFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            fis.close();
            return bos.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "readAudioFile error: " + e.getMessage());
            return null;
        }
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public interface AsrCallback {
        void onSuccess(String text);
        void onError(String message);
    }
}
