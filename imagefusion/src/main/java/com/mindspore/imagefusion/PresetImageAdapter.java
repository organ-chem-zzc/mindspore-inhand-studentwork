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
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 预设图片选择适配器
 */
public class PresetImageAdapter extends RecyclerView.Adapter<PresetImageAdapter.ViewHolder> {

    private static final String TAG = "PresetImageAdapter";
    private Context context;
    private int[] imageResources;
    private OnPresetImageSelectedListener listener;
    private int selectedPosition = -1;

    public interface OnPresetImageSelectedListener {
        void onPresetImageSelected(int position, Bitmap bitmap);
    }

    public PresetImageAdapter(Context context, int[] imageResources, OnPresetImageSelectedListener listener) {
        this.context = context;
        this.imageResources = imageResources;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_preset_image, parent, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int imageRes = imageResources[position];
        holder.imageView.setImageResource(imageRes);

        // 设置选中状态
        if (position == selectedPosition) {
            holder.imageView.setBackgroundResource(R.drawable.preset_image_selected);
            // 添加选中时的缩放效果
            holder.imageView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
        } else {
            holder.imageView.setBackgroundResource(R.drawable.preset_image_normal);
            // 恢复正常大小
            holder.imageView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
        
        // 只在 onBindViewHolder 中设置点击监听器
        holder.imageView.setOnClickListener(v -> {
            int clickedPosition = holder.getAdapterPosition();
            if (clickedPosition != RecyclerView.NO_POSITION) {
                Log.d(TAG, "Preset image clicked at position: " + clickedPosition);
                selectedPosition = clickedPosition;
                notifyDataSetChanged();
                if (listener != null) {
                    try {
                        int clickedImageRes = imageResources[clickedPosition];
                        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), clickedImageRes);
                        Log.d(TAG, "Bitmap decoded: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
                        listener.onPresetImageSelected(clickedPosition, bitmap);
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding bitmap at position " + clickedPosition + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "Listener is null, cannot notify selection");
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageResources.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img_preset);
        }
    }
}
