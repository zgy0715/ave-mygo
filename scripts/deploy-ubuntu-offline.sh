#!/usr/bin/env bash
# ============================================================================
# BanG Dream! Live —— 离线部署（服务器只装 apt 包，不联网拉 GitHub/npm/Maven）
#
# 适用场景：服务器出站网络差（GitHub / npm / Maven 都连不上）。
# 做法：在本地构建好 jar 与前端 dist，随本脚本一起打包上传，服务器只做安装与启动。
#
# 本地准备（Windows / Git Bash，仓库根目录）：
#   cd backend  && ./mvnw -q -DskipTests clean package
#   cd ../frontend && npm ci && npm run build
#   cd .. && tar -czf /tmp/ave-prebuilt.tar.gz \
#       backend/target/ave-mygo-*.jar frontend/dist scripts/deploy-ubuntu-offline.sh
#   scp /tmp/ave-prebuilt.tar.gz ubuntu@<服务器IP>:/tmp/
#
# 服务器执行：
#   sudo mkdir -p /opt/ave-mygo && sudo tar -xzf /tmp/ave-prebuilt.tar.gz -C /opt/ave-mygo
#   cd /opt/ave-mygo && sudo LLM_API_KEY=sk-xxx bash scripts/deploy-ubuntu-offline.sh
# ============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
DB_NAME="${DB_NAME:-bandori_live}"
DB_USER="${DB_USER:-bandori}"
BACKEND="$APP_DIR/backend"
FRONTEND="$APP_DIR/frontend"
ENV_FILE="$BACKEND/.env"
SERVICE=avemygo

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\n[ERROR] %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 或 sudo 执行"

JAR=$(ls "$BACKEND"/target/ave-mygo-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || die "没找到 backend/target/ave-mygo-*.jar —— 请先在本地打包并上传"
[ -d "$FRONTEND/dist" ] || die "没找到 frontend/dist —— 请先在本地构建并上传"
echo "jar : $JAR ($(du -h "$JAR" | cut -f1))"
echo "dist: $FRONTEND/dist ($(du -sh "$FRONTEND/dist" | cut -f1))"

# ---------------------------------------------------------------- 1. 依赖
say "[1/5] 安装 JDK17 / MySQL8 / Nginx（走 apt，国内源通常可用）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk mysql-server nginx curl >/dev/null
systemctl enable --now mysql >/dev/null 2>&1 || true
systemctl enable --now nginx >/dev/null 2>&1 || true

# ---------------------------------------------------------------- 2. 数据库
say "[2/5] 数据库 $DB_NAME"
DB_PASS=""
if [ -f "$ENV_FILE" ] && grep -q '^DB_PASSWORD=' "$ENV_FILE"; then
  DB_PASS=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
  echo "    复用 .env 已有密码"
fi
if [ -z "$DB_PASS" ]; then
  DB_PASS=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 20)
  echo "    生成随机密码"
fi
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL

# ---------------------------------------------------------------- 3. .env
say "[3/5] 写 backend/.env"
if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<EOF
DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
JWT_EXPIRATION_HOURS=72
EOF
fi
grep -q '^JWT_EXPIRATION_HOURS=' "$ENV_FILE" || echo "JWT_EXPIRATION_HOURS=72" >> "$ENV_FILE"
if [ -n "${LLM_API_KEY:-}" ] && ! grep -q '^LLM_API_KEY=' "$ENV_FILE"; then
  cat >> "$ENV_FILE" <<EOF
LLM_BASE_URL=${LLM_BASE_URL:-https://api.deepseek.com/v1/chat/completions}
LLM_MODEL=${LLM_MODEL:-deepseek-chat}
LLM_API_KEY=$LLM_API_KEY
EOF
  echo "    已写入 LLM_* 配置"
else
  grep -q '^LLM_API_KEY=' "$ENV_FILE" && echo "    已有 LLM_* 配置，保持原样" \
    || echo "    [提示] 未配置 LLM_*：小灯显示离线，其余功能正常"
fi
chmod 600 "$ENV_FILE"

# ---------------------------------------------------------------- 4. systemd
say "[4/5] 注册 systemd 服务 $SERVICE"
TOTAL_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo)
HEAP=$(( TOTAL_MB / 2 )); [ "$HEAP" -gt 1024 ] && HEAP=1024; [ "$HEAP" -lt 512 ] && HEAP=512
echo "    内存 ${TOTAL_MB}MB → -Xmx${HEAP}m"

cat > /etc/systemd/system/$SERVICE.service <<EOF
[Unit]
Description=BanG Dream! Live backend
After=mysql.service network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$BACKEND
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/java -Xmx${HEAP}m -jar $JAR
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable $SERVICE >/dev/null 2>&1 || true
systemctl restart $SERVICE

# ---------------------------------------------------------------- 5. nginx
say "[5/5] 配置 Nginx（静态站 + /api 反代，SSE 300s）"
cat > /etc/nginx/sites-available/$SERVICE <<EOF
server {
    listen 80 default_server;
    server_name _;

    root $FRONTEND/dist;
    index index.html;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    location /assets/ {
        try_files \$uri =404;
        add_header Cache-Control "public, max-age=2592000";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
        proxy_buffering off;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
ln -sf /etc/nginx/sites-available/$SERVICE /etc/nginx/sites-enabled/$SERVICE
rm -f /etc/nginx/sites-enabled/default
nginx -t >/dev/null 2>&1 || die "nginx 配置校验失败"
systemctl reload nginx

# ---------------------------------------------------------------- 验证
say "等待后端启动（Flyway 建表）"
ok=0
for i in $(seq 1 40); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done

IP=$(hostname -I | awk '{print $1}')
if [ "$ok" -eq 1 ]; then
  echo "    后端健康检查通过"
  echo "    抽样: $(curl -fsS --max-time 5 'http://127.0.0.1/api/v1/songs?page=1&pageSize=1' | head -c 90)"
else
  echo "    [警告] 后端 40 次探测未通过：journalctl -u $SERVICE -n 80 --no-pager"
fi

cat <<EOF

================ 部署完成 ================
访问地址   http://$IP
后端日志   journalctl -u $SERVICE -f
改配置     vim $ENV_FILE && systemctl restart $SERVICE
==========================================
EOF
