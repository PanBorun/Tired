from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
from datetime import datetime, timedelta
import json
import openai

import config
import db

app = FastAPI(
    title="Tired? Digital Wellness Agent Backend",
    description="支持端侧核心画像、自演化记忆、Tool Calling 与 Qwen-Plus 的数字健康智能体后端",
    version="2.1.0"
)

# 允许跨域（支持 Android 模拟器或局域网真机访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化 OpenAI 兼容客户端（阿里云百炼）
llm_client = openai.OpenAI(
    api_key=config.BAILIAN_API_KEY,
    base_url=config.BAILIAN_BASE_URL
)

# ----------------- 数据模型 -----------------

class ChatMessage(BaseModel):
    role: str
    content: str

class TopAppUsage(BaseModel):
    app_name: str
    duration_minutes: int
    category: Optional[str] = ""

class DailyDeviceStats(BaseModel):
    date: str
    days_ago: int = 0
    total_screen_time_minutes: int = 0
    late_night_minutes: int = 0
    launch_count: int = 0
    top_apps: List[TopAppUsage] = []

class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    device_history_pool: List[DailyDeviceStats] = []
    user_id: Optional[str] = "default_user"

class TraitMutationRequest(BaseModel):
    category: str
    content: str
    status: Optional[str] = "ACTIVE"
    confidence: Optional[str] = "HIGH"

class PlanMutationRequest(BaseModel):
    title: str
    target_fatigue_type: str
    target_time_window: str
    trigger_apps: List[str] = []
    max_continuous_minutes: int
    prompt_message: str
    recovery_action_type: str
    action_duration_minutes: Optional[int] = 3
    micro_tips: Optional[List[str]] = []

# ----------------- Agent 工具定义 (Tools Schema) -----------------

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_device_usage_stats",
            "description": "查询用户最近几天的真实手机屏幕使用数据（包括总时长、深夜时长、启动切屏次数、以及各应用详细使用时长列表）。当用户询问自己今天或过去某天的手机使用情况、具体在哪个App花时间最多、深夜使用情况，或者询问为什么疲劳心慌时调用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "days_ago": {
                        "type": "integer",
                        "description": "查询几天前的数据。0 代表今天，1 代表昨天，2 代表前天...最多支持 6 天前。"
                    }
                },
                "required": ["days_ago"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "upsert_user_trait",
            "description": "记录、更新或转正用户的长期画像特征（作息节奏、失控诱因、身心反应、安抚偏好、生活背景）。当用户在对话中透露了新的作息/偏好/习惯，或者确认了某条待跟进的生活剧变（如确认了正在倒夜班）时调用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "category": {
                        "type": "string",
                        "enum": ["WORK_SCHEDULE", "TRIGGER_HABIT", "SYMPTOM", "RELIEF_PREF", "LIFE_CONTEXT"],
                        "description": "特征类别：WORK_SCHEDULE(作息节奏)、TRIGGER_HABIT(失控诱因)、SYMPTOM(典型反应)、RELIEF_PREF(安抚偏好与红线)、LIFE_CONTEXT(生活背景)"
                    },
                    "content": {
                        "type": "string",
                        "description": "提炼出的客观事实，例如：'新换了工作需要上夜班'、'偏好雨声音频，反感大道理说教'"
                    },
                    "status": {
                        "type": "string",
                        "enum": ["ACTIVE", "PENDING_CONFIRM"],
                        "description": "生命周期状态：ACTIVE(稳定的长期习惯/偏好)；PENDING_CONFIRM(具有时效性或突发的近期剧变，如换夜班/备考，下次制定方案前需向用户主动确认)"
                    },
                    "trait_id": {
                        "type": "integer",
                        "description": "可选。如果是对既有某个标签的更新或转正（如将已有的夜班待求证转为长期生效），传入其ID；全新记录则不传。"
                    }
                },
                "required": ["category", "content", "status"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "delete_user_trait",
            "description": "当用户在对话中明确纠正、否定或声明某个旧习惯/作息已彻底结束时（如'我早就不上夜班了'、'我不刷短视频了'），调用此工具删除对应的画像标签。",
            "parameters": {
                "type": "object",
                "properties": {
                    "trait_id": {
                        "type": "integer",
                        "description": "需要删除的特征标签 ID"
                    },
                    "reason": {
                        "type": "string",
                        "description": "删除原因记录"
                    }
                },
                "required": ["trait_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "save_anti_fatigue_plan",
            "description": "为用户制定、保存或覆盖更新一份个性化防疲劳方案（单一当前方案）。当用户与你沟通确认了其真实生活作息与疲劳诱因、请求制定减耗/防疲劳方案，或者要求调整当前方案的具体参数时调用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {
                        "type": "string",
                        "description": "方案精炼标题，如：'夜班清醒续航与晨间防蓝光方案'、'睡前防短视频内耗软着陆方案'"
                    },
                    "target_fatigue_type": {
                        "type": "string",
                        "enum": ["DOPAMINE_OVERLOAD", "SLEEP_PROCRASTINATION", "FRAGMENTED_ANXIETY", "INFO_OVERLOAD", "PHYSICAL_EXHAUSTION"],
                        "description": "针对的疲劳类型：DOPAMINE_OVERLOAD(多巴胺过载/刷屏上瘾)、SLEEP_PROCRASTINATION(拖延睡眠/晚睡)、FRAGMENTED_ANXIETY(碎片切换焦虑)、INFO_OVERLOAD(信息过载脑胀)、PHYSICAL_EXHAUSTION(身心透支体虚)"
                    },
                    "target_time_window": {
                        "type": "string",
                        "description": "重点监控与执行时段，如 '22:00-06:00' 或 '23:30-01:30'，必须与用户真实作息严格匹配"
                    },
                    "trigger_apps": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "高危诱因应用列表，如 ['抖音', '快手', '短视频', '社交信息流']"
                    },
                    "max_continuous_minutes": {
                        "type": "integer",
                        "description": "连续使用多少分钟时在手机端触发温和干预，建议 20-60 分钟"
                    },
                    "prompt_message": {
                        "type": "string",
                        "description": "手机端触发干预时弹出的温和提醒语，体现极致同理心，严禁居高临下批评或说教"
                    },
                    "recovery_action_type": {
                        "type": "string",
                        "enum": ["WHITE_NOISE_RAIN", "BREATHING_EXERCISE", "MICRO_STRETCH", "SCREEN_OFF_REST"],
                        "description": "推荐的软着陆恢复手段：WHITE_NOISE_RAIN(雨声白噪音)、BREATHING_EXERCISE(腹式深呼吸)、MICRO_STRETCH(微动作伸展放松)、SCREEN_OFF_REST(闭目养神扣下手机)"
                    },
                    "action_duration_minutes": {
                        "type": "integer",
                        "description": "建议动作执行时长（分钟），如 3 或 5"
                    },
                    "micro_tips": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "2-3条低摩擦、极简的落地小贴士，如 ['手机扣在桌上倒数60秒', '喝一杯常温洋甘菊水']"
                    }
                },
                "required": [
                    "title",
                    "target_fatigue_type",
                    "target_time_window",
                    "trigger_apps",
                    "max_continuous_minutes",
                    "prompt_message",
                    "recovery_action_type",
                    "micro_tips"
                ]
            }
        }
    }
]

