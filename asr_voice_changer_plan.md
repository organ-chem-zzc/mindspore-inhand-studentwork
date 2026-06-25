# 语音识别 (ASR) + 变声器 功能实施计划

## 一、功能概述

### 功能 A：语音转文字（ASR）
- 用户录制语音 → MiMo-v2.5-asr API 识别 → 显示文字结果
- 支持从麦克风实时录音
- 支持导入已有音频文件
- 识别结果可编辑、复制

### 功能 B：语音转语音（变声器）
- 用户录制语音 → ASR 识别文字 → 选择目标音色 → TTS 合成新语音
- 即：原声 → 文字 → 目标音色输出
- 支持 9 种音色 + 语速/音调控制
- 对比播放原声与变声后的音频

---

## 二、MiMo API 分析

### 2.1 已知 API 模式

基于现有 TTS API 的调用模式：

```
端点: POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
认证: Authorization: Bearer {API_KEY}
模型命名: mimo-v2.5-{task}
```

### 2.2 ASR API 推测格式

参考 MiMo 平台的 API 设计风格，ASR 接口推测为：

**方式 A：Chat Completions 格式（与 TTS 统一）**
```json
POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
{
    "model": "mimo-v2.5-asr",
    "messages": [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "请识别以下语音内容"},
                {"type": "audio_url", "audio_url": {"url": "data:audio/mp3;base64,{BASE64_AUDIO}"}}
            ]
        }
    ]
}
```
响应：`response.choices[0].message.content` 包含识别文字

**方式 B：专用 Audio Transcriptions 端点**
```json
POST https://token-plan-cn.xiaomimimo.com/v1/audio/transcriptions
Content-Type: multipart/form-data

file: {audio_binary}
model: mimo-v2.5-asr
language: zh
```
响应：`{"text": "识别出的文字内容"}`

**方式 C：Audio Translations 端点**
```json
POST https://token-plan-cn.xiaomimimo.com/v1/audio/translations
Content-Type: multipart/form-data

file: {audio_binary}
model: mimo-v2.5-asr
```

> ⚠️ **实施策略**：先实现方式 A（与现有 TTS API 模式一致），如失败再尝试方式 B/C。
> 需要实际测试确认可用的 API 格式。

### 2.3 TTS API（已验证）

```
POST https://token-plan-cn.xiaomimimo.com/v1/chat/completions
{
    "model": "mimo-v2.5-tts-voicedesign",
    "messages": [
        {"role": "user", "content": "请朗读以下文字"},
        {"role": "assistant", "content": "{文本内容}"}
    ],
    "modalities": ["text", "audio"],
    "audio": {"format": "mp3", "speed": 1.0, "pitch": 0},
    "voice": "冰糖"
}
响应: response.choices[0].message.audio.data (base64 MP3)
```

---

## 三、架构设计

### 3.1 模块架构

```
app/src/main/java/com/mindspore/himindspore/ui/voice/
├── AsrExecutor.java            ← ASR 语音识别引擎
├── VoiceChangerExecutor.java   ← 变声器引擎（ASR + TTS 组合）
├── AsrActivity.java            ← 语音转文字界面
├── VoiceChangerActivity.java   ← 变声器界面
└── AudioRecorderHelper.java    ← 录音工具类

app/src/main/res/layout/
├── activity_asr.xml            ← 语音转文字布局
└── activity_voice_changer.xml  ← 变声器布局
```

### 3.2 数据流

#### 语音转文字（ASR）
```
麦克风录音 / 导入音频
    ↓
AudioRecorderHelper (PCM/WAV 录制)
    ↓
AsrExecutor.asr(byte[] audioData)
    ↓
MiMo-v2.5-asr API (base64 音频上传)
    ↓
返回识别文字 → 显示在 EditText
```

#### 变声器（Voice Changer）
```
麦克风录音
    ↓
AudioRecorderHelper 录制原声
    ↓ (步骤1: ASR)
AsrExecutor.asr(originalAudio) → 识别文字
    ↓ (步骤2: TTS)
TtsExecutor.synthesize(text, targetVoice, speed, pitch)
    ↓
变声音频 → 对比播放
    ↓
导出 MP3
```

