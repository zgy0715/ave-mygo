# BanG Dream! Live

[![CI](https://github.com/zgy0715/ave-mygo/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/zgy0715/ave-mygo/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-EA2D2E)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

一个围绕 MyGO!!!!! × Ave Mujica 的个人全栈粉丝站，包含音乐目录、官方外链试听与 MV 嵌入、演出票务、收藏歌单、评论、管理端和 AI 助手。

项目由个人独立开发，开发周期约一个月，覆盖前后端、数据库迁移、自动化测试与服务器部署。购票和支付用于演示，不接入真实支付。

**在线演示：[http://193.112.16.57/](http://193.112.16.57/)**。演示服务器由维护者单独更新，线上版本可能与当前主分支不同。

## 导航

- [功能与工程实现](#功能与工程实现)
- [技术栈与结构](#技术栈与结构)
- [本地运行](#本地运行)
- [图片与视频](#图片与视频)
- [测试与 CI](#测试与-ci)
- [部署到 Ubuntu](#部署到-ubuntu)
- [维护与已知边界](#维护与已知边界)
- [文档与许可](#文档与许可)

## 功能与工程实现

| 功能 | 实现 |
|---|---|
| 音乐目录 | 歌曲、专辑、乐队与成员；乐队筛选、关键词搜索、收藏、歌单、评论 |
| 试听与 MV | 官方试听外链、Bilibili 播放器嵌入；外部内容不可用时提供降级展示 |
| 演出票务 | 演出、场次、票档、余票与曲目单；下单、模拟支付、取消、超时取消与管理员退款 |
| 个人账户 | 注册登录、JWT 认证、个人资料与订单；切换账号时清理缓存，阻止旧请求污染新账号 |
| 管理端 | 歌曲与演出管理、票档库存、订单退款、用户管理、数据看板、Hero 视频上传 |
| AI 助手 | 多人格对话、SSE 流式响应、站内工具查询；写操作需管理员权限和二次确认 |

工程实现重点：

- 订单事务中校验库存与限购，数据库 CHECK 约束作为超卖兜底；价格快照随订单保存。
- 支付、取消、退款按订单状态机执行，取消和退款回滚库存；超时订单由定时任务处理。
- Controller → Service → Repository 分层；Jakarta Validation 校验输入，统一业务响应与异常处理。
- Spring Security 在后端校验身份和角色；前端路由守卫用于交互引导。
- 通过 Flyway 管理数据库历史，JPA 只验证结构；旧迁移冻结，新结构追加版本。
- 流式对话支持取消、重置和上下文隔离，工具操作留有审计记录。

## 技术栈与结构

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 4.1.1、Spring Security、Spring Data JPA、Jackson 3 |
| 数据库 | MySQL 8.0、Flyway；生产与测试使用独立数据库 |
| 前端 | Vue 3、Vite、Pinia、Vue Router、Tailwind CSS、ECharts |
| AI | 本地 Ollama，或 OpenAI 兼容的云端 Chat Completions 接口 |
| 测试 | JUnit、Mockito、MockMvc、Vitest、Playwright |
| 部署 | Ubuntu、systemd、Nginx 同源反向代理 |

```text
backend/       Spring Boot 服务与 Flyway 迁移
frontend/      Vue 页面、组件、前端测试及可选本地 public 素材
db/seed/       可选参考数据补录，不包含用户和订单
scripts/       本地启动、检查、备份与 Ubuntu 部署工具
docs/          当前设计、素材说明和 metrics；archive/ 为历史记录
var/           服务器持久素材与部署辅助文件，不入 Git
backup/        本地备份与一次性验证产物，不入 Git
```

业务数据、接口和测试数量统一见 [docs/metrics.md](docs/metrics.md)，其中标注了各项快照日期。

## 本地运行

前置条件：JDK 17、MySQL 8.0、Node 22.18+（22.x）或 24.12+。CI 使用 [.nvmrc](.nvmrc) 固定的 Node 版本。

```bash
git clone https://github.com/zgy0715/ave-mygo.git
cd ave-mygo
cd backend
cp .env.example .env
# 编辑 .env，填写本机数据库连接和至少 32 字节的随机 JWT_SECRET。
# Linux / macOS：
bash ./mvnw spring-boot:run
```

另开终端启动前端：

```bash
cd ave-mygo/frontend
npm ci
npm run dev
```

Windows 可先安装前端依赖、配置 `backend/.env`，再从仓库根目录执行 `scripts\start.bat`。
脚本优先运行已有后端 jar；后端源码更新后，可先执行 `scripts\build.bat` 或使用 Maven 从源码启动。
中文目录下 Maven wrapper 的非交互限制和缓存定位方式见 [脚本说明](scripts/README.md)。

| 地址 | 用途 |
|---|---|
| `http://localhost:5173/` | 前端开发服务器 |
| `http://localhost:8080/api/v1/health` | 后端进程存活 |
| `http://localhost:8080/api/v1/ready` | 后端与数据库就绪 |

前端开发默认直接请求 `http://localhost:8080/api/v1`，通过后端 CORS 配置允许访问；需要变更时设置 `VITE_API_BASE`，模板见 [frontend/.env.example](frontend/.env.example)。生产构建默认使用 `/api/v1`，由 Nginx 转发。

后端读取工作目录下的 `.env`。首次启动自动执行 Flyway；V1–V55 不得修改，新结构从当前最大版本之后追加。可选参考种子和重复导入规则见 [scripts/README.md](scripts/README.md)。不要将测试配置指向生产库。

自行注册普通账号后，按维护需要授予管理员角色；项目不提供公开管理员密码。管理页面为 `/admin`，后端仍会独立校验 ADMIN 权限。

AI 为可选能力：本地可运行 Ollama；云端配置 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`。未配置或模型不可用时，对话可能提示不可用或使用规则兜底，曲库和票务不依赖模型。

## 图片与视频

**代码仓库与运行素材分别维护。** 本地已有图片和视频不会被删除，但不随当前 Git 版本分发；新克隆没有素材时显示渐变和文字占位。

| 场景 | 行为 |
|---|---|
| `npm run dev` | 默认读取本地 `frontend/public/`，可查看已有页面效果 |
| `INCLUDE_LOCAL_ASSETS=0 npm run dev` | 显式关闭本地素材 |
| `npm run build` | 默认不将 `public/` 打入构建产物，CI 验证此模式 |
| `INCLUDE_LOCAL_ASSETS=1 npm run build` | 显式将自行准备并确认可使用的素材一并打包 |
| Ubuntu 部署 | Nginx 从持久目录提供素材，前端重新构建不清理这些目录 |

上表环境变量写法适用于 Bash；PowerShell 可先设置 `$env:INCLUDE_LOCAL_ASSETS='1'`，再执行构建命令。

| 服务器目录（相对项目根目录） | 对外路径 |
|---|---|
| `var/site-assets/img/` | `/img/` 下的封面、成员图、Logo |
| `var/site-assets/assets/band-*-poster.jpg` | 首页视频首帧的同名 `/assets/` 路径 |
| `var/site-assets/favicon.ico` | `/favicon.ico` |
| `var/hero-assets/band-*-720p.mp4` | 首页背景视频的同名 `/assets/` 路径 |

部署脚本通过 [preserve-assets.sh](scripts/preserve-assets.sh)，在自动拉取和构建前从现有 `frontend/public/`、旧 `frontend/dist/` 依次补齐持久目录，已有文件不覆盖。管理端上传仍写入独立 Hero 目录。

首次更新旧站前必须备份素材；不要先重建或清理旧 dist。新服务器需要单独上传素材，替换已有素材时直接更新持久目录中的对应文件。
具体路径、来源记录与使用边界见 [开源许可与素材](docs/20-开源许可与素材.md)及[素材清单](docs/assets-manifest.csv)。

## 测试与 CI

从仓库根目录执行：

```powershell
scripts\check.bat          # Windows：后端测试与前端单测
scripts\check.bat tools    # 仅检查运行工具
```

```bash
bash scripts/check.sh       # Linux / macOS：相同基线
cd frontend
npm run build              # 生产构建
cd ..
python scripts/check-assets.py --dist
```

后端集成测试使用 `bandori_live_test`，部分种子回放测试会创建独立测试库；测试账号需有相应建库权限。前端单测无需启动网站。完整运行条件见 [测试与验收](docs/08b-测试与验收.md)。

E2E 为可选检查，需前后端均运行，命令为 `scripts\check.bat e2e`。它会注册测试账号、创建订单等，应仅在专用测试环境运行；本轮 E2E 未执行，不能将历史快照当作本轮验收结果。

GitHub Actions 在 push、PR 和手动触发时执行后端 MySQL 集成测试与打包、前端单测与构建，以及部署脚本语法和素材检查。工作流仅有仓库读取权限，第三方 Action 固定到提交哈希。

## 部署到 Ubuntu

当前入口为 [scripts/deploy-ubuntu.sh](scripts/deploy-ubuntu.sh)，面向 Ubuntu 22.04/24.04、x86_64，建议项目目录 `/opt/ave-mygo`。路径必须是无空格的规范绝对路径；应用由专用账号运行，后端仅监听本机 8080，Nginx 对外提供 80。

### 新服务器

在已经安装 Git 的服务器上：

```bash
sudo git clone https://github.com/zgy0715/ave-mygo.git /opt/ave-mygo
cd /opt/ave-mygo
# 如需完整图片视频，先按上方持久目录上传自行准备的素材。
sudo bash scripts/deploy-ubuntu.sh
```

脚本会安装运行依赖、构建前后端、创建独立数据库账号与随机凭据，配置 systemd 和 Nginx，并验证就绪接口。公网访问需放行 80；MySQL 和应用端口无需直接对外开放。域名和 HTTPS 需在服务器上另外配置。

### 更新已有站点

1. 先备份数据库、`backend/.env`、前端 `public/` 与旧 `dist/`，以及已有 `var/site-assets/`、`var/hero-assets/`。备份应放在部署目录之外。
2. 核对现有部署路径、数据库账号与 Nginx 配置。新版要求专用非 root 数据库账号；旧站若仍使用 root，应先完成账号迁移。脚本不会自动重置密码或覆盖不匹配的连接配置。
3. 保存服务器工作区的实际改动，再更新 `main`。首次采用本版脚本时，即使拉取已移除旧 public 文件，只要旧 dist 仍在，就可以补齐素材。不要先手动重建前端。
4. 在项目根目录运行部署入口。已采用本版部署方式的站点，可使用以下命令自动快进更新：

```bash
sudo env USE_GIT=1 BRANCH=main DB_USER=bandori bash scripts/deploy-ubuntu.sh
```

`DB_USER` 应与现有 `backend/.env` 的 `DB_USERNAME` 一致；数据库名可通过 `DB_NAME` 指定。默认部署当前干净检出，不自动拉取；拉取失败、分支分叉或工作区有改动时停止。禁止用强制重置覆盖服务器实际改动。

脚本会构建并替换当前 jar / dist，再重启后端、重载 Nginx。**它不提供自动发布回滚**，执行前须保留上一版本及其配置和素材。已有自定义域名、TLS 或其他虚拟主机的服务器，应先合并 Nginx 配置，不能直接用默认配置替换。

部署脚本涉及安装软件、创建专用账号、写入 systemd / Nginx 配置和重启服务；首次执行前应确认这些系统变更符合当前服务器环境。

### 部署验收与运维

```bash
curl -fsS http://127.0.0.1/api/v1/ready
sudo systemctl status avemygo nginx
sudo journalctl -u avemygo -n 100 --no-pager
# 修改配置后的维护操作：
sudo systemctl restart avemygo
sudo nginx -t && sudo systemctl reload nginx
```

通过服务器 IP 或域名检查首页、歌曲封面、两个 Hero 视频、登录、下单与模拟支付；有云模型时再检查流式对话。检查浏览器没有请求访客机器的 localhost。数据库就绪并不能替代这些页面与业务验收。

可先在本机只生成候选配置，查看路径与服务权限，命令见 [脚本说明](scripts/README.md)。生产环境变量、限流和备份要求见 [上线检查清单](docs/08a-上线检查清单.md)。

## 维护与已知边界

- 主维护分支为 `main`；本轮清理关闭未合并的自动依赖升级，继续使用已验证的依赖版本。依赖升级改为人工评估，不自动创建升级分支。
- 原创代码、脚本与文档采用 MIT；第三方内容的权利独立处理，不能因代码开源而视为素材已获授权。
- 当前版本已排除未核实的本地素材，旧 Git 历史仍可能包含这些文件；本轮没有重写历史。
- 当前演出票务与支付是模拟业务；AI 依赖模型服务，外链试听和播放器依赖原站可用性。
- 部署脚本面向单机 Ubuntu，构建发生在服务器原目录，不是容器编排或带自动回滚的发布系统。
- 测试通过不等于线上版本已更新。服务器由维护者执行部署并完成验收，实际验收状态应按当次结果记录。

## 文档与许可

| 文档 | 用途 |
|---|---|
| [AGENTS.md](AGENTS.md) | 项目全景、工程约定与业务红线 |
| [需求说明](docs/04-需求说明.md) | 产品目标与业务范围 |
| [数据库设计](docs/05-数据库设计.md) / [迁移总览](docs/SQL迁移总览.md) | 数据模型与迁移历史 |
| [接口设计](docs/06-接口设计.md) | 接口契约 |
| [开发规范](docs/02-开发规范.md) | 命名、分层、Git 与敏感信息约定 |
| [上线检查](docs/08a-上线检查清单.md) / [测试验收](docs/08b-测试与验收.md) | 测试与部署核对 |
| [脚本说明](scripts/README.md) | 启动、检查、备份和部署工具 |
| [Agent 设计](docs/10-Agent运营层设计.md) | 工具、循环、确认门控、审计与多轮对话 |
| [后续演进](docs/17-后续演进计划.md) / [工程深度](docs/18-工程深度与体量方案.md) | 可选后续方向，不作为本轮发布完成声明 |
| [素材说明](docs/20-开源许可与素材.md) / [项目度量](docs/metrics.md) | 素材路径及统一数字口径 |
| [历史记录](docs/archive/) | 一次性任务、审查修复与开发纪实 |

许可见 [LICENSE](LICENSE) 与 [NOTICE.md](NOTICE.md)。
