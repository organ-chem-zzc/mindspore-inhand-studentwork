# 方案A：图像超分辨率（Real-ESRGAN Mobile）完整实现计划

## 一、项目目标

在现有 MindSpore InHand 掌中宝 App 中新增 **图像超分辨率** 功能模块：
- 用户选择/拍摄低分辨率图片 → 一键生成 4 倍高清图片
- 模型在华为云 ModelArts 上使用 MindSpore 框架训练
- INT8 量化压缩后部署到 Android 手机端（MindSpore Lite 推理）
- 目标模型大小 ≤ 10MB，推理耗时 ≤ 300ms（骁龙 865 级别）

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    华为云 ModelArts                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐              │
│  │ 数据准备  │──→│ 模型训练  │──→│ 模型导出  │              │
│  │(OBS存储)  │   │(MindSpore│   │(ONNX→.ms)│              │
│  └──────────┘   │  GPU集群) │   └──────────┘              │
│                  └──────────┘                             │
└────────────────────────┬────────────────────────────────┘
                         │ 下载 .ms 模型文件
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    本地开发环境                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐              │
│  │ 量化压缩  │──→│ 模型验证  │──→│ Android  │              │
│  │(INT8 PTQ)│   │(PSNR测试)│   │  集成    │              │
│  └──────────┘   └──────────┘   └──────────┘              │
└─────────────────────────────────────────────────────────┘
                         │ adb install
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   Android 手机                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐              │
│  │ 选图/拍照 │──→│ MindSpore│──→│ 超分结果 │              │
│  │          │   │Lite 推理 │   │ 对比展示  │              │
│  └──────────┘   └──────────┘   └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

---

## 三、详细步骤

### 阶段 1：华为云环境准备（Day 1）

#### 1.1 注册与配置
- [ ] 注册华为云账号并完成实名认证
- [ ] 开通 ModelArts 服务
- [ ] 创建 OBS（对象存储）桶用于存放训练数据和模型
- [ ] 获取 AK/SK（访问密钥）

#### 1.2 ModelArts 开发环境
- [ ] 创建 ModelArts Notebook 实例（选择 MindSpore 2.x + GPU 环境）
- [ ] 验证 MindSpore 安装：`python -c "import mindspore; print(mindspore.__version__)"`
- [ ] 验证 GPU 可用：`nvidia-smi`

#### 1.3 数据集准备
- [ ] 下载 DIV2K 数据集（高分辨率图片 2K 分辨率）
  - 训练集：800 张
  - 验证集：100 张
- [ ] 下载 Flickr2K 数据集（2650 张高分辨率图片）
- [ ] 上传至 OBS 桶：`obs://your-bucket/datasets/super_resolution/`
- [ ] 数据预处理脚本：降采样生成 LR（低分辨率）/ HR（高分辨率）训练对
  - 降采样因子：4x（bicubic）
  - 裁剪 patch：64×64（LR）→ 256×256（HR）

```python
# 数据预处理脚本框架
import mindspore.dataset as ds
import mindspore.dataset.vision as vision

def create_dataset(obs_path, batch_size=16, patch_size=64, scale=4):
    """创建训练数据集"""
    dataset = ds.ObsDataset(obs_path)
    # LR: patch_size x patch_size
    # HR: (patch_size * scale) x (patch_size * scale)
    transforms_lr = [vision.Resize(patch_size), vision.Normalize(mean=[0,0,0], std=[255,255,255])]
    transforms_hr = [vision.Resize(patch_size * scale), vision.Normalize(mean=[0,0,0], std=[255,255,255])]
    dataset = dataset.map(operations=transforms_lr, input_columns=["LR"])
    dataset = dataset.map(operations=transforms_hr, input_columns=["HR"])
    dataset = dataset.batch(batch_size)
    return dataset
```

---

### 阶段 2：模型训练（Day 2-3）

