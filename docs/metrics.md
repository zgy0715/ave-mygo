# 项目度量快照（唯一口径来源）

> **快照时间：2026-11-02 21:00**（分支 `chore/repo-cleanup`，测试账号已清理）
> 原则：**本文是唯一数字来源**。`docs/17`、`docs/18` 等方案文档引用本文，不再各自复制数字——
> 之前就是因为数字散落在多份文档里，一轮修复就出现「V1–V49 / V1–V52 / V1–V37」三种口径。

---

## 一、代码规模

| 维度 | 数值 | 取数命令 |
|---|---|---|
| 后端 Java 文件 | **121** | `find backend/src/main/java -name "*.java" \| wc -l` |
| 后端代码行 | **8,571** | `find backend/src/main/java -name "*.java" -exec cat {} + \| wc -l` |
| 前端源文件（不含测试） | **65**（其中 `.vue` **43**） | `find frontend/src -name "*.vue" -o -name "*.js" \| grep -v __tests__ \| wc -l` |
| 前端代码行（不含测试） | **8,225** | 同上 `\| xargs cat \| wc -l` |
| 后端接口（Mapping 注解） | **61** | `grep -rhoE "@(Get\|Post\|Patch\|Put\|Delete)Mapping" src/main/java --include=*Controller.java \| wc -l` |
| Flyway 迁移 | **55 个文件（V1–V55）** | `ls src/main/resources/db/migration/*.sql \| wc -l` |
| 数据库表 | **19 张业务表** + `flyway_schema_history` | `SELECT COUNT(*) FROM information_schema.tables ...` |

## 二、测试规模

> 后端与前端单测于 **2026-11-12** 执行 `scripts\check.bat` 验证通过（无失败、无跳过）；
> 后端数量取本次 Surefire 报告。E2E 本轮未运行，以下 E2E 仍为历史快照，业务数据快照日期保持不变。

| 层 | 规模 | 命令 |
|---|---|---|
| 后端测试 | **215 用例 / 41 个测试类** | `./mvnw test` |
| 前端单测 | **111 用例 / 18 个文件** | `npm run test` |
| E2E | **20 用例 / 8 个 spec** | `npx playwright test --list` |

> E2E 前置：后端 8080 + 前端 5173 都要在跑，否则会大面积 `ERR_CONNECTION_REFUSED`（不是代码问题）。
> 全量跑前建议清一次测试残留：`DELETE FROM playlist WHERE name LIKE 'E2E歌单%';`
> 注意 `frontend/e2e/helpers.js:25` 每跑一次会注册一个新 `e2e_<时间戳>` 账号（限购绕行需要），跑多了 `user` 表会再涨。

## 三、业务数据

| 实体 | 有效数 | 说明 |
|---|---|---|
| 曲目 `song` | **55** | 其中无专辑 2、无音源 3、无 MV 6、无时长 2 |
| 专辑 `album` | 16 | |
| 演出 `live_event` | **6** | 含 ENDED 5 场 |
| 场次 `live_session` | **16 有效**（表内 25，含 9 条所属演出已软删的 E2E 残留） | 统计时要 `JOIN live_event` 过滤，否则会把残留算进去 |
| 票档 `ticket_type` | 27 | |
| 歌单条目 `setlist_item` | 16 | 覆盖 **4 个场次**（覆盖率 4/16 ≈ 25%） |
| 成员 `member` | 12 | 3 支乐队 |
| 用户 `user` | **3** | 真实 2（`tomori_fan` ADMIN、`YZBzgy` USER）+ `e2e_admin`（E2E 固定管理员，勿删）；88 个测试账号已于 2026-11-02 清理 |
| 订单 `ticket_order` | 11 | 库存 `sold_count` 已按 `PENDING_PAYMENT/PAID` 重算 |
| Agent 审计 `agent_tool_calls` | 37 | 每次只读/写工具调用各一条 |

## 四、Agent 能力面

| 项 | 数值 |
|---|---|
| 工具总数 | **15**（只读 7 / 写 8） |
| 只读 | search_songs、get_song、list_events、get_event、list_sessions、list_members、list_bands |
| 写 | update_song_mv、update_song_tags、set_song_status、set_ticket_status、update_event_info、update_session_time、delete_session、delete_event |
| 非管理员可见工具 | **7**（写工具不出现在 schema 里） |
| 对外入口 | `POST /api/v1/assistant/chat/v2`（SSE 事件：status/delta/tool/confirm/done/error） |
| 实测性能 | 首事件 0.77~1.3s；工具类问答总耗时 10~20s（本机 8B CPU） |

## 五、口径约定

1. **迁移版本**：写"V1–V55"，新增从未使用的最大版本 +1 开始（**当前下一个是 V56**）。
   历史上 `docs/17` 曾写"从 V54 起"，与已存在的 V54/V55 冲突，已修正。
2. **表数**：说"19 张业务表"，不要把 `flyway_schema_history` 算进业务模型。
3. **场次统计**：必须 `JOIN live_event WHERE e.is_deleted=0`，否则被 E2E 残留污染（曾把 16 报成 23/25）。
4. **测试用例数**：只写"用例数"，不写"测试类数"，因为一个类里可能有十几个用例，容易误导。
5. 改完数字后，**只改本文**，方案文档引用本节即可。
