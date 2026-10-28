import sqlite3
import os
import time
import json
from datetime import datetime
from typing import List, Dict, Optional, Any

import config

def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(config.DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    """初始化数据库表结构与默认数据"""
    with get_connection() as conn:
        # 1. 用户特征画像表 (多条标签演化)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS user_profile_traits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL DEFAULT 'default_user',
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                confidence TEXT DEFAULT 'HIGH',
                source_date TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """)

        # 2. 个性化防疲劳方案表 (单用户单记录覆写，无冗余归档)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS anti_fatigue_plans (
                user_id TEXT PRIMARY KEY DEFAULT 'default_user',
                title TEXT NOT NULL,
                target_fatigue_type TEXT NOT NULL,
                target_time_window TEXT NOT NULL,
                trigger_apps TEXT NOT NULL,
                max_continuous_minutes INTEGER NOT NULL,
                prompt_message TEXT NOT NULL,
                recovery_action_type TEXT NOT NULL,
                action_duration_minutes INTEGER NOT NULL DEFAULT 3,
                micro_tips TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """)
        conn.commit()

    # 初始化默认基准画像（如果为空）
    seed_default_traits_if_empty()

def seed_default_traits_if_empty(user_id: str = "default_user"):
    """为新用户注入初始基准画像标签"""
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) as count FROM user_profile_traits WHERE user_id = ?", (user_id,))
        count = cursor.fetchone()["count"]
        if count == 0:
            today_str = datetime.now().strftime("%Y-%m-%d")
            now_ms = int(time.time() * 1000)
            defaults = [
                ("WORK_SCHEDULE", "常规工作日早九晚六作息", "ACTIVE", "HIGH"),
                ("TRIGGER_HABIT", "睡前短视频、社交平台信息流无意识滑屏", "ACTIVE", "HIGH"),
                ("RELIEF_PREF", "偏好白噪音与轻柔雨声，反感大道理说教，注重温和同理心", "ACTIVE", "HIGH"),
                ("LIFE_CONTEXT", "白天高强度脑力消耗，晚上下班后容易通过手机报复性放松", "ACTIVE", "HIGH")
            ]
            for cat, content, status, conf in defaults:
                cursor.execute("""
                    INSERT INTO user_profile_traits (user_id, category, content, status, confidence, source_date, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (user_id, cat, content, status, conf, today_str, now_ms))
            conn.commit()

# ==================== 用户画像标签 CRUD ====================

def list_traits(user_id: str = "default_user", include_archived: bool = False) -> List[Dict[str, Any]]:
    """获取指定用户的特征标签列表"""
    with get_connection() as conn:
        cursor = conn.cursor()
        if include_archived:
            cursor.execute(
                "SELECT * FROM user_profile_traits WHERE user_id = ? ORDER BY id ASC",
                (user_id,)
            )
        else:
            cursor.execute(
                "SELECT * FROM user_profile_traits WHERE user_id = ? AND status != 'ARCHIVED' ORDER BY id ASC",
                (user_id,)
            )
        rows = cursor.fetchall()
        return [dict(row) for row in rows]

def upsert_trait(
    user_id: str,
    category: str,
    content: str,
    status: str = "ACTIVE",
    confidence: str = "HIGH",
    trait_id: Optional[int] = None,
    source_date: Optional[str] = None
) -> Dict[str, Any]:
    """新增或更新一条画像特征"""
    now_ms = int(time.time() * 1000)
    today_str = source_date or datetime.now().strftime("%Y-%m-%d")

    with get_connection() as conn:
        cursor = conn.cursor()

        if trait_id is not None:
            cursor.execute("""
                UPDATE user_profile_traits
                SET category = ?, content = ?, status = ?, confidence = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
            """, (category, content, status, confidence, now_ms, trait_id, user_id))
            target_id = trait_id
        else:
            cursor.execute("""
                SELECT id FROM user_profile_traits
                WHERE user_id = ? AND category = ? AND content = ?
            """, (user_id, category, content))
            existing = cursor.fetchone()

            if existing:
                target_id = existing["id"]
                cursor.execute("""
                    UPDATE user_profile_traits
                    SET status = ?, confidence = ?, updated_at = ?
                    WHERE id = ?
                """, (status, confidence, now_ms, target_id))
            else:
                cursor.execute("""
                    INSERT INTO user_profile_traits (user_id, category, content, status, confidence, source_date, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (user_id, category, content, status, confidence, today_str, now_ms))
                target_id = cursor.lastrowid

        conn.commit()
        cursor.execute("SELECT * FROM user_profile_traits WHERE id = ?", (target_id,))
        return dict(cursor.fetchone())

def delete_trait(user_id: str, trait_id: int) -> bool:
    """删除一条画像特征"""
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM user_profile_traits WHERE id = ? AND user_id = ?", (trait_id, user_id))
        conn.commit()
        return cursor.rowcount > 0

CATEGORY_NAME_MAP = {
    "WORK_SCHEDULE": "作息节奏",
    "TRIGGER_HABIT": "失控诱因",
    "SYMPTOM": "典型反应",
    "RELIEF_PREF": "安抚偏好与红线",
    "LIFE_CONTEXT": "生活背景"
}

def format_traits_for_prompt(user_id: str = "default_user") -> str:
    """将用户画像标签格式化为可直接注入 System Prompt 的结构化记忆上下文"""
    traits = list_traits(user_id=user_id, include_archived=False)
    if not traits:
        return "[Long-term User Memory & Core Profile]\n(暂无记录画像)"

    active_traits = [t for t in traits if t["status"] == "ACTIVE"]
    pending_traits = [t for t in traits if t["status"] == "PENDING_CONFIRM"]

    lines = [
        "[Long-term User Memory & Core Profile (自演化长期画像记忆)]",
        "这是系统长期沉淀的关于该用户的核心特征与近期动态。请严格根据此记忆提供针对性同理心支持、定制方案与边界约束："
    ]

    # 1. 长期稳定基线
    lines.append("\n【稳定生活与偏好基线】")
    for t in active_traits:
        cat_name = CATEGORY_NAME_MAP.get(t["category"], t["category"])
        lines.append(f"- [{cat_name}] {t['content']} (置信度: {t['confidence']}, ID: {t['id']})")

    # 2. 近期重大变动追踪 (如换夜班，需主动追问)
    if pending_traits:
        lines.append("\n【⚠️ 近期重大生活/作息异动 (处于待跟进求证状态)】")
        today = datetime.now()
        for t in pending_traits:
            cat_name = CATEGORY_NAME_MAP.get(t["category"], t["category"])
            try:
                record_dt = datetime.strptime(t["source_date"], "%Y-%m-%d")
                days_ago = max(0, (today - record_dt).days)
                time_desc = f"{days_ago} 天前" if days_ago > 0 else "今天刚记录"
            except Exception:
                time_desc = t["source_date"]

            lines.append(f"- [{cat_name}] {t['content']} (记录于 {time_desc}, ID: {t['id']})")

        lines.append(
            "\n【强制指令】：用户存在处于'待跟进求证'状态的近期生活异动（如换夜班）。"
            "在与用户沟通并为其制定、调整任何防疲劳策略前，必须先在回复中温和、自然地询问该状态当前是否仍在持续（例如询问是否还在倒夜班），"
            "切忌在未确认的情况下直接套用常规作息！当用户确认后，调用 upsert_user_trait 工具更新其状态。"
        )

    return "\n".join(lines)

# ==================== 阶段 3：个性化防疲劳方案 CRUD (单用户单记录) ====================

def get_plan(user_id: str = "default_user") -> Optional[Dict[str, Any]]:
    """获取用户当前生效的唯一防疲劳方案"""
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM anti_fatigue_plans WHERE user_id = ?", (user_id,))
        row = cursor.fetchone()
        if not row:
            return None

        data = dict(row)
        # 反序列化 JSON 数组
        try:
            data["trigger_apps"] = json.loads(data["trigger_apps"])
        except Exception:
            data["trigger_apps"] = [data["trigger_apps"]] if data["trigger_apps"] else []

        try:
            data["micro_tips"] = json.loads(data["micro_tips"])
        except Exception:
            data["micro_tips"] = [data["micro_tips"]] if data["micro_tips"] else []

        return data

def save_plan(
    user_id: str,
    title: str,
    target_fatigue_type: str,
    target_time_window: str,
    trigger_apps: Any,
    max_continuous_minutes: int,
    prompt_message: str,
    recovery_action_type: str,
    action_duration_minutes: int = 3,
    micro_tips: Any = None
) -> Dict[str, Any]:
    """保存或覆盖更新用户的当前防疲劳方案（单一记录，覆写机制）"""
    now_ms = int(time.time() * 1000)

    # 规范化数组字段为 JSON 字符串
    if isinstance(trigger_apps, list):
        apps_json = json.dumps(trigger_apps, ensure_ascii=False)
    else:
        apps_json = json.dumps([str(trigger_apps)], ensure_ascii=False)

    if isinstance(micro_tips, list):
        tips_json = json.dumps(micro_tips, ensure_ascii=False)
    elif micro_tips is not None:
        tips_json = json.dumps([str(micro_tips)], ensure_ascii=False)
    else:
        tips_json = json.dumps([], ensure_ascii=False)

    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO anti_fatigue_plans (
                user_id, title, target_fatigue_type, target_time_window,
                trigger_apps, max_continuous_minutes, prompt_message,
                recovery_action_type, action_duration_minutes, micro_tips, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                title = excluded.title,
                target_fatigue_type = excluded.target_fatigue_type,
                target_time_window = excluded.target_time_window,
                trigger_apps = excluded.trigger_apps,
                max_continuous_minutes = excluded.max_continuous_minutes,
                prompt_message = excluded.prompt_message,
                recovery_action_type = excluded.recovery_action_type,
                action_duration_minutes = excluded.action_duration_minutes,
                micro_tips = excluded.micro_tips,
                updated_at = excluded.updated_at
        """, (
            user_id, title, target_fatigue_type, target_time_window,
            apps_json, max_continuous_minutes, prompt_message,
            recovery_action_type, action_duration_minutes, tips_json, now_ms
        ))
        conn.commit()

    return get_plan(user_id=user_id)

def delete_plan(user_id: str = "default_user") -> bool:
    """删除用户的防疲劳方案"""
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM anti_fatigue_plans WHERE user_id = ?", (user_id,))
        conn.commit()
        return cursor.rowcount > 0

def format_plan_for_prompt(user_id: str = "default_user") -> str:
    """
    将当前生效的防疲劳方案格式化为可直接注入 System Prompt 的结构化记忆上下文，
    并注入方案继承与演进准则！
    """
    plan = get_plan(user_id=user_id)
    if not plan:
        return (
            "\n[Current Anti-Fatigue Plan (当前执行中的防疲劳方案)]\n"
            "(用户当前尚未制定任何防疲劳方案。当用户提出需要防疲劳、制定夜间作息策略或寻求改善时，结合其长期画像与当前对话，适时调用 save_anti_fatigue_plan 为其定制。)"
        )

    apps_str = ", ".join(plan["trigger_apps"]) if plan["trigger_apps"] else "无特定限制"
    tips_str = "；".join(plan["micro_tips"]) if plan["micro_tips"] else "暂无额外贴士"

    lines = [
        "\n[Current Anti-Fatigue Plan (当前执行中的防疲劳方案)]",
        "这是用户当前正在执行的唯一有效防疲劳策略：",
        f"- 方案标题: {plan['title']}",
        f"- 针对疲劳类型: {plan['target_fatigue_type']}",
        f"- 重点监控时段: {plan['target_time_window']}",
        f"- 关注诱因应用: {apps_str}",
        f"- 连续使用预警阈值: {plan['max_continuous_minutes']} 分钟",
        f"- 端侧温和干预提示语: \"{plan['prompt_message']}\"",
        f"- 推荐软着陆恢复动作: {plan['recovery_action_type']} ({plan['action_duration_minutes']} 分钟)",
        f"- 实用小贴士: {tips_str}",
        "\n【方案演进与继承准则】：",
        "如果用户在对话中提出调整或修改方案的需求（例如“把提醒提前半小时”、“换成深呼吸”、“连续时长改成25分钟”）：",
        "1. 必须以【当前执行中的防疲劳方案】为基线进行继承；",
        "2. 用户要求改动的字段（如阈值或动作）进行精准更新；",
        "3. 用户未提及修改的字段（如时段、关注应用、原有贴士），必须完全保留继承；",
        "4. 调用 save_anti_fatigue_plan 工具覆盖生效，确保返回给客户端的方案永远完整可用。"
    ]
    return "\n".join(lines)

# 服务启动时初始化
init_db()
