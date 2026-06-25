# MindSpore InHand 掌中宝 — 项目总结与扩展计划

## 一、项目概述

**项目名称：** MindSpore InHand（掌中宝）
**包名：** `com.mindspore.himindspore`
**技术栈：** Android (Java) + MindSpore Lite + HMS ML Kit
**构建系统：** Gradle (multi-module)
**最低SDK：** 21 (Android 5.0) | 目标SDK：30 (Android 11)

---

## 二、现有模块功能汇总

| 模块 | 功能 | 模型来源 | 状态 |
|------|------|----------|------|
| `imageObject` | 图像分类（相机实时）、目标检测（拍照+相机） | MindSpore Lite JNI (`mlkit-label-MS.so`) | ✅ 可用 |
| `styletransfer` | 风格迁移（拍照+风格选择） | MindSpore Lite Java API (`style_predict_quant.ms`, `style_transfer_quant.ms`) | ✅ 可用 |
| `hms` | PoseNet骨骼检测、图像分割、手势识别、文字识别、文字翻译、场景检测 | HMS ML Kit | ✅ 可用 |
| `dance` | 舞蹈姿态估计 | MindSpore Lite | ⚠️ 横屏显示问题 |
| `custommodel` | 自定义模型加载与推理 | **纯UI模式，无真实推理** | ⚠️ 仅有UI框架 |
| `superresolution` | 图像超分辨率（4倍放大） | MindSpore Lite（模型待导入） | ✅ UI完成，推理接口已预留 |
| `common` | 通用工具类、Base类 | — | ✅ 基础库 |
| `customView` | 自定义对话框、UI组件 | — | ✅ 基础库 |

### 已有问题
1. 仅支持ARM架构手机，x86虚拟机无法加载模型
2. Quick Start网页内容已失效
3. `custommodel`模块仅有UI，推理为模拟实现（`Thread.sleep` + 随机输出）

---

## 三、现有代码架构分析

```
app/                          ← 主应用壳
├── ui/main/                  ← MainActivity + MVP
├── ui/experience/            ← 体验页（VisionFragment 入口）
├── ui/college/               ← 学院页
├── ui/me/                    ← 个人页
├── ui/poetry/                ← 智能写诗（已隐藏）
└── ui/guide/                 ← 启动页

imageObject/                  ← MindSpore图像模块
├── imageclassification/      ← 图像分类（JNI调用）
└── objectdetection/          ← 目标检测（JNI调用）

styletransfer/                ← 风格迁移模块
├── StyleTransferModelExecutor ← MindSpore Lite Java API推理
└── StyleMainActivity         ← 拍照+风格选择UI

hms/                          ← HMS ML Kit模块
├── ImageSegmentation/        ← 图像分割
├── bonedetection/            ← 骨骼检测(PoseNet)
├── gesturerecognition/       ← 手势识别
├── textrecognition/          ← 文字识别
├── texttranslation/          ← 文字翻译
└── scenedetection/           ← 场景检测

custommodel/                  ← 自定义模型模块（当前为空壳）
├── CustomModelMainActivity   ← 选择模型+图片+执行UI
└── CustomModelExecutor       ← 模拟推理（无真实模型）

superresolution/              ← 超分辨率模块（新增）
├── SuperResolutionActivity   ← 主界面：选图/拍照/超分/对比/保存
├── SuperResolutionExecutor   ← MindSpore Lite推理引擎（接口已预留）
└── SuperResolutionResultView ← 左右滑动对比自定义View
```

---

## 四、扩展计划：新增超分辨率模型（Real-ESRGAN Mobile）

### 4.1 选型理由

| 维度 | 说明 |
|------|------|
| **不重复** | 现有模块无超分辨率功能；与图像分类、风格迁移、分割等正交 |
| **实用性** | 图像超分是手机端高频需求（老照片修复、截图增强） |
| **模型轻量** | Real-ESRGAN Mobile (~6.5MB) 可在手机端实时运行 |
| **展示性** | 输入低分辨率图 → 输出高清图，效果直观、演示冲击力强 |
| **MindSpore支持** | 可通过MindSpore训练后导出`.ms`格式，用MindSpore Lite推理 |

### 4.2 实现方案

#### 第一阶段：模型准备与量化压缩（Python端）