#### 2.1 模型选择：Real-ESRGAN Mobile
采用轻量级 RRDB（Residual-in-Residual Dense Block）网络，针对移动端优化：
- 移除 BN 层（减少计算量）
- 通道数：64 → 32（减半）
- 残差块数：16 → 6（减少层数）
- 使用 LeakyReLU 替代 ReLU

#### 2.2 网络结构

```python
import mindspore.nn as nn
import mindspore.ops as ops

class ResidualDenseBlock(nn.Cell):
    """轻量级残差密集块"""
    def __init__(self, nf=32, gc=16):
        super().__init__()
        self.conv1 = nn.Conv2d(nf, gc, 3, 1, padding=1, pad_mode='pad')
        self.conv2 = nn.Conv2d(nf + gc, gc, 3, 1, padding=1, pad_mode='pad')
        self.conv3 = nn.Conv2d(nf + 2 * gc, gc, 3, 1, padding=1, pad_mode='pad')
        self.conv4 = nn.Conv2d(nf + 3 * gc, gc, 3, 1, padding=1, pad_mode='pad')
        self.conv5 = nn.Conv2d(nf + 4 * gc, nf, 3, 1, padding=1, pad_mode='pad')
        self.lrelu = nn.LeakyReLU(0.2)

    def construct(self, x):
        x1 = self.lrelu(self.conv1(x))
        x2 = self.lrelu(self.conv2(ops.concat((x, x1), 1)))
        x3 = self.lrelu(self.conv3(ops.concat((x, x1, x2), 1)))
        x4 = self.lrelu(self.conv4(ops.concat((x, x1, x2, x3), 1)))
        x5 = self.conv5(ops.concat((x, x1, x2, x3, x4), 1))
        return x5 * 0.2 + x  # 残差缩放


class RRDB(nn.Cell):
    """Residual-in-Residual Dense Block"""
    def __init__(self, nf=32):
        super().__init__()
        self.rdb1 = ResidualDenseBlock(nf)
        self.rdb2 = ResidualDenseBlock(nf)
        self.rdb3 = ResidualDenseBlock(nf)

    def construct(self, x):
        out = self.rdb1(x)
        out = self.rdb2(out)
        out = self.rdb3(out)
        return out * 0.2 + x


class RealESRGANMobile(nn.Cell):
    """Real-ESRGAN Mobile 超分辨率网络"""
    def __init__(self, in_nc=3, out_nc=3, nf=32, num_blocks=6, scale=4):
        super().__init__()
        self.conv_first = nn.Conv2d(in_nc, nf, 3, 1, padding=1, pad_mode='pad')

        # RRDB blocks
        self.body = nn.SequentialCell([RRDB(nf) for _ in range(num_blocks)])
        self.conv_body = nn.Conv2d(nf, nf, 3, 1, padding=1, pad_mode='pad')

        # Upsample (PixelShuffle)
        self.upconv1 = nn.Conv2d(nf, nf * 4, 3, 1, padding=1, pad_mode='pad')
        self.pixel_shuffle1 = nn.PixelShuffle(upscale_factor=2)
        self.upconv2 = nn.Conv2d(nf, nf * 4, 3, 1, padding=1, pad_mode='pad')
        self.pixel_shuffle2 = nn.PixelShuffle(upscale_factor=2)
        self.lrelu = nn.LeakyReLU(0.2)

        # Final output
        self.conv_hr = nn.Conv2d(nf, nf, 3, 1, padding=1, pad_mode='pad')
        self.conv_last = nn.Conv2d(nf, out_nc, 3, 1, padding=1, pad_mode='pad)

    def construct(self, x):
        feat = self.conv_first(x)
        body_feat = self.conv_body(self.body(feat))
        feat = feat + body_feat  # 全局残差

        # 2次 2x 上采样 = 4x
        feat = self.lrelu(self.pixel_shuffle1(self.upconv1(feat)))
        feat = self.lrelu(self.pixel_shuffle2(self.upconv2(feat)))

        out = self.conv_last(self.lrelu(self.conv_hr(feat)))
        return out
```

#### 2.3 损失函数

