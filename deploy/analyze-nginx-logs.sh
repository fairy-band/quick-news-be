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
grep -o 'rt=[0-9.]*' "$LOG_FILE" | cut -d= -f2 | sort -n | awk '
BEGIN {
    count=0; sum=0; max=0; min=999999;
}
{
    if ($1 != "" && $1 != "-") {
        count++;
        sum+=$1;
        if ($1 > max) max=$1;
        if ($1 < min) min=$1;
        
        # 배열에 저장 (중앙값 계산용)
        values[count] = $1;
    }
}
END {
    if (count > 0) {
        avg=sum/count;
        
        # 중앙값 (이미 정렬됨)
        mid = int(count/2);
        if (count % 2 == 1) {
            median = values[mid+1];
        } else {
            median = (values[mid] + values[mid+1]) / 2;
        }
        
        # 백분위수
        p95_idx = int(count * 0.95);
        if (p95_idx < 1) p95_idx = 1;
        p99_idx = int(count * 0.99);
        if (p99_idx < 1) p99_idx = 1;

        printf "요청 수: %d\n", count;
        printf "평균 응답 시간: %.3f초\n", avg;
        printf "최소 응답 시간: %.3f초\n", min;
        printf "최대 응답 시간: %.3f초\n", max;
        printf "중앙값: %.3f초\n", median;
        printf "95 백분위수: %.3f초\n", values[p95_idx];
        printf "99 백분위수: %.3f초\n", values[p99_idx];
    } else {
        print "응답 시간 데이터가 없습니다.";
    }
}'
echo ""

# 5. 느린 API 요청 (3초 이상)
echo "🐌 느린 API 요청 (3초 이상)"
echo "------------------------------------------"
grep 'rt=[0-9.]*' "$LOG_FILE" | awk '
{
    # rt= 값 추출
    for (i=1; i<=NF; i++) {
        if ($i ~ /^rt=/) {
            split($i, arr, "=");
            rt = arr[2];
            if (rt >= 3.0) {
                # 요청 라인 추출 (따옴표 사이)
                request = "";
                in_quote = 0;
                for (j=1; j<=NF; j++) {
                    if ($j ~ /^"/ && $j !~ /"$/) {
                        in_quote = 1;
                        request = substr($j, 2);
                    } else if (in_quote && $j ~ /"$/) {
                        request = request " " substr($j, 1, length($j)-1);
                        break;
                    } else if (in_quote) {
                        request = request " " $j;
                    } else if ($j ~ /^".*"$/) {
                        request = substr($j, 2, length($j)-2);
                        break;
                    }
                }
                
                # 타임스탬프 추출
                timestamp = $4;
                gsub(/\[/, "", timestamp);
                
                printf "%.3f초 - \"%s\" [%s]\n", rt, request, timestamp;
            }
        }
    }
}
' | sort -rn | head -20
echo ""

# 6. 업스트림 응답 시간 통계 (upstream_response_time)
echo "🔄 업스트림(백엔드) 응답 시간 통계"
echo "------------------------------------------"
grep -o 'urt="[0-9.]*"' "$LOG_FILE" | cut -d'"' -f2 | awk '
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
