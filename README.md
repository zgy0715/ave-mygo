# 全栈项目工作区（D:\全栈）

> 目标：未来几天在此完成一次**专业、企业级、细节完整**的全栈项目开发。

## 当前状态（2026-10-15）

| 项 | 状态 |
|---|---|
| 项目方向 | ✅ 已定：MyGO!!/Ave Mujica 粉丝站「Bandori Live」（音乐播放 + 演出票务 + 内容管理） |
| 数据库 | ✅ MySQL |
| 前端技术栈 | ✅ Vue3 + Vite + Tailwind + Pinia |
| 后端技术栈 | ✅ Spring Boot 3 + Java 17 + Spring Data JPA + Flyway + JWT |
| 协作方式 | AI 当教练：用户动手实现，AI 解释/出测试/教 debug |
| 环境 | Node 22.22 / npm 10.9 / Git 2.55 / Python 3.13 ✅ |

## 目录约定

```
D:\全栈
├── README.md            # 本文件：工作区总览
├── docs/                # 项目文档（规范、架构、里程碑）
├── frontend/            # 前端工程（启动后创建）
├── backend/             # 后端工程（启动后创建）
├── sql/                 # 数据库建表/迁移脚本
├── scripts/             # 开发辅助脚本
└── .workbuddy/          # 工作区配置与记忆（勿删）
```

## 文档索引

- [docs/01-技术选型.md](docs/01-技术选型.md) —— 选型结论与待定项
- [docs/02-开发规范.md](docs/02-开发规范.md) —— Git、命名、目录、代码规范
- [docs/03-里程碑计划.md](docs/03-里程碑计划.md) —— 开发前准备清单 + 里程碑模板
- [docs/04-需求说明.md](docs/04-需求说明.md) —— Bandori Live 需求文档 v1.0（业务需求与功能范围）
- [docs/05-数据库设计.md](docs/05-数据库设计.md) —— 字段级表结构、状态机、业务规则
- [docs/06-接口设计.md](docs/06-接口设计.md) —— 字段级 API 规格、错误码表
- [docs/07-前端设计.md](docs/07-前端设计.md) —— 路由、页面、组件、四态规范
- [docs/08-测试与验收.md](docs/08-测试与验收.md) —— 用例级测试计划、验收线
- [docs/09-环境与依赖清单.md](docs/09-环境与依赖清单.md) —— 软件安装、Maven/npm 依赖、配置清单

## 环境前置（开工前需确认）

- [ ] MySQL 8.x 本机可用（或 Docker 起 `mysql:8`）
- [ ] 数据库图形化客户端（可选：Navicat / DBeaver / DataGrip）
- [ ] VS Code + Volar（Vue）/ 对应后端插件
