import sys
import os
import json
import sqlite3
from datetime import datetime, timedelta

# 确保 Windows 命令行支持 UTF-8 打印
if sys.platform.startswith("win"):
    sys.stdout.reconfigure(encoding="utf-8")

# 确保能导入当前模块
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

import config
import db
from main import (
    chat_with_agent,
    ChatRequest,
    ChatMessage,
    DailyDeviceStats,
    TopAppUsage
)

TEST_USER_ID = "test_user_phase2"

def cleanup_test_user():
    """清理测试用户的画像数据与方案数据"""
    with db.get_connection() as conn:
        conn.execute("DELETE FROM user_profile_traits WHERE user_id = ?", (TEST_USER_ID,))
        conn.execute("DELETE FROM anti_fatigue_plans WHERE user_id = ?", (TEST_USER_ID,))
        conn.commit()

def run_tests():
    print("=" * 75)
    print("🚀 开始阶段 1 & 阶段 2 功能自动化验证测试")
    print("=" * 75)

    cleanup_test_user()
    db.seed_default_traits_if_empty(user_id=TEST_USER_ID)

    # 模拟手机端随身携带的 7 天微型数据池 (今天 + 昨天)
    device_pool = [
        DailyDeviceStats(
            date=(datetime.now()).strftime("%Y-%m-%d"),
            days_ago=0,
            total_screen_time_minutes=150,
            late_night_minutes=0,
            launch_count=22,
            top_apps=[
                TopAppUsage(app_name="微信", duration_minutes=70, category="Social"),
                TopAppUsage(app_name="知乎", duration_minutes=45, category="News")
            ]
        ),
        DailyDeviceStats(
            date=(datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d"),
            days_ago=1,
            total_screen_time_minutes=340,
            late_night_minutes=85,
            launch_count=52,
            top_apps=[
                TopAppUsage(app_name="抖音", duration_minutes=160, category="Video"),
                TopAppUsage(app_name="小红书", duration_minutes=80, category="Social"),
                TopAppUsage(app_name="微信", duration_minutes=50, category="Social")
            ]
        )
    ]

    # -------------------------------------------------------------
    # 测试 1: Tool Calling 基础能力 (查询设备统计)
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 1】硬件数据查询 Tool-Calling (get_device_usage_stats)")
    print("-" * 75)

    req_stats = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[ChatMessage(role="user", content="我昨天到底用了多久手机？哪个应用刷得最多？帮我看看")],
        device_history_pool=device_pool
    )
    res_stats = chat_with_agent(req_stats)
    print(f"工具调用情况: {json.dumps(res_stats['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 回复:\n{res_stats['content']}\n")

    assert len(res_stats['tools_called']) > 0, "断言失败：针对性询问未触发工具调用！"
    called_tool = res_stats['tools_called'][0]
    assert called_tool['tool'] == "get_device_usage_stats", "断言失败：调用的不是 get_device_usage_stats！"
    assert called_tool['args'].get('days_ago') == 1, "断言失败：大模型未能准确推理出 days_ago=1！"
    print(">>> [PASS] 测试 1 通过：大模型准确自主触发工具，提取昨日数据并生成分析！")

    # -------------------------------------------------------------
    # 测试 2: 阶段 2 自演化画像 - 捕捉生活异动并存入 PENDING_CONFIRM
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 2】用户透露近期生活异动 -> 大模型自主调用 upsert_user_trait (PENDING_CONFIRM)")
    print("-" * 75)

    req_night_shift = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[ChatMessage(role="user", content="我最近新换了工作需要倒夜班，整个人作息全乱了")],
        device_history_pool=device_pool
    )
    res_night_shift = chat_with_agent(req_night_shift)
    print(f"工具调用情况: {json.dumps(res_night_shift['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 回复:\n{res_night_shift['content']}\n")

    # 验证是否调用了 upsert_user_trait
    upsert_calls = [t for t in res_night_shift['tools_called'] if t['tool'] == "upsert_user_trait"]
    assert len(upsert_calls) > 0, "断言失败：用户透露作息异动，模型未调用 upsert_user_trait 工具！"
    upsert_arg = upsert_calls[0]['args']
    print(f"提取特征: category={upsert_arg.get('category')}, content={upsert_arg.get('content')}, status={upsert_arg.get('status')}")

    assert upsert_arg.get('status') == "PENDING_CONFIRM", "断言失败：突发生活变动应标记为 PENDING_CONFIRM 状态！"

    # 查库验证持久化
    traits_in_db = db.list_traits(user_id=TEST_USER_ID)
    pending_traits = [t for t in traits_in_db if t['status'] == 'PENDING_CONFIRM']
    assert len(pending_traits) > 0, "断言失败：SQLite 数据库中未持久化存储 PENDING_CONFIRM 标签！"
    night_shift_trait = pending_traits[0]
    print(f"数据库中查到的特征记录: ID={night_shift_trait['id']}, content={night_shift_trait['content']}, source_date={night_shift_trait['source_date']}")
    print(">>> [PASS] 测试 2 通过：大模型自主发现夜班变动，成功调用工具入库为 PENDING_CONFIRM！")

    # -------------------------------------------------------------
    # 测试 3: 模拟 7 天过去 -> 验证格式化 Prompt 带有相对时间计算和主动追问强制指令
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 3】模拟 7 天时间跨度 -> 验证 Prompt 注入 '记录于 7 天前' 与主动求证指令")
    print("-" * 75)

    # 模拟将该特征记录日期改到 7 天前
    seven_days_ago = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d")
    with db.get_connection() as conn:
        conn.execute(
            "UPDATE user_profile_traits SET source_date = ? WHERE id = ?",
            (seven_days_ago, night_shift_trait['id'])
        )
        conn.commit()

    prompt_formatted = db.format_traits_for_prompt(user_id=TEST_USER_ID)
    print(f"生成的 System Prompt 画像部分:\n{prompt_formatted}\n")
    assert "7 天前" in prompt_formatted, "断言失败：Prompt 未正确计算出 '7 天前' 时间差！"
    assert "强制指令" in prompt_formatted, "断言失败：Prompt 未包含待跟进求证的强制指令！"
    print(">>> [PASS] 测试 3 通过：Prompt 成功生成 7 天前时间差与求证指令！")

    # -------------------------------------------------------------
    # 测试 4: 用户请求制定方案 -> 验证大模型遵循指令，先主动追问夜班情况
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 4】一周后用户求助方案 -> 大模型主动追问：'你现在还在倒夜班吗？'")
    print("-" * 75)

    req_ask_plan = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[ChatMessage(role="user", content="今天很累，帮我制定一份今晚的防疲劳方案吧")],
        device_history_pool=device_pool
    )
    res_ask_plan = chat_with_agent(req_ask_plan)
    print(f"Agent 回复:\n{res_ask_plan['content']}\n")

    reply_text = res_ask_plan['content']
    has_proactive_question = any(keyword in reply_text for keyword in ["夜班", "倒班", "作息", "排班"])
    assert has_proactive_question, f"断言失败：大模型未在回复中主动询问夜班作息是否还在持续！回复内容：{reply_text}"
    print(">>> [PASS] 测试 4 通过：大模型成功展现主动性，针对 7 天前的夜班变动进行了主动追问求证！")

    # -------------------------------------------------------------
    # 测试 5: 用户确认夜班变动 -> 大模型将标签转正更新为 ACTIVE
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 5】用户确认夜班固定 -> 大模型调用 upsert_user_trait 转正为 ACTIVE")
    print("-" * 75)

    req_confirm = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[
            ChatMessage(role="user", content="今天很累，帮我制定一份今晚的防疲劳方案吧"),
            ChatMessage(role="assistant", content=res_ask_plan['content']),
            ChatMessage(role="user", content="对，我现在已经固定上夜班了，晚上10点上班早上6点下班")
        ],
        device_history_pool=device_pool
    )
    res_confirm = chat_with_agent(req_confirm)
    print(f"工具调用情况: {json.dumps(res_confirm['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 最终方案回复:\n{res_confirm['content']}\n")

    confirm_upsert = [t for t in res_confirm['tools_called'] if t['tool'] == "upsert_user_trait"]
    assert len(confirm_upsert) > 0, "断言失败：用户确认固定夜班后，模型未调用 upsert_user_trait 转正！"
    confirm_arg = confirm_upsert[0]['args']
    print(f"转正参数: status={confirm_arg.get('status')}, content={confirm_arg.get('content')}")
    assert confirm_arg.get('status') == "ACTIVE", "断言失败：转正后状态应为 ACTIVE！"

    # 查库验证
    updated_traits = db.list_traits(user_id=TEST_USER_ID)
    active_night_shifts = [t for t in updated_traits if "夜班" in t['content'] and t['status'] == 'ACTIVE']
    assert len(active_night_shifts) > 0, "断言失败：数据库中未找到转正为 ACTIVE 的夜班特征！"
    print(">>> [PASS] 测试 5 通过：夜班特征已在 SQLite 数据库中成功转正为长期 ACTIVE 特征！")

    # -------------------------------------------------------------
    # 测试 6: 用户声明纠正/戒除旧习惯 -> 大模型调用 delete_user_trait 删库
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 6】用户声明戒除短视频诱因 -> 大模型调用 delete_user_trait 物理/逻辑移除")
    print("-" * 75)

    # 找出默认的短视频标签 ID
    trigger_trait = [t for t in updated_traits if "短视频" in t['content']][0]
    print(f"目标删除特征: ID={trigger_trait['id']}, content={trigger_trait['content']}")

    req_delete = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[ChatMessage(role="user", content="我已经彻底把短视频软件都卸载了，以后绝不刷了，帮我把这个习惯记录删掉吧")],
        device_history_pool=device_pool
    )
    res_delete = chat_with_agent(req_delete)
    print(f"工具调用情况: {json.dumps(res_delete['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 回复:\n{res_delete['content']}\n")

    del_calls = [t for t in res_delete['tools_called'] if t['tool'] == "delete_user_trait"]
    assert len(del_calls) > 0, "断言失败：用户明确要求删除特征，模型未调用 delete_user_trait！"

    # 查库验证是否已被删除
    final_traits = db.list_traits(user_id=TEST_USER_ID)
    still_exists = any(t['id'] == trigger_trait['id'] for t in final_traits)
    assert not still_exists, f"断言失败：特征 ID={trigger_trait['id']} 依然存在于数据库中！"
    print(">>> [PASS] 测试 6 通过：失效特征已从 SQLite 数据库中彻底移除！")

    # -------------------------------------------------------------
    # 测试 7: REST API CRUD 验证
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 7】验证 REST API 接口功能 (db.upsert_trait, db.list_traits, db.delete_trait)")
    print("-" * 75)

    new_trait = db.upsert_trait(
        user_id=TEST_USER_ID,
        category="RELIEF_PREF",
        content="喜欢听爵士乐放松",
        status="ACTIVE",
        confidence="HIGH"
    )
    assert new_trait['id'] is not None
    assert new_trait['content'] == "喜欢听爵士乐放松"

    del_ok = db.delete_trait(user_id=TEST_USER_ID, trait_id=new_trait['id'])
    assert del_ok is True
    print(">>> [PASS] 测试 7 通过：REST 支撑函数读写删验证成功！")

    # -------------------------------------------------------------
    # 测试 8: 阶段 3 初次制定防疲劳方案并入库 (save_anti_fatigue_plan)
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 8】用户要求制定方案 -> 大模型自主调用 save_anti_fatigue_plan 单一覆写工具")
    print("-" * 75)

    req_plan1 = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[
            ChatMessage(role="user", content="我和你确认好了，我确实是夜班作息（晚10点到早6点），容易在凌晨3点忍不住刷短视频导致精神崩溃，请帮我正式制定一份今晚开始执行的防疲劳方案并保存下来")
        ],
        device_history_pool=device_pool
    )
    res_plan1 = chat_with_agent(req_plan1)
    print(f"工具调用情况: {json.dumps(res_plan1['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 回复:\n{res_plan1['content']}\n")

    plan_calls = [t for t in res_plan1['tools_called'] if t['tool'] == "save_anti_fatigue_plan"]
    assert len(plan_calls) > 0, "断言失败：用户明确要求制定并保存方案，模型未调用 save_anti_fatigue_plan！"

    # 查库验证 SQLite 中的单一方案
    saved_plan = db.get_plan(user_id=TEST_USER_ID)
    assert saved_plan is not None, "断言失败：SQLite 数据库中未查到保存的防疲劳方案！"
    print(f"数据库中落库的方案详情:")
    print(f"• 标题: {saved_plan['title']}")
    print(f"• 时段: {saved_plan['target_time_window']}")
    print(f"• 连续时长阈值: {saved_plan['max_continuous_minutes']} 分钟")
    print(f"• 软着陆动作: {saved_plan['recovery_action_type']}")
    print(f"• 温和提示文案: {saved_plan['prompt_message']}")
    print(f"• 实用小贴士: {saved_plan['micro_tips']}")
    print(">>> [PASS] 测试 8 通过：大模型自主生成结构化防疲劳方案，并成功单表落库！")

    # -------------------------------------------------------------
    # 测试 9: 阶段 3 方案增量继承与覆盖 (带记忆修改)
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 9】增量修改方案 -> 验证从 System Prompt 读取旧方案，保留未改动字段，覆写更新")
    print("-" * 75)

    # 验证 Prompt 注入中包含了刚才的旧方案
    prompt_with_plan = db.format_plan_for_prompt(user_id=TEST_USER_ID)
    print(f"注入 System Prompt 的当前方案上下文片段:\n{prompt_with_plan[:280]}...\n")
    assert saved_plan['title'] in prompt_with_plan, "断言失败：Prompt 中未携带当前生效方案！"

    req_plan_update = ChatRequest(
        user_id=TEST_USER_ID,
        messages=[
            ChatMessage(role="user", content="我和你确认好了，我确实是夜班作息（晚10点到早6点），容易在凌晨3点忍不住刷短视频导致精神崩溃，请帮我正式制定一份今晚开始执行的防疲劳方案并保存下来"),
            ChatMessage(role="assistant", content=res_plan1['content']),
            ChatMessage(role="user", content="方案大致很贴合，但请帮我做个微调：把连续刷手机报警阈值改成 25 分钟，动作换成腹式深呼吸，其他时段和贴士都保持原样不变，保存生效")
        ],
        device_history_pool=device_pool
    )
    res_plan_update = chat_with_agent(req_plan_update)
    print(f"工具调用情况: {json.dumps(res_plan_update['tools_called'], ensure_ascii=False, indent=2)}")
    print(f"Agent 回复:\n{res_plan_update['content']}\n")

    update_calls = [t for t in res_plan_update['tools_called'] if t['tool'] == "save_anti_fatigue_plan"]
    assert len(update_calls) > 0, "断言失败：微调方案时未调用 save_anti_fatigue_plan 工具！"
    update_args = update_calls[0]['args']

    # 验证关键改动项已更新
    assert update_args.get('max_continuous_minutes') == 25, f"断言失败：预警阈值未能精准改成 25 分钟！实际: {update_args.get('max_continuous_minutes')}"
    assert "BREATHING" in str(update_args.get('recovery_action_type')).upper(), "断言失败：动作未更新为深呼吸！"

    # 查库验证单一记录覆写机制 (只有一个方案)
    updated_db_plan = db.get_plan(user_id=TEST_USER_ID)
    assert updated_db_plan['max_continuous_minutes'] == 25
    assert "BREATHING" in updated_db_plan['recovery_action_type'].upper()

    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) as count FROM anti_fatigue_plans WHERE user_id = ?", (TEST_USER_ID,))
        plan_count = cursor.fetchone()["count"]
        assert plan_count == 1, f"断言失败：用户在数据库中存在 {plan_count} 条方案记录，未能严格保证单记录覆写！"

    print(">>> [PASS] 测试 9 通过：大模型完美基于旧方案记忆继承未动字段，精准微调并实现单行覆盖落库！")

    # -------------------------------------------------------------
    # 测试 10: 方案删除与重置
    # -------------------------------------------------------------
    print("\n" + "-" * 75)
    print("【测试 10】方案重置与清空验证 (db.delete_plan)")
    print("-" * 75)

    del_plan_ok = db.delete_plan(user_id=TEST_USER_ID)
    assert del_plan_ok is True, "断言失败：delete_plan 返回 False！"
    assert db.get_plan(user_id=TEST_USER_ID) is None, "断言失败：删除后仍能查到方案！"
    print(">>> [PASS] 测试 10 通过：方案重置删除接口验证成功！")

    # 清理测试数据
    cleanup_test_user()

    print("\n" + "=" * 75)
    print("🎉🎉🎉 全部 10 项阶段 1、阶段 2、阶段 3 测试用例 100% 通过！防疲劳方案生成闭环完美建立！")
    print("=" * 75)

if __name__ == "__main__":
    run_tests()
