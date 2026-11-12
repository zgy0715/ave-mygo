# scripts —— 项目脚本说明

所有脚本都从**仓库根目录**出发（脚本内部自己 `cd` 到根，双击运行也可以）。
Windows 中文路径下，非交互 `mvnw.cmd` 有定位问题。检查入口依次使用 `MVN` 指定的可执行文件、PATH 中的 Maven、用户目录中的 wrapper 缓存；首次可在交互终端执行 `backend\mvnw.cmd -v` 初始化缓存。
`JAVA_HOME` 已设置时使用该 JDK，否则从 PATH 查找 Java/Javac；检查脚本不会修改系统环境变量。Node 要求见 `frontend/package.json`，CI 固定版本见 `.nvmrc`。

### 本地开发与检查

| 脚本 | 用途 | 用法 |
|---|---|---|
| `start.bat` | 启动后端（优先使用现有 jar）和前端 Vite；`preview` 模式预览构建产物（4173） | `scripts\start.bat`；`scripts\start.bat preview` |
| `_backend.bat` | `start.bat` 调用的后端启动辅助脚本 | 通常不用手动运行 |
| `env.bat` | 为 Windows 脚本定位 JDK 17 与 Maven；被 `start`/`build` 调用，故意只用 ASCII | `call scripts\env.bat` |
| `build.bat` | 构建后端 jar 和前端 `dist/` | `scripts\build.bat` |
| `check.bat` / `check.ps1` | 检查入口及其 PowerShell 实现；`e2e` 模式要求后端和前端已启动 | `scripts\check.bat`、`scripts\check.bat e2e` |

### 数据维护与审计

跨平台检查：Linux/macOS 执行 `bash scripts/check.sh`；PowerShell 7 可执行 `./scripts/check.ps1`。各入口支持 `unit`（默认）、`e2e` 与 `tools`（只验证工具）模式。

| 脚本 | 用途 | 用法 |
|---|---|---|
| `backup-db.bat` | 用 `mysqldump` 备份 `bandori_live` 到 `backup\db\`，保留 7 天；失败会删掉半截文件。凭据读 `DB_USERNAME`/`DB_PASSWORD`；`MYSQL_BIN` 指向 **MySQL 的 bin 目录**（不设则用 PATH 里的 `mysqldump`） | `scripts\backup-db.bat` |
| `gen_seed.py` | 从开发库只读导出参考数据；目录元数据更新，演出域按业务键仅补缺失行 | `$env:DB_PASSWORD="…"; python scripts\gen_seed.py` |
| `audit_data.py` | 只读数据完整性及音频/MV 链接审计；按需运行 | `python scripts\audit_data.py` |
| `audit_mv.py` | 只读 B 站 MV 标题、UP 主和视频状态核查；按需运行 | `python scripts\audit_mv.py` |

### 部署

| 脚本 | 用途 | 用法 |
|---|---|---|
| `deploy-ubuntu.sh` | **当前服务器部署入口**：Ubuntu 上安装运行依赖，本地构建前后端，配置 systemd 和 Nginx；可重复执行更新 | `sudo bash scripts/deploy-ubuntu.sh` |
| `preserve-assets.sh` | 部署前从 public / 旧 dist 补齐持久素材目录，已有文件不覆盖；不修改系统配置 | `bash scripts/preserve-assets.sh "$PWD"` |

`cloudstudio.sh` 已由 `deploy-ubuntu.sh` 取代并移除。首次部署如需启用云端助手，可将 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY` 传给脚本；配置已写入 `backend/.env` 后，重复部署会保留它们。

部署默认使用当前干净检出，不自动拉取。允许更新时显式传 `USE_GIT=1 BRANCH=main`，只接受当前分支快进；工作区有改动、拉取失败或分支分叉均停止。
应用由专用 `avemygo` 系统账号运行，仅监听127.0.0.1；只授予上传目录写权限，配置文件为root:avemygo的0640。Nginx直接面向公网时覆盖客户端传入的转发头。
首次创建随机数据库/JWT凭据；再次部署保留现有配置，不重置数据库密码。已有账号/库配置不匹配时停止并说明，需显式迁移；部署脚本面向本机MySQL，密码需16–128位且符合脚本检查。
新配置先执行 `systemd-analyze verify` 和独立的 `nginx -t`；等待数据库 `/api/v1/ready`，超时或代理探针失败返回非零。构建时会替换jar/dist，部署前需保留上一发布版本，脚本不提供自动回滚。
可在本地只生成配置用于审查：`APP_DIR=/opt/ave-mygo bash scripts/deploy-ubuntu.sh --render-config backup/oneoff/deploy-review`，此模式不安装依赖、不写系统目录。真实部署须在Ubuntu上验证。

