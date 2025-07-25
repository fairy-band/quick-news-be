#!/bin/bash

echo "🚀 Newsletter 서비스 배포 시작..."

# 환경 변수 체크
if [ ! -f .env ]; then
    echo "❌ .env 파일이 없습니다. 먼저 .env 파일을 생성해주세요."
    exit 1
fi

# Git 최신 코드 pull
echo "📦 최신 코드 가져오는 중..."
git pull origin main

# 기존 컨테이너 정리
echo "🧹 기존 컨테이너 정리 중..."
docker-compose -f docker-compose.prod.yml down

# 새 이미지 빌드
echo "🔨 새 이미지 빌드 중..."
docker-compose -f docker-compose.prod.yml build --no-cache

# 서비스 시작
echo "🚀 서비스 시작 중..."
docker-compose -f docker-compose.prod.yml up -d

# 컨테이너 상태 확인
echo "📊 컨테이너 상태 확인 중..."
sleep 10
docker-compose -f docker-compose.prod.yml ps

# 로그 확인
echo "📋 API 서버 로그 (최근 20줄):"
docker-compose -f docker-compose.prod.yml logs --tail=20 api

echo "✅ 배포 완료!"
echo "🌐 서비스 URL: https://your-domain.com"
echo "📊 모니터링: docker-compose -f docker-compose.prod.yml logs -f"
