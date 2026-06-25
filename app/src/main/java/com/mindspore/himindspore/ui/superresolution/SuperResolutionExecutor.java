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

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * SuperResolutionExecutor — 图像超分辨率推理引擎
 *
 * 模型接口已预留，当前为 UI 占位模式。
 * 后续导入 .ms 模型文件后，取消注释 MindSpore Lite 推理代码即可。
 *
 * 模型文件放置路径：app/src/main/assets/realesrgan_mobile_quant.ms
 * 输入：64×64 RGB 图片 → 输出：256×256 高清图片（4倍超分）
 */
public class SuperResolutionExecutor {

    private static final String TAG = "SRExecutor";

    // ========== 模型配置（导入模型后修改此处） ==========
    /** 模型文件名（assets 目录下） */
    private static final String MODEL_FILE = "realesrgan_mobile_quant.ms";
    /** 模型输入尺寸 */
    private static final int INPUT_WIDTH = 64;
    private static final int INPUT_HEIGHT = 64;
    /** 模型输出尺寸（4x 超分） */
    private static final int OUTPUT_WIDTH = 256;
    private static final int OUTPUT_HEIGHT = 256;
    /** 推理线程数 */
    private static final int NUM_THREADS = 4;

    private Context mContext;
    private boolean isModelLoaded = false;

    // 计时
    private long preProcessTime;
    private long inferenceTime;
    private long postProcessTime;

    // ===== MindSpore Lite 推理对象（导入模型后取消注释） =====
    // private Model model;
    // private LiteSession session;
    // private MSConfig msConfig;

    public SuperResolutionExecutor(Context context) {
        mContext = context;
    }

