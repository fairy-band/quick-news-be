#!/bin/bash

# Blue-Green 배포 스크립트
# 현재 활성 환경과 반대 환경에 새 버전을 배포하고 점진적으로 전환

set -e  # 오류 발생 시 스크립트 중단

# 색상 설정
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✅${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ❌${NC} $1"
}

# 배포 모드 설정 (기본값: gradual)
DEPLOY_MODE=${1:-gradual}

log "🚀 Blue-Green 배포 시작 (모드: $DEPLOY_MODE)"

# 현재 활성 환경 확인
get_active_environment() {
    if [ -f "./active-env.conf" ]; then
        grep "set.*active_env" ./active-env.conf | sed 's/.*set.*active_env[[:space:]]*\([^;]*\);.*/\1/' | tr -d ' '
    else
        echo "blue"
    fi
}

# 환경 전환 함수
switch_environment() {
    local target_env=$1
    log "🔄 환경을 $target_env로 전환 중..."
    echo "set \$active_env $target_env;" > ./active-env.conf
    
    # nginx 설정 리로드
    if docker exec newsletter-nginx nginx -s reload 2>/dev/null; then
        log_success "Nginx 설정이 성공적으로 리로드되었습니다"
    else
        log_warning "Nginx 리로드 실패 - 컨테이너가 실행 중이 아닐 수 있습니다"
    fi
}

# 헬스체크 함수
health_check() {
    local env=$1
    local max_attempts=30
    local attempt=1
    
    log "🔍 $env 환경 헬스체크 시작..."
    
    while [ $attempt -le $max_attempts ]; do
        # API 헬스체크
        if curl -f -s "http://localhost:8080/health" > /dev/null 2>&1 && \
           curl -f -s "http://localhost:8082/health" > /dev/null 2>&1 && \
           curl -f -s "http://localhost:8083/health" > /dev/null 2>&1; then
            log_success "$env 환경 헬스체크 통과 (시도: $attempt/$max_attempts)"
            return 0
        fi
        
        log "⏳ $env 환경 헬스체크 실패 (시도: $attempt/$max_attempts) - 재시도 중..."
        sleep 10
        ((attempt++))
    done
    
    log_error "$env 환경 헬스체크 실패"
    return 1
}

# 환경별 서비스 상태 확인
check_environment_status() {
    local env=$1
    log "📊 $env 환경 상태 확인 중..."
    
    local api_container="newsletter-api-$env"
    local batch_container="newsletter-batch-$env"
    local admin_container="newsletter-admin-$env"
    
    local api_status=$(docker inspect -f '{{.State.Status}}' $api_container 2>/dev/null || echo "not-found")
    local batch_status=$(docker inspect -f '{{.State.Status}}' $batch_container 2>/dev/null || echo "not-found")
    local admin_status=$(docker inspect -f '{{.State.Status}}' $admin_container 2>/dev/null || echo "not-found")
    
    echo "  API: $api_status, Batch: $batch_status, Admin: $admin_status"
    
    if [[ "$api_status" == "running" && "$batch_status" == "running" && "$admin_status" == "running" ]]; then
        return 0
    else
        return 1
    fi
}

# 롤백 함수
rollback() {
    local original_env=$1
    log_error "배포 실패 - $original_env 환경으로 롤백 중..."
    switch_environment $original_env
    log_success "롤백 완료"
}

# 권한 설정
log "🔧 권한 설정 확인 및 수정..."
sudo mkdir -p /var/www/certbot
sudo chown -R 101:101 /var/www/certbot 2>/dev/null || sudo chown -R www-data:www-data /var/www/certbot
sudo chmod -R 755 /var/www/certbot

