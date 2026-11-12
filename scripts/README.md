# scripts —— 项目脚本说明

所有脚本都从**仓库根目录**出发（脚本内部自己 `cd` 到根，双击运行也可以）。
仓库路径含中文（`D:\全栈`），凡涉及 Maven 的脚本都直接调用 `%USERPROFILE%\.m2\wrapper\dists\…\bin\mvn.cmd`，不要用 `mvnw.cmd`（非交互 cmd 下会报 `Cannot start maven from wrapper`）。

### 本地开发与检查

| 脚本 | 用途 | 用法 |
|---|---|---|
| `start.bat` | 启动后端（优先使用现有 jar）和前端 Vite；`preview` 模式预览构建产物（4173） | `scripts\start.bat`；`scripts\start.bat preview` |
| `_backend.bat` | `start.bat` 调用的后端启动辅助脚本 | 通常不用手动运行 |
| `env.bat` | 为 Windows 脚本定位 JDK 17 与 Maven；被 `start`/`build` 调用，故意只用 ASCII | `call scripts\env.bat` |
| `build.bat` | 构建后端 jar 和前端 `dist/` | `scripts\build.bat` |
| `check.bat` / `check.ps1` | 检查入口及其 PowerShell 实现；`e2e` 模式要求后端和前端已启动 | `scripts\check.bat`、`scripts\check.bat e2e` |

### 数据维护与审计

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

`cloudstudio.sh` 已由 `deploy-ubuntu.sh` 取代并移除。首次部署如需启用云端助手，可将 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY` 传给脚本；配置已写入 `backend/.env` 后，重复部署会保留它们。

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

线上部署走 `deploy-ubuntu.sh`，从仓库根目录执行；脚本会尝试更新 Git 工作区，失败时使用当前目录代码。
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