```python
class PerceptualLoss(nn.Cell):
    """感知损失：L1 + VGG 特征感知"""
    def __init__(self):
        super().__init__()
        self.l1_loss = nn.L1Loss()
        # 使用预训练 VGG19 提取特征
        self.vgg = vgg19_features(pretrained=True)

    def construct(self, sr, hr):
        l1 = self.l1_loss(sr, hr)
        sr_feat = self.vgg(sr)
        hr_feat = self.vgg(hr)
        perceptual = self.l1_loss(sr_feat, hr_feat)
        return l1 + 0.1 * perceptual
```

#### 2.4 训练配置

| 参数 | 值 |
|------|-----|
| 优化器 | Adam (β1=0.9, β2=0.999) |
| 初始学习率 | 2e-4 |
| 学习率调度 | CosineAnnealing |
| Batch Size | 16 |
| 训练轮次 | 200 epochs |
| Patch Size | 64 (LR) → 256 (HR) |
| GPU | 1× Tesla V100 (32GB) |
| 预计训练时间 | 8-12 小时 |

#### 2.5 训练脚本

```python
import mindspore as ms
from mindspore import context, nn, train

context.set_context(mode=context.GRAPH_MODE, device_target="GPU")

# 模型
net = RealESRGANMobile()
criterion = PerceptualLoss()
optimizer = nn.Adam(net.trainable_params(), learning_rate=2e-4)

# 数据集
train_dataset = create_dataset("obs://bucket/datasets/sr/train")

# 训练循环
model = train.Model(net, loss_fn=criterion, optimizer=optimizer)
model.train(epoch=200, train_dataset=train_dataset,
            callbacks=[train.LossMonitor(), train.TimeMonitor()])

# 保存 checkpoint
ms.save_checkpoint(net, "realesrgan_mobile.ckpt")
```

---

### 阶段 3：模型导出与量化（Day 4）

#### 3.1 导出 ONNX

```python
import mindspore as ms
from mindspore import export, Tensor
import numpy as np

# 加载训练好的模型
net = RealESRGANMobile()
param_dict = ms.load_checkpoint("realesrgan_mobile.ckpt")
ms.load_param_into_net(net, param_dict)
net.set_train(False)

# 导出 ONNX
input_data = Tensor(np.random.randn(1, 3, 64, 64).astype(np.float32))
export(net, input_data, file_name="realesrgan_mobile", file_format="ONNX")
print("ONNX 模型导出成功")
```

#### 3.2 转换为 MindSpore Lite 格式

```bash
# 使用 MindSpore Lite Converter
mindspore_lite-converter \
    --fmk=ONNX \
    --modelFile=realesrgan_mobile.onnx \
    --outputFile=realesrgan_mobile \
    --inputShape="input:1,3,64,64"
```

#### 3.3 INT8 量化压缩

```python
import mindspore_lite as mslite

# 配置量化参数
config = mslite.QuantConfig()
config.set_quant_type(mslite.QuantType.QUANT_INT8)
config.set_activation_quant_algo(mslite.ActivationQuantAlgo.MIN_MAX)

# 执行量化
converter = mslite.Converter()
converter.quantize(config)
converter.convert(
    fmk_type=mslite.FmkType.ONNX,
    model_file="realesrgan_mobile.onnx",
    output_file="realesrgan_mobile_quant"
)
```

**量化前后对比（预期）：**

| 指标 | FP32 | INT8 |
|------|------|------|
| 模型大小 | ~26MB | ~6.5MB |
| 推理速度 (SD865) | ~150ms | ~80ms |
| PSNR (Set5) | ~31.5dB | ~31.0dB |
| 精度损失 | — | < 0.5dB |

---

### 阶段 4：Android 端集成（Day 5-6）

#### 4.1 新增文件

