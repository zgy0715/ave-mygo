#!/usr/bin/env bash
# ============================================================================
# BanG Dream! Live —— Ubuntu 22.04/24.04 一键部署（幂等，可重复执行）
#
# 用法（在服务器上，root 或 sudo）：
#   cd /opt/ave-mygo && sudo bash scripts/deploy-ubuntu.sh
#
# 可选环境变量：
#   APP_DIR=/opt/ave-mygo        安装目录（默认就是脚本所在仓库）
#   BRANCH=main                  分支
#   DB_NAME=bandori_live         库名
#   LLM_BASE_URL / LLM_MODEL / LLM_API_KEY   云端大模型（不填则用小灯规则兜底，助手不可用）
#   SKIP_BUILD=1                 只重启服务，不重新构建
#
# 做的事：装 JDK17 + MySQL8 + Nginx + Node22 → 建库建账号（密码随机）
#        → 写 backend/.env（已存在则保留）→ 后端打包 → 前端构建
#        → systemd 常驻 → Nginx 静态站 + /api 反代 → 健康检查
# ============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
BRANCH="${BRANCH:-main}"
DB_NAME="${DB_NAME:-bandori_live}"
DB_USER="${DB_USER:-bandori}"
BACKEND="$APP_DIR/backend"
FRONTEND="$APP_DIR/frontend"
ENV_FILE="$BACKEND/.env"
SERVICE=avemygo
JAR_GLOB="target/ave-mygo-*.jar"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\n[ERROR] %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 或 sudo 执行"
[ -d "$BACKEND" ] || die "没找到 $BACKEND，请在仓库根目录运行（或设置 APP_DIR）"

# ---------------------------------------------------------------- 1. 系统依赖
say "[1/8] 安装系统依赖（JDK17 / MySQL8 / Nginx / git / curl）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk mysql-server nginx git curl ca-certificates >/dev/null
systemctl enable --now mysql >/dev/null 2>&1 || true
systemctl enable --now nginx >/dev/null 2>&1 || true

# ---------------------------------------------------------------- 2. Node 22
need_node=1
if command -v node >/dev/null 2>&1; then
  major=$(node -v | sed 's/v\([0-9]*\).*/\1/')
  [ "$major" -ge 22 ] && need_node=0
fi
if [ "$need_node" -eq 1 ]; then
  say "[2/8] 安装 Node.js 22（前端要求 ^22.18 | >=24.12，系统自带 18 太旧）"
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash - >/dev/null
  apt-get install -y -qq nodejs >/dev/null
fi
say "[2/8] Node: $(node -v)  /  Java: $(java -version 2>&1 | head -1)"

# ---------------------------------------------------------------- 3. 代码更新
say "[3/8] 更新代码（$BRANCH）"
cd "$APP_DIR"
if [ -d .git ]; then
  git fetch --depth=1 origin "$BRANCH"
  git reset --hard FETCH_HEAD
else
  die "$APP_DIR 不是 git 仓库"
fi
echo "    当前提交: $(git log --oneline -1)"

# ---------------------------------------------------------------- 4. 数据库
say "[4/8] 数据库：$DB_NAME"
DB_PASS=""
if [ -f "$ENV_FILE" ] && grep -q '^DB_PASSWORD=' "$ENV_FILE"; then
  DB_PASS=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
  echo "    复用 .env 里已有密码"
fi
if [ -z "$DB_PASS" ]; then
  DB_PASS=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 20)
  echo "    生成随机数据库密码（写入 backend/.env）"
fi

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
echo "    库与账号就绪（Flyway 会在后端启动时自动建表）"

# ---------------------------------------------------------------- 5. .env
say "[5/8] 写配置 backend/.env（已存在则只补缺失键）"
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
  grep -q '^LLM_API_KEY=' "$ENV_FILE" && echo "    已存在 LLM_* 配置，保持原样" \
    || echo "    [提示] 未配置 LLM_*：小灯会显示离线，其余功能正常"
fi
chmod 600 "$ENV_FILE"

# ---------------------------------------------------------------- 6. 构建
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  say "[6/8] 跳过构建（SKIP_BUILD=1）"
else
  say "[6/8] 构建后端（跳过测试）"
  cd "$BACKEND"
  chmod +x mvnw
  ./mvnw -q -B -DskipTests clean package
  JAR=$(ls $JAR_GLOB 2>/dev/null | head -1 || true)
  [ -n "$JAR" ] || die "没打出 jar 包"
  echo "    产物: $JAR ($(du -h "$JAR" | cut -f1))"

  say "[6/8] 构建前端"
  cd "$FRONTEND"
  if [ -f package-lock.json ]; then npm ci --silent; else npm install --silent; fi
  npm run build --silent
  [ -d dist ] || die "前端构建失败：没有 dist/"
  echo "    产物: dist/ ($(du -sh dist | cut -f1))"
fi

# ---------------------------------------------------------------- 7. systemd
say "[7/8] 注册 systemd 服务：$SERVICE"
TOTAL_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo)
HEAP=$(( TOTAL_MB / 2 ))
[ "$HEAP" -gt 1024 ] && HEAP=1024
[ "$HEAP" -lt 512 ] && HEAP=512
echo "    内存 ${TOTAL_MB}MB → -Xmx${HEAP}m"

JAR_ABS=$(ls "$BACKEND"/$JAR_GLOB 2>/dev/null | head -1 || true)
[ -n "$JAR_ABS" ] || die "找不到 jar，请先构建（去掉 SKIP_BUILD=1）"

cat > /etc/systemd/system/$SERVICE.service <<EOF
[Unit]
Description=BanG Dream! Live backend
After=mysql.service network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$BACKEND
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/java -Xmx${HEAP}m -jar $JAR_ABS
SuccessExitStatus=143
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable $SERVICE >/dev/null 2>&1 || true
systemctl restart $SERVICE

# ---------------------------------------------------------------- 8. Nginx
say "[8/8] 配置 Nginx（静态站 + /api 反代，SSE 超时 300s）"
cat > /etc/nginx/sites-available/$SERVICE <<EOF
server {
    listen 80 default_server;
    server_name _;

    root $FRONTEND/dist;
    index index.html;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    # Hero 视频较大：允许长连接、按段请求
    location /assets/ {
        try_files \$uri =404;
        add_header Cache-Control "public, max-age=2592000";
    }

    # 小灯是 SSE 流式，必须放宽读超时，否则 60s 被掐断
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
say "等待后端启动（Flyway 建表 + 预热）"
ok=0
for i in $(seq 1 40); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done

IP=$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
if [ "$ok" -eq 1 ]; then
  echo "    后端健康检查通过"
  echo "    接口抽样: $(curl -fsS --max-time 5 'http://127.0.0.1/api/v1/songs?page=1&pageSize=1' | head -c 90)"
else
  echo "    [警告] 后端 40 次探测未通过，查日志：journalctl -u $SERVICE -n 80 --no-pager"
fi

cat <<EOF

================ 部署完成 ================
访问地址   http://$IP
后端日志   journalctl -u $SERVICE -f
改配置     vim $ENV_FILE && systemctl restart $SERVICE
更新代码   cd $APP_DIR && sudo bash scripts/deploy-ubuntu.sh
==========================================
EOF