### 3.3 页面设计

#### ASR 语音转文字页面

```
┌─────────────────────────────────────┐
│  ← 语音转文字          ❓           │
├─────────────────────────────────────┤
│                                     │
│    [🎙️ 长按录音]  或  [导入音频]   │
│                                     │
│  ┌─ 录音状态 ────────────────────┐  │
│  │ 🔴 录音中... 00:05            │  │
│  │ ████████░░░░ 音量波形         │  │
│  └────────────────────────────────┘  │
│                                     │
│  ┌─ 识别结果 ────────────────────┐  │
│  │                               │  │
│  │  (识别出的文字显示在此处)     │  │
│  │                               │  │
│  │                               │  │
│  └────────────────────────────────┘  │
│                                     │
│  [📋 复制]  [🔄 重新识别]  [🔊 朗读]│
│                                     │
│  状态: 就绪                         │
└─────────────────────────────────────┘
```

#### 变声器页面

```
┌─────────────────────────────────────┐
│  ← AI变声器            ❓           │
├─────────────────────────────────────┤
│                                     │
│  [🎙️ 录制原声]  [⏹ 停止]          │
│                                     │
│  原声: ████████ 0:05  [▶ 试听原声]  │
│                                     │
│  ┌─ 识别文字 ────────────────────┐  │
│  │ (ASR识别结果，可编辑)         │  │
│  └────────────────────────────────┘  │
│                                     │
│  目标音色: [冰糖▼]  语速: [===●=]   │
│  音调: [===●===] 0                  │
│                                     │
│  [🔄 开始变声]                      │
│                                     │
│  变声: ████████ 0:08  [▶ 试听变声]  │
│                                     │
│  [💾 导出MP3]  [📊 对比播放]        │
│                                     │
│  状态: 就绪                         │
└─────────────────────────────────────┘
```

---

## 四、文件清单

### 4.1 新增文件

| 文件 | 功能 | 行数估计 |
|------|------|----------|
| `AsrExecutor.java` | ASR API 调用引擎 | ~200 行 |
| `AudioRecorderHelper.java` | 录音工具（MediaRecorder 封装） | ~150 行 |
| `AsrActivity.java` | 语音转文字主界面 | ~300 行 |
| `VoiceChangerExecutor.java` | 变声器引擎（ASR+TTS 组合） | ~100 行 |
| `VoiceChangerActivity.java` | 变声器主界面 | ~350 行 |
| `activity_asr.xml` | ASR 布局 | ~150 行 |
| `activity_voice_changer.xml` | 变声器布局 | ~180 行 |

### 4.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `VisionFragment.java` | 添加两个按钮点击监听 |
| `fragment_vision.xml` | 添加「语音转文字」「变声器」按钮 |
| `AndroidManifest.xml` | 注册两个新 Activity + 录音权限 |
| `strings.xml` / `values-zh` | 添加新字符串资源 |
| `local.properties` | 已有 mimo.api.key（复用） |

---

## 五、技术细节

### 5.1 录音实现（AudioRecorderHelper）

```java
// 使用 MediaRecorder 录制 AAC/MP3 格式
MediaRecorder recorder = new MediaRecorder();
recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
recorder.setAudioSamplingRate(16000);  // ASR 推荐 16kHz
recorder.setAudioEncodingBitRate(128000);
recorder.setOutputFile(outputFilePath);
```

### 5.2 ASR API 调用（AsrExecutor.java）

```java
// 读取音频文件 → base64 编码 → 发送 API
byte[] audioBytes = readFile(audioPath);
String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

// 构建请求（方式A: chat completions）
JSONObject payload = new JSONObject();
payload.put("model", "mimo-v2.5-asr");
JSONArray messages = new JSONArray();
JSONObject msg = new JSONObject();
msg.put("role", "user");
JSONArray content = new JSONArray();
content.put(new JSONObject().put("type", "text").put("text", "请识别以下语音内容"));
content.put(new JSONObject().put("type", "audio_url")
    .put("audio_url", new JSONObject().put("url", "data:audio/mp3;base64," + base64Audio)));
msg.put("content", content);
messages.put(msg);
payload.put("messages", messages);

// 发送请求
String response = httpPost(API_URL, payload.toString());
JSONObject result = new JSONObject(response);
String recognizedText = result.getJSONArray("choices")
    .getJSONObject(0).getJSONObject("message").getString("content");
```

