#!/usr/bin/env bash
# ============================================================================
# BanG Dream! Live —— Ubuntu 22.04/24.04 一键部署（服务器本地构建，全程国内源）
#
# 适用：腾讯云/阿里云大陆节点（到 GitHub / npmjs / Maven Central 不通，
#       但 apt 内网源、maven.aliyun.com、registry.npmmirror.com 可用）。
#
# 用法（服务器上，root 或 sudo）：
#   cd /opt/ave-mygo && sudo bash -c 'LLM_API_KEY=sk-xxx bash scripts/deploy-ubuntu.sh'
#
# 可选环境变量：
#   APP_DIR / BRANCH / DB_NAME / DB_USER
#   LLM_BASE_URL / LLM_MODEL / LLM_API_KEY
#   SKIP_BUILD=1   只重启服务，不重新构建
#   USE_GIT=0      即使目录是 git 仓库也不尝试拉取
#
# 全流程（幂等，可重复执行）：
#   1 装 JDK17 / MySQL8 / Nginx / Maven（apt 内网源）
#   2 装 Node 22（从 npmmirror 取官方二进制，不走 NodeSource）
#   3 配 Maven 阿里云源 + npm npmmirror 源
#   4 代码准备（能拉 git 就拉，拉不到用当前目录）
#   5 建库建账号（密码随机落 .env，已存在则复用）
#   6 服务器本地构建：mvn package + npm ci && vite build
#   7 systemd 常驻（按内存定 -Xmx）
#   8 Nginx 静态站 + /api 反代（SSE 300s）
# ============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
BRANCH="${BRANCH:-main}"
DB_NAME="${DB_NAME:-bandori_live}"
DB_USER="${DB_USER:-bandori}"
BACKEND="$APP_DIR/backend"
FRONTEND="$APP_DIR/frontend"
HERO_ASSETS="$APP_DIR/var/hero-assets"
ENV_FILE="$BACKEND/.env"
SERVICE=avemygo
NODE_VER="${NODE_VER:-v22.18.0}"
NPM_REGISTRY="https://registry.npmmirror.com"
MAVEN_MIRROR="https://maven.aliyun.com/repository/public"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\n[ERROR] %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 或 sudo 执行"
[ -d "$BACKEND" ] || die "没找到 $BACKEND（APP_DIR 应为仓库根目录）"

# ---------------------------------------------------------------- 1. 系统依赖
say "[1/8] 安装 JDK17 / MySQL8 / Nginx / Maven（走 apt，国内源通常可用）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk mysql-server nginx maven curl ca-certificates xz-utils >/dev/null
systemctl enable --now mysql >/dev/null 2>&1 || true
systemctl enable --now nginx >/dev/null 2>&1 || true
echo "    Java: $(java -version 2>&1 | head -1)"
echo "    Maven: $(mvn -v 2>/dev/null | head -1 || echo '未安装')"

# ---------------------------------------------------------------- 2. Node 22
node_ok() { command -v node >/dev/null 2>&1 && [ "$(node -v | sed 's/v\([0-9]*\).*/\1/')" -ge 22 ]; }
if node_ok; then
  say "[2/8] Node 已满足：$(node -v)"
else
  say "[2/8] 安装 Node ${NODE_VER}（从 npmmirror 取官方二进制，不走 NodeSource）"
  TARBALL="node-${NODE_VER}-linux-x64.tar.xz"
  curl -fL --retry 3 -m 300 "${NPM_REGISTRY}/-/binary/node/${NODE_VER}/${TARBALL}" -o "/tmp/${TARBALL}"
  mkdir -p /opt/nodejs
  tar -xJf "/tmp/${TARBALL}" -C /opt/nodejs --strip-components=1
  ln -sf /opt/nodejs/bin/node /usr/local/bin/node
  ln -sf /opt/nodejs/bin/npm  /usr/local/bin/npm
  ln -sf /opt/nodejs/bin/npx  /usr/local/bin/npx
  hash -r
  echo "    Node: $(node -v)  npm: $(npm -v)"
fi

# ---------------------------------------------------------------- 3. 国内镜像
say "[3/8] 配置国内镜像（Maven → aliyun，npm → npmmirror）"
mkdir -p /root/.m2
cat > /root/.m2/settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>Aliyun Public</name>
      <url>${MAVEN_MIRROR}</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
npm config set registry "$NPM_REGISTRY" --global >/dev/null
echo "    Maven → ${MAVEN_MIRROR}"
echo "    npm   → $(npm config get registry)"

# ---------------------------------------------------------------- 4. 代码
say "[4/8] 代码准备"
cd "$APP_DIR"
if [ -d .git ] && [ "${USE_GIT:-1}" = "1" ]; then
  if git fetch --depth=1 origin "$BRANCH" 2>/dev/null; then
    git reset --hard FETCH_HEAD
    echo "    已更新到 $(git log --oneline -1)"
  else
    echo "    [提示] 拉取失败（本机到 GitHub 不通），使用当前目录代码"
  fi