```bash
# 1. 训练/获取Real-ESRGAN Mobile模型
# 2. 导出为ONNX格式
# 3. 转换为MindSpore Lite格式(.ms)
# 4. 量化压缩（INT8动态量化）
```

**模型压缩流程：**
```
PyTorch/ONNX → MindSpore IR → 量化工具(mindspore_lite.quant) → .ms模型文件
```

**量化参数：**
- 量化方式：INT8 动态量化（Post-Training Quantization）
- 输入尺寸：64×64（低分辨率）→ 输出256×256（4倍超分）
- 模型大小：FP32 ~26MB → INT8 ~6.5MB
- 推理速度：目标 < 200ms（骁龙865）

#### 第二阶段：Android端集成

**新增文件清单：**

```
app/src/main/assets/
└── realesrgan_mobile_quant.ms    ← 量化后的超分模型

app/src/main/java/com/mindspore/himindspore/ui/superresolution/
├── SuperResolutionActivity.java   ← 主界面
├── SuperResolutionExecutor.java   ← MindSpore Lite推理封装
└── SuperResultView.java           ← 对比展示View（滑动对比）

app/src/main/res/layout/
└── activity_super_resolution.xml  ← 布局文件
```

**核心推理代码框架（SuperResolutionExecutor）：**
```java
public class SuperResolutionExecutor {
    private Model model;
    private LiteSession session;
    private MSConfig config;

    public void init(Context context) {
        model = new Model();
        model.loadModel(context, "realesrgan_mobile_quant.ms");
        config = new MSConfig();
        config.init(DeviceType.DT_CPU, 4, CpuBindMode.MID_CPU);
        session = new LiteSession();
        session.init(config);
        session.compileGraph(model);
        model.freeBuffer();
    }

    public Bitmap upscale(Bitmap lowResBitmap) {
        // 预处理：归一化
        ByteBuffer input = preprocess(lowResBitmap);
        // 推理
        List<MSTensor> inputs = session.getInputs();
        inputs.get(0).setData(input);
        session.runGraph();
        // 后处理：反归一化 → Bitmap
        return postprocess(session.getOutputMapByTensor());
    }
}
```

#### 第三阶段：UI与交互设计

**界面功能：**
1. 从相册选择图片 / 拍照
2. 自动降采样生成低分辨率输入
3. 一键超分，显示处理时间
4. 左右滑动对比原图与超分结果
5. 保存超分结果到相册

#### 第四阶段：编译部署测试

```bash
# 编译
./gradlew assembleDebug

# 安装到手机
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 测试用例
# 1. 选择不同分辨率图片测试
# 2. 测量推理耗时
# 3. 对比超分前后PSNR/SSIM
```

### 4.3 项目结构变更

```
settings.gradle 新增：
  无需新增模块，直接在app模块中实现

app/build.gradle 修改：
  无新增外部依赖，使用已有的mindsporelibrary

app/src/main/AndroidManifest.xml 修改：
  注册 SuperResolutionActivity
```

### 4.4 时间规划

| 阶段 | 任务 | 预计耗时 |
|------|------|----------|
| **阶段1** | 模型获取、转换、量化压缩 | 2天 |
| **阶段2** | Android端推理引擎集成 | 2天 |
| **阶段3** | UI开发与交互优化 | 1天 |
| **阶段4** | 编译、测试、Bug修复 | 1天 |
| **阶段5** | 演示视频录制、文档整理 | 1天 |
| **总计** | | **7天** |

### 4.5 交付物

1. **代码仓** — 包含完整新增代码，可编译运行
2. **汇总文档** — `PROJECT_SUMMARY.md`（本文件）
3. **功能演示视频** — 展示：
   - 模型加载过程
   - 选择/拍摄低分辨率图片
   - 超分辨率处理过程与耗时
   - 前后对比效果
   - 结果保存
4. **模型文件** — 量化后的`.ms`模型文件
5. **量化报告** — 模型大小、精度对比、推理速度

---

## 五、风险与对策

| 风险 | 对策 |
|------|------|
| Real-ESRGAN模型转MindSpore格式失败 | 备选方案：使用MindSpore Hub预训练超分模型 |
| 量化后精度下降明显 | 使用混合量化（敏感层FP16，其余INT8） |
| 推理速度不达标 | 降低输入分辨率（64→48）或使用GPU Delegate |
| ARM-only限制 | 编译多架构so（arm64-v8a + armeabi-v7a） |
