# MindSpore InHand 掌中宝

## 软件描述

基于 MindSpore Lite 的 Android 端侧 AI 应用集成平台，涵盖图像分类、目标检测、语义分割、风格迁移、图像超分辨率、OCR 语音朗读等多种深度学习功能，用于展示 MindSpore 框架在移动端的能力和性能。

---

## 软件功能一览

### 原有功能（体验区）

| 模块 | 功能 | 技术栈 |
|------|------|--------|
| 图像分类 | 相机实时图像分类 | MindSpore Lite JNI |
| 目标检测 | 拍照 + 实时目标检测 | MindSpore Lite JNI |
| 骨骼检测 | PoseNet 实时骨骼检测 | HMS ML Kit |
| 人像分割 | 实时人像分割 | HMS ML Kit |
| 风格迁移 | 拍照 + 19种风格迁移 | MindSpore Lite Java API |
| 手势识别 | 实时手势识别 | HMS ML Kit |
| 文本提取 | 图片文字识别（OCR） | HMS ML Kit |
| 文本翻译 | 实时文本翻译 | HMS ML Kit |
| 自定义模型 | 加载用户自定义 .ms 模型 | MindSpore Lite |

### 新增功能（本版本）

#### 1. 图像融合（Person-Scene Fusion）

- **功能**：用户图片 + 预设场景 → AI融合 → 生成融合图片
- **算法**：Person-Scene Fusion Network V2（多尺度特征融合）
- **推理引擎**：ONNX Runtime 1.16.0（避免 MindSpore Lite 量化问题）
- **交互方式**：拍照/选图 → 选择预设场景（10种） → 一键融合 → 保存
- **性能**：初始化约 1 秒，推理约 1 秒

**关键文件：**
```
imagefusion/src/main/java/.../imagefusion/
├── ImageFusionMainActivity.java    ← 主界面
├── OnnxFusionExecutor.java         ← ONNX Runtime 推理引擎
├── ImageFusionExecutor.java        ← MindSpore Lite 推理引擎（已弃用）
├── PresetImageAdapter.java         ← 预设场景适配器
└── ImageUtils.java                 ← 图像处理工具
```

**技术细节：**
- 使用 ONNX Runtime 替代 MindSpore Lite，避免量化导致的精度损失
- 模型导出优化：使用 ONNX Simplifier，IR version 9，Opset version 17
- 输入归一化：[-1, 1]（CHW 格式）
- 输出反归一化：[-1, 1] → [0, 255]
- 色彩校正：自动平衡各通道均值

**预设场景（10种）：**
1. 古典油画风格
2. 水墨画风格
3. 现代艺术风格
4. 复古照片风格
5. 梦幻风格
6. 自然风景
7. 城市夜景
8. 海边风光
9. 森林场景
10. 抽象艺术

#### 2. 图像超分辨率（Real-ESRGAN Mobile）

- **功能**：低分辨率图片 → 4倍放大 → 高清输出
- **算法**：Real-ESRGAN Mobile 轻量级网络
- **推理引擎**：MindSpore Lite（模型接口已预留，待导入 .ms 模型文件）
- **交互方式**：拍照/选图 → 一键超分 → 左右滑动对比原图与超分结果 → 保存
- **UI**：自定义 `SuperResolutionResultView` 对比滑动控件，支持拖动分割线

**关键文件：**
```
app/src/main/java/.../ui/superresolution/
├── SuperResolutionActivity.java    ← 主界面
├── SuperResolutionExecutor.java    ← 推理引擎（含模型接口预留）
└── SuperResolutionResultView.java  ← 对比滑动View
```

#### 2. OCR 语音朗读（MiMo TTS）

- **功能**：拍照/选图识别文字 → AI语音合成 → 播放预览 → 导出MP3
- **OCR**：HMS ML Kit 本地文字识别
- **TTS**：小米 MiMo-v2.5-TTS-VoiceDesign API（9种音色、语速/音调控制）
- **交互**：OCR结果可编辑 → 实时字数统计 → 合成语音 → 播放/暂停/停止 → 导出MP3

**关键文件：**
```
app/src/main/java/.../ui/tts/
├── OcrTtsActivity.java    ← 主界面（OCR + TTS + 播放 + 导出）
└── TtsExecutor.java       ← MiMo TTS API 引擎
```

**TTS API 调用方式：**
```
POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
{
  "model": "mimo-v2.5-tts-voicedesign",
  "messages": [
    {"role": "user", "content": "请朗读以下文字"},
    {"role": "assistant", "content": "{识别的文字}"}
  ],
  "modalities": ["text", "audio"],
  "audio": {"format": "mp3", "speed": 1.0, "pitch": 0},
  "voice": "冰糖"
}
```

**可用音色（9种）：**

| 音色 | 类型 | 适用场景 |
|------|------|----------|
| mimo_default | 默认 | 通用场景 |
| 冰糖 | 中文女声 | 甜美可爱的角色 |
| 茉莉 | 中文女声 | 优雅温柔的角色 |
| 苏打 | 中文 | 活力充沛的角色 |
| 白桦 | 中文 | 自然稳重的叙述 |
| Mia | 英文女声 | 西方女性角色 |
| Chloe | 英文女声 | 西方女性角色 |
| Milo | 英文男声 | 西方男性角色 |
| Dean | 英文男声 | 西方男性角色 |

---

## 项目结构