else
  echo "    使用当前目录代码（非 git 仓库或 USE_GIT=0）"
fi

# ---------------------------------------------------------------- 5. 数据库
say "[5/8] 数据库 $DB_NAME"
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

if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<EOF
DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
JWT_EXPIRATION_HOURS=72
EOF
fi
# 关键：.env 可能来自别的环境（如开发者本地），其 DB_USERNAME/DB_PASSWORD 与本机不符
# → 一律用本次生成的库名/账号/密码覆盖，避免 "Access denied for user 'root'" 这类启动失败
sed -i "s|^DB_URL=.*|DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false\&serverTimezone=Asia/Shanghai\&allowPublicKeyRetrieval=true|" "$ENV_FILE"
sed -i "s|^DB_USERNAME=.*|DB_USERNAME=$DB_USER|" "$ENV_FILE"
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$DB_PASS|" "$ENV_FILE"
grep -q '^DB_URL=' "$ENV_FILE" || echo "DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" >> "$ENV_FILE"
grep -q '^DB_USERNAME=' "$ENV_FILE" || echo "DB_USERNAME=$DB_USER" >> "$ENV_FILE"
grep -q '^DB_PASSWORD=' "$ENV_FILE" || echo "DB_PASSWORD=$DB_PASS" >> "$ENV_FILE"
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

# ---------------------------------------------------------------- 6. 构建
# 上传内容独立于 public/dist；首次部署复制默认视频，之后构建和更新不覆盖管理员上传。
install -d -m 0755 "$APP_DIR/var" "$HERO_ASSETS"
for hero_slot in mygo mujica; do
  hero_video="band-${hero_slot}-720p.mp4"
  if [ ! -f "$HERO_ASSETS/$hero_video" ] && [ -f "$FRONTEND/public/assets/$hero_video" ]; then
    install -m 0644 "$FRONTEND/public/assets/$hero_video" "$HERO_ASSETS/$hero_video"
  fi
done

if [ "${SKIP_BUILD:-0}" = "1" ]; then
  say "[6/8] 跳过构建（SKIP_BUILD=1）"
else
  say "[6/8] 服务器本地构建后端（Maven + 阿里云源，首次下依赖 2~5 分钟）"
  cd "$BACKEND"
  mvn -B -q -DskipTests clean package
  JAR=$(ls target/ave-mygo-*.jar 2>/dev/null | head -1 || true)
  [ -n "$JAR" ] || die "后端打包失败：没有 target/ave-mygo-*.jar"
  echo "    产物: $JAR ($(du -h "$JAR" | cut -f1))"

  say "[6/8] 服务器本地构建前端（npm ci + vite build）"
  cd "$FRONTEND"
  if [ -f package-lock.json ]; then
    npm ci --registry="$NPM_REGISTRY" --no-audit --no-fund
  else
    npm install --registry="$NPM_REGISTRY" --no-audit --no-fund
  fi
  npm run build
  [ -d dist ] || die "前端构建失败：没有 dist/"
  echo "    产物: dist/ ($(du -sh dist | cut -f1))"
fi

# ---------------------------------------------------------------- 7. systemd
say "[7/8] 注册 systemd 服务 $SERVICE"
JAR_ABS=$(ls "$BACKEND"/target/ave-mygo-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR_ABS" ] || die "找不到 jar，请先构建（去掉 SKIP_BUILD=1）"
TOTAL_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo)
# 实测 2G 机型：MySQL + 系统已占 ~1GB，可用仅 ~900MB。RAM/2 会直接 OOM。
if [ "$TOTAL_MB" -le 2048 ]; then
  HEAP=640
elif [ "$TOTAL_MB" -le 4096 ]; then
  HEAP=1024
else
  HEAP=$(( TOTAL_MB / 2 )); [ "$HEAP" -gt 2048 ] && HEAP=2048
fi
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
ExecStart=/usr/bin/java -Xmx${HEAP}m -jar $JAR_ABS "--hero.assets-dir=$HERO_ASSETS"
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable $SERVICE >/dev/null 2>&1 || true
systemctl restart $SERVICE

# ---------------------------------------------------------------- 8. nginx
say "[8/8] 配置 Nginx（静态站 + /api 反代，SSE 300s）"
cat > /etc/nginx/sites-available/$SERVICE <<EOF
server {
    listen 80 default_server;
    server_name _;

    root $FRONTEND/dist;
    index index.html;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    # 两个固定 URL 对应上传目录，每次访问重新验证，避免继续命中旧视频的长缓存。
    location ~ ^/assets/(band-(mygo|mujica)-720p[.]mp4)$ {
        alias $HERO_ASSETS/\$1;
        add_header Cache-Control "no-cache" always;
    }

    location /assets/ {
        try_files \$uri =404;
        add_header Cache-Control "public, max-age=2592000";
    }

    location /api/ {
        client_max_body_size 110m;
        client_body_timeout 300s;
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
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
重新部署   cd $APP_DIR && sudo bash scripts/deploy-ubuntu.sh
==========================================
EOF
