# OCR + TTS 语音朗读功能 — 实施计划

## 一、功能概述

**核心流程：** 拍照/选图 → OCR 识别文字 → MiMo TTS API 合成语音 → 预览播放 → 导出 MP3

**技术栈：**
- OCR：HMS ML Kit 文字识别（已集成在 hms 模块）
- TTS：小米 MiMo-v2.5-TTS-VoiceDesign API（HTTP REST）
- 音频：Android MediaPlayer + MediaRecorder

## 二、MiMo TTS API 关键参数

```
POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
Authorization: Bearer {API_KEY}

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

响应: response.choices[0].message.audio.data (base64编码的MP3)
```

## 三、新增文件清单

```
app/src/main/java/com/mindspore/himindspore/ui/tts/
├── TtsExecutor.java           ← TTS API 调用引擎（HTTP + base64解码）
├── OcrTtsActivity.java        ← 主界面（OCR识别 + TTS播放 + 导出）
└── AudioPlayerView.java       ← 自定义音频播放控件

app/src/main/res/layout/
└── activity_ocr_tts.xml       ← 布局文件
```

## 四、修改文件清单

| 文件 | 修改内容 |
|------|----------|
| VisionFragment.java | 添加入口按钮点击 |
| fragment_vision.xml | 添加"语音朗读"按钮 |
| AndroidManifest.xml | 注册 OcrTtsActivity |
| strings.xml / strings-zh | 添加字符串资源 |
| app/build.gradle | 无需新增依赖（用 HttpURLConnection） |

## 五、UI 设计

```
┌─────────────────────────────────────┐
│  ← OCR语音朗读          ❓          │
├─────────────────────────────────────┤
│ [拍照识别]  [相册选图]  [导入模型]  │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ OCR识别结果（可编辑）           │ │
│ │                                 │ │
│ │ 这里显示识别出的文字...         │ │
│ │                                 │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ 音色: [冰糖▼]  语速: [===●===] 1.0│
│ 音调: [===●===] 0                   │
├─────────────────────────────────────┤
│ [▶ 播放预览]  [⏹ 停止]  [💾 导出] │
├─────────────────────────────────────┤
│ 状态: 就绪 / 合成中... / 已完成    │
└─────────────────────────────────────┘
```

## 六、执行步骤

1. 创建 TtsExecutor.java（API调用 + base64解码 + 文件保存）
2. 创建 AudioPlayerView.java（播放/暂停/进度条）
3. 创建 activity_ocr_tts.xml（布局）
4. 创建 OcrTtsActivity.java（整合OCR + TTS + 播放 + 导出）
5. 修改 VisionFragment + fragment_vision.xml（入口）
6. 修改 AndroidManifest.xml + strings.xml
7. 编译测试
8. 打包 APK