    /**
     * 从 assets 加载默认模型
     * @return true 加载成功
     */
    public boolean loadModel() {
        try {
            // ===== 导入模型后取消注释以下代码 =====
            // model = new Model();
            // if (!model.loadModel(mContext, MODEL_FILE)) {
            //     Log.e(TAG, "Failed to load model: " + MODEL_FILE);
            //     return false;
            // }
            // msConfig = new MSConfig();
            // if (!msConfig.init(DeviceType.DT_CPU, NUM_THREADS, CpuBindMode.MID_CPU)) {
            //     Log.e(TAG, "MSConfig init failed");
            //     return false;
            // }
            // session = new LiteSession();
            // if (!session.init(msConfig)) {
            //     Log.e(TAG, "LiteSession init failed");
            //     return false;
            // }
            // if (!session.compileGraph(model)) {
            //     Log.e(TAG, "Compile graph failed");
            //     return false;
            // }
            // model.freeBuffer();

            isModelLoaded = true;
            Log.i(TAG, "Model loaded successfully (placeholder): " + MODEL_FILE);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "loadModel error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从自定义路径加载模型（支持用户选择 .ms 文件）
     * @param modelPath 模型文件绝对路径
     * @return true 加载成功
     */
    public boolean loadModelFromPath(String modelPath) {
        try {
            // ===== 导入模型后取消注释以下代码 =====
            // if (model != null) model.free();
            // model = new Model();
            // if (!model.loadModel(modelPath)) {
            //     Log.e(TAG, "Failed to load model from: " + modelPath);
            //     return false;
            // }
            // msConfig = new MSConfig();
            // msConfig.init(DeviceType.DT_CPU, NUM_THREADS, CpuBindMode.MID_CPU);
            // session = new LiteSession();
            // session.init(msConfig);
            // session.compileGraph(model);
            // model.freeBuffer();

            isModelLoaded = true;
            Log.i(TAG, "Custom model loaded (placeholder): " + modelPath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "loadModelFromPath error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 执行超分辨率推理
     * @param inputBitmap 低分辨率输入图片
     * @return 推理结果（含超分图片和耗时信息），失败返回 null
     */
    public SRResult execute(Bitmap inputBitmap) {
        if (!isModelLoaded) {
            Log.e(TAG, "Model not loaded");
            return null;
        }

        long totalTime = SystemClock.uptimeMillis();

        try {
            // ── 1. 预处理：缩放到输入尺寸 + 归一化 ──
            preProcessTime = SystemClock.uptimeMillis();
            Bitmap resized = Bitmap.createScaledBitmap(inputBitmap, INPUT_WIDTH, INPUT_HEIGHT, true);
            ByteBuffer inputBuffer = bitmapToByteBuffer(resized);
            preProcessTime = SystemClock.uptimeMillis() - preProcessTime;

            // ── 2. 推理 ──
            inferenceTime = SystemClock.uptimeMillis();

            // ===== 导入模型后取消注释以下代码 =====
            // List<MSTensor> inputs = session.getInputs();
            // if (inputs.isEmpty()) {
            //     Log.e(TAG, "Model has no input tensors");
            //     return null;
            // }
            // inputs.get(0).setData(inputBuffer);
            // if (!session.runGraph()) {
            //     Log.e(TAG, "runGraph failed");
            //     return null;
            // }

            // 模拟推理耗时（占位）
            Thread.sleep(150);

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime;

            // ── 3. 后处理：输出转 Bitmap ──
            postProcessTime = SystemClock.uptimeMillis();

            // ===== 导入模型后取消注释以下代码 =====
            // Map<String, MSTensor> outputs = session.getOutputMapByTensor();
            // Bitmap resultBitmap = outputToBitmap(outputs);

            // 占位：将输入图放大作为模拟输出
            Bitmap resultBitmap = Bitmap.createScaledBitmap(inputBitmap, OUTPUT_WIDTH, OUTPUT_HEIGHT, true);

            postProcessTime = SystemClock.uptimeMillis() - postProcessTime;
            totalTime = SystemClock.uptimeMillis() - totalTime;

            Log.d(TAG, String.format("SR done — pre:%dms, infer:%dms, post:%dms, total:%dms",
                    preProcessTime, inferenceTime, postProcessTime, totalTime));

            return new SRResult(resultBitmap, preProcessTime, inferenceTime, postProcessTime, totalTime);

        } catch (Exception e) {
            Log.e(TAG, "execute error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Bitmap → ByteBuffer（RGB 归一化到 [0,1]）
     */
    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_WIDTH * INPUT_HEIGHT * 4);
        buffer.order(ByteOrder.nativeOrder());
        buffer.rewind();

        int[] pixels = new int[INPUT_WIDTH * INPUT_HEIGHT];
        bitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            buffer.putFloat((pixel & 0xFF) / 255.0f);
        }
        buffer.rewind();
        return buffer;
    }

    /**
     * 输出张量 → Bitmap（导入模型后取消注释）
     */
    // private Bitmap outputToBitmap(Map<String, MSTensor> outputs) {
    //     float[] data = null;
    //     for (MSTensor tensor : outputs.values()) {
    //         data = tensor.getFloatData();
    //         break;
    //     }
    //     if (data == null) return null;
    //
    //     Bitmap bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888);
    //     int[] pixels = new int[OUTPUT_WIDTH * OUTPUT_HEIGHT];
    //     for (int i = 0; i < OUTPUT_WIDTH * OUTPUT_HEIGHT; i++) {
    //         int r = clamp((int)(data[i * 3] * 255), 0, 255);
    //         int g = clamp((int)(data[i * 3 + 1] * 255), 0, 255);
    //         int b = clamp((int)(data[i * 3 + 2] * 255), 0, 255);
    //         pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
    //     }
    //     bitmap.setPixels(pixels, 0, OUTPUT_WIDTH, 0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT);
    //     return bitmap;
    // }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    /**
     * 释放资源
     */
    public void release() {
        // ===== 导入模型后取消注释 =====
        // if (session != null) session.free();
        // if (msConfig != null) msConfig.free();
        isModelLoaded = false;
        Log.i(TAG, "SuperResolutionExecutor released");
    }

    // ========== 结果数据类 ==========

    public static class SRResult {
        private final Bitmap resultBitmap;
        private final long preProcessTime;
        private final long inferenceTime;
        private final long postProcessTime;
        private final long totalTime;

        public SRResult(Bitmap resultBitmap, long preProcessTime, long inferenceTime,
                        long postProcessTime, long totalTime) {
            this.resultBitmap = resultBitmap;
            this.preProcessTime = preProcessTime;
            this.inferenceTime = inferenceTime;
            this.postProcessTime = postProcessTime;
            this.totalTime = totalTime;
        }

        public Bitmap getResultBitmap() { return resultBitmap; }
        public long getPreProcessTime() { return preProcessTime; }
        public long getInferenceTime() { return inferenceTime; }
        public long getPostProcessTime() { return postProcessTime; }
        public long getTotalTime() { return totalTime; }

        public String getFormattedLog() {
            return String.format("预处理: %d ms\n推理耗时: %d ms\n后处理: %d ms\n总耗时: %d ms",
                    preProcessTime, inferenceTime, postProcessTime, totalTime);
        }
    }
}
