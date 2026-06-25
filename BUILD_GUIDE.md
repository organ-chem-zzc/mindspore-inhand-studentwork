# MindSpore InHand — 环境配置与 APK 打包指南

## 一、环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| **操作系统** | Windows 10/11 (64-bit) | macOS / Linux 亦可 |
| **JDK** | **17**（推荐）或 11 | Gradle 7.5 不支持 JDK 21+ |
| **Android Studio** | 2022.3+ (Flamingo) 或更高 | 自带 SDK Manager |
| **Android SDK** | API 30 (Android 11) | compileSdkVersion |
| **Android SDK Build-Tools** | 30.0.3 | 自动安装 |
| **NDK** | 21.3.6528147 | MindSpore Lite JNI 编译需要 |
| **Gradle** | 7.5（已含 wrapper） | 无需手动安装 |
| **磁盘空间** | ≥ 10GB | SDK + NDK + 项目构建缓存 |

---

## 二、环境配置步骤

### 2.1 安装 JDK 17

**方式 A：手动下载**
1. 访问 https://adoptium.net/temurin/releases/?version=17
2. 下载 **JDK 17 LTS** — Windows x64 `.msi` 安装包
3. 安装时勾选 **"Set JAVA_HOME variable"**

**方式 B：Android Studio 自带 JDK**
- Android Studio 内置 JBR (JetBrains Runtime)，无需额外安装
- 路径：`<Android Studio>/jbr/`

**验证安装：**
```bash
java -version
# 应显示 openjdk version "17.x.x"
```

### 2.2 安装 Android Studio + SDK

1. 下载 Android Studio：https://developer.android.com/studio
2. 安装后打开 → **More Actions** → **SDK Manager**
3. 勾选安装：
   - **SDK Platforms** → Android 11.0 (API 30) ✅
   - **SDK Tools** 选项卡：
     - Android SDK Build-Tools 30.0.3 ✅
     - NDK (Side by side) → 21.3.6528147 ✅
     - Android SDK Platform-Tools ✅
     - CMake 3.18.1 ✅（custommodel 模块 JNI 需要）

### 2.3 配置环境变量

**Windows（系统环境变量）：**

```
变量名: JAVA_HOME
变量值: C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
       （或 Android Studio 内置 JBR 路径）

变量名: ANDROID_HOME
变量值: C:\Users\<你的用户名>\AppData\Local\Android\Sdk

Path 中追加:
  %ANDROID_HOME%\platform-tools
  %ANDROID_HOME%\tools
```

**验证：**
```bash
echo %JAVA_HOME%
echo %ANDROID_HOME%
adb --version
```

### 2.4 （可选）使用本地 JDK 17 而非系统默认

如果系统默认 Java 版本过高（如 JDK 21/25），在项目根目录创建 `local.properties`：

```properties
# local.properties（不提交到 git）
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.13.11-hotspot
```

---

## 三、打开项目

1. 启动 Android Studio
2. **File → Open** → 选择 `Mindspore_inhand-main` 文件夹
3. 等待 Gradle Sync 完成（首次约 5-10 分钟）
4. 如果提示缺少 SDK/NDK，点击 **Install** 自动下载

**常见 Sync 问题：**

| 问题 | 解决方案 |
|------|----------|
| `Could not determine java version` | 检查 JAVA_HOME 指向 JDK 17 |
| `NDK not configured` | SDK Manager → SDK Tools → 安装 NDK 21.3.6528147 |
| `Failed to find target with hash string 'android-30'` | SDK Manager → 安装 API 30 |
| `Connection timed out` (下载依赖) | 检查网络，或使用代理/镜像 |

---

## 四、APK 打包

### 4.1 通过 Android Studio（推荐）

**试用版 APK（Trial）：**
1. 菜单栏 → **Build → Select Build Variant...**
2. 左侧面板出现 **Build Variants** 窗口
3. 将 `app` 模块的 Active Build Variant 改为 **`trial`**
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. 产出路径：`app/build/outputs/apk/trial/app-trial.apk`

