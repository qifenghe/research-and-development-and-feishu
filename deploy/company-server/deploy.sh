#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")"
if [[ $EUID -ne 0 ]]; then echo "Use: sudo bash deploy.sh"; exit 1; fi
command -v python3 >/dev/null
command -v curl >/dev/null
docker compose version
python3 prepare-config.py
mkdir -p backups
chmod 700 backups
if [[ -f images.tar.gz || -f images.tar ]]; then
  image_archive=images.tar
  [[ ! -f images.tar.gz ]] || image_archive=images.tar.gz
  docker load -i "$image_archive"
  docker compose run --rm --no-deps --user 0 --entrypoint chown backend -R 10001:10001 /data/archive
  docker compose up -d --no-build --pull never
else
  docker compose build --pull=false
  docker compose run --rm --no-deps --user 0 --entrypoint chown backend -R 10001:10001 /data/archive
  docker compose up -d --no-build --pull never
fi
ready=0
for ((i=0;i<90;i++)); do
  if curl -fsS --max-time 3 http://127.0.0.1:18080/actuator/health >/dev/null; then ready=1; break; fi
  sleep 2
done
if [[ $ready != 1 ]]; then docker compose logs --tail=100 backend; exit 1; fi
python3 init-admin.py
docker compose ps
curl -fsS --max-time 10 http://127.0.0.1/admin/login >/dev/null
curl -fsS --max-time 10 http://127.0.0.1/m/login >/dev/null
if command -v systemctl >/dev/null && [[ -d /run/systemd/system ]]; then
  systemctl enable docker
  install_dir=$(pwd)
  cat > /etc/systemd/system/longhui-backup.service <<EOF
[Unit]
Description=Longhui database and archive backup
Requires=docker.service
After=docker.service
[Service]
Type=oneshot
WorkingDirectory=$install_dir
ExecStart=/bin/bash $install_dir/backup.sh
EOF
  cat > /etc/systemd/system/longhui-backup.timer <<'EOF'
[Unit]
Description=Daily Longhui backup
[Timer]
OnCalendar=*-*-* 03:00:00 Asia/Shanghai
Persistent=true
[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload
  systemctl enable --now longhui-backup.timer
fi
echo "Local deployment ready: http://10.77.0.10/admin/login and /m/login"
echo "View administrator credentials: sudo cat $(pwd)/credentials.txt"
echo "Configure Oray HTTPS mapping only after testing local login."
