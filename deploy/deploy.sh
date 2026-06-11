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

if command -v crontab >/dev/null 2>&1; then
  CRON_FILE="$(mktemp)"
  crontab -l 2>/dev/null | grep -v 'run-content-enrichment-worker.sh' > "$CRON_FILE" || true
  echo "$CRON_SCHEDULE cd $(pwd) && ./run-content-enrichment-worker.sh >> logs/content-enrichment-worker/cron.log 2>&1" >> "$CRON_FILE"
  crontab "$CRON_FILE"
  rm -f "$CRON_FILE"
  crontab -l | grep 'run-content-enrichment-worker.sh' || true
else
  echo "⚠️ crontab 명령을 찾을 수 없어 content enrichment cron을 등록하지 못했습니다."
fi

echo "⏳ 서비스 시작 대기..."
sleep 15

echo "🔍 nginx 컨테이너 로그 확인..."
docker logs newsletter-nginx --tail 10

echo "🔍 서비스 상태 확인..."
docker compose ps api nginx

echo "✅ 배포 완료!"
