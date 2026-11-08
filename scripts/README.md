# scripts —— 一键脚本说明

所有脚本都从**仓库根目录**出发（脚本内部自己 `cd` 到根，双击运行也可以）。
仓库路径含中文（`D:\全栈`），凡涉及 Maven 的脚本都直接调用 `%USERPROFILE%\.m2\wrapper\dists\…\bin\mvn.cmd`，不要用 `mvnw.cmd`（非交互 cmd 下会报 `Cannot start maven from wrapper`）。

| 脚本 | 用途 | 用法 |
|---|---|---|
| `start.bat` | 一键启动：后端（`backend/target/*.jar`，没有 jar 就 `mvn spring-boot:run`）+ 前端 Vite | `scripts\start.bat`；`scripts\start.bat preview` 用构建产物预览（4173） |
| `_backend.bat` | 后端启动的实际执行体（由 `start.bat` 开新窗口调用） | 一般不用手动跑 |
| `env.bat` | 公共环境探测：自动定位 JDK 17（`JAVA_HOME`）与 `mvn.cmd`，被 `start`/`build` 调用；**故意只用 ASCII**（中文写在 .bat 里会受 OEM 代码页影响） | `call scripts\env.bat` |
| `build.bat` | 构建后端 jar + 前端 `dist/` | `scripts\build.bat` |
| `check.bat` / `check.ps1` | 基线检查：后端 149 例 + 前端 Vitest 82 例；`e2e` 模式跑 Playwright 21 例（需 8080/5173 已启动） | `scripts\check.bat`、`scripts\check.bat e2e` |
| `backup-db.bat` | `mysqldump` 备份 `bandori_live` 到 `backups\`，保留 7 天（凭据读环境变量 `DB_USERNAME`/`DB_PASSWORD`） | `scripts\backup-db.bat` |
| `gen_seed.py` | 从开发库导出参考数据到 `db/seed/reference.sql`（幂等 upsert，只读 SELECT） | `$env:DB_PASSWORD="…"; python scripts\gen_seed.py` |
| `audit_data.py` | 数据体检：统计各表条数、软删、外链缺失等 | `python scripts\audit_data.py` |
| `audit_mv.py` | MV 链接核查（B 站 bvid / ep_id 可用性） | `python scripts\audit_mv.py` |
| `deploy-ubuntu.sh` | **独立服务器部署（当前线上方案）**：apt 装 JDK17/MySQL/Nginx/Maven → Node22 取自 npmmirror → 服务器**本地构建**后端与前端（Maven 走阿里云源、npm 走 npmmirror）→ systemd 常驻 → nginx 反代。幂等，重复执行即更新 | `sudo bash -c 'LLM_API_KEY=sk-xxx bash scripts/deploy-ubuntu.sh'` |
| `cloudstudio.sh` | 腾讯云 CloudStudio 演示机部署（旧方案，工作区已被自有服务器取代） | `bash scripts/cloudstudio.sh` |

Python 脚本依赖 `pymysql`（`pip install pymysql`），连接参数用环境变量覆盖：`DB_HOST`/`DB_PORT`/`DB_USER`/`DB_PASSWORD`/`DB_NAME`（默认 `127.0.0.1:3306`、`root`、`bandori_live`）。

一次性脚本（历史重写、账号清理、README 拆分等）不在本目录，放在本地 `backup/oneoff/`，不入库。

## 部署相关（服务器侧）

线上部署走 `deploy-ubuntu.sh`，前提是代码已经在服务器上（scp 上传源码 tar，或 git 可达时自动拉取）。
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