### 5.3 变声器流程（VoiceChangerExecutor）

```java
public void voiceChange(byte[] originalAudio, String targetVoice,
                         float speed, int pitch, VoiceChangeCallback callback) {
    executor.execute(() -> {
        // 步骤1: ASR 识别
        callback.onProgress("正在识别语音...");
        String text = asrExecutor.recognize(originalAudio);
        if (text == null) { callback.onError("语音识别失败"); return; }
        callback.onAsrResult(text);

        // 步骤2: TTS 合成
        callback.onProgress("正在合成变声...");
        byte[] resultAudio = ttsExecutor.synthesize(text, targetVoice, speed, pitch);
        if (resultAudio == null) { callback.onError("语音合成失败"); return; }

        callback.onSuccess(resultAudio);
    });
}
```

### 5.4 权限配置

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

---

## 六、执行步骤

### 步骤 1：创建录音工具类
- `AudioRecorderHelper.java`
- 封装 MediaRecorder 的 start/stop/getAmplitude
- 支持录制 WAV 格式（兼容性最好）

### 步骤 2：创建 ASR 引擎
- `AsrExecutor.java`
- 实现三种 API 格式的自动尝试
- 子线程异步调用

### 步骤 3：创建 ASR 界面
- `activity_asr.xml` + `AsrActivity.java`
- 录音按钮 + 音量波形 + 识别结果 + 复制/朗读

### 步骤 4：创建变声器引擎
- `VoiceChangerExecutor.java`
- 组合 ASR + TTS 流程

### 步骤 5：创建变声器界面
- `activity_voice_changer.xml` + `VoiceChangerActivity.java`
- 录制原声 → ASR → 选择音色 → TTS → 对比播放

### 步骤 6：集成到主界面
- 更新 VisionFragment + fragment_vision.xml
- 更新 AndroidManifest.xml + strings.xml

### 步骤 7：编译测试
- 迭代修复编译错误
- 模拟器/真机测试

### 步骤 8：打包提交
- assembleTrial
- 推送 GitHub

---

## 七、API 测试脚本（Python 预验证）

在 Android 开发前，先用 Python 脚本验证 ASR API 格式：

```python
import requests, base64, json

API_KEY = "tp-c5fr90s7c3998ryara9063mqxj6igps54iy25egvfvvbhepm"
API_URL = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"

# 读取音频文件
with open("test_audio.mp3", "rb") as f:
    audio_b64 = base64.b64encode(f.read()).decode()

# 方式A: chat completions
headers = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
payload = {
    "model": "mimo-v2.5-asr",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "text", "text": "请识别以下语音内容"},
            {"type": "audio_url", "audio_url": {"url": f"data:audio/mp3;base64,{audio_b64}"}}
        ]
    }]
}
r = requests.post(API_URL, headers=headers, json=payload, timeout=60)
print(f"Status: {r.status_code}")
print(r.text[:500])
```

---

## 八、风险与应对

| 风险 | 应对方案 |
|------|----------|
| ASR API 格式不确定 | Python 脚本预验证，实现三种格式自动尝试 |
| ASR API 不存在 | 降级使用 HMS ML Kit 本地语音识别 |
| 音频格式不兼容 | 统一录制为 WAV 16kHz mono |
| 录音权限被拒 | 运行时动态申请 + 引导用户开启 |
| 长录音 base64 过大 | 分段录音，每段 ≤ 30s |

---

## 九、预期交付物

| 交付物 | 说明 |
|--------|------|
| 7 个新增 Java 文件 | ASR + 录音 + 变声器 + 界面 |
| 2 个新增布局文件 | ASR 界面 + 变声器界面 |
| 修改 4 个现有文件 | 入口 + 权限 + 字符串 |
| Python 测试脚本 | API 格式验证 |
| APK 文件 | 可安装运行 |
| GitHub 更新 | 代码推送 |
