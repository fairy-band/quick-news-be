#!/bin/bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAIL_TARGET="/etc/fail2ban/jail.d/newsletter-nginx.local"
JAIL_TEMPLATE="$DEPLOY_DIR/fail2ban/jail.d/newsletter-nginx.local"

if ! command -v fail2ban-client >/dev/null 2>&1; then
  echo "fail2ban-client 명령을 찾을 수 없어 fail2ban 설정을 건너뜁니다."
  exit 0
fi

if ! command -v sudo >/dev/null 2>&1; then
  echo "sudo 명령을 찾을 수 없어 fail2ban 설정을 건너뜁니다."
  exit 0
fi

mkdir -p "$DEPLOY_DIR/logs/nginx"
touch "$DEPLOY_DIR/logs/nginx/error.log" "$DEPLOY_DIR/logs/nginx/bot_scan.log"

sudo mkdir -p /etc/fail2ban/filter.d /etc/fail2ban/jail.d
sudo cp "$DEPLOY_DIR"/fail2ban/filter.d/*.conf /etc/fail2ban/filter.d/
sed "s#__DEPLOY_DIR__#$DEPLOY_DIR#g" "$JAIL_TEMPLATE" | sudo tee "$JAIL_TARGET" >/dev/null

if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl restart fail2ban
else
  sudo service fail2ban restart
fi

sudo fail2ban-client status nginx-limit-req || true
sudo fail2ban-client status nginx-bot-scan || true
sudo fail2ban-client status nginx-error-bot-scan || true