部署脚本在自动拉取与构建前，调用 `preserve-assets.sh` 从 `frontend/public/` 和旧 `frontend/dist/`
补齐 `var/site-assets/`（图片、poster、图标）与 `var/hero-assets/`（视频），已有文件不覆盖。
这些目录均不入库，由 Nginx 独立提供；前端构建不会清空它们。首次更新旧站前先备份 public、dist、
backend/.env 和已有 var 素材目录，不要在保留素材前手动重建 dist。新服务器要单独上传素材；
公开使用前须确认使用权。替换已有素材时直接更新持久目录中的对应文件。
systemd 的 `--hero.assets-dir` 与 Nginx 的两个视频 URL 指向同一目录，保留管理端已有上传。
本地开发默认加载 `frontend/public/`，上传仍写入其中的 assets。文件上限100MB，请求体上限110MB，上传等待上限5分钟。
Linux 上传目录使用0755、发布视频使用0644，使 Nginx 可以读取；原子替换不可用时上传失败，保留原视频。

`gen_seed.py` 依赖 `pymysql`（`pip install pymysql`），连接参数用环境变量覆盖：`DB_HOST`/`DB_PORT`/`DB_USER`/`DB_PASSWORD`/`DB_NAME`（默认 `127.0.0.1:3306`、`root`、`bandori_live`）。

参考种子使用规则：

- 新环境先完整运行 Flyway，种子为可选补录；请先备份，在维护期间单进程执行。
- 乐队/成员/专辑/歌曲沿用冻结迁移中的 ID，重复执行会更新这些目录元数据。
- 演出按乐队与标题、场次按演出与名称、票档按场次与名称、曲目单按场次与曲序匹配，
  主键由目标库生成，避免开发库自增 ID 与新环境冲突。标题或名称已被改动时视为不同业务记录，执行前核对。
- 演出域仅插入缺失行；已有配置、已售数、上下架及软删除保持原样，软删记录不会被种子复活。
- 新票档使用数据库默认已售数 0；用户、订单、配额不导出，不能用本文件修复已有交易差异。
- 脚本用一致性只读快照导出；导入文件有事务边界，执行失败应回滚排查，不要加客户端 `--force`。
- 回归检查：后端 `ReferenceSeedReplayTest` 在独立随机测试库回放全部迁移，再重复导入，
  验证没有重复演出、错挂票档或库存/订单/配额覆盖；测试账号需有创建和删除测试库的权限。

两份审计脚本**不走 pymysql**，而是调用本机 `mysql` 客户端：路径硬编码在各自第 15 行的常量 `M`（当前为 `D:/MySQL/MySQL Server 8.0/bin/mysql.exe`），换机器需要改这一行。审计过程会访问外部音频或 B 站接口，需联网。

一次性脚本（历史重写、账号清理、README 拆分等）不在本目录，放在本地 `backup/oneoff/`，不入库。

## 部署相关（服务器侧）

线上部署走 `deploy-ubuntu.sh`，从仓库根目录执行；默认部署当前干净检出。显式传 USE_GIT=1 才更新 Git，失败即停止。
国内服务器到 GitHub / npmjs / Maven Central 通常不通，脚本已内置对策：

- Maven：`/root/.m2/settings.xml` → `maven.aliyun.com/repository/public`
- npm：`--registry=https://registry.npmmirror.com`
- Node 22：从 npmmirror 的 binary 镜像取官方 tar.xz（不使用 NodeSource）

常用运维：

```bash
sudo systemctl restart avemygo     # 重启后端
sudo journalctl -u avemygo -f      # 跟日志
sudo systemctl reload nginx        # 重载静态站
```
