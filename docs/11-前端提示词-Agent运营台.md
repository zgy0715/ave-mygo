# 11 · 前端提示词：小灯 Agent 运营台（M4）

> 用途：粘贴给前端会话直接开发，不需要再读后端代码。
> 后端已完成并测试通过（122 用例全绿），接口契约见下文，**不要改后端**。

---

## 一、背景

现在的小灯（`AssistantWidget.vue`）是**只读聊天**：调 `/api/v1/assistant/chat/stream`（SSE），只能回答问题。

后端新增了 **Agent 运营层**：它能真正调用管理端工具改数据（改 MV、票档上下架、删场次/演出），写操作会**先返回一张待确认卡片**，管理员点确认才执行。

你现在要做的是：**管理端的「运营台」页面**，让人用自然语言指挥小灯干活。

---

## 二、接口契约（已实现，不要改）

前缀：`/api/v1`（沿用现有 `request.js` 的 axios 实例，自动带 token、自动拆包）
权限：仅 ADMIN（走既有路由守卫 `requiresAdmin`）

### 1. 下达指令

```
POST /admin/agent/chat
Body: { "message": "把迷星叫的 MV 换成 https://www.bilibili.com/video/BVxxx", "conversationId": "可选" }
```

响应 A —— 纯文本回复（任务完成或需要追问）：

```json
{ "code": 0, "data": {
  "type": "reply",
  "content": "找到《迷星叫》，现在的 MV 是 …，确认要换成新的吗？",
  "token": null, "toolName": null, "arguments": null,
  "toolCalls": [
    { "name": "search_songs", "status": "EXECUTED", "summary": "共 1 首：- id=1 《迷星叫》 MyGO!!!!! MV=有" }
  ]
}}
```

响应 B —— 待确认（写操作已解析，但**尚未执行**）：

```json
{ "code": 0, "data": {
  "type": "pending_confirmation",
  "content": "准备执行：修改歌曲 id=1 的 MV 地址为 https://…。请确认后我才会动手。",
  "token": "8f3c…（uuid）",
  "toolName": "update_song_mv",
  "arguments": { "songId": 1, "mvUrl": "https://…" },
  "toolCalls": [ { "name": "search_songs", "status": "EXECUTED", "summary": "…" } ]
}}
```

### 2. 确认 / 取消

```
POST /admin/agent/confirm
Body: { "token": "8f3c…", "approve": true }   // true 执行，false 取消
```

```json
{ "code": 0, "data": { "status": "EXECUTED", "result": "已更新《迷星叫》的 MV 地址：old → new" } }
```

`status` 三种：`EXECUTED` 成功 / `REJECTED` 已取消 / `FAILED` 执行失败（`result` 里有原因，如「该演出存在待支付或已支付订单…」）。

### 3. 错误码（`request.js` 已统一 reject 成 `Error(message)`）

| code | 含义 | 前端表现 |
|---|---|---|
| 50301 | LLM 不可用（Ollama 没开） | 提示「小灯暂时离线（需要 LLM 服务）」，不要弹红屏 |
| 40902 | token 已处理/已过期(10min)/非本人 | 提示「这次确认已失效，请重新下达指令」 |
| 40004 | token 不存在 | 同上 |
| 40001 | 消息为空 | 前端先做非空校验，不依赖后端 |

---

## 三、要做的事（按文件顺序，一个文件一个文件写）

### 文件 1：`frontend/src/api/agent.js`

```js
import request from './request'

/** Agent 对话：返回 { type:'reply'|'pending_confirmation', content, token, toolName, arguments, toolCalls } */
export const agentChat = (message, conversationId) =>
  request.post('/admin/agent/chat', { message, conversationId }, { timeout: 180_000 })

/** 确认/取消待执行写操作 */
export const agentConfirm = (token, approve) =>
  request.post('/admin/agent/confirm', { token, approve }, { timeout: 60_000 })
```

要求：
- 复用 `src/api/request.js`（自动带 token、剥壳、统一错误），**不要新建 axios 实例**
- **必须单独放开超时**：共享实例默认 10s，而 Agent 一次请求内部要跑多轮 LLM（工具调用 → 回填 → 回答），本地 8B 模型冷启动可达 60s+。实测沿用 10s 会稳定报「请求超时」，看起来像功能坏了（2026-10-31 踩过）
- 不要写任何 `localhost` 硬编码

### 文件 2：`frontend/src/components/AgentConsole.vue`

这是主体，要求如下：

**布局**（跟现有视觉体系一致：`card`、`wrap`、`btn-primary`、`input`、light 主题、`serif` 标题、`tabular` 数字）：

```
┌──────────────────────────────────────────────┐
│ 小灯 · 运营台            [清空对话]           │
├──────────────────────────────────────────────┤
│  💡 试试：把迷星叫的 MV 换成…                 │
│                                              │
│  你：把那场 Day1 场次删掉                      │
│                                              │
│  小灯：                                        │
│   🔧 search_songs  命中 2 首        ← 工具条目 │
│   🔧 list_sessions 2 个场次                   │
│   准备删除场次 id=17「DAY1」…                  │
│  ┌────────────────────────────────────┐      │
│  │ ⚠️ 待确认：删除场次 id=17           │      │
│  │ 参数                                         │
│  │   sessionId: 17                             │
│  │ [取消]        [确认执行]  ← 危险色按钮       │
│  └────────────────────────────────────┘      │
└──────────────────────────────────────────────┘
│ [输入指令…]                            [发送] │
└──────────────────────────────────────────────┘
```

