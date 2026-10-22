# AGENT.md —— BanG Dream! Live 项目全景（合并自 docs/01-09）

> 一份文档读完整个项目：技术栈、约定、数据模型、接口、红线、测试、协作规则。
> 生成于 2026-10-19，内容来自 docs/01~09 合并精简 + **代码与数据库实际状态核对**（已过时的计划项已剔除）。

---

## 1. 项目是什么

**BanG Dream! Live** —— MyGO!!!!! × Ave Mujica 粉丝站：音乐库（官方外链试听 + MV 内嵌）、演出票务（模拟支付、防超卖、15 分钟超时取消）、内容管理（管理端）、AI 助手（本地 Ollama 四人格）。

- 定位：个人粉丝站 + 简历项目（企业级工程规范）
- 状态（2026-10-19）：**M1–M4.10 全部完成**，`main` 已合并（102 提交）；剩余 M5 质量加固、M6 部署交付

---

## 2. 技术栈（实际锁定）

| 层 | 选型 | 版本事实 |
|---|---|---|
| 后端 | Spring Boot **4.1.1** + Java **17** | Spring 7 / Tomcat 11 / Hibernate 7 |
| 数据访问 | Spring Data JPA（`ddl-auto: validate`）+ **Flyway** | 迁移文件 = 唯一建表真相 |
| 数据库 | **MySQL 8.0**（utf8mb4）`bandori_live` | 本机 root / 密码在 `.env` |
| 认证 | Spring Security + JJWT 0.12.6（JWT 内嵌 role） | `USER` / `ADMIN` |
| 前端 | Vue 3 + Vite + Pinia + Vue Router + **Tailwind CSS** | JS（非 TS） |
| AI | **Ollama + qwen3:8b**（OpenAI 兼容端点） | 自动探测模型；不可用时规则引擎兜底 |
| 构建 | mvnw（后端）/ npm（前端） | Node 22.22.2 |

> 注意：Spring Boot 4 使用 **Jackson 3（`tools.jackson.*`）**，不是 `com.fasterxml.*`。

---

## 3. 启动与运行

```bash
# 后端（工作目录 ave-mygo/，端口 8080）
JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" ./mvnw.cmd spring-boot:run

# 前端（frontend/，端口 5173）
npm run dev

# Ollama（AI 助手需要；不启动则助手降级为规则引擎）
# 默认 http://localhost:11434，模型自动探测
```

- 数据库连接：`application.yaml` 用 `${DB_URL}/${DB_USERNAME}/${DB_PASSWORD:}`，实际值在 `.env`（gitignore）
- 管理员账号：`tomori_fan`（`user_id=1`，V22 提权 ADMIN）
- 配置项：`llm.base-url / llm.model / llm.api-key`（可切云端）、`hero.assets-dir`（Hero 视频目录）

---

## 4. 目录结构

```
D:/全栈/
├── ave-mygo/                     # 后端 Spring Boot
│   └── src/main/
│       ├── java/com/avemygo/
│       │   ├── auth/             # 注册登录、JWT 过滤器
│       │   ├── song/             # 乐队/成员/专辑/歌曲（Song/Band/Member/Album + DTO）
│       │   ├── event/            # 演出/场次/票档/演出曲表（setlist）
│       │   ├── order/            # 订单状态机、下单事务
│       │   ├── admin/            # 管理端（歌曲 CRUD、Hero 视频上传）
│       │   ├── assistant/        # AI 助手（意图识别、LLM 客户端、人格）
│       │   ├── common/           # ApiResponse / BizException / GlobalExceptionHandler
│       │   └── config/           # SecurityConfig / SchedulingConfig
│       └── resources/db/migration/   # V1–V37 Flyway 迁移
├── frontend/                     # Vue3 前端
│   ├── src/{views,components,api,stores,router}/
│   └── public/{img/covers,img/logo,img/bands,assets}/
├── docs/                         # 历史设计文档（已合并至此，保留备查）
├── AGENT.md                      # 本文档
└── 项目未完成.md                  # 剩余计划
```

---

## 5. 工程约定（必须遵守）

### Git
- 分支：`main`（可运行）+ `feat/xxx`；Conventional Commits（`feat(scope): 描述`）
- 提交前必须编译通过；迁移文件按版本号递增，**建迁移前先 `ls` 目录确认最大号**

