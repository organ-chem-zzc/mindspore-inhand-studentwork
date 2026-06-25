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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 超分辨率结果对比 View
 *
 * 左侧显示原图，右侧显示超分结果，中间有一条可拖动的分割线。
 * 用户可以左右滑动来对比原图和超分后的效果。
 */
public class SuperResolutionResultView extends View {

    private Bitmap originalBitmap;
    private Bitmap resultBitmap;

    private Paint borderPaint;
    private Paint linePaint;
    private Paint textPaint;
    private Paint textBgPaint;

    /** 分割线位置比例 0.0 ~ 1.0，默认中间 */
    private float splitRatio = 0.5f;
    private boolean isDragging = false;

    // 标签
    private static final String LABEL_ORIGINAL = "原图";
    private static final String LABEL_RESULT = "超分";

    public SuperResolutionResultView(Context context) {
        super(context);
        init();
    }

    public SuperResolutionResultView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SuperResolutionResultView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(Color.WHITE);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setColor(Color.WHITE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(32f);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(0x99000000);
    }

    /**
     * 设置对比图片
     */
    public void setImages(Bitmap original, Bitmap result) {
        this.originalBitmap = original;
        this.resultBitmap = result;
        this.splitRatio = 0.5f;
        invalidate();
    }

    /**
     * 重置到中间位置
     */
    public void resetSplit() {
        this.splitRatio = 0.5f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (originalBitmap == null || resultBitmap == null) {
            // 无图片时绘制占位
            drawPlaceholder(canvas);
            return;
        }

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW == 0 || viewH == 0) return;

        int splitX = (int) (viewW * splitRatio);

        // ── 绘制左侧原图 ──
        Rect srcLeft = new Rect(0, 0,
                (int)(originalBitmap.getWidth() * splitRatio), originalBitmap.getHeight());
        Rect dstLeft = new Rect(0, 0, splitX, viewH);
        canvas.drawBitmap(originalBitmap, srcLeft, dstLeft, null);

        // ── 绘制右侧超分结果 ──
        int resultSplitX = (int)(resultBitmap.getWidth() * splitRatio);
        Rect srcRight = new Rect(resultSplitX, 0, resultBitmap.getWidth(), resultBitmap.getHeight());
        Rect dstRight = new Rect(splitX, 0, viewW, viewH);
        canvas.drawBitmap(resultBitmap, srcRight, dstRight, null);

        // ── 绘制分割线 ──
        canvas.drawLine(splitX, 0, splitX, viewH, linePaint);

        // ── 绘制拖动手柄（圆圈） ──
        float handleY = viewH / 2f;
        float handleRadius = 24f;
        Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setShadowLayer(6f, 0, 0, 0x80000000);
        canvas.drawCircle(splitX, handleY, handleRadius, handlePaint);

        // 绘制手柄中的左右箭头
        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.DKGRAY);
        arrowPaint.setStrokeWidth(3f);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        // 左箭头
        canvas.drawLine(splitX - 8, handleY, splitX - 16, handleY, arrowPaint);
        canvas.drawLine(splitX - 16, handleY, splitX - 12, handleY - 5, arrowPaint);
        canvas.drawLine(splitX - 16, handleY, splitX - 12, handleY + 5, arrowPaint);
        // 右箭头
        canvas.drawLine(splitX + 8, handleY, splitX + 16, handleY, arrowPaint);
        canvas.drawLine(splitX + 16, handleY, splitX + 12, handleY - 5, arrowPaint);
        canvas.drawLine(splitX + 16, handleY, splitX + 12, handleY + 5, arrowPaint);

        // ── 绘制标签 ──
        drawLabel(canvas, LABEL_ORIGINAL, splitX / 2f, 50f);
        drawLabel(canvas, LABEL_RESULT, splitX + (viewW - splitX) / 2f, 50f);
    }

    private void drawPlaceholder(Canvas canvas) {
        Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        placeholderPaint.setColor(0xFFE6E6E6);
        canvas.drawRect(0, 0, getWidth(), getHeight(), placeholderPaint);

        Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.GRAY);
        hintPaint.setTextSize(36f);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("选择图片后，左右滑动对比原图与超分结果",
                getWidth() / 2f, getHeight() / 2f, hintPaint);
    }

    private void drawLabel(Canvas canvas, String text, float cx, float cy) {
        float textWidth = textPaint.measureText(text);
        float padding = 16f;
        RectF bgRect = new RectF(
                cx - textWidth / 2 - padding,
                cy - 24 - padding / 2,
                cx + textWidth / 2 + padding,
                cy + padding / 2);
        canvas.drawRoundRect(bgRect, 8f, 8f, textBgPaint);
        canvas.drawText(text, cx, cy, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (originalBitmap == null || resultBitmap == null) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检查是否在分割线附近
                float splitX = getWidth() * splitRatio;
                if (Math.abs(event.getX() - splitX) < 60) {
                    isDragging = true;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    splitRatio = Math.max(0.05f, Math.min(0.95f, event.getX() / getWidth()));
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return super.onTouchEvent(event);
    }
}
