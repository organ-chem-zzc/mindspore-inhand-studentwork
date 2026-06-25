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

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * 图片融合模型执行器（MindSpore Lite 版本）
 *
 * 注意：此类已弃用，请使用 OnnxFusionExecutor
 * 保留此类是为了向后兼容，实际使用 ONNX Runtime
 *
 * @deprecated 使用 {@link OnnxFusionExecutor} 代替
 */
@Deprecated
public class ImageFusionExecutor {

    private static final String TAG = "ImageFusionExecutor";

    public ImageFusionExecutor(Context context) {
        Log.w(TAG, "ImageFusionExecutor is deprecated. Please use OnnxFusionExecutor instead.");
        throw new UnsupportedOperationException(
            "ImageFusionExecutor is deprecated. Please use OnnxFusionExecutor instead. " +
            "Set useOnnxRuntime = true in ImageFusionMainActivity."
        );
    }

    public ImageFusionResult execute(Bitmap userBitmap, Bitmap presetBitmap) {
        throw new UnsupportedOperationException(
            "ImageFusionExecutor is deprecated. Please use OnnxFusionExecutor instead."
        );
    }

    public long getFullExecutionTime() {
        return 0;
    }

    public long getPreProcessTime() {
        return 0;
    }

    public long getInferenceTime() {
        return 0;
    }

    public long getPostProcessTime() {
        return 0;
    }

    public void free() {
        // 空实现，为了向后兼容
    }
}