**调试版 APK（Debug）：**
- 同上，选择 `debug` variant
- 产出路径：`app/build/outputs/apk/debug/app-debug.apk`

**正式版 APK（Release）：**
- 选择 `release` variant
- 需要签名（见 4.3 节）

### 4.2 通过命令行

```bash
cd Mindspore_inhand-main

# 试用版
.\gradlew assembleTrial

# 调试版
.\gradlew assembleDebug

# 正式版（需要签名配置）
.\gradlew assembleRelease
```

**APK 产出位置：**

| Variant | 路径 |
|---------|------|
| trial | `app/build/outputs/apk/trial/app-trial.apk` |
| debug | `app/build/outputs/apk/debug/app-debug.apk` |
| release | `app/build/outputs/apk/release/app-release.apk` |

### 4.3 正式版签名配置（可选）

在 `app/build.gradle` 的 `android {}` 块中添加：

```groovy
signingConfigs {
    release {
        storeFile file("keystore/mindspore.jks")
        storePassword "your_store_password"
        keyAlias "mindspore"
        keyPassword "your_key_password"
    }
}

buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

生成签名文件：
```bash
keytool -genkey -v -keystore keystore/mindspore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mindspore
```

### 4.4 安装到手机

```bash
# USB 连接手机（开启开发者模式 + USB调试）
adb devices                          # 确认设备已连接
adb install -r app-trial.apk         # 安装试用版

# 或直接运行
.\gradlew installTrial
```

---

## 五、Build Variant 说明

| Variant | 用途 | applicationId | 调试 | 签名 |
|---------|------|---------------|------|------|
| **debug** | 开发调试 | `com.mindspore.himindspore` | ✅ | debug keystore |
| **trial** | 试用分发 | `com.mindspore.himindspore.trial` | ✅ | debug keystore |
| **release** | 正式发布 | `com.mindspore.himindspore` | ❌ | release keystore |

> **trial** 和 **debug** 可以同时安装在同一台手机上（包名不同），方便对比测试。

---

## 六、项目各模块编译说明

```
settings.gradle 中包含的模块：

:app              ← 主应用（打包入口）
:common           ← 通用工具库
:customView       ← 自定义 UI 组件
:imageObject      ← MindSpore 图像分类/目标检测（JNI）
:styletransfer    ← 风格迁移（MindSpore Lite Java API）
:hms              ← HMS ML Kit 功能
:dance            ← 舞蹈姿态
:custommodel      ← 自定义模型
:mindsporelibrary ← MindSpore Lite AAR（预编译）
```

**不需要额外操作**，Gradle 会自动编译所有依赖模块。

---

## 七、常见编译错误排查

| 错误信息 | 原因 | 解决方案 |
|----------|------|----------|
| `Unsupported class file major version 65` | JDK 版本过高 | 切换到 JDK 17 |
| `NDK not configured` | 缺少 NDK | SDK Manager 安装 NDK 21.3.6528147 |
| `Could not resolve com.huawei.agconnect` | 仓库地址不通 | 确认 `https://developer.huawei.com/repo/` 可访问 |
| `AAPT2 error` | SDK Build-Tools 版本不匹配 | 安装 30.0.3 |
| `Execution failed for task ':app:mergeTrialResources'` | 资源冲突 | Clean Project 后重新 Build |
| `DexArchiveBuilderException` | Java 版本不兼容 | 确保 sourceCompatibility = 1.8 |

**终极排错流程：**
```bash
.\gradlew clean
.\gradlew assembleTrial --stacktrace --info
```

---

## 八、快速检查清单

在开始打包前，确认以下条件全部满足：

- [ ] JDK 17 已安装，`java -version` 显示 17.x
- [ ] `ANDROID_HOME` 环境变量已设置
- [ ] Android SDK API 30 已安装
- [ ] NDK 21.3.6528147 已安装
- [ ] 网络可访问 `developer.huawei.com`（AGConnect 依赖）
- [ ] Gradle Sync 成功无报错
- [ ] 手机已开启 USB 调试（如需安装测试）
