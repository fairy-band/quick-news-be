# Firebase Cloud Messaging (FCM) 설정 가이드

## 1. Firebase 설정 파일 준비

### 서버용 서비스 계정 키
1. Firebase Console에서 다운로드받은 서비스 계정 키 파일을 다음 경로에 복사:
   ```
   api/src/main/resources/firebase-service-account.json
   ```

### 앱 개발자용 설정 파일
- **Android**: `google-services.json`
- **iOS**: `GoogleService-Info.plist`

## 2. API 엔드포인트

### FCM 토큰 관리
- `POST /api/notifications/token` - 토큰 등록
- `DELETE /api/notifications/token?deviceToken={token}` - 토큰 해제
- `DELETE /api/notifications/token/user/{userId}` - 사용자의 모든 토큰 해제

### 알림 발송 (테스트용)
- `POST /api/notifications/send/{userId}` - 특정 사용자에게 발송
- `POST /api/notifications/send/token/{token}` - 특정 토큰으로 발송

## 3. 요청/응답 예시

### 토큰 등록
```json
POST /api/notifications/token
{
  "userId": 1,
  "deviceToken": "fcm-token-string",
  "deviceType": "ANDROID"
}
```

### 알림 발송
```json
POST /api/notifications/send/1
{
  "title": "뉴스레터 알림",
  "body": "새로운 콘텐츠가 도착했습니다!",
  "data": {
    "contentId": "123",
    "type": "newsletter"
  }
}
```

## 4. 데이터베이스 테이블

### fcm_tokens 테이블
```sql
CREATE TABLE fcm_tokens (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_token VARCHAR(500) NOT NULL,
    device_type VARCHAR(20) NOT NULL CHECK (device_type IN ('ANDROID', 'IOS')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_device_token UNIQUE (user_id, device_token)
);
```

## 5. 주요 기능

### 자동 토큰 관리
- 유효하지 않은 토큰 자동 비활성화
- 중복 토큰 등록 방지
- 사용자별 멀티 디바이스 지원

### 오류 처리
- FCM API 오류 로깅
- 토큰 유효성 검사
- 재시도 로직 (필요시 추가 구현 가능)

## 6. 보안 고려사항

- Firebase 서비스 계정 키는 절대 Git에 커밋하지 말 것
- `.gitignore`에 `firebase-service-account.json` 추가
- 프로덕션 환경에서는 환경 변수 또는 시크릿 관리 시스템 사용 권장

## 7. 모니터링

- 알림 발송 성공/실패 로그 확인
- 비활성화된 토큰 주기적 정리
- Firebase Console에서 알림 통계 확인