def execute_get_device_usage_stats(days_ago: int, pool: List[DailyDeviceStats]) -> Dict[str, Any]:
    """从随身数据池中检索对应天数的使用统计"""
    for day in pool:
        if day.days_ago == days_ago:
            return {
                "date": day.date,
                "days_ago": day.days_ago,
                "total_screen_time_minutes": day.total_screen_time_minutes,
                "late_night_minutes": day.late_night_minutes,
                "launch_count": day.launch_count,
                "top_apps": [
                    {
                        "app_name": app.app_name,
                        "duration_minutes": app.duration_minutes,
                        "category": app.category
                    }
                    for app in day.top_apps
                ]
            }

    target_date = (datetime.now() - timedelta(days=days_ago)).strftime("%Y-%m-%d")
    for day in pool:
        if day.date == target_date:
            return {
                "date": day.date,
                "days_ago": days_ago,
                "total_screen_time_minutes": day.total_screen_time_minutes,
                "late_night_minutes": day.late_night_minutes,
                "launch_count": day.launch_count,
                "top_apps": [
                    {
                        "app_name": app.app_name,
                        "duration_minutes": app.duration_minutes,
                        "category": app.category
                    }
                    for app in day.top_apps
                ]
            }

    return {
        "days_ago": days_ago,
        "message": f"暂无 {days_ago} 天前的详细屏幕数据记录。"
    }

# ----------------- 路由接口 -----------------

@app.get("/api/health")
def health_check():
    return {
        "status": "ok",
        "chat_model": config.CHAT_MODEL,
        "tools_supported": [
            "get_device_usage_stats",
            "upsert_user_trait",
            "delete_user_trait",
            "save_anti_fatigue_plan"
        ],
        "database": "sqlite3_active"
    }

# ----- 用户画像 REST 接口 (供手机设置页查看与编辑) -----

@app.get("/api/profile/traits")
def get_user_traits(user_id: str = Query("default_user")):
    """获取用户所有画像标签列表"""
    traits = db.list_traits(user_id=user_id, include_archived=False)
    return {"user_id": user_id, "traits": traits}

