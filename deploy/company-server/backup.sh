#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")"
umask 077
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
work=$(mktemp -d)
trap 'docker compose start backend >/dev/null 2>&1 || true; rm -rf "$work"' EXIT
docker compose stop backend
docker compose exec -T db pg_dump -U rnd_sample -d rnd_sample -Fc > "$work/database.dump"
docker compose run --rm --no-deps --user 0 --entrypoint tar backend -czf - -C /data archive > "$work/archive.tar.gz"
cp .env credentials.txt "$work/"
tar -czf "backups/longhui-$stamp.tar.gz" -C "$work" .
find backups -name 'longhui-*.tar.gz' -mtime +14 -delete
echo "Backup: backups/longhui-$stamp.tar.gz; copy it to a separate disk/NAS."
