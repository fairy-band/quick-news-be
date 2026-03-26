#!/bin/bash

echo "🚀 뉴스레터 서비스 배포 시작..."

echo "🧹 Docker 이미지 정리..."
docker image prune -f

echo "📥 최신 서비스 이미지 pull 중..."
docker compose pull api nginx

echo "🛑 기존 서비스 중지..."
docker compose down

echo "🔄 서비스 재시작 중..."
docker compose up -d

echo "⏳ 서비스 시작 대기..."
sleep 15

echo "🔍 nginx 컨테이너 로그 확인..."
docker logs newsletter-nginx --tail 10

echo "🔍 서비스 상태 확인..."
docker compose ps api nginx

echo "✅ 배포 완료!"