@app.post("/api/profile/traits")
def add_user_trait(req: TraitMutationRequest, user_id: str = Query("default_user")):
    """用户手动新增画像标签"""
    trait = db.upsert_trait(
        user_id=user_id,
        category=req.category,
        content=req.content,
        status=req.status or "ACTIVE",
        confidence=req.confidence or "HIGH"
    )
    return {"status": "success", "trait": trait}

@app.put("/api/profile/traits/{trait_id}")
def update_user_trait(trait_id: int, req: TraitMutationRequest, user_id: str = Query("default_user")):
    """用户手动修改画像标签"""
    trait = db.upsert_trait(
        user_id=user_id,
        category=req.category,
        content=req.content,
        status=req.status or "ACTIVE",
        confidence=req.confidence or "HIGH",
        trait_id=trait_id
    )
    return {"status": "success", "trait": trait}

@app.delete("/api/profile/traits/{trait_id}")
def delete_user_trait(trait_id: int, user_id: str = Query("default_user")):
    """用户手动删除画像标签"""
    success = db.delete_trait(user_id=user_id, trait_id=trait_id)
    if not success:
        raise HTTPException(status_code=404, detail="Trait not found")
    return {"status": "success", "deleted_id": trait_id}

# ----- 阶段 3：防疲劳方案 REST 接口 (供手机端同步与阶段4本地后台守护) -----

@app.get("/api/plan")
def get_current_plan(user_id: str = Query("default_user")):
    """获取用户当前唯一生效的防疲劳方案"""
    plan = db.get_plan(user_id=user_id)
    return {"user_id": user_id, "plan": plan}

@app.post("/api/plan")
def set_current_plan(req: PlanMutationRequest, user_id: str = Query("default_user")):
    """手动保存或更新防疲劳方案"""
    plan = db.save_plan(
        user_id=user_id,
        title=req.title,
        target_fatigue_type=req.target_fatigue_type,
        target_time_window=req.target_time_window,
        trigger_apps=req.trigger_apps,
        max_continuous_minutes=req.max_continuous_minutes,
        prompt_message=req.prompt_message,
        recovery_action_type=req.recovery_action_type,
        action_duration_minutes=req.action_duration_minutes or 3,
        micro_tips=req.micro_tips or []
    )
    return {"status": "success", "plan": plan}

@app.delete("/api/plan")
def remove_current_plan(user_id: str = Query("default_user")):
    """重置/删除防疲劳方案"""
    success = db.delete_plan(user_id=user_id)
    return {"status": "success" if success else "not_found"}

# ----- Agent 核心对话接口 -----