# SSL 인증서 권한 확인
sudo chmod 644 /etc/letsencrypt/live/fairy-band.com/*.pem 2>/dev/null || true
sudo chmod 755 /etc/letsencrypt/live/fairy-band.com 2>/dev/null || true

# 현재 활성 환경 확인
CURRENT_ENV=$(get_active_environment)
if [ "$CURRENT_ENV" = "blue" ]; then
    TARGET_ENV="green"
else
    TARGET_ENV="blue"
fi

log "📍 현재 활성 환경: $CURRENT_ENV"
log "🎯 배포 대상 환경: $TARGET_ENV"

# Docker 이미지 정리
log "🧹 Docker 이미지 정리..."
docker image prune -f

# 최신 이미지 pull
log "📥 최신 서비스 이미지 pull 중..."
docker pull ahj0/fairy-band:api
docker pull ahj0/fairy-band:batch
docker pull ahj0/fairy-band:admin

# 배포 모드에 따른 처리
case $DEPLOY_MODE in
    "immediate")
        log "⚡ 즉시 배포 모드"
        
        # 현재 환경 중지
        log "🛑 $CURRENT_ENV 환경 중지..."
        docker compose -f docker-compose-$CURRENT_ENV.yml down || true
        
        # 새 환경 시작
        log "🚀 $TARGET_ENV 환경 시작..."
        docker compose -f docker-compose-$TARGET_ENV.yml up -d
        
        # 헬스체크
        if health_check $TARGET_ENV; then
            switch_environment $TARGET_ENV
            log_success "즉시 배포 완료"
        else
            log_error "배포 실패"
            exit 1
        fi
        ;;
        
    "gradual"|*)
        log "🔄 점진적 배포 모드"
        
        # 비활성 환경에 새 버전 배포
        log "🚀 $TARGET_ENV 환경에 새 버전 배포 중..."
        docker compose -f docker-compose-$TARGET_ENV.yml down || true
        docker compose -f docker-compose-$TARGET_ENV.yml up -d
        
        # 새 환경 헬스체크
        if ! health_check $TARGET_ENV; then
            log_error "$TARGET_ENV 환경 배포 실패"
            exit 1
        fi
        
        # 새 환경 상태 재확인
        sleep 30
        if ! check_environment_status $TARGET_ENV; then
            log_error "$TARGET_ENV 환경이 안정적이지 않습니다"
            exit 1
        fi
        
        log_success "$TARGET_ENV 환경 배포 완료 및 안정화 확인"
        
        # 사용자 확인 (CI/CD 환경에서는 자동 진행)
        if [ "${CI:-false}" != "true" ]; then
            echo
            log "🤔 $TARGET_ENV 환경으로 트래픽을 전환하시겠습니까?"
            echo "   - $TARGET_ENV 환경 테스트: curl -H 'Host: fairy-band.com' http://localhost:${TARGET_ENV}80/health"
            echo "   - 현재 환경: $CURRENT_ENV"
            echo "   - 대상 환경: $TARGET_ENV"
            echo
            read -p "계속하시겠습니까? (y/N): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                log "배포가 취소되었습니다. $TARGET_ENV 환경은 대기 상태로 유지됩니다."
                exit 0
            fi
        fi
        
        # 트래픽 전환
        log "🔄 트래픽을 $TARGET_ENV 환경으로 전환 중..."
        switch_environment $TARGET_ENV
        
        # 전환 후 최종 헬스체크
        sleep 10
        if curl -f -s "https://fairy-band.com/health" > /dev/null 2>&1; then
            log_success "트래픽 전환 완료 및 서비스 정상 확인"
        else
            log_warning "외부 접근 헬스체크 실패 - 방화벽 또는 DNS 설정을 확인하세요"
        fi
        
        # 이전 환경 정리 (optional)
        log "🧹 이전 환경 ($CURRENT_ENV) 정리 중..."
        sleep 30  # 안전을 위한 대기
        docker compose -f docker-compose-$CURRENT_ENV.yml down || true
        
        log_success "점진적 배포 완료!"
        ;;
esac

# 배포 완료 상태 확인
echo
log "📊 배포 완료 상태 요약:"
echo "  - 활성 환경: $(get_active_environment)"
echo "  - 배포 시간: $(date)"

# 최종 서비스 상태 확인
log "🔍 최종 서비스 상태 확인..."
docker compose -f docker-compose-$(get_active_environment).yml ps

# 연결 테스트
log "🌐 최종 연결 테스트..."
if curl -I -s "https://fairy-band.com/health" | head -n 1; then
    log_success "서비스가 정상적으로 실행 중입니다"
else
    log_warning "외부 연결 확인이 필요합니다"
fi

log_success "🎉 Blue-Green 배포가 성공적으로 완료되었습니다!"
