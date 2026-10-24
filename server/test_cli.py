import sys
import os

# 确保 Windows 命令行支持 UTF-8 打印
if sys.platform.startswith("win"):
    sys.stdout.reconfigure(encoding="utf-8")

# 确保能导入当前模块
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

import openai
import config

def run_test():
    print("=" * 60)
    print("[TEST] 开始测试: 核心画像记忆 + Qwen-Plus 对话")
    print("=" * 60)

    # 1. 模拟用户画像与提问
    test_query = "我最近这两天总觉得心慌发懵，玩手机停不下来，静不下来，救救我"
    print(f"\n[Step 1] 模拟用户提问:")
    print(f"  Query: \"{test_query}\"")

    # 2. 组装 Prompt 调用 Qwen-Plus
    print("\n[Step 2] 正在注入用户画像并请求 Qwen-Plus...")
    
    system_prompt = f"""You are the AI digital wellness & energy recovery companion inside the Tired? app.
Your role: An empathetic, realistic, non-preachy rest guide.
Core rules:
1. Reply in natural, warm, empathetic Chinese.
2. NEVER preach with clichés like 'you lack discipline' or 'put down your phone immediately'.
3. Keep responses concise (3-6 sentences).

[Long-term User Memory & Core Profile]
- High-risk fatigue window: 23:00 - 01:30
- High-drain habits: 短视频、社交信息流
- Recovery preference: 偏好音频回血/雨声，讨厌说教
- User lifestyle context: 白天高强度脑力消耗，晚上下班后容易通过手机报复性放松

INSTRUCTION:
根据上述画像，用极具同理心、懂他疲惫的方式回复，给出温和的微步软着陆方案。"""

    client = openai.OpenAI(api_key=config.BAILIAN_API_KEY, base_url=config.BAILIAN_BASE_URL)
    response = client.chat.completions.create(
        model=config.CHAT_MODEL,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": test_query}
        ],
        temperature=0.7
    )

    answer = response.choices[0].message.content.strip()

    print("\n" + "=" * 60)
    print("[AGENT RESULT] AI Agent 回答效果:")
    print("=" * 60)
    print(answer)
    print("=" * 60)

if __name__ == "__main__":
    run_test()