@app.post("/api/chat")
def chat_with_agent(req: ChatRequest):
    """
    【Tool-Calling Agent 对话接口】
    1. 动态注入：直接从后端 SQLite 数据库读取最新自演化长期画像标签
    2. 大模型自主推理是否需要调用：
       - get_device_usage_stats (查屏幕数据)
       - upsert_user_trait (自动新增、转正习惯标签，如换夜班)
       - delete_user_trait (删除失效标签)
    3. 在服务端完成真实的 ReAct 循环，并在 SQLite 中真正持久化落库
    """
    if not req.messages:
        raise HTTPException(status_code=400, detail="Messages cannot be empty")

    user_id = req.user_id or "default_user"

    # 1. 组装 System Prompt (注入最新的长期画像与近期变动追踪)
    system_prompt_parts = [
        "You are the AI digital wellness & energy recovery companion inside the Tired? app.",
        "Your role: An empathetic, realistic, non-preachy rest guide. You help the user understand and recover from 'fake-rest' (exhausting doomscrolling/phone addiction) and cognitive burnout.",
        "Core rules:",
        "1. Reply in the exact same language as the user (natural, empathetic Chinese if user speaks Chinese, English if English).",
        "2. Be warm, candid, and practical. NEVER preach or scold the user with textbook health clichés like 'you lack discipline' or 'put down your phone immediately'.",
        "3. Keep responses concise (3-6 sentences or short punchy bullet points) to avoid adding reading fatigue.",
        "4. Tool Calling Instructions:",
        "   - 'get_device_usage_stats': Call when the user asks about screen time, app usage, or why they feel physically drained.",
        "   - 'upsert_user_trait': Call whenever the user reveals a new habit, routine change (e.g. starting night shifts), or confirms an ongoing temporary change. Set status='PENDING_CONFIRM' if it is a recent/tentative change that needs follow-up, or 'ACTIVE' if confirmed.",
        "   - 'delete_user_trait': Call when the user negates or declares an old habit/schedule is over.",
        "   - 'save_anti_fatigue_plan': Call when formulating, refining, or adjusting the user's structured anti-fatigue strategy. If an active plan already exists, always inherit and preserve all unaffected fields, only updating the requested fields."
    ]

    # 直接从后端 SQLite 数据库提取格式化画像（包含时间差计算与待确认指令）
    formatted_profile = db.format_traits_for_prompt(user_id=user_id)
    system_prompt_parts.append(formatted_profile)

    # 动态注入当前生效的防疲劳方案（若存在，确保大模型具备连续性记忆进行增量修改）
    formatted_plan = db.format_plan_for_prompt(user_id=user_id)
    system_prompt_parts.append(formatted_plan)

    full_system_prompt = "\n".join(system_prompt_parts)

    # 2. 组装消息列表
    llm_messages = [{"role": "system", "content": full_system_prompt}]
    for msg in req.messages[-10:]:
        llm_messages.append({"role": msg.role, "content": msg.content})

    tools_called_log = []

    try:
        # 3. 第一次请求大模型（开放 tools 清单）
        first_resp = llm_client.chat.completions.create(
            model=config.CHAT_MODEL,
            messages=llm_messages,
            tools=TOOLS,
            tool_choice="auto",
            temperature=0.7,
            max_tokens=900
        )
        choice = first_resp.choices[0]
        response_msg = choice.message

        # 4. 判断大模型是否自主发起了 Tool Call (ReAct 循环)
        if response_msg.tool_calls:
            llm_messages.append(response_msg)

            for tool_call in response_msg.tool_calls:
                func_name = tool_call.function.name
                func_args_str = tool_call.function.arguments
                try:
                    args = json.loads(func_args_str)
                except Exception:
                    args = {}

                # 工具 1：查询硬件屏幕数据
                if func_name == "get_device_usage_stats":
                    days_ago = args.get("days_ago", 0)
                    tool_result = execute_get_device_usage_stats(days_ago, req.device_history_pool)
                    tools_called_log.append({
                        "tool": func_name,
                        "args": args,
                        "result": tool_result
                    })
                    llm_messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": json.dumps(tool_result, ensure_ascii=False)
                    })

                # 工具 2：记录或更新画像特征
                elif func_name == "upsert_user_trait":
                    saved_trait = db.upsert_trait(
                        user_id=user_id,
                        category=args.get("category", "LIFE_CONTEXT"),
                        content=args.get("content", ""),
                        status=args.get("status", "ACTIVE"),
                        trait_id=args.get("trait_id")
                    )
                    tool_result = {
                        "status": "success",
                        "trait": saved_trait,
                        "message": "画像特征已成功在后端数据库中持久化记录。"
                    }
                    tools_called_log.append({
                        "tool": func_name,
                        "args": args,
                        "result": tool_result
                    })
                    llm_messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": json.dumps(tool_result, ensure_ascii=False)
                    })

                # 工具 3：删除画像特征
                elif func_name == "delete_user_trait":
                    trait_id = args.get("trait_id")
                    success = db.delete_trait(user_id=user_id, trait_id=trait_id)
                    tool_result = {
                        "status": "success" if success else "failed",
                        "message": f"特征 ID {trait_id} 已从数据库移除。"
                    }
                    tools_called_log.append({
                        "tool": func_name,
                        "args": args,
                        "result": tool_result
                    })
                    llm_messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": json.dumps(tool_result, ensure_ascii=False)
                    })

                # 工具 4：制定或调整防疲劳方案
                elif func_name == "save_anti_fatigue_plan":
                    saved_plan = db.save_plan(
                        user_id=user_id,
                        title=args.get("title", "个性化防疲劳方案"),
                        target_fatigue_type=args.get("target_fatigue_type", "DOPAMINE_OVERLOAD"),
                        target_time_window=args.get("target_time_window", "23:00-02:00"),
                        trigger_apps=args.get("trigger_apps", []),
                        max_continuous_minutes=args.get("max_continuous_minutes", 40),
                        prompt_message=args.get("prompt_message", "眼皮在抗议了，停下来缓一缓。"),
                        recovery_action_type=args.get("recovery_action_type", "WHITE_NOISE_RAIN"),
                        action_duration_minutes=args.get("action_duration_minutes", 3),
                        micro_tips=args.get("micro_tips", [])
                    )
                    tool_result = {
                        "status": "success",
                        "plan": saved_plan,
                        "message": "防疲劳方案已成功在后端数据库中保存生效。手机端将基于该策略进行本地防疲劳监控与软着陆干预。"
                    }
                    tools_called_log.append({
                        "tool": func_name,
                        "args": args,
                        "result": tool_result
                    })
                    llm_messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": json.dumps(tool_result, ensure_ascii=False)
                    })

            # 5. 携带工具执行反馈，驱动大模型输出最终结论
            second_resp = llm_client.chat.completions.create(
                model=config.CHAT_MODEL,
                messages=llm_messages,
                temperature=0.7,
                max_tokens=900
            )
            final_content = second_resp.choices[0].message.content.strip()
        else:
            final_content = response_msg.content.strip()

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM call failed: {str(e)}")

    return {
        "content": final_content,
        "tools_called": tools_called_log
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
