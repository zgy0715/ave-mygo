# Bandori Live —— MyGO!!!!! × Ave Mujica 粉丝站

> 音乐库（官方外链试听 + B 站 MV 内嵌）｜演出票务（模拟支付、防超卖、15 分钟超时取消）｜管理端｜AI 助手（本地 Ollama 四人格）
> 个人粉丝站 + 简历项目。完整工程说明见 [AGENT.md](AGENT.md)，剩余计划见 [项目未完成.md](项目未完成.md)。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.1（Java 17）+ Spring Data JPA + Flyway + Spring Security/JWT |
| 数据库 | MySQL 8.0（`bandori_live`，迁移 V1–V38） |
| 前端 | Vue 3 + Vite + Pinia + Vue Router + Tailwind CSS |
| AI | Ollama（qwen3:8b，OpenAI 兼容端点；不可用时规则引擎兜底） |

## 目录

```
D:\全栈
├── ave-mygo/        # 后端（端口 8080）
├── frontend/        # 前端（开发 5173 / 预览 4173）
├── scripts/         # build.bat / start.bat（Windows 一键构建与启动）
├── docs/            # 设计文档 + SQL 迁移总览
├── AGENT.md         # 工程全景（约定/数据模型/接口/红线）
└── 项目未完成.md     # 剩余计划与验收线
```

## 开发模式启动

```bash
# 1. 数据库：MySQL 8 运行，首次启动 Flyway 自动建库建表
# 2. 后端（ave-mygo/）
JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" ./mvnw.cmd spring-boot:run

# 3. 前端（frontend/）
npm install && npm run dev        # http://localhost:5173
```

- 后端配置：`ave-mygo/.env`（不入库，参考 `.env.example`）：`DB_PASSWORD`、`LLM_BASE_URL` 等
- 管理员：`tomori_fan`（`user_id=1`，`role=ADMIN`）
- AI 助手：需本机 Ollama 运行（`ollama serve`）；未运行则自动降级为站内规则问答

## 生产部署（Windows 本机，无 Docker）

```bat
scripts\build.bat    :: 构建后端 jar + 前端 dist（首次会 npm ci，需几分钟）
scripts\start.bat    :: 后端 java -jar + 前端静态站（vite preview :4173）
```

| 地址 | 作用 |
|---|---|
| http://localhost:4173 | 站点（构建产物，非开发服务器） |
| http://localhost:8080/api/v1 | 后端接口 |
| http://localhost:8080/api/v1/health | 健康检查 |

前置条件：JDK 17、Node 18+、MySQL 8 已安装并运行；`ave-mygo/.env` 已配置。

> 如需对外网访问：把 `frontend/dist` 交给任意静态托管，后端 `java -jar` 跑在服务器上，
> 并把 `frontend/.env.production` 的 `VITE_API_BASE` 改成公网接口地址后重新构建。
> Docker 化（compose：app+mysql+nginx）留作后续演进，不影响当前交付。

## 测试

```bash
cd ave-mygo && ./mvnw.cmd test      # 20 个用例：并发超卖/限购/CHECK兜底/超时回滚/支付幂等/权限/校验/追踪号
```

测试使用独立库 `bandori_live_test`（Flyway 自动建表），**不会触碰开发库数据**。

## 文档索引

| 文档 | 内容 |
|---|---|
| [AGENT.md](AGENT.md) | 技术栈、约定、15 张表模型、真实接口清单、红线、协作规则 |
| [项目未完成.md](项目未完成.md) | 阻塞项、质量加固、数据缺口、M6 部署、验收线 |
| [docs/SQL迁移总览.md](docs/SQL迁移总览.md) | V1–V38 分类、结构变更、删除审计 |
| [docs/04-需求说明.md](docs/04-需求说明.md) 等 | 早期设计文档（保留备查） |

## 免责声明

本站为粉丝自制学习项目，非官方。音频仅使用官方平台试听片段，视频为 B 站官方/正版内容的 iframe 内嵌，不托管任何受版权保护的音视频文件。
