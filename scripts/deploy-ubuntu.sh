#!/usr/bin/env bash
# Ubuntu 22.04/24.04. Default: deploy the current clean checkout.
# USE_GIT=1 permits fast-forward of BRANCH. --render-config DIR only writes configs.
set -euo pipefail
APP_DIR="$(printenv APP_DIR || true)"
if [[ -z "$APP_DIR" ]]; then APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"; fi
BACKEND="$APP_DIR/backend"
FRONTEND="$APP_DIR/frontend"
HERO_ASSETS="$APP_DIR/var/hero-assets"
ENV_FILE="$BACKEND/.env"
SERVICE=avemygo
BRANCH="$(printenv BRANCH || true)"; [[ -n "$BRANCH" ]] || BRANCH=main
DB_NAME="$(printenv DB_NAME || true)"; [[ -n "$DB_NAME" ]] || DB_NAME=bandori_live
DB_USER="$(printenv DB_USER || true)"; [[ -n "$DB_USER" ]] || DB_USER=bandori
say() { printf '\n==> %s\n' "$*"; }
die() { printf '\n[ERROR] %s\n' "$*" >&2; exit 1; }
[[ "$APP_DIR" =~ ^/[A-Za-z0-9_./-]+$ && "$APP_DIR" != *'/../'* ]] || die 'Use a canonical absolute APP_DIR without spaces (for example /opt/ave-mygo).'
[[ "$DB_NAME" =~ ^[A-Za-z][A-Za-z0-9_]{0,63}$ ]] || die 'Invalid DB_NAME.'
[[ "$DB_USER" =~ ^[A-Za-z][A-Za-z0-9_]{0,31}$ && "$DB_USER" != root ]] || die 'Use a dedicated non-root DB_USER.'
render_configs() {
  local output_dir="$1"
  local heap_argument="$HEAP"
  heap_argument+=m
  mkdir -p "$output_dir"
  cat > "$output_dir/$SERVICE.service" <<EOF
[Unit]
Description=BanG Dream! Live backend
After=mysql.service network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE
Group=$SERVICE
WorkingDirectory=$BACKEND
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/java -Xmx$heap_argument -jar $JAR_ABS --hero.assets-dir=$HERO_ASSETS --server.address=127.0.0.1
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
UMask=0027
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$HERO_ASSETS

[Install]
WantedBy=multi-user.target
EOF
  cat > "$output_dir/$SERVICE.nginx" <<EOF
server {
    listen 80 default_server;
    server_name _;
    root $FRONTEND/dist;
    index index.html;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;
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
        # Single Internet-facing proxy; overwrite client-supplied forwarding headers.
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        proxy_buffering off;
    }
    location / { try_files \$uri \$uri/ /index.html; }
}
EOF
}
if [[ $# -gt 0 ]]; then
  [[ $# -eq 2 && "$1" == --render-config ]] || die 'Usage: --render-config OUTPUT_DIR'
  HEAP=640
  JAR_ABS="$BACKEND/target/ave-mygo-0.0.1-SNAPSHOT.jar"
  render_configs "$2"
  exit 0
fi
[[ "$(id -u)" == 0 ]] || die 'Run as root or sudo.'
[[ -d "$BACKEND" ]] || die 'APP_DIR must contain backend/.'
[[ "$(realpath "$APP_DIR")" == "$APP_DIR" ]] || die 'Use a canonical APP_DIR without symlinks or dot segments.'
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tr -d '\r'; }
if [[ -f "$ENV_FILE" ]]; then
  [[ "$(read_env DB_USERNAME)" == "$DB_USER" ]] || die 'Existing DB_USERNAME differs; specify DB_USER explicitly. Existing configuration is preserved.'
  expected_url="jdbc:mysql://localhost:3306/$DB_NAME"
  [[ "$(read_env DB_URL)" == "$expected_url" || "$(read_env DB_URL)" == "$expected_url?"* ]] || die 'Existing DB_URL differs; migrate configuration explicitly before deployment.'
fi
say '[1/8] Verify source checkout'
cd "$APP_DIR"
if [[ "$(git rev-parse --is-inside-work-tree 2>/dev/null || true)" == true ]]; then
  [[ -z "$(git status --porcelain)" ]] || die 'Working tree has changes; save or commit them before deploying.'
  if [[ "$(printenv USE_GIT || true)" == 1 ]]; then
    git check-ref-format --branch "$BRANCH" >/dev/null || die 'Invalid BRANCH.'
    [[ "$(git branch --show-current)" == "$BRANCH" ]] || die 'Check out BRANCH before deployment.'
    git fetch origin "$BRANCH" || die 'Fetch failed; deployment stopped.'
    git merge --ff-only FETCH_HEAD || die 'Fast-forward failed; deployment stopped.'
  fi
  git log -1 --format='Deploy commit: %h %s'
fi
say '[2/8] Install system dependencies'
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk mysql-server nginx maven curl ca-certificates xz-utils openssl >/dev/null
systemctl enable --now mysql
node_ok() { command -v node >/dev/null && node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit((a===22&&b>=18)||(a===24&&b>=12)||a>24?0:1)'; }
if ! node_ok; then
  node_version="$(cat "$APP_DIR/.nvmrc")"
  [[ "$node_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die 'Invalid .nvmrc.'
  [[ "$(uname -m)" == x86_64 ]] || die 'Install a compatible Node manually on this architecture.'
  node_dir=$(mktemp -d)
  node_archive="node-v$node_version-linux-x64.tar.xz"
  node_mirror="https://registry.npmmirror.com/-/binary/node/v$node_version"
  curl -fL --retry 3 --max-time 300 "$node_mirror/$node_archive" -o "$node_dir/$node_archive"
  curl -fL --retry 3 --max-time 60 "$node_mirror/SHASUMS256.txt" -o "$node_dir/SHASUMS256.txt"
  (cd "$node_dir" && awk -v f="$node_archive" '$2==f{print;found=1} END{if(!found)exit 1}' SHASUMS256.txt | sha256sum --check --status) || die 'Node checksum validation failed.'
  install -d /opt/nodejs
  tar -xJf "$node_dir/$node_archive" -C /opt/nodejs --strip-components=1
  ln -sf /opt/nodejs/bin/node /usr/local/bin/node
  ln -sf /opt/nodejs/bin/npm /usr/local/bin/npm
  ln -sf /opt/nodejs/bin/npx /usr/local/bin/npx
  hash -r
fi
say '[3/8] Configure dedicated runtime user and storage'
id "$SERVICE" >/dev/null 2>&1 || useradd --system --user-group --home-dir /nonexistent --no-create-home --shell /usr/sbin/nologin "$SERVICE"
[[ "$(id -u "$SERVICE")" != 0 ]] || die 'Runtime account must not be root.'
install -d -m 0755 "$APP_DIR/var"
install -d -m 0755 -o "$SERVICE" -g "$SERVICE" "$HERO_ASSETS"
for hero_slot in mygo mujica; do
  hero_video="band-$hero_slot-720p.mp4"
  if [[ ! -f "$HERO_ASSETS/$hero_video" && -f "$FRONTEND/public/assets/$hero_video" ]]; then
    install -m 0644 -o "$SERVICE" -g "$SERVICE" "$FRONTEND/public/assets/$hero_video" "$HERO_ASSETS/$hero_video"
  elif [[ -f "$HERO_ASSETS/$hero_video" ]]; then
    chmod 0644 "$HERO_ASSETS/$hero_video"
    chown "$SERVICE:$SERVICE" "$HERO_ASSETS/$hero_video"
  fi
done
say '[4/8] Configure application database (preserve existing credentials)'
if [[ -f "$ENV_FILE" ]]; then DB_PASS="$(read_env DB_PASSWORD)"; else DB_PASS=$(openssl rand -hex 24); fi
[[ "$DB_PASS" =~ ^[A-Za-z0-9@%_+=:.,/-]{16,128}$ ]] || die 'DB password must be 16-128 supported printable characters; no credential is changed.'
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
SQL
MYSQL_PWD="$DB_PASS" mysql -u "$DB_USER" "$DB_NAME" -e 'SELECT 1' >/dev/null || die 'Database credentials do not match; no password reset was attempted.'
if [[ ! -f "$ENV_FILE" ]]; then
  (umask 077; cat > "$ENV_FILE" <<EOF
DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS
JWT_SECRET=$(openssl rand -hex 48)
JWT_EXPIRATION_HOURS=24
EOF
  )
fi
[[ "$(read_env JWT_SECRET)" =~ ^[A-Za-z0-9+/=_-]{32,}$ ]] || die 'Set a valid random JWT_SECRET of at least 32 bytes in .env.'
if [[ -n "$(printenv LLM_API_KEY || true)" ]] && ! grep -q '^LLM_API_KEY=' "$ENV_FILE"; then
  for llm_name in LLM_API_KEY LLM_MODEL LLM_BASE_URL; do
    llm_value="$(printenv "$llm_name" || true)"
    [[ -n "$llm_value" && "$llm_value" != *$'\n'* && "$llm_value" != *$'\r'* ]] || die 'Provide all LLM_* values without newlines.'
  done
  for llm_name in LLM_API_KEY LLM_MODEL LLM_BASE_URL; do
    llm_value="$(printenv "$llm_name")"
    printf '%s=%s\n' "$llm_name" "$llm_value" >> "$ENV_FILE"
  done
fi
chown root:"$SERVICE" "$ENV_FILE"
chmod 0640 "$ENV_FILE"
say '[5/8] Build application'
if [[ "$(printenv SKIP_BUILD || true)" != 1 ]]; then
  install -d -m 0755 "$APP_DIR/var/build"
  cat > "$APP_DIR/var/build/maven-settings.xml" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><mirrors><mirror><id>aliyun-public</id><url>https://maven.aliyun.com/repository/public</url><mirrorOf>*</mirrorOf></mirror></mirrors></settings>
EOF
  (cd "$BACKEND" && mvn -s "$APP_DIR/var/build/maven-settings.xml" -B -q -DskipTests clean package)
  (cd "$FRONTEND" && npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund && VITE_API_BASE=/api/v1 npm run build)
fi
jar_count=$(find "$BACKEND/target" -maxdepth 1 -name 'ave-mygo-*.jar' -type f | wc -l)
[[ "$jar_count" == 1 ]] || die 'Expected exactly one backend jar.'
JAR_ABS=$(find "$BACKEND/target" -maxdepth 1 -name 'ave-mygo-*.jar' -type f)
[[ -f "$FRONTEND/dist/index.html" ]] || die 'Missing frontend build.'
runuser -u "$SERVICE" -- test -r "$JAR_ABS" || die 'Runtime user cannot read jar.'
runuser -u www-data -- test -r "$FRONTEND/dist/index.html" || die 'Nginx user cannot read frontend; use a traversable /opt checkout.'
say '[6/8] Render and validate service/proxy configuration'
TOTAL_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo)
if (( TOTAL_MB <= 2048 )); then HEAP=640; elif (( TOTAL_MB <= 4096 )); then HEAP=1024; else HEAP=2048; fi
config_dir=$(mktemp -d)
render_configs "$config_dir"
systemd-analyze verify "$config_dir/$SERVICE.service"
# Validate a standalone candidate before writing any live configuration.
printf 'events {}\nhttp { include /etc/nginx/mime.types; include %s; }\n' "$config_dir/$SERVICE.nginx" > "$config_dir/nginx-test.conf"
nginx -t -c "$config_dir/nginx-test.conf"
install -m 0644 "$config_dir/$SERVICE.service" "/etc/systemd/system/$SERVICE.service"
install -m 0644 "$config_dir/$SERVICE.nginx" "/etc/nginx/sites-available/$SERVICE"
ln -sf "/etc/nginx/sites-available/$SERVICE" "/etc/nginx/sites-enabled/$SERVICE"
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl restart "$SERVICE"
say '[7/8] Wait for database readiness'
ready=0
for attempt in $(seq 1 40); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/api/v1/ready | grep -Eq '"code"[[:space:]]*:[[:space:]]*0'; then ready=1; break; fi
  sleep 3
done
[[ "$ready" == 1 ]] || die "Readiness failed; inspect journalctl -u $SERVICE. Deployment is incomplete."
systemctl enable nginx
systemctl reload-or-restart nginx
say '[8/8] Verify public API through Nginx'
curl -fsS --max-time 10 http://127.0.0.1/api/v1/ready | grep -Eq '"code"[[:space:]]*:[[:space:]]*0' || die 'Nginx readiness check failed.'
echo "Deployment complete. Logs: journalctl -u $SERVICE -f"
