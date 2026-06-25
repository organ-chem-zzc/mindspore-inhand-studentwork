package com.mindspore.imagefusion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.InputStream;
import java.nio.FloatBuffer;

/**
 * 使用 ONNX Runtime 进行图像融合推理
 * 避免 MindSpore Lite 的量化和 Resize 算子问题
 */
public class OnnxFusionExecutor {
    private static final String TAG = "OnnxFusionExecutor";
    private static final int IMAGE_SIZE = 256;

    private Context mContext;
    private OrtEnvironment env;
    private OrtSession session;

    private long fullExecutionTime;
    private long preProcessTime;
    private long inferenceTime;
    private long postProcessTime;

    public OnnxFusionExecutor(Context context) {
        mContext = context;
        init();
    }

    private void init() {
        try {
            long startTime, endTime;

            // 创建 ONNX Runtime 环境
            startTime = System.currentTimeMillis();
            env = OrtEnvironment.getEnvironment();
            endTime = System.currentTimeMillis();
            Log.d(TAG, "Environment created in " + (endTime - startTime) + "ms");

            // 从 assets 加载模型
            startTime = System.currentTimeMillis();
            InputStream modelStream = mContext.getAssets().open("model_v2_fixed_resized.onnx");
            int modelSize = modelStream.available();
            Log.d(TAG, "Model size: " + (modelSize / 1024 / 1024) + "MB");

            byte[] modelBytes = new byte[modelSize];
            modelStream.read(modelBytes);
            modelStream.close();
            endTime = System.currentTimeMillis();
            Log.d(TAG, "Model loaded in " + (endTime - startTime) + "ms");

            // 创建会话选项
            startTime = System.currentTimeMillis();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();

            // 设置优化级别（启用所有优化）
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            // 设置执行模式（顺序执行，更快）
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);

            // 设置线程数
            int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
            options.setIntraOpNumThreads(numThreads);
            options.setInterOpNumThreads(1);

            Log.d(TAG, "Using " + numThreads + " threads for inference");

            // 创建会话
            session = env.createSession(modelBytes, options);
            endTime = System.currentTimeMillis();
            Log.d(TAG, "Session created in " + (endTime - startTime) + "ms");

            Log.d(TAG, "ONNX Runtime initialized successfully");
            Log.d(TAG, "Input names: " + session.getInputNames());
            Log.d(TAG, "Output names: " + session.getOutputNames());

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e);
        }
    }

    public Bitmap execute(Bitmap userBitmap, Bitmap presetBitmap) {
        fullExecutionTime = System.currentTimeMillis();
        
        try {
            // 预处理
            long startTime = System.currentTimeMillis();
            
            // 缩放图像到 256x256
            Bitmap personBitmap = Bitmap.createScaledBitmap(userBitmap, IMAGE_SIZE, IMAGE_SIZE, true);
            Bitmap sceneBitmap = Bitmap.createScaledBitmap(presetBitmap, IMAGE_SIZE, IMAGE_SIZE, true);
            
            // 转换为 float 数组，归一化到 [-1, 1]，CHW 格式
            float[] personData = bitmapToFloatArray(personBitmap);
            float[] sceneData = bitmapToFloatArray(sceneBitmap);
            
            preProcessTime = System.currentTimeMillis() - startTime;
            
            // 创建输入张量
            long[] shape = {1, 3, IMAGE_SIZE, IMAGE_SIZE};
            OnnxTensor personTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(personData), shape);
            OnnxTensor sceneTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(sceneData), shape);
            
            // 推理
            startTime = System.currentTimeMillis();
            OrtSession.Result result = session.run(
                new java.util.HashMap<String, OnnxTensor>() {{
                    put("person", personTensor);
                    put("scene", sceneTensor);
                }}
            );
            inferenceTime = System.currentTimeMillis() - startTime;
            
            // 获取输出
            startTime = System.currentTimeMillis();
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            float[] outputData = outputTensor.getFloatBuffer().array();
            
            // 后处理：转换为 Bitmap
            // 调试：输出统计信息
            float minVal = Float.MAX_VALUE, maxVal = Float.MIN_VALUE, sum = 0;
            for (float v : outputData) {
                minVal = Math.min(minVal, v);
                maxVal = Math.max(maxVal, v);
                sum += v;
            }
            Log.d(TAG, String.format("Output stats: min=%.2f, max=%.2f, mean=%.2f",
                minVal, maxVal, sum / outputData.length));

            Bitmap resultBitmap = floatArrayToBitmap(outputData);
            
            postProcessTime = System.currentTimeMillis() - startTime;
            fullExecutionTime = System.currentTimeMillis() - fullExecutionTime;
            
            // 清理
            personTensor.close();
            sceneTensor.close();
            outputTensor.close();
            
            Log.d(TAG, String.format("Execution times: pre=%dms, infer=%dms, post=%dms, total=%dms",
                preProcessTime, inferenceTime, postProcessTime, fullExecutionTime));
            
            return resultBitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Inference failed", e);
            return null;
        }
    }

    /**
     * 将 Bitmap 转换为 float 数组
     * 归一化到 [-1, 1]，CHW 格式
     */
    private float[] bitmapToFloatArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float[] data = new float[3 * width * height];
        
        int index = 0;
        // R channel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                float r = Color.red(pixel) / 127.5f - 1.0f;
                data[index++] = r;
            }
        }
        
        // G channel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                float g = Color.green(pixel) / 127.5f - 1.0f;
                data[index++] = g;
            }
        }
        
        // B channel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                float b = Color.blue(pixel) / 127.5f - 1.0f;
                data[index++] = b;
            }
        }
        
        return data;
    }

    /**
     * 将 float 数组转换为 Bitmap
     * 反归一化：[-1, 1] -> [0, 255]，CHW -> HWC
     */
    private Bitmap floatArrayToBitmap(float[] data) {
        int totalPixels = IMAGE_SIZE * IMAGE_SIZE;
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888);

        // 提取各通道
        float[] rChannel = new float[totalPixels];
        float[] gChannel = new float[totalPixels];
        float[] bChannel = new float[totalPixels];

        System.arraycopy(data, 0, rChannel, 0, totalPixels);
        System.arraycopy(data, totalPixels, gChannel, 0, totalPixels);
        System.arraycopy(data, totalPixels * 2, bChannel, 0, totalPixels);

        // 计算各通道的均值（用于色彩校正）
        float rMean = 0, gMean = 0, bMean = 0;
        for (int i = 0; i < totalPixels; i++) {
            rMean += rChannel[i];
            gMean += gChannel[i];
            bMean += bChannel[i];
        }
        rMean /= totalPixels;
        gMean /= totalPixels;
        bMean /= totalPixels;

        // 色彩校正：将各通道均值调整到 0
        Log.d(TAG, String.format("Channel means before correction: R=%.3f, G=%.3f, B=%.3f",
            rMean, gMean, bMean));

        // 转换为像素
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int index = y * IMAGE_SIZE + x;

                // 应用色彩校正
                float rVal = rChannel[index] - rMean;
                float gVal = gChannel[index] - gMean;
                float bVal = bChannel[index] - bMean;

                // 反归一化
                int r = (int) ((rVal + 1.0f) * 127.5f);
                int g = (int) ((gVal + 1.0f) * 127.5f);
                int b = (int) ((bVal + 1.0f) * 127.5f);

                // 限制范围
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                bitmap.setPixel(x, y, Color.argb(255, r, g, b));
            }
        }

        return bitmap;
    }

    public long getFullExecutionTime() {
        return fullExecutionTime;
    }

    public long getPreProcessTime() {
        return preProcessTime;
    }

    public long getInferenceTime() {
        return inferenceTime;
    }

    public long getPostProcessTime() {
        return postProcessTime;
    }
}
