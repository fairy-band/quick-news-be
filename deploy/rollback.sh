#!/bin/bash

# 롤백 스크립트
# Blue-Green 배포에서 문제 발생 시 이전 환경으로 신속하게 롤백

set -e

# 색상 설정
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] ✅${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] ⚠️${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ❌${NC} $1"
}

# 현재 활성 환경 확인
get_active_environment() {
    if [ -f "./active-env.conf" ]; then
        grep "set.*active_env" ./active-env.conf | sed 's/.*set.*active_env[[:space:]]*\([^;]*\);.*/\1/' | tr -d ' '
    else
        echo "blue"
    fi
}

# 환경 전환
switch_environment() {
    local target_env=$1
    log "🔄 환경을 $target_env로 전환 중..."
    echo "set \$active_env $target_env;" > ./active-env.conf
    
    # nginx 리로드
    if docker exec newsletter-nginx nginx -s reload 2>/dev/null; then
        log_success "Nginx 설정이 성공적으로 리로드되었습니다"
        return 0
    else
        log_error "Nginx 리로드 실패"
        return 1
    fi
}

# 헬스체크
quick_health_check() {
    local env=$1
    local max_attempts=10
    local attempt=1
    
    log "🔍 $env 환경 빠른 헬스체크..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "https://fairy-band.com/health" > /dev/null 2>&1; then
            log_success "$env 환경 헬스체크 통과"
            return 0
        fi
        
        log "⏳ 헬스체크 실패 (시도: $attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done
    
    log_error "$env 환경 헬스체크 실패"
    return 1
}

# 환경 상태 확인
check_environment_readiness() {
    local env=$1
    log "📊 $env 환경 준비 상태 확인..."
    
    local services=("api" "batch" "admin")
    for service in "${services[@]}"; do
        local container="newsletter-$service-$env"
        local status=$(docker inspect -f '{{.State.Status}}' $container 2>/dev/null || echo "not-found")
        
        if [ "$status" != "running" ]; then
            log_error "$container 컨테이너가 실행 중이 아닙니다 (상태: $status)"
            return 1
        fi
    done
    
    log_success "$env 환경의 모든 컨테이너가 실행 중입니다"
    return 0
}

# 응급 복구 모드
emergency_recovery() {
    log_error "🚨 응급 복구 모드 시작"
    
    # 1. 두 환경 모두 확인
    local blue_ready=false
    local green_ready=false
    
    if check_environment_readiness "blue"; then
        blue_ready=true
    fi
    
    if check_environment_readiness "green"; then
        green_ready=true
    fi
    
    # 2. 사용 가능한 환경으로 전환
    if [ "$blue_ready" = true ]; then
        log "🔄 Blue 환경으로 응급 전환..."
        switch_environment "blue"
        if quick_health_check "blue"; then
            log_success "Blue 환경으로 응급 복구 완료"
            return 0
        fi
    fi
    
    if [ "$green_ready" = true ]; then
        log "🔄 Green 환경으로 응급 전환..."
        switch_environment "green"
        if quick_health_check "green"; then
            log_success "Green 환경으로 응급 복구 완료"
            return 0
        fi
    fi
    
    # 3. 모든 환경이 실패한 경우
    log_error "🚨 모든 환경이 사용 불가능합니다. 수동 개입이 필요합니다."
    return 1
}

