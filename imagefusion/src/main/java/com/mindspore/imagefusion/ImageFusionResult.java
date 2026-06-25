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

import android.graphics.Bitmap;

/**
 * 图片融合结果封装类
 */
public class ImageFusionResult {

    private final Bitmap fusedImage;
    private final long preProcessTime;
    private final long inferenceTime;
    private final long postProcessTime;
    private final long totalTime;
    private final String executionLog;

    public ImageFusionResult(Bitmap fusedImage,
                             long preProcessTime,
                             long inferenceTime,
                             long postProcessTime,
                             long totalTime,
                             String executionLog) {
        this.fusedImage = fusedImage;
        this.preProcessTime = preProcessTime;
        this.inferenceTime = inferenceTime;
        this.postProcessTime = postProcessTime;
        this.totalTime = totalTime;
        this.executionLog = executionLog;
    }

    public Bitmap getFusedImage() {
        return fusedImage;
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

    public long getTotalTime() {
        return totalTime;
    }

    public String getExecutionLog() {
        return executionLog;
    }
}
