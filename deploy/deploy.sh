#!/bin/bash

echo "🚀 뉴스레터 서비스 배포 시작..."

echo "🧹 Docker 이미지 정리..."
docker image prune -f

echo "📥 최신 서비스 이미지 pull 중..."
docker compose pull api nginx content-enrichment-worker

echo "🛑 기존 서비스 중지..."
docker compose down
docker rm -f newsletter-content-enrichment-worker 2>/dev/null || true

echo "🔄 서비스 재시작 중..."
docker compose up -d

echo "⏰ content enrichment cron 등록 중..."
mkdir -p logs/content-enrichment-worker

CRON_SCHEDULE="$(grep -E '^CONTENT_ENRICHMENT_CRON_SCHEDULE=' .env 2>/dev/null | tail -1 | cut -d= -f2- || true)"
CRON_SCHEDULE="${CRON_SCHEDULE:-17 * * * *}"
CRON_SCHEDULE="${CRON_SCHEDULE%\"}"
CRON_SCHEDULE="${CRON_SCHEDULE#\"}"
CRON_SCHEDULE="${CRON_SCHEDULE%\'}"
CRON_SCHEDULE="${CRON_SCHEDULE#\'}"

cron_field_to_systemd_field() {
  local field="$1"
  if [[ "$field" == "*/"* ]]; then
    echo "0/${field#*/}"
  else
    echo "$field"
  fi
}

cron_schedule_to_systemd_calendar() {
  local minute hour day_of_month month day_of_week extra
  read -r minute hour day_of_month month day_of_week extra <<< "$1"

  if [ -n "${extra:-}" ] || [ -z "${day_of_week:-}" ]; then
    return 1
  fi

  if [ "$day_of_week" != "*" ]; then
    return 1
  fi

  minute="$(cron_field_to_systemd_field "$minute")"
  hour="$(cron_field_to_systemd_field "$hour")"
  day_of_month="$(cron_field_to_systemd_field "$day_of_month")"
  month="$(cron_field_to_systemd_field "$month")"

  echo "*-${month}-${day_of_month} ${hour}:${minute}:00"
}

if command -v crontab >/dev/null 2>&1; then
  CRON_FILE="$(mktemp)"
  crontab -l 2>/dev/null | grep -v 'run-content-enrichment-worker.sh' > "$CRON_FILE" || true
  echo "$CRON_SCHEDULE cd $(pwd) && ./run-content-enrichment-worker.sh >> logs/content-enrichment-worker/cron.log 2>&1" >> "$CRON_FILE"
  crontab "$CRON_FILE"
  rm -f "$CRON_FILE"
  crontab -l | grep 'run-content-enrichment-worker.sh' || true
elif command -v systemctl >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  TIMER_NAME="newsletter-content-enrichment-worker"
  SERVICE_PATH="/etc/systemd/system/${TIMER_NAME}.service"
  TIMER_PATH="/etc/systemd/system/${TIMER_NAME}.timer"
  DEPLOY_DIR="$(pwd)"

  if ! ON_CALENDAR="$(cron_schedule_to_systemd_calendar "$CRON_SCHEDULE")"; then
    ON_CALENDAR="*-*-* *:17:00"
    echo "⚠️ systemd timer로 변환할 수 없는 cron 표현식입니다: $CRON_SCHEDULE"
    echo "⚠️ 기본 스케줄($ON_CALENDAR)로 content enrichment timer를 등록합니다."
  fi

  sudo tee "$SERVICE_PATH" >/dev/null <<EOF
[Unit]
Description=Newsletter content enrichment worker

[Service]
Type=oneshot
WorkingDirectory=$DEPLOY_DIR
ExecStart=/bin/bash -lc './run-content-enrichment-worker.sh >> logs/content-enrichment-worker/cron.log 2>&1'
EOF

  sudo tee "$TIMER_PATH" >/dev/null <<EOF
[Unit]
Description=Run newsletter content enrichment worker periodically

[Timer]
OnCalendar=$ON_CALENDAR
Persistent=true
Unit=${TIMER_NAME}.service

[Install]
WantedBy=timers.target
EOF

  sudo systemctl daemon-reload
  sudo systemctl enable --now "${TIMER_NAME}.timer"
  sudo systemctl list-timers --all "${TIMER_NAME}.timer" || true
else
  echo "⚠️ crontab/systemd timer를 사용할 수 없어 content enrichment 주기 실행을 등록하지 못했습니다."
fi

echo "🛡️ fail2ban 설정 적용 중..."
if [ -x ./install-fail2ban.sh ]; then
  ./install-fail2ban.sh || echo "⚠️ fail2ban 설정 적용에 실패했습니다. 서버에서 deploy/install-fail2ban.sh를 확인하세요."
else
  echo "⚠️ install-fail2ban.sh 파일을 찾을 수 없어 fail2ban 설정을 건너뜁니다."
fi

echo "⏳ 서비스 시작 대기..."
sleep 15

echo "🔍 nginx 컨테이너 로그 확인..."
docker logs newsletter-nginx --tail 10

echo "🔍 서비스 상태 확인..."
docker compose ps api nginx

echo "✅ 배포 완료!"