### 后端
- 统一响应：`{ "code": 0, "message": "ok", "data": ... }`；错误也走 HTTP 200 + 非 0 code（前端统一拆包）
- 错误码：`40001` 参数 / `40101` 未登录 / `403xx` 无权限 / `40004` 不存在 / `40901` 库存不足 / `40903` 超限购 / `40904` 用户名已存在 / `50001` 系统异常
- 分层：Controller（HTTP）→ Service（业务+事务）→ Repository（数据）；Controller 不写业务
- 异常：`BizException(code, msg)` + 全局处理器；堆栈只进日志
- 校验：DTO 用 Jakarta Validation，禁止信任客户端
- 列表查询防 N+1：批量 `IN` 查询 + 内存组装

### 数据库
- 表三要素：`id` 自增主键、`created_at`、`updated_at`；业务表`is_deleted` 软删除
- 一切结构变更走 Flyway（**禁止手工改表后不补历史**；手动执行过的迁移必须改幂等写法）
- 关键约束：`ticket_type` 有 `chk_stock CHECK (sold_count <= total_stock)`（防超卖最后防线）

### 敏感信息
- `.env` 不入库，提供 `.env.example`；前端 baseURL 走 `VITE_API_BASE`

---

## 6. 数据模型（15 张表，实际）

```
user              （id, username, password_hash, nickname, role, status, avatar_url, ...）
band / member / album / song
song              （title, band_id, album_id, track_no, release_date, duration,
                     cover_url, audio_url, stream_url, mv_url, mv_platform, tags, status）
live_event        （title, band_id?, description, poster_url, venue, status: DRAFT/PUBLISHED/ENDED）
live_session      （event_id, name, start_time, end_time, sale_start_time,
                     status: NOT_OPEN/ONSALE/ENDED；SOLD_OUT 为查询态派生不落库）
ticket_type       （session_id, name, price, total_stock, sold_count, max_per_user, status）
ticket_order      （order_no, user_id, session_id, ticket_type_id, quantity, amount,
                     status: PENDING/PAID/CANCELLED/REFUNDED, pay_deadline）
favorite / playlist / playlist_item / setlist_item / comment / conversation_history
```

**订单状态机**：`PENDING --支付--> PAID --退款--> REFUNDED`；`PENDING --手动取消/超时15min--> CANCELLED`（回滚库存）
**场次状态机**：定时任务按 `sale_start_time`/`end_time` 翻转 `NOT_OPEN→ONSALE→ENDED`
**业务规则（核心 9 条）**：下单同事务校验+扣库存；单价快照入库；超限购按 user+session 累计；支付幂等；取消/退款回滚库存；DRAFT 演出前台不可见；下架歌曲前台不可见（`status=1`）；软删除自动过滤（`@SQLRestriction`）；外链只存官方来源。

---

## 7. 接口清单（已实现，前缀 `/api/v1`）

| 模块 | 端点数 | 接口 |
|---|---|---|
| 认证 | 3 | `POST /auth/register`、`POST /auth/login`、`GET /auth/me` |
| 歌曲 | 2 | `GET /songs?bandId&keyword&tag&page&pageSize`、`GET /songs/{id}` |
| 乐队/专辑 | 3 | `GET /bands`、`GET /bands/{id}`、`GET /albums` |
| 演出 | 3 | `GET /events?status&bandId&page`、`GET /events/{id}`、`GET /events/sessions/{id}/setlist` |
| 订单 | 4 | `POST /orders`、`GET /orders/my`、`PATCH /orders/{orderNo}/cancel`、`POST /orders/{orderNo}/pay` |
| 收藏 | 2 | `GET /favorites`、`POST/DELETE /favorites` |
| 健康 | 1 | `GET /health` |
| 助手 | 1 | `POST /assistant/chat`（`{message, sessionId, persona}` → `{reply, intent, persona, personaName}`） |
| 管理端 | 5 | `/admin/songs` CRUD、`POST /admin/hero-videos`（multipart，slot=mygo\|mujica，≤100MB，原子替换） |

**安全**：公开接口 = auth/songs/bands/albums/events/health/assistant；`/admin/**` 强制 `ROLE_ADMIN`；其余需登录。

---

## 8. 前端（12 个页面，实际）