**行为细节**：
1. 会话内消息列表（`ref([])`），每条 `{ role: 'user'|'assistant', content, toolCalls: [], pending: {...}|null, status }`
2. 发送：非空校验 → push 用户消息 → 显示「小灯思考中…」→ await `agentChat`
3. `type === 'reply'`：把 `content` 与 `toolCalls` 渲染进助手消息
4. `type === 'pending_confirmation'`：渲染确认卡片
   - 卡片标题：`⚠️ 待确认：<中文动作>`，动作由 `toolName` 映射：
     `update_song_mv → 修改歌曲 MV`、`set_ticket_status → 票档上下架`、`delete_session → 删除场次`、`delete_event → 删除演出`
   - 参数表：遍历 `arguments` 的键值对，长值（URL/JSON）自动换行 `break-all`
   - **[确认执行]** 危险色（`!bg-red-500 hover:!bg-red-600`，与现有删除按钮一致）+ **[取消]** ghost
   - 点击后按钮进入 loading（`确认中…`），防重复点击
5. 确认结果三态：
   - `EXECUTED` → 卡片变成浅绿「✅ 已执行」，展示 `result`
   - `REJECTED` → 卡片变成灰色「已取消」
   - `FAILED` → 卡片变红「❌ 执行失败」，展示 `result`（例如有订单被拒），**保留原文不要改写**
6. 错误处理：`catch (e)` 把 `e.message` 显示为一条系统提示（灰色小字），不要整页报错；`50301` 额外提示可以去「智能问答」用只读模式
7. `conversationId`：页面加载时生成一次 `crypto.randomUUID()`，整页复用（后端用于审计聚合）
8. 键盘：`Enter` 发送、`Shift+Enter` 换行（与 AssistantWidget 一致）
9. 滚动到底：新消息后 `scrollIntoView`

**禁止**：不要引入新依赖、不要写 `<style scoped>` 里的复杂 CSS（用 Tailwind 类）、不要复制 AssistantWidget 的人设切换逻辑（运营台固定小灯一个语气即可）

### 文件 3：接入管理端

在 `frontend/src/views/AdminView.vue` 的标签页数组里加一项：

```js
{ id: 'agent', label: '运营台' }
```

并在模板里加：

```vue
<AgentConsole v-if="tab === 'agent'" />
```

（放在「用户管理」之后；`import AgentConsole from '@/components/AgentConsole.vue'`）

---

## 四、验收清单

**功能**
- [ ] 输入「列出所有演出」→ 小灯调用 `list_events`，工具条目显示「EXECUTED」，回复列出演出
- [ ] 输入「把迷星叫的 MV 换成 https://example.com/a」→ 出现确认卡片，**此时数据库未变**
- [ ] 点「确认执行」→ 卡片变绿显示已更新；刷新页面后该歌 MV 确实变了
- [ ] 再走一次同样流程，点「取消」→ 卡片变灰，数据未变
- [ ] 输入「删掉所有演出」→ 只会对某一个具体演出出确认卡片（LLM 应先用 list_events 查真实 id），不会批量执行
- [ ] 确认后 10 分钟内再点同一张卡（如果没禁用）→ 提示「确认已失效」
- [ ] 关掉本地 Ollama 后发指令 → 提示「小灯暂时离线」，页面不崩

**界面**
- [ ] 与现有管理端风格一致（白底、卡片、主色/危险色按钮）
- [ ] 工具条目是灰色小字，不抢主体内容
- [ ] 长 URL 不撑破卡片（`break-all`）
- [ ] 手机宽度（375px）下不横向溢出

**测试**
- [ ] 加一个 Vitest 用例：mock `agentChat` 返回 `pending_confirmation` → 断言确认卡片渲染、点「确认执行」时调用了 `agentConfirm(token, true)`
- [ ] `npm run build` 通过
- [ ] 现有 E2E 不回归（`npx playwright test guest.spec.js admin.spec.js`）

---

## 五、联调前置条件（重要）

后端 Agent 依赖本地 LLM：

```bash
# 1. 启动 Ollama 并确认模型在
ollama list          # 应有 qwen3:8b
# 2. 启动后端（.env 里 DB 配置照旧）
cd ave-mygo && ./mvnw spring-boot:run
# 3. 前端
cd frontend && npm run dev
```

Ollama 没启动时所有 Agent 指令都会报 50301，这是预期行为（Agent 不做规则兜底）。

**提示词质量注意**：`qwen3:8b` 的 function calling 偶尔会漏调工具或乱填 id。如果实测回答跑偏，优先调整后端 `AgentService.SYSTEM_PROMPT`（要求它「先查再改」），不要在前端做适配 hack。
