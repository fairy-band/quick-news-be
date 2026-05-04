#!/bin/bash

# nginx 로그 분석 스크립트
# API 응답 속도 및 통계 확인

LOG_FILE="${1:-./logs/nginx/access.log}"

if [ ! -f "$LOG_FILE" ]; then
    echo "로그 파일을 찾을 수 없습니다: $LOG_FILE"
    echo "사용법: $0 [로그파일경로]"
    exit 1
fi

echo "=========================================="
echo "Nginx 로그 분석 리포트"
echo "로그 파일: $LOG_FILE"
echo "=========================================="
echo ""

# 1. 전체 요청 수
echo "📊 전체 요청 통계"
echo "------------------------------------------"
TOTAL_REQUESTS=$(wc -l < "$LOG_FILE")
echo "총 요청 수: $TOTAL_REQUESTS"
echo ""

# 2. HTTP 상태 코드별 통계
echo "📈 HTTP 상태 코드 분포"
echo "------------------------------------------"
awk '{print $9}' "$LOG_FILE" | sort | uniq -c | sort -rn | head -10
echo ""

# 3. 가장 많이 호출된 API 엔드포인트 (Top 10)
echo "🔥 가장 많이 호출된 API (Top 10)"
echo "------------------------------------------"
awk '{print $7}' "$LOG_FILE" | grep "^/api/" | sort | uniq -c | sort -rn | head -10
echo ""

# 4. 응답 시간 통계 (request_time)
echo "⏱️  API 응답 시간 통계"
echo "------------------------------------------"
awk -F'rt=' '{if (NF>1) print $2}' "$LOG_FILE" | awk '{print $1}' | \
awk '
BEGIN {
    count=0; sum=0; max=0; min=999999;
}
{
    if ($1 != "" && $1 != "-") {
        count++;
        sum+=$1;
        if ($1 > max) max=$1;
        if ($1 < min) min=$1;
        times[count]=$1;
    }
}
END {
    if (count > 0) {
        avg=sum/count;
        # 정렬하여 중앙값 계산
        asort(times);
        if (count % 2 == 1) {
            median = times[int(count/2)+1];
        } else {
            median = (times[count/2] + times[count/2+1]) / 2;
        }
        p95_idx = int(count * 0.95);
        p99_idx = int(count * 0.99);

        printf "요청 수: %d\n", count;
        printf "평균 응답 시간: %.3f초\n", avg;
        printf "최소 응답 시간: %.3f초\n", min;
        printf "최대 응답 시간: %.3f초\n", max;
        printf "중앙값: %.3f초\n", median;
        printf "95 백분위수: %.3f초\n", times[p95_idx];
        printf "99 백분위수: %.3f초\n", times[p99_idx];
    } else {
        print "응답 시간 데이터가 없습니다.";
    }
}'
echo ""

# 5. 느린 API 요청 (3초 이상)
echo "🐌 느린 API 요청 (3초 이상)"
echo "------------------------------------------"
awk -F'rt=' '{if (NF>1) {rt=$2; sub(/ .*/, "", rt); if (rt+0 >= 3.0) print}}' "$LOG_FILE" | \
awk '{printf "%.3f초 - %s %s [%s]\n", $(NF-3), $6, $7, $4}' | sort -rn | head -20
echo ""

# 6. 업스트림 응답 시간 통계 (upstream_response_time)
echo "🔄 업스트림(백엔드) 응답 시간 통계"
echo "------------------------------------------"
awk -F'urt=' '{if (NF>1) print $2}' "$LOG_FILE" | awk '{gsub(/"/, "", $1); print $1}' | \
awk '
BEGIN {
    count=0; sum=0; max=0; min=999999;
}
{
    if ($1 != "" && $1 != "-") {
        count++;
        sum+=$1;
        if ($1 > max) max=$1;
        if ($1 < min) min=$1;
    }
}
END {
    if (count > 0) {
        avg=sum/count;
        printf "요청 수: %d\n", count;
        printf "평균 업스트림 응답 시간: %.3f초\n", avg;
        printf "최소 업스트림 응답 시간: %.3f초\n", min;
        printf "최대 업스트림 응답 시간: %.3f초\n", max;
    } else {
        print "업스트림 응답 시간 데이터가 없습니다.";
    }
}'
echo ""

# 7. 시간대별 요청 분포
echo "🕐 시간대별 요청 분포"
echo "------------------------------------------"
awk '{print $4}' "$LOG_FILE" | cut -d: -f2 | sort | uniq -c | sort -k2n
echo ""

# 8. IP별 요청 수 (Top 10)
echo "🌐 IP별 요청 수 (Top 10)"
echo "------------------------------------------"
awk '{print $1}' "$LOG_FILE" | sort | uniq -c | sort -rn | head -10
echo ""

echo "=========================================="
echo "분석 완료!"
echo "=========================================="