```
app/src/main/assets/
└── realesrgan_mobile_quant.ms          ← 量化后模型（放入assets）

app/src/main/java/com/mindspore/himindspore/ui/superresolution/
├── SuperResolutionActivity.java        ← 主界面Activity
├── SuperResolutionExecutor.java        ← MindSpore Lite推理引擎
└── SuperResolutionResultView.java      ← 左右对比自定义View

app/src/main/res/layout/
└── activity_super_resolution.xml       ← 布局文件

app/src/main/res/drawable-xxhdpi/
└── btn_super_resolution.png            ← 入口按钮图标
```

#### 4.2 推理引擎（SuperResolutionExecutor.java）

```java
public class SuperResolutionExecutor {
    private static final String TAG = "SRExecutor";
    private static final String MODEL_NAME = "realesrgan_mobile_quant.ms";
    private static final int INPUT_SIZE = 64;
    private static final int OUTPUT_SIZE = 256;  // 4x upscale

    private Model model;
    private LiteSession session;
    private MSConfig config;
    private boolean isModelLoaded = false;

    public boolean init(Context context) {
        try {
            model = new Model();
            if (!model.loadModel(context, MODEL_NAME)) {
                Log.e(TAG, "Failed to load model");
                return false;
            }

            config = new MSConfig();
            if (!config.init(DeviceType.DT_CPU, 4, CpuBindMode.MID_CPU)) {
                Log.e(TAG, "Config init failed");
                return false;
            }

            session = new LiteSession();
            if (!session.init(config)) {
                Log.e(TAG, "Session init failed");
                return false;
            }

            if (!session.compileGraph(model)) {
                Log.e(TAG, "Compile graph failed");
                return false;
            }
            model.freeBuffer();
            isModelLoaded = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Init error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 执行超分辨率推理
     * @param lowResBitmap 低分辨率输入图片
     * @param callback 结果回调（含耗时信息）
     */
    public void upscale(Bitmap lowResBitmap, SRCallback callback) {
        if (!isModelLoaded) {
            callback.onError("Model not loaded");
            return;
        }

        new Thread(() -> {
            long totalTime = SystemClock.uptimeMillis();

            // 1. 预处理：缩放到输入尺寸 + 归一化
            long preTime = SystemClock.uptimeMillis();
            Bitmap resized = Bitmap.createScaledBitmap(lowResBitmap,
                    INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = bitmapToByteBuffer(resized);
            preTime = SystemClock.uptimeMillis() - preTime;

            // 2. 推理
            long inferTime = SystemClock.uptimeMillis();
            List<MSTensor> inputs = session.getInputs();
            inputs.get(0).setData(inputBuffer);
            session.runGraph();
            inferTime = SystemClock.uptimeMillis() - inferTime;

            // 3. 后处理：输出转Bitmap
            long postTime = SystemClock.uptimeMillis();
            Map<String, MSTensor> outputs = session.getOutputMapByTensor();
            Bitmap result = outputToBitmap(outputs);
            postTime = SystemClock.uptimeMillis() - postTime;

            totalTime = SystemClock.uptimeMillis() - totalTime;

            callback.onSuccess(result, preTime, inferTime, postTime, totalTime);
        }).start();
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                1 * 3 * INPUT_SIZE * INPUT_SIZE * 4);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            buffer.putFloat((pixel & 0xFF) / 255.0f);
        }
        buffer.rewind();
        return buffer;
    }

    private Bitmap outputToBitmap(Map<String, MSTensor> outputs) {
        float[] data = null;
        for (MSTensor tensor : outputs.values()) {
            data = tensor.getFloatData();
            break;
        }
        if (data == null) return null;

        Bitmap bitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE,
                Bitmap.Config.ARGB_8888);
        int[] pixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE * OUTPUT_SIZE; i++) {
            int r = clamp((int)(data[i * 3] * 255), 0, 255);
            int g = clamp((int)(data[i * 3 + 1] * 255), 0, 255);
            int b = clamp((int)(data[i * 3 + 2] * 255), 0, 255);
            pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
        bitmap.setPixels(pixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
        return bitmap;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public void release() {
        if (session != null) session.free();
        if (config != null) config.free();
        isModelLoaded = false;
    }

    public interface SRCallback {
        void onSuccess(Bitmap result, long preTime, long inferTime,
                       long postTime, long totalTime);
        void onError(String message);
    }
}
```

