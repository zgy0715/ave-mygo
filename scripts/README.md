# scripts —— 一键脚本说明

所有脚本都从**仓库根目录**出发（脚本内部自己 `cd` 到根，双击运行也可以）。
仓库路径含中文（`D:\全栈`），凡涉及 Maven 的脚本都直接调用 `%USERPROFILE%\.m2\wrapper\dists\…\bin\mvn.cmd`，不要用 `mvnw.cmd`（非交互 cmd 下会报 `Cannot start maven from wrapper`）。

| 脚本 | 用途 | 用法 |
|---|---|---|
| `start.bat` | 一键启动：后端（`backend/target/*.jar`，没有 jar 就回退 `mvnw spring-boot:run`）+ 前端 Vite | `scripts\start.bat`；`scripts\start.bat preview` 用构建产物预览（4173） |
| `build.bat` | 构建后端 jar + 前端 `dist/` | `scripts\build.bat` |
| `check.bat` / `check.ps1` | 基线检查：后端 149 例 + 前端 Vitest 82 例；`e2e` 模式跑 Playwright 21 例（需 8080/5173 已启动） | `scripts\check.bat`、`scripts\check.bat e2e` |
| `backup-db.bat` | `mysqldump` 备份 `bandori_live` 到 `backups\`，保留 7 天（凭据读环境变量 `DB_USERNAME`/`DB_PASSWORD`） | `scripts\backup-db.bat` |
| `gen_seed.py` | 从开发库导出参考数据到 `db/seed/reference.sql`（幂等 upsert，只读 SELECT） | `$env:DB_PASSWORD="…"; python scripts\gen_seed.py` |
| `audit_data.py` | 数据体检：统计各表条数、软删、外链缺失等 | `python scripts\audit_data.py` |
| `audit_mv.py` | MV 链接核查（B 站 bvid / ep_id 可用性） | `python scripts\audit_mv.py` |
| `cloudstudio.sh` | 腾讯云 CloudStudio 演示机一键部署/更新/重启（拉代码 → MySQL → 后端 → 前端 → nginx → 验证） | `bash scripts/cloudstudio.sh`、`REBUILD=1 bash scripts/cloudstudio.sh` |

Python 脚本依赖 `pymysql`（`pip install pymysql`），连接参数用环境变量覆盖：`DB_HOST`/`DB_PORT`/`DB_USER`/`DB_PASSWORD`/`DB_NAME`（默认 `127.0.0.1:3306`、`root`、`bandori_live`）。

一次性脚本（历史重写、账号清理、README 拆分等）不在本目录，放在本地 `backup/oneoff/`，不入库。
