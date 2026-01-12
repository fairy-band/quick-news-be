#!/bin/bash

echo "🚀 뉴스레터 서비스 배포 시작..."

echo "🔧 권한 설정 확인 및 수정..."
# SSL 및 certbot 디렉토리 권한 설정
sudo mkdir -p /var/www/certbot
sudo chown -R 101:101 /var/www/certbot 2>/dev/null || sudo chown -R www-data:www-data /var/www/certbot
sudo chmod -R 755 /var/www/certbot

# SSL 인증서 권한 확인 (에러 무시)
sudo chmod 644 /etc/letsencrypt/live/fairy-band.com/*.pem 2>/dev/null || true
sudo chmod 755 /etc/letsencrypt/live/fairy-band.com 2>/dev/null || true

echo "🧹 Docker 이미지 정리..."
docker image prune -f

echo "📥 최신 서비스 이미지 pull 중..."
docker compose pull api admin nginx

echo "🛑 기존 서비스 중지..."
docker compose down

echo "🔄 서비스 재시작 중..."
docker compose up -d

echo "⏳ 서비스 시작 대기..."
sleep 15

echo "🔍 nginx 컨테이너 로그 확인..."
docker logs newsletter-nginx --tail 10

echo "🔍 서비스 상태 확인..."
docker compose ps api admin nginx

echo "🌐 연결 테스트..."
curl -I https://fairy-band.com/health || echo "⚠️ 연결 확인 필요"

echo "✅ 배포 완료!"
