#!/usr/bin/env python3
"""MiMo ASR API 格式验证脚本"""
import requests, base64, json, sys, os

API_KEY = os.environ.get("MIMO_API_KEY", "tp-c5fr90s7c3998ryara9063mqxj6igps54iy25egvfvvbhepm")
BASE_URL = "https://token-plan-cn.xiaomimimo.com"
HEADERS = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}

# 用 voice_test 中的音频文件测试
TEST_AUDIO = r"D:\desktable_character\voice\voice_test\tts_mimo_default.mp3"

def load_audio_b64(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode()

# ========== 测试1: 列出可用模型 ==========
def test_list_models():
    print("\n=== 测试1: GET /v1/models ===")
    try:
        r = requests.get(f"{BASE_URL}/v1/models", headers=HEADERS, timeout=15)
        print(f"Status: {r.status_code}")
        if r.status_code == 200:
            models = r.json().get("data", [])
            for m in models:
                mid = m.get("id", "")
                if "asr" in mid.lower() or "speech" in mid.lower() or "audio" in mid.lower() or "transcri" in mid.lower():
                    print(f"  [ASR相关] {mid}")
            print(f"  共 {len(models)} 个模型")
        else:
            print(f"  {r.text[:300]}")
    except Exception as e:
        print(f"  Error: {e}")

# ========== 测试2: chat/completions 格式（multimodal） ==========
def test_chat_completions_multimodal(audio_b64):
    print("\n=== 测试2: chat/completions + multimodal audio ===")
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
    try:
        r = requests.post(f"{BASE_URL}/v1/chat/completions", headers=HEADERS, json=payload, timeout=60)
        print(f"Status: {r.status_code}")
        print(f"  {r.text[:500]}")
    except Exception as e:
        print(f"  Error: {e}")

# ========== 测试3: chat/completions 格式（audio input via assistant） ==========
def test_chat_completions_assistant(audio_b64):
    print("\n=== 测试3: chat/completions + audio in assistant ===")
    payload = {
        "model": "mimo-v2.5-asr",
        "messages": [
            {"role": "user", "content": "请识别这段语音"},
            {"role": "assistant", "content": f"[audio:base64]{audio_b64[:100]}..."}
        ]
    }
    try:
        r = requests.post(f"{BASE_URL}/v1/chat/completions", headers=HEADERS, json=payload, timeout=60)
        print(f"Status: {r.status_code}")
        print(f"  {r.text[:500]}")
    except Exception as e:
        print(f"  Error: {e}")

# ========== 测试4: /v1/audio/transcriptions ==========
def test_audio_transcriptions(audio_path):
    print("\n=== 测试4: POST /v1/audio/transcriptions ===")
    try:
        with open(audio_path, "rb") as f:
            files = {"file": ("test.mp3", f, "audio/mp3")}
            data = {"model": "mimo-v2.5-asr", "language": "zh"}
            h = {"Authorization": f"Bearer {API_KEY}"}
            r = requests.post(f"{BASE_URL}/v1/audio/transcriptions",
                            headers=h, files=files, data=data, timeout=60)
        print(f"Status: {r.status_code}")
        print(f"  {r.text[:500]}")
    except Exception as e:
        print(f"  Error: {e}")

# ========== 测试5: /v1/audio/translations ==========
def test_audio_translations(audio_path):
    print("\n=== 测试5: POST /v1/audio/translations ===")
    try:
        with open(audio_path, "rb") as f:
            files = {"file": ("test.mp3", f, "audio/mp3")}
            data = {"model": "mimo-v2.5-asr"}
            h = {"Authorization": f"Bearer {API_KEY}"}
            r = requests.post(f"{BASE_URL}/v1/audio/translations",
                            headers=h, files=files, data=data, timeout=60)
        print(f"Status: {r.status_code}")
        print(f"  {r.text[:500]}")
    except Exception as e:
        print(f"  Error: {e}")

if __name__ == "__main__":
    print("=" * 60)
    print("MiMo ASR API 格式验证")
    print("=" * 60)

    test_list_models()

    if os.path.exists(TEST_AUDIO):
        audio_b64 = load_audio_b64(TEST_AUDIO)
        print(f"\n测试音频: {TEST_AUDIO} ({len(audio_b64)} base64 chars)")
        test_chat_completions_multimodal(audio_b64)
        test_chat_completions_assistant(audio_b64)
        test_audio_transcriptions(TEST_AUDIO)
        test_audio_translations(TEST_AUDIO)
    else:
        print(f"\n测试音频不存在: {TEST_AUDIO}")
        print("请指定一个有效的音频文件路径")
