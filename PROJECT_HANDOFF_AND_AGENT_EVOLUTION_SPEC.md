# 《Tired?》项目全景交付与新会话交接指南 (Project Handoff & Agent Evolution Spec)

> **目标受众**：接力本项目的下一个 AI 对话助手 / 开发团队  
> **文档版本**：v2.0 (全景交接版)  
> **项目根目录**：`c:\Users\Administrator\Desktop\TiredApp\`  
> **核心使命**：详细记录项目已实现的技术架构、代码文件分布、核心记忆与容灾机制、当前存在的本质硬伤（Pipeline vs 真正的 Agent），以及新对话框继续重构的演进路线图。

---

## 一、 项目背景与产品核心定位

- **项目名称**：`Tired?`（数字疲劳与假休息解救 Agent）
- **核心痛点**：解决现代人**“明明是在玩手机，但是感觉越玩越累、身心透支、内疚空虚”**的假性休息（Fake Rest / 数字宿醉 Digital Hangover）痛点。
- **产品哲学**：
  - **拒绝“自律警察/道德批判”**：绝不说教，不讲“你缺乏自律/立刻放下手机”等居高临下的废话。
  - **定位转变**：从被动的“屏幕时间统计工具”重塑为懂你疲惫的**“数字宿醉解救者与精力精算师”**，站在用户的防御机制这一边。

---

## 二、 完整项目工程结构与代码全景

项目采用 **“移动客户端 (Android 原生) + 本地轻量智能体后端 (Python FastAPI) + 云端大模型底座 (阿里云百炼 DashScope)”** 的端云协同架构。

### 1. 目录分工与文件地图

```text
c:\Users\Administrator\Desktop\TiredApp\
│
├── app/                                    <=== 【Android 原生客户端 (Kotlin + Jetpack Compose)】
│   ├── src/main/java/com/tired/app/
│   │   ├── presentation/                   <--- UI 展现与 ViewModel 逻辑层
│   │   │   ├── screens/home/               <--- 主页透视看板 (HomeViewModel: 屏幕数据驱动 + 开机惰性归档)
│   │   │   ├── screens/chat/               <--- 陪伴式 AI 对话页面 (AIAssistantScreen & ViewModel)
│   │   │   ├── screens/settings/           <--- 设置页 (包含用户常驻核心画像的可视化编辑弹窗)
│   │   │   └── screens/trends/             <--- 历史趋势与多维图表
│   │   ├── data/                           <--- 数据层
│   │   │   ├── local/database/             <--- Room 数据库 v4 (TiredDatabase.kt: user_profile画像表, chat_messages表)
│   │   │   ├── local/dao/                  <--- UserProfileDao.kt, ChatMessageDao.kt
│   │   │   ├── local/datasource/           <--- UsageStatsDataSource.kt (穿透系统底层获取硬件屏幕时长、切屏次数、Top5 App)
│   │   │   └── remote/datasource/          <--- QwenChatService.kt (双轨容灾网络层: 优先调本地后端，离线平滑降级直连云端)
│   │   └── domain/analyzer/                <--- LocalRuleEngine.kt (纯本地离线确定性疲劳诊断算法引擎)
│   ├── res/xml/network_security_config.xml <--- 模拟器与局域网 HTTP 明文网络白名单 (10.0.2.2 & localhost)
│   └── build.gradle.kts                    <--- 配置阿里云百炼 API Key 及各项移动端依赖
│
└── server/                                 <=== 【Python 智能体后端 (FastAPI + ChromaDB)】
    ├── main.py                             <--- 核心接口网关 (/api/chat, /api/consolidate, /api/health)
    ├── memory_store.py                     <--- ChromaDB 向量数据库引擎 (集成 text-embedding-v3 与相似度检索)
    ├── config.py                           <--- 阿里云百炼 DashScope API Key 及 Endpoint 配置
    ├── test_cli.py                         <--- 终端独立命令行测试脚本
    └── data/chroma_db/                     <--- 本地持久化向量数据库存储目录 (已加入 .gitignore)
