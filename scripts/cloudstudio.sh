#!/usr/bin/env bash
# ============================================================
# CloudStudio 一键部署 / 更新 / 重启脚本
# 用法（CloudStudio 终端）：
#   bash scripts/cloudstudio.sh            # 日常启动/更新（增量，快）
#   REBUILD=1 bash scripts/cloudstudio.sh  # 强制重新构建后端 jar
# 流程：拉代码 -> MySQL -> 后端构建+重启 -> 前端构建 -> nginx -> 验证
# ============================================================
ROOT=/workspace
BACKEND=$ROOT/backend
FRONTEND=$ROOT/frontend

echo "==> [1/7] 拉取最新代码"
cd "$ROOT"
# 部署机不需要保留本地改动：直接对齐到 origin/main。
# 用 reset 而非 pull：历史被重建（force push）后 pull --ff-only 必然失败，
# 旧脚本会"失败但继续"，导致以为更新了、实际跑的是旧代码。
if git fetch --depth=1 origin main; then
  git reset --hard FETCH_HEAD
  echo "  已对齐 origin/main: $(git log --oneline -1)"
else
  echo "  [警告] 拉取失败，将使用现有代码继续（可能是旧版本，请检查网络）"
fi

echo "==> [2/7] MySQL 启动 + 数据库账号同步"
sudo service mysql start >/dev/null 2>&1 || sudo service mysql restart >/dev/null 2>&1
# .env 不存在则生成默认配置（JWT_SECRET 随机）
if [ ! -f "$BACKEND/.env" ]; then
  SECRET=$(openssl rand -base64 48 | tr -d '\n')
  # 数据库密码随机生成，不把固定口令写进仓库（旧版本此处写死过固定值）
  DB_NEW_PASS=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 20)
  cat > "$BACKEND/.env" <<EOF
DB_URL=jdbc:mysql://localhost:3306/bandori_live?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=bandori
DB_PASSWORD=$DB_NEW_PASS
JWT_SECRET=$SECRET
JWT_EXPIRATION_HOURS=72
# AI 助手（可选）：不填则助手返回 50301，站点其他功能不受影响
# LLM_BASE_URL=http://localhost:11434/v1/chat/completions
# LLM_MODEL=qwen3:8b
# LLM_API_KEY=
EOF
  echo "  已生成默认 $BACKEND/.env（数据库密码随机）"
fi
# 密码以 .env 为唯一事实源，数据库账号自动同步
DB_PASSWORD=$(grep '^DB_PASSWORD=' "$BACKEND/.env" | head -1 | cut -d= -f2-)
sudo mysql -e "CREATE DATABASE IF NOT EXISTS bandori_live CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'bandori'@'localhost' IDENTIFIED BY '$DB_PASSWORD'; ALTER USER 'bandori'@'localhost' IDENTIFIED BY '$DB_PASSWORD'; GRANT ALL PRIVILEGES ON bandori_live.* TO 'bandori'@'localhost'; FLUSH PRIVILEGES;" 2>/dev/null
echo "  MySQL OK（账号密码已与 .env 同步）"

echo "==> [3/7] 后端构建（已有 jar 跳过，REBUILD=1 强制重建）"
cd "$BACKEND"
JAR=$(ls target/ave-mygo-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ] || [ "${REBUILD:-0}" = "1" ]; then
  chmod +x mvnw
  ./mvnw -q package -DskipTests
  JAR=$(ls target/ave-mygo-*.jar | head -1)
fi
echo "  使用 $JAR"

echo "==> [4/7] 重启后端（启动即执行 Flyway 迁移）"
# .env 可以手写，缺键就给默认值：空字符串会让 Spring 绑定 long 失败
[ -n "$(grep '^JWT_EXPIRATION_HOURS=' .env | head -1 | cut -d= -f2-)" ] || echo "JWT_EXPIRATION_HOURS=72" >> .env
pkill -f 'java -jar' 2>/dev/null || true
sleep 2
# LLM_* 从 .env 透传（可选）：线上没有 Ollama 时必须配云端 API，否则小灯 50301。
# 用数组而不是 ${VAR:+...} 拼接，避免值里带空格/特殊字符时被拆成多个参数。
ENV_LLM_BASE_URL="$(grep '^LLM_BASE_URL=' .env | head -1 | cut -d= -f2-)"
ENV_LLM_MODEL="$(grep '^LLM_MODEL=' .env | head -1 | cut -d= -f2-)"
ENV_LLM_API_KEY="$(grep '^LLM_API_KEY=' .env | head -1 | cut -d= -f2-)"
EXTRA_ENV=()
[ -n "$ENV_LLM_BASE_URL" ] && EXTRA_ENV+=("LLM_BASE_URL=$ENV_LLM_BASE_URL")
[ -n "$ENV_LLM_MODEL" ] && EXTRA_ENV+=("LLM_MODEL=$ENV_LLM_MODEL")
[ -n "$ENV_LLM_API_KEY" ] && EXTRA_ENV+=("LLM_API_KEY=$ENV_LLM_API_KEY")

nohup env \
  DB_URL="$(grep '^DB_URL=' .env | head -1 | cut -d= -f2-)" \
  DB_USERNAME="$(grep '^DB_USERNAME=' .env | head -1 | cut -d= -f2-)" \
  DB_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="$(grep '^JWT_SECRET=' .env | head -1 | cut -d= -f2-)" \
  JWT_EXPIRATION_HOURS="$(grep '^JWT_EXPIRATION_HOURS=' .env | head -1 | cut -d= -f2- || true)" \
  "${EXTRA_ENV[@]}" \
  java -jar "$JAR" > backend.log 2>&1 &

echo "==> [5/7] 前端构建（同源 API 路径）"
cd "$FRONTEND"
[ -d node_modules ] || npm install
VITE_API_BASE=/api/v1 npm run build

echo "==> [6/7] nginx 启动"
sudo service nginx start >/dev/null 2>&1 || true
# 配置守卫：缺 /api 反代时写入标准配置
if ! grep -q "proxy_pass http://127.0.0.1:8080" /etc/nginx/sites-available/default 2>/dev/null; then
  sudo tee /etc/nginx/sites-available/default > /dev/null <<'NGINX'
server {
    listen 80 default_server;
    server_name _;
    root /workspace/frontend/dist;
    index index.html;
    location / { try_files $uri /index.html; }
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        # 同源部署：置空 Origin，避免 Spring CORS 把 https(浏览器)->http(反代) 判为跨域而 403
        proxy_set_header Origin "";
    }
}
NGINX
fi
sudo nginx -t && sudo service nginx reload

echo "==> [7/7] 验证（等后端 20 秒）"
sleep 20
echo "--- 后端健康 ---"
curl -s "http://127.0.0.1:8080/api/v1/health" | head -c 120; echo
echo "--- 后端直连 ---"
curl -s "http://127.0.0.1:8080/api/v1/songs?page=0&size=1" | head -c 100; echo
echo "--- nginx 全链路 ---"
curl -s "http://127.0.0.1/api/v1/songs?page=0&size=1" | head -c 100; echo
curl -s http://127.0.0.1/ | grep -o "<title>[^<]*</title>"
echo "================ 完成 ================"
echo "打不开站点时：确认 CloudStudio 端口管理里 80 端口为「公开」"
