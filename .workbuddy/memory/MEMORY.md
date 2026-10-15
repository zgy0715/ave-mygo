# 项目长期备忘（D:\全栈）

## 项目定位
- 本工作区用于一次专业、企业级、详细的全栈项目开发（2026-10 启动前准备）。

## 已定选型
- **项目方向（2026-10-15 已定）**：MyGO!!/Ave Mujica 粉丝站「Bandori Live」——音乐播放（官方音源外链，不托管版权音频）+ 演出票务（模拟支付、防超卖）+ 内容管理。需求文档：docs/04-需求说明.md
- 数据库：MySQL 8.x（utf8mb4，建表脚本版本化放 sql/）
- 前端：Vue3 + Vite + Tailwind + Pinia（已定）
- 后端：Spring Boot 3 + Java 17 + Spring Data JPA + Flyway + JWT（已定，用户有 DeepAgent Java 经验，简历导向）
- 协作方式：AI 当教练——用户动手写代码，AI 负责解释、出测试、教 debug（用户明确担忧"只会让 AI 开发"，要练工程能力）
- 参照产品：Bestdori（社区 API）、hina-is、大麦购票流程、网易云播放器交互、setlist.fm

## 工程约定（详见 docs/02-开发规范.md）
- 统一响应 `{code, message, data}`；API 前缀 `/api/v1/`
- Conventional Commits；分支 main（+可选 dev、feat/xxx）
- 数据表含 id/created_at/updated_at，默认软删除 is_deleted
- 敏感信息走 .env（不入库），提供 .env.example

## 环境
- Node 22.22.2 / npm 10.9.7 / Git 2.55 / Python 3.13.14
- Git 仓库已初始化（main 分支，root commit 1c42470），远程仓库未建