```

---

## 三、 当前已实现的核心机制与关键技术决策复盘

### 1. 双层记忆架构（Two-Tier Memory）
- **第一层：端侧核心画像（Core Profile Memory）**
  - **存储介质**：手机本地 Room 数据库 `user_profile` 表。
  - **字段内容**：高危熬夜时段、易失控习惯、喜欢的安抚偏好、职业生活背景。
  - **特性**：隐私安全，用户在设置页面拥有完全的查看与编辑权。每次向 AI 发起请求都会强制携带，从根本上锁死 AI 的同理心与防说教边界。
- **第二层：情景向量记忆（Episodic Vector Memory）**
  - **存储介质**：后端 ChromaDB 持久化集合 `fatigue_diaries`。
  - **内容格式**：客观硬件数据（屏幕总时长、深夜时长、主导应用、疲劳类型）+ 主观身心状态（用户全天倾诉与情绪）熔炼而成的高密度切片。
  - **向量检索**：调用阿里云百炼 `text-embedding-v3`（1024 维），余弦距离严格限制在 `<= 0.78`，确保只唤醒高相关度的历史经历。

### 2. 关键设计决策背后的思考（Why & How）
- **决策一：为什么选 ChromaDB 而不是 FAISS？**
  - FAISS 是纯数学索引库，无法直接存储原始日记文本与时间元数据，必须手写大量 SQLite 胶水代码去映射 ID；而 ChromaDB 是现代 Agent 专属向量库，天然将 SQLite（存原文元数据）与 HNSW（存高维向量）封装为一体，开箱即用。
- **决策二：日记存储粒度——坚决按自然日归档，杜绝“每句话存向量”**
  - 若每聊一句话都向量化入库，库里会充斥海量“你好/好的/谢谢”等垃圾噪音，导致语义空间严重污染；且白天的屏幕时长是实时变化的，频繁写库会造成状态冲突。因此以 `diary_YYYY-MM-DD` 为唯一键，一天至多提炼固化一条高质量切片。
- **决策三：“最后一秒未开 App”的解法——次日开机惰性结算（Lazy Settlement）**
  - 在 `HomeViewModel.kt` 中设计了开机自动检查：App 启动时对比 DataStore 的 `last_consolidated_date`，若昨日未归档，自动拉取昨日**不可篡改的最终 UsageStats** 和聊天记录，调用 `/api/consolidate` 入库。0 后台耗电，哪怕隔几天打开也会自动补齐。
- **决策四：双轨容灾降级架构**
  - 在 `QwenChatService.kt` 中：优先打 `http://10.0.2.2:8000/api/chat`（享受 Python 后端向量记忆）；若电脑离线或后端未启动，捕获 `IOException` 自动平滑降级为端侧直连云端 Qwen-Plus，带上本地画像，保障 App 永不崩溃闪退。
- **决策五：瘦身与环境管理**
  - 移除了冗余的 `server/.venv`（直接复用系统 Python `C:\py\python\python.exe` 节省数百兆磁盘空间）；将 `server/data/` 加入 `.gitignore` 避免向量数据污染版本库。

---

## 四、 【重要反思】当前架构的本质局限与硬伤

**这是新会话必须要接力突破的最核心命题！**

我们在与用户的深度探讨中明确了当前项目的硬伤：**现在的系统其实是一个“硬编码工作流管道（Pipeline）”，还不是真正意义上的“工具型智能体（Tool Calling / ReAct Agent）”。**

| 维度 | 当前实现 (Pipeline 管道) | 用户期望与真正 Agent 的标准形态 |
| :--- | :--- | :--- |
| **工具存在形式** | 本质是写在 Python/Kotlin 里的**普通函数** | 封装为对 LLM 开放的 **标准 JSON Schema (Tools)** |
| **调用决策者** | **Python 代码里的 if-else 说了算** (`should_trigger_memory_search`) | **由大模型在对话上下文中自主推理决定何时调用** (Tool Calling) |
| **大模型的角色** | 处于流水线最末端，只负责把人拼好的 Prompt 润色为回复 | 处于中枢指挥地位，通过 **ReAct（思考-行动-观察）** 循环多步调用工具 |
| **手机介入能力** | **只能动嘴文字聊天，无法干预手机** (缺少 Action Tools) | **能够直接下发指令唤醒手机端行动**（如开启回血舱、播放白噪音） |