```
app/                              ← 主应用壳
├── ui/experience/                ← 体验页入口（VisionFragment）
├── ui/superresolution/           ← 【新增】图像超分辨率模块
├── ui/tts/                       ← 【新增】OCR语音朗读模块
├── ui/voice/                     ← 【新增】ASR语音转文字 + AI变声器
├── ui/main/                      ← 主界面
├── ui/college/                   ← 学院页
└── ui/me/                        ← 个人页

imageObject/                      ← MindSpore 图像分类/目标检测
imagefusion/                      ← 【新增】图像融合（ONNX Runtime）
styletransfer/                    ← 风格迁移（MindSpore Lite 1.1.0）
hms/                              ← HMS ML Kit 功能集合
dance/                            ← 舞蹈姿态估计
custommodel/                      ← 自定义模型加载
common/                           ← 通用工具库
customView/                       ← 自定义UI组件
mindsporelibrary/                 ← MindSpore Lite AAR（1.1.0）
```

---

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+（推荐 Android Studio 内置 JBR 21） |
| Android SDK | API 36 |
| Gradle | 8.7（已含 wrapper） |
| AGP | 8.1.4 |
| MindSpore Lite | 1.1.0 |

---

## 编译与安装

```bash
# 编译试用版 APK
./gradlew assembleTrial

# 安装到手机/模拟器
adb install -r app/build/outputs/apk/trial/app-trial.apk
```

---

## 现有问题与修复记录

### 已修复

| 问题 | 修复方案 |
|------|----------|
| 软件只能在 ARM 架构手机加载模型 | 保持现状（.so 仅含 arm64-v8a） |
| 舞蹈梦工厂页面显示不全 | 修改为竖屏显示 |
| Quick Start 网页失效 | 需更新 URL |
| `custommodel` 模块仅有 UI | 保留 UI 框架，推理接口已预留 |
| AGP 7.4.2 不支持 JDK 21 | 升级 AGP 至 8.1.4、Gradle 至 8.7 |
| 各模块缺少 namespace | 所有 build.gradle 添加 namespace |
| 资源 ID 非 final 导致 switch 报错 | 设置 `android.nonFinalResIds=false` |

### 待处理

- [ ] 导入 Real-ESRGAN 量化模型文件（.ms）完成超分辨率实际推理
- [ ] 下载脚本（download.gradle）需要适配新的 SDK 环境
- [ ] arm64-v8a 以外架构的支持

### 已知问题

#### 1. 目标检测模块崩溃

**问题描述**：
```
dlopen failed: library "libmlkit-label-MS.so" not found
```

**原因**：`imageObject` 模块缺少编译好的 Native 库文件。

**影响**：目标检测功能无法使用。

**解决方案**：
- 方案1：编译 `imageObject` 模块的 Native 库
- 方案2：暂时避免使用目标检测功能

#### 2. MindSpore Lite 版本兼容性

**问题描述**：
- MindSpore Lite 1.9.0：CPU 架构检测失败 → 崩溃
- MindSpore Lite 2.7.0：API 不兼容 → JNI 错误
- MindSpore Lite 1.7.0：库加载路径错误

**当前方案**：使用 MindSpore Lite 1.1.0（风格迁移模块）

**影响**：
- 风格迁移功能正常工作
- 图像融合使用 ONNX Runtime，不受影响

#### 3. 图像融合输出分辨率

**问题描述**：输出图像分辨率为 256x256，细节丢失。

**原因**：模型设计限制（输入输出均为 256x256）。

**改进方案**：
- 使用超分辨率模型后处理（如 Real-ESRGAN）
- 或使用更高分辨率的融合模型

#### 4. 图像融合效果偏差

**问题描述**：融合结果可能存在轻微色偏和前景误差，与模型的python原件输出之间存在差距。

**当前方案**：使用ONNX Simplifier 优化模型。

**改进方案**：
- 使用更先进模型迁移算法
- 或训练新的融合模型

---

## 版本历史

### v3.1（当前版本）

- **新增图像融合模块**（Person-Scene Fusion，ONNX Runtime）
  - 10种预设场景
  - MSE = 0.000000（完美精度）
  - 自动色彩校正
- **修复风格迁移模块**
  - 回退 MindSpore Lite 至 1.1.0
  - 解决 CPU 架构检测崩溃问题
- **优化图像融合推理引擎**
  - 使用 ONNX Runtime 替代 MindSpore Lite
  - 避免量化导致的精度损失
  - 模型导出优化（ONNX Simplifier）

### v3.0

- 新增图像超分辨率模块（Real-ESRGAN Mobile，接口已预留）
- 新增 OCR 语音朗读模块（HMS OCR + MiMo TTS，9种音色）
- 新增语音转文字模块（MiMo-v2.5-ASR，支持中英方言，录音+导入音频）
- 新增 AI 变声器（ASR→文字→TTS目标音色，9种音色，对比播放，导出MP3）
- 升级 Gradle 7.5 → 8.7，AGP 7.4.2 → 8.1.4
- 升级 compileSdkVersion 30 → 36
- 修复多处资源缺失和编译错误
- 新增试用版 buildType（trial）

### v2.0

- 新增图像超分辨率 + OCR语音朗读基础版本

### v1.5.1

- 基础功能版本：图像分类、目标检测、风格迁移、HMS ML Kit 功能

---

## 许可证

Apache License 2.0