#### 4.3 主界面布局（activity_super_resolution.xml）

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/sr_toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorPrimary" />

    <!-- 图片对比区域 -->
    <com.mindspore.himindspore.ui.superresolution.SuperResolutionResultView
        android:id="@+id/sr_result_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <!-- 耗时信息 -->
    <TextView
        android:id="@+id/tv_time_info"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="8dp"
        android:text="等待处理..."
        android:textSize="14sp" />

    <!-- 操作按钮 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp">

        <Button
            android:id="@+id/btn_select_photo"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="选择图片"
            android:layout_marginEnd="4dp" />

        <Button
            android:id="@+id/btn_take_photo"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="拍照"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp" />

        <Button
            android:id="@+id/btn_upscale"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="一键超分"
            android:layout_marginStart="4dp" />
    </LinearLayout>

    <Button
        android:id="@+id/btn_save"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="8dp"
        android:text="保存结果"
        android:visibility="gone" />
</LinearLayout>
```

#### 4.4 注册 Activity（AndroidManifest.xml）

```xml
<activity
    android:name=".ui.superresolution.SuperResolutionActivity"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.AppCompat.NoActionBar" />
```

#### 4.5 入口注册（VisionFragment.java）

在 `onClick()` 中添加：
```java
case R.id.btn_super_resolution:
    ARouter.getInstance().build("/superresolution/SuperResolutionActivity").navigation();
    break;
```

---

### 阶段 5：编译测试与部署（Day 7）

#### 5.1 编译
```bash
./gradlew assembleDebug
```

#### 5.2 测试用例

| 测试项 | 输入 | 预期结果 |
|--------|------|----------|
| 小图超分 | 64×64 图片 | 输出 256×256 高清图 |
| 大图超分 | 1920×1080 降采样到 480×270 | 输出 1920×1080 |
| 推理耗时 | 任意 64×64 输入 | < 300ms |
| 模型加载 | App 启动 | < 1s |
| 内存占用 | 推理过程 | < 200MB |

#### 5.3 安装到手机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### 阶段 6：交付物整理（Day 7）

- [ ] 代码推送到仓库
- [ ] 功能演示视频录制（3-5 分钟）
- [ ] 量化报告文档
- [ ] README 更新

---

## 四、时间线总览

```
Day 1  ████████████████████████████████████████  华为云环境准备 + 数据上传
Day 2  ████████████████████████████████████████  模型训练（上半程）
Day 3  ████████████████████████████████████████  模型训练（下半程）+ 调参
Day 4  ████████████████████████████████████████  模型导出 + ONNX转换 + INT8量化
Day 5  ████████████████████████████████████████  Android推理引擎开发
Day 6  ████████████████████████████████████████  Android UI开发 + 集成测试
Day 7  ████████████████████████████████████████  编译部署 + 视频录制 + 文档
```

---

## 五、风险与备选

| 风险 | 应对 |
|------|------|
| ModelArts GPU 配额不足 | 使用 AutoDL / 本地 GPU 训练 |
| ONNX → .ms 转换失败 | 使用 MindSpore 直接导出 .ms 格式 |
| 量化精度损失 > 1dB | 改用混合量化（敏感层 FP16） |
| 推理耗时 > 300ms | 减少 RRDB blocks（6→4），或降低输出尺寸 |
| 现有 mindsporelibrary AAR 版本过旧 | 升级到 MindSpore Lite 2.x |

---

## 六、关键依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| MindSpore (训练) | 2.x | 华为云 ModelArts 训练框架 |
| MindSpore Lite (推理) | 1.1.0 (现有) 或 2.x | Android 端推理 |
| Python | 3.7+ | 训练脚本 |
| ONNX | 1.12+ | 模型中间格式 |
| Android SDK | 30 | App 编译 |
| NDK | 21.3.6528147 | JNI 编译 |