---

## 五、 新会话的重构目标与演进路线图 (Evolution Roadmap)

新开的对话框应以此为蓝图，将 App 从“套壳 Pipeline”重构升级为**“真正具备工具调用与行动干预能力的 Agentic Workflow 智能体”**：

### 阶段 1：将后端全面升级为 OpenAI / DashScope 标准的 Tool Calling Agent
1. **定义第一批标准工具元信息（Tools Schema）**：
   - `search_fatigue_memory(query: str)`：大模型自主决定何时翻查历史相似经历。
   - `get_device_screen_stats(days_ago: int)`：大模型自主决定何时查阅用户的真实屏幕数据。
   - `trigger_soft_landing(mode: str, duration_minutes: int)`：大模型下发干预手机的指令（核心行动型工具）。
2. **在 `server/main.py` 实现标准 ReAct 循环**：
   - 将 tools 传给大模型；
   - 捕获大模型输出的 `tool_calls`；
   - 本地执行对应工具函数并带回结果（Observation）；
   - 驱动大模型输出最终结论或执行下一步。

### 阶段 2：Android 端实现“行动型工具”承接——3分钟数字宿醉回血舱 (Soft Landing)
- 解决 Agent“光动嘴说教、不能动手帮忙”的问题。
- **Compose 沉浸界面**：低频呼吸动态光圈、灰阶护眼视觉引导、内置轻量音频流（雨声/粉红噪音）。
- **端侧动作暴露**：当大模型返回 `trigger_soft_landing` 指令时，手机端直接弹出或跳转至回血过渡舱。

### 阶段 3：主页重构——精力透支账单 (Energy Drain Bill)
- 将目前单调的“屏幕分钟数统计”改造为：
  - **今日认知电量跌幅（Battery Drain %）**；
  - **假休息（短视频/碎片切屏）vs 真休息（慢阅读/音乐）定性占比**；
  - **上下文切换惩罚（Context Switch Penalty）**。

### 阶段 4：引入 Agentic Workflow 反思与自我纠错机制 (Reflection)
- 在大模型输出最终回答前，设置轻量内部反思节点（Reflection Critic）：
  - 自动检测：“回复中是否包含‘你应该自律’等说教性词汇？是否充分尊重了第一层画像的偏好？”
  - 若触发警报，自动打回重写，打造行业极致的“零说教、强共情”陪伴体验。

---

## 六、 开发者常用命令与运行速查

### 1. 启动 Python 智能体后端
```bash
cd c:\Users\Administrator\Desktop\TiredApp\server
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. 验证向量检索与大模型通信
```bash
cd c:\Users\Administrator\Desktop\TiredApp\server
python test_cli.py
```

### 3. 查看向量数据库当前沉淀的切片
在浏览器或终端直接 GET 请求：
```bash
curl http://127.0.0.1:8000/api/memories
```

---

> [!TIP]
> **新会话开启提示词（Prompt Template）建议**：  
> *“你好！请先完整阅读项目根目录下的 [PROJECT_HANDOFF_AND_AGENT_EVOLUTION_SPEC.md](file:///c:/Users/Administrator/Desktop/TiredApp/PROJECT_HANDOFF_AND_AGENT_EVOLUTION_SPEC.md)。我们已经完成了端云基建、两层向量记忆和开机惰性归档，现在的核心目标是解决‘当前只是硬编码流水线而非真正 Tool-Calling Agent’的问题，开始落地标准的 Tools 架构与 3分钟数字宿醉软着陆回血舱。请确认你已理解文档并告诉我下一步如何启动！”*
