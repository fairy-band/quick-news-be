#!/bin/bash

echo "🚀 뉴스레터 서비스 배포 시작..."

docker image prune

echo "📥 서비스 이미지 pull 중..."
docker compose pull api batch admin nginx

docker compose down

echo "🔄 서비스 재시작 중..."
docker compose up -d

docker compose restart nginx

echo "✅ 배포 완료!"
echo "📊 서비스 상태 확인:"
docker compose ps api batch admin nginx
