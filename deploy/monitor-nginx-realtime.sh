#!/bin/bash

# nginx 실시간 로그 모니터링 스크립트
# API 응답 속도를 실시간으로 확인

LOG_FILE="${1:-./logs/nginx/access.log}"

if [ ! -f "$LOG_FILE" ]; then
    echo "로그 파일을 찾을 수 없습니다: $LOG_FILE"
    echo "사용법: $0 [로그파일경로]"
    exit 1
fi

echo "=========================================="
echo "Nginx 실시간 로그 모니터링"
echo "로그 파일: $LOG_FILE"
echo "=========================================="
echo ""
echo "형식: [시간] 상태코드 응답시간 메서드 경로"
echo ""

# 실시간 로그 모니터링 (응답 시간 강조)
tail -f "$LOG_FILE" | awk '
{
    # 시간 추출
    time = $4;
    gsub(/\[/, "", time);

    # HTTP 메서드와 경로 추출
    method = $6;
    gsub(/"/, "", method);
    path = $7;

    # 상태 코드
    status = $9;

    # 응답 시간 추출 (rt=)
    for (i=1; i<=NF; i++) {
        if ($i ~ /^rt=/) {
            split($i, arr, "=");
            response_time = arr[2];
            break;
        }
    }

    # 색상 코드 (응답 시간에 따라)
    color = "";
    if (response_time+0 >= 3.0) {
        color = "🔴"; # 3초 이상 - 매우 느림
    } else if (response_time+0 >= 1.0) {
        color = "🟡"; # 1초 이상 - 느림
    } else if (response_time+0 >= 0.5) {
        color = "🟢"; # 0.5초 이상 - 보통
    } else {
        color = "⚡"; # 0.5초 미만 - 빠름
    }

    # 상태 코드 색상
    status_icon = "";
    if (status >= 500) {
        status_icon = "❌";
    } else if (status >= 400) {
        status_icon = "⚠️ ";
    } else if (status >= 300) {
        status_icon = "↪️ ";
    } else if (status >= 200) {
        status_icon = "✅";
    }

    printf "%s %s %s %6.3fs %s %s\n", color, status_icon, time, response_time, method, path;
    fflush();
}
'
