#!/bin/bash

# 상세 헬스체크 스크립트
# 각 서비스의 상태를 개별적으로 확인하고 종합 리포트 제공

set -e

# 색상 설정
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 전역 변수
TIMEOUT=30
RETRIES=3
CHECK_EXTERNAL=${CHECK_EXTERNAL:-true}

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

# 개별 서비스 헬스체크
check_service_health() {
    local service=$1
    local port=$2
    local path=${3:-health}
    local expected_status=${4:-200}
    
    log "🔍 $service 서비스 헬스체크 (포트: $port, 경로: /$path)"
    
    local attempt=1
    while [ $attempt -le $RETRIES ]; do
        local response=$(curl -s -w "%{http_code}" -o /dev/null --max-time $TIMEOUT "http://localhost:$port/$path" 2>/dev/null || echo "000")
        
        if [ "$response" = "$expected_status" ]; then
            log_success "$service 서비스 정상 (HTTP $response)"
            return 0
        else
            log "⏳ $service 서비스 응답 실패 (HTTP $response) - 재시도 $attempt/$RETRIES"
            sleep 5
            ((attempt++))
        fi
    done
    
    log_error "$service 서비스 헬스체크 실패"
    return 1
}

# 데이터베이스 연결 확인
check_database_connectivity() {
    log "🗄️ 데이터베이스 연결 확인..."
    
    # PostgreSQL 확인
    if docker exec newsletter-postgres pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
        log_success "PostgreSQL 연결 정상"
    else
        log_error "PostgreSQL 연결 실패"
        return 1
    fi
    
    # MongoDB 확인
    if docker exec newsletter-mongodb mongosh --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
        log_success "MongoDB 연결 정상"
    else
        log_error "MongoDB 연결 실패"
        return 1
    fi
    
    return 0
}

# 외부 접근성 확인
check_external_access() {
    if [ "$CHECK_EXTERNAL" != "true" ]; then
        log "🌐 외부 접근성 확인 건너뛰기"
        return 0
    fi
    
    log "🌐 외부 접근성 확인..."
    
    local domain="fairy-band.com"
    local endpoints=("/health" "/api/health" "/batch/health" "/admin/health")
    
    for endpoint in "${endpoints[@]}"; do
        local response=$(curl -s -w "%{http_code}" -o /dev/null --max-time $TIMEOUT "https://$domain$endpoint" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            log_success "외부 접근 정상: $endpoint (HTTP $response)"
        else
            log_warning "외부 접근 실패: $endpoint (HTTP $response)"
        fi
    done
}

# 현재 활성 환경 확인
get_active_environment() {
    if [ -f "./active-env.conf" ]; then
        grep "set.*active_env" ./active-env.conf | sed 's/.*set.*active_env[[:space:]]*\([^;]*\);.*/\1/' | tr -d ' '
    else
        echo "unknown"
    fi
}

# 환경별 상세 헬스체크
check_environment_health() {
    local env=$1
    log "🔍 $env 환경 상세 헬스체크..."
    
    local services=("api:8080" "batch:8082" "admin:8083")
    local all_healthy=true
    
    for service_info in "${services[@]}"; do
        IFS=':' read -r service port <<< "$service_info"
        local container="newsletter-$service-$env"
        
        # 컨테이너 상태 확인
        local container_status=$(docker inspect -f '{{.State.Status}}' $container 2>/dev/null || echo "not-found")
        
        if [ "$container_status" != "running" ]; then
            log_error "$container 컨테이너가 실행 중이 아닙니다 (상태: $container_status)"
            all_healthy=false
            continue
        fi
        
        # 환경별 포트 매핑 확인
        if [ "$env" = "green" ]; then
            case $service in
                "api") port="8090" ;;
                "batch") port="8092" ;;
                "admin") port="8093" ;;
            esac
        fi
        
        # 서비스 헬스체크
        if ! check_service_health "$service-$env" "$port"; then
            all_healthy=false
        fi
    done
    
    if [ "$all_healthy" = true ]; then
        log_success "$env 환경 전체 서비스 정상"
        return 0
    else
        log_error "$env 환경에 문제가 있는 서비스가 있습니다"
        return 1
    fi
}

# 로드 테스트 (간단한)
simple_load_test() {
    local endpoint=${1:-/health}
    local requests=${2:-10}
    
    log "⚡ 간단한 로드 테스트 ($requests 요청): $endpoint"
    
    local success_count=0
    for i in $(seq 1 $requests); do
        if curl -s -f "https://fairy-band.com$endpoint" >/dev/null 2>&1; then
            ((success_count++))
        fi
    done
    
    local success_rate=$((success_count * 100 / requests))
    
    if [ $success_rate -ge 95 ]; then
        log_success "로드 테스트 통과: $success_count/$requests 성공 ($success_rate%)"
        return 0
    else
        log_warning "로드 테스트 경고: $success_count/$requests 성공 ($success_rate%)"
        return 1
    fi
}

# 메인 헬스체크 함수
run_comprehensive_health_check() {
    local target_env=${1:-$(get_active_environment)}
    
    echo "===========================================" 
    log "🏥 종합 헬스체크 시작 (대상 환경: $target_env)"
    echo "==========================================="
    
    local overall_status=true
    
    # 1. 기본 인프라 확인
    echo
    log "📋 1단계: 기본 인프라 확인"
    if ! check_database_connectivity; then
        overall_status=false
    fi
    
    # 2. 환경별 서비스 확인
    echo
    log "📋 2단계: $target_env 환경 서비스 확인"
    if ! check_environment_health "$target_env"; then
        overall_status=false
    fi
    
    # 3. 외부 접근성 확인
    echo
    log "📋 3단계: 외부 접근성 확인"
    check_external_access
    
    # 4. 간단한 로드 테스트
    echo
    log "📋 4단계: 간단한 로드 테스트"
    simple_load_test "/health" 5
    
    # 최종 결과
    echo
    echo "==========================================="
    if [ "$overall_status" = true ]; then
        log_success "🎉 종합 헬스체크 통과!"
        echo "✅ 모든 핵심 서비스가 정상적으로 작동 중입니다."
    else
        log_error "❌ 종합 헬스체크 실패!"
        echo "⚠️ 일부 서비스에 문제가 있습니다. 로그를 확인해주세요."
    fi
    echo "==========================================="
    
    return $([ "$overall_status" = true ] && echo 0 || echo 1)
}

# 사용법 출력
usage() {
    echo "사용법: $0 [환경] [옵션]"
    echo "  환경: blue, green, active (기본값: active)"
    echo "  옵션:"
    echo "    --no-external  외부 접근성 확인 건너뛰기"
    echo "    --timeout N    타임아웃 설정 (기본값: 30초)"
    echo "    --retries N    재시도 횟수 (기본값: 3회)"
    echo
    echo "예시:"
    echo "  $0                    # 현재 활성 환경 확인"
    echo "  $0 blue              # Blue 환경 확인"
    echo "  $0 green --no-external  # Green 환경 확인 (외부 접근 제외)"
}

# 명령행 인수 처리
while [[ $# -gt 0 ]]; do
    case $1 in
        --no-external)
            CHECK_EXTERNAL=false
            shift
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --retries)
            RETRIES="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        blue|green|active)
            TARGET_ENV="$1"
            shift
            ;;
        *)
            echo "알 수 없는 옵션: $1"
            usage
            exit 1
            ;;
    esac
done

# 실행
if [ "${TARGET_ENV:-active}" = "active" ]; then
    run_comprehensive_health_check
else
    run_comprehensive_health_check "$TARGET_ENV"
fi
