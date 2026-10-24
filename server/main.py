from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import openai

import config

app = FastAPI(
    title="Tired? Digital Wellness Agent Backend",
    description="支持端侧核心画像与阿里云百炼 Qwen-Plus 的数字健康后端",
    version="1.1.0"
)

# 允许跨域（支持 Android 模拟器或本地前端访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化 OpenAI 兼容客户端
llm_client = openai.OpenAI(
    api_key=config.BAILIAN_API_KEY,
    base_url=config.BAILIAN_BASE_URL
)

# ----------------- 数据模型 -----------------

class ChatMessage(BaseModel):
    role: str
    content: str

class UserProfile(BaseModel):
    dangerTimeRange: Optional[str] = "23:00 - 01:30"
    triggerAppHabits: Optional[str] = "短视频、社交平台信息流"
    reliefPreferences: Optional[str] = "偏好白噪音/轻音乐，讨厌大道理说教，温和同理心"
    contextBio: Optional[str] = "白天高强度脑力消耗，晚上下班后容易通过手机报复性放松"

class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    device_summary: Optional[str] = ""
    user_profile: Optional[UserProfile] = None

# ----------------- 路由接口 -----------------

@app.get("/api/health")
def health_check():
    return {
        "status": "ok",
        "chat_model": config.CHAT_MODEL
    }

@app.post("/api/chat")
def chat_with_agent(req: ChatRequest):
    """
    【AI 对话接口】
    1. 动态组装：常驻画像 + 今日设备现状
    2. 调用 Qwen-Plus 给出极富共情力与个性化的回答
    """
    if not req.messages:
        raise HTTPException(status_code=400, detail="Messages cannot be empty")

    # 1. 动态组装 System Prompt
    system_prompt_parts = [
        "You are the AI digital wellness & energy recovery companion inside the Tired? app.",
        "Your role: An empathetic, realistic, non-preachy rest guide. You help the user understand and recover from 'fake-rest' (exhausting doomscrolling/phone addiction) and cognitive burnout.",
        "Core rules:",
        "1. Reply in the exact same language as the user (natural, empathetic Chinese if user speaks Chinese, English if English).",
        "2. Be warm, candid, and practical. NEVER preach or scold the user with textbook health clichés like 'you lack discipline' or 'put down your phone immediately'.",
        "3. Keep responses concise (3-6 sentences or short punchy bullet points) to avoid adding reading fatigue."
    ]

    # 注入用户核心画像
    if req.user_profile:
        system_prompt_parts.append(f"""
[Long-term User Memory & Core Profile]
- High-risk fatigue window: {req.user_profile.dangerTimeRange}
- High-drain habits: {req.user_profile.triggerAppHabits}
- Recovery preference & boundaries: {req.user_profile.reliefPreferences}
- User lifestyle context: {req.user_profile.contextBio}
INSTRUCTION: Tailor your empathy, tone, and practical recovery suggestions directly according to this profile.""")

    # 注入今日设备数据
    if req.device_summary and req.device_summary.strip():
        system_prompt_parts.append(f"""
[Today's Device Activity Context]
{req.device_summary}""")

    full_system_prompt = "\n".join(system_prompt_parts)

    # 2. 组装发给大模型的消息列表
    llm_messages = [{"role": "system", "content": full_system_prompt}]
    for msg in req.messages[-10:]:  # 取最近10条保持上下文连贯
        llm_messages.append({"role": msg.role, "content": msg.content})

    # 3. 调用百炼 Qwen-Plus
    try:
        response = llm_client.chat.completions.create(
            model=config.CHAT_MODEL,
            messages=llm_messages,
            temperature=0.7,
            max_tokens=900
        )
        answer = response.choices[0].message.content.strip()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM call failed: {str(e)}")

    return {
        "content": answer
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
