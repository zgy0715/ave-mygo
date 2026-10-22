#!/usr/bin/env bash
# ============================================================
# CloudStudio 一键部署 / 更新 / 重启脚本
# 用法（CloudStudio 终端）：
#   bash scripts/cloudstudio.sh            # 日常启动/更新（增量，快）
#   REBUILD=1 bash scripts/cloudstudio.sh  # 强制重新构建后端 jar
# 流程：拉代码 -> MySQL -> 后端构建+重启 -> 前端构建 -> nginx -> 验证
# ============================================================
ROOT=/workspace
BACKEND=$ROOT/ave-mygo
FRONTEND=$ROOT/frontend

echo "==> [1/7] 拉取最新代码"
cd "$ROOT" && git pull --ff-only || echo "  (git pull 失败，使用现有代码继续)"

echo "==> [2/7] MySQL 启动 + 数据库账号同步"
sudo service mysql start >/dev/null 2>&1 || sudo service mysql restart >/dev/null 2>&1
# .env 不存在则生成默认配置（JWT_SECRET 随机）
if [ ! -f "$BACKEND/.env" ]; then
  SECRET=$(openssl rand -base64 48 | tr -d '\n')
  cat > "$BACKEND/.env" <<EOF
DB_URL=jdbc:mysql://localhost:3306/bandori_live?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=bandori
DB_PASSWORD=Bandori_2026
JWT_SECRET=$SECRET
JWT_EXPIRATION_HOURS=72
EOF
  echo "  已生成默认 $BACKEND/.env"
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
pkill -f 'java -jar' 2>/dev/null || true
sleep 2
nohup env \
  DB_URL="$(grep '^DB_URL=' .env | head -1 | cut -d= -f2-)" \
  DB_USERNAME="$(grep '^DB_USERNAME=' .env | head -1 | cut -d= -f2-)" \
  DB_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="$(grep '^JWT_SECRET=' .env | head -1 | cut -d= -f2-)" \
  JWT_EXPIRATION_HOURS="$(grep '^JWT_EXPIRATION_HOURS=' .env | head -1 | cut -d= -f2-)" \
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
    location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
}
NGINX
fi
sudo nginx -t && sudo service nginx reload

echo "==> [7/7] 验证（等后端 20 秒）"
sleep 20
echo "--- 后端直连 ---"
curl -s "http://127.0.0.1:8080/api/v1/songs?page=0&size=1" | head -c 100; echo
echo "--- nginx 全链路 ---"
curl -s "http://127.0.0.1/api/v1/songs?page=0&size=1" | head -c 100; echo
curl -s http://127.0.0.1/ | grep -o "<title>[^<]*</title>"
echo "================ 完成 ================"
echo "打不开站点时：确认 CloudStudio 端口管理里 80 端口为「公开」"