# 메인 롤백 함수
perform_rollback() {
    local target_env=$1
    local current_env=$(get_active_environment)
    
    echo "==========================================="
    log "🔙 롤백 시작"
    echo "  현재 환경: $current_env"
    echo "  롤백 대상: $target_env"
    echo "==========================================="
    
    # 1. 대상 환경 준비 상태 확인
    if ! check_environment_readiness "$target_env"; then
        log_warning "$target_env 환경이 준비되지 않았습니다. 환경을 재시작합니다..."
        
        # 환경 재시작 시도
        docker compose -f docker-compose-$target_env.yml down || true
        sleep 5
        docker compose -f docker-compose-$target_env.yml up -d
        
        # 재시작 후 대기
        log "⏳ 환경 재시작 후 대기 중..."
        sleep 30
        
        if ! check_environment_readiness "$target_env"; then
            log_error "$target_env 환경 재시작 실패"
            return 1
        fi
    fi
    
    # 2. 트래픽 전환
    if switch_environment "$target_env"; then
        log_success "트래픽이 $target_env 환경으로 전환되었습니다"
    else
        log_error "트래픽 전환 실패"
        return 1
    fi
    
    # 3. 롤백 후 헬스체크
    if quick_health_check "$target_env"; then
        log_success "🎉 롤백 완료! $target_env 환경이 정상적으로 작동 중입니다."
    else
        log_error "롤백 후 헬스체크 실패. 응급 복구를 시도합니다."
        emergency_recovery
        return $?
    fi
    
    # 4. 문제가 있던 환경 정리
    log "🧹 문제가 있던 $current_env 환경 정리 중..."
    docker compose -f docker-compose-$current_env.yml down || true
    
    echo "==========================================="
    log_success "✅ 롤백이 성공적으로 완료되었습니다!"
    echo "  활성 환경: $target_env"
    echo "  정리된 환경: $current_env"
    echo "==========================================="
    
    return 0
}

# 사용법
usage() {
    echo "사용법: $0 [옵션] [환경]"
    echo
    echo "옵션:"
    echo "  --auto          현재 환경의 반대 환경으로 자동 롤백"
    echo "  --emergency     응급 복구 모드 (사용 가능한 모든 환경 시도)"
    echo "  --status        현재 상태만 확인"
    echo
    echo "환경:"
    echo "  blue           Blue 환경으로 롤백"
    echo "  green          Green 환경으로 롤백"
    echo
    echo "예시:"
    echo "  $0 --auto                  # 자동 롤백"
    echo "  $0 blue                   # Blue 환경으로 롤백"
    echo "  $0 --emergency            # 응급 복구"
    echo "  $0 --status               # 상태 확인"
}

# 상태 확인
show_status() {
    local current_env=$(get_active_environment)
    echo "==========================================="
    log "📊 현재 배포 상태"
    echo "==========================================="
    echo "  🟢 활성 환경: $current_env"
    echo
    
    for env in blue green; do
        local status_icon="🔴"
        if [ "$env" = "$current_env" ]; then
            status_icon="🟢"
        fi
        
        echo "  $status_icon $env 환경:"
        
        for service in api batch admin; do
            local container="newsletter-$service-$env"
            local status=$(docker inspect -f '{{.State.Status}}' $container 2>/dev/null || echo "not-found")
            local status_emoji="❌"
            
            case $status in
                "running") status_emoji="✅" ;;
                "exited") status_emoji="🛑" ;;
                "not-found") status_emoji="📭" ;;
            esac
            
            echo "    $status_emoji $service: $status"
        done
        echo
    done
    
    echo "💡 롤백 명령어:"
    if [ "$current_env" = "blue" ]; then
        echo "  - Green으로 롤백: $0 green"
    else
        echo "  - Blue로 롤백: $0 blue"
    fi
    echo "  - 자동 롤백: $0 --auto"
    echo "  - 응급 복구: $0 --emergency"
}

# 메인 로직
case "${1:-status}" in
    "--auto")
        current_env=$(get_active_environment)
        if [ "$current_env" = "blue" ]; then
            target_env="green"
        else
            target_env="blue"
        fi
        log "🤖 자동 롤백: $current_env → $target_env"
        perform_rollback "$target_env"
        ;;
    "--emergency")
        emergency_recovery
        ;;
    "--status")
        show_status
        ;;
    "blue"|"green")
        perform_rollback "$1"
        ;;
    "--help"|"-h")
        usage
        ;;
    "status")
        show_status
        ;;
    *)
        echo "❌ 잘못된 옵션: $1"
        usage
        exit 1
        ;;
esac
