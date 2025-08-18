#!/bin/bash

# 환경 전환 유틸리티 스크립트

set -e

# 색상 설정
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] ✅${NC} $1"
}

# 사용법 출력
usage() {
    echo "사용법: $0 [blue|green|status]"
    echo "  blue   - Blue 환경으로 전환"
    echo "  green  - Green 환경으로 전환"
    echo "  status - 현재 활성 환경 확인"
    exit 1
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
    
    if [[ "$target_env" != "blue" && "$target_env" != "green" ]]; then
        echo "❌ 잘못된 환경: $target_env"
        usage
    fi
    
    log "🔄 환경을 $target_env로 전환 중..."
    echo "set \$active_env $target_env;" > ./active-env.conf
    
    # nginx 리로드
    if docker exec newsletter-nginx nginx -s reload 2>/dev/null; then
        log_success "환경이 $target_env로 전환되었습니다"
    else
        echo "⚠️ Nginx 리로드 실패 - 수동으로 nginx를 재시작해주세요"
    fi
    
    # 현재 상태 출력
    show_status
}

# 상태 확인
show_status() {
    local current_env=$(get_active_environment)
    echo
    echo "📊 현재 배포 상태:"
    echo "  🟢 활성 환경: $current_env"
    
    # 각 환경의 컨테이너 상태 확인
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
    done
    
    echo
    echo "💡 명령어:"
    echo "  - Blue로 전환: $0 blue"
    echo "  - Green으로 전환: $0 green"
    echo "  - 현재 상태: $0 status"
}

# 메인 로직
case "${1:-status}" in
    "blue")
        switch_environment "blue"
        ;;
    "green")
        switch_environment "green"
        ;;
    "status")
        show_status
        ;;
    *)
        usage
        ;;
esac