| 路由 | 页面 | 说明 |
|---|---|---|
| `/` | HomeView | Hero 视频背景（/assets/band-*.mp4）+ 乐队区（官方 logo）+ 演出/新曲 |
| `/songs` `/songs/:id` | 歌曲库/详情 | 筛选（乐队/标签/搜索）、MV 区（B站 iframe bvid/ep_id）、全曲外链 |
| `/events` `/events/:id` | 演出/详情 | 场次 Tab、票档余票、**本场歌单（安可标记）**、下单+15 分钟倒计时 |
| `/bands` `/bands/:id` | 乐队列表/详情 | KV 视频头图 + 官方 logo + 专辑区 + 成员 |
| `/login` `/register` `/profile` | 账户 | JWT 存 sessionStorage、401 自动跳登录 |
| `/my/orders` | 我的订单 | 状态筛选、支付/取消 |
| `/admin` | 管理页 | 仅 ADMIN；歌曲管理 + Hero 视频上传 |
| 404 | NotFoundView | 显示访问地址 + 返回上一页 |

全局组件：`PlayerBar`（底部播放条）、`AssistantWidget`（小灯悬浮球，四人格 chips：小灯/Anon/小祥/睦子米）、`CoverArt`（404 自动回退渐变）。

---

## 9. 业务红线（违反即返工）

1. **版权**：不托管/不上传版权音频视频；只存官方外链（B站 player embed / YouTube / iTunes 官方 30s 预览 / 网易云页面）
2. **事实准确性**：BanG Dream 相关设定（成员、曲目、发行日、专辑归属）**必须查证官方来源**后写入，禁止凭记忆；查不到就留空并标注
3. **素材**：禁止 AI 生成图片；外部资源拉取失败必须停下来给用户选项，不擅自决策
4. **系统级配置**：改环境变量/系统目录前先列出影响并征得同意
5. **生成物位置**：所有产物在 `D:\全栈\`，不放 C 盘会话目录

---

## 10. 测试计划（M5 主体，docs/08 精简）

| 层 | 工具 | 目标 |
|---|---|---|
| 单元 | JUnit5 + Mockito | 订单状态机 5 条流转、限购计算、价格快照 |
| 接口 | MockMvc | 每接口 ≥1 正例 + 主要错误码 + 权限（401/403） |
| 集成 | SpringBootTest + **Testcontainers（真 MySQL）** | 并发下单、库存扣减、超时任务 |
| E2E | Playwright（可选） | 游客/购票/管理三条旅程 |

**P0 用例（验收线）**
- T01 正常下单：余票 50 买 2 → 200、`sold_count=2`、订单 PENDING、金额=单价×2
- **T02 并发超卖（核心）**：余票 1，两线程各买 1 → 恰好 1 个成功、1 个 40901，`sold_count=1`
- T03 超限购：`max_per_user=4` 已购 3 再买 2 → 40903，库存不变
- T04 库存快照：下单后立即查详情，余票减 2
- T05 CHECK 兜底：直接 SQL 把 `sold_count` 改超 `total_stock` → 数据库拒绝
- T06 超时取消：`pay_deadline` 过期 → 状态 CANCELLED 且库存回滚
- T07 权限：未登录下单 401；USER 访问 `/admin/**` 403

---

## 11. 部署目标（M6）

- `docker-compose up` 一键起：`app`（Spring Boot）+ `mysql:8` + `nginx`（前端静态 + 反代 `/api`）
- 前置：**本机需安装 Docker Desktop**（当前未安装）
- 交付物：`Dockerfile`（多阶段构建）、`docker-compose.yml`、`nginx.conf`、README 部署章节、`.env.example`
- 验收：新机器按 README 10 分钟内跑通首页 + 登录 + 下单

---

## 12. AI Agent 协作规则（踩过的坑，务必遵守）

1. **迁移只走 Flyway**：禁止 `mysql < Vxx.sql` 后不补历史（会引发 `Duplicate column` 启动失败）；新增迁移必须可由 Flyway 正常执行
2. **迁移撞号**：建文件前 `ls db/migration/` 取最大号 +1；多会话并行时先声明占用
3. **推送**：沙盒出网代理对 GitHub 间歇性 502，**用 8-10 次循环重试**（间隔 6-8s），成功率高
4. **数据库直连**：`D:/MySQL/MySQL Server 8.0/bin/mysql.exe -u root -p<密码> bandori_live`（`--default-character-set=utf8mb4`）
5. **前端改动验证**：`npm run build` 会清 `dist/` 可能触发沙盒拦截；轻量验证用 `curl localhost:5173/src/<文件>` 看编译 200，运行时报错需用户刷新页面看 Console（曾因少 `computed` 导入导致整站白屏）
6. **双会话分工**：后端/数据/资产由本会话负责，UI/交互由前端会话负责；跨会话任务写成「可直接粘贴的提示词」
7. **回答风格**：直接给结论与下一步，不要长篇铺垫；事实类先查证再答
