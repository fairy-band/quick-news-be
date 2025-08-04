# Firebase 빠른 설정 가이드

## 1단계: JSON 파일 변환

다운로드받은 `firebase-service-account.json` 파일을 열어서 **모든 내용을 한 줄로** 복사하세요.

### 방법 1: 수동 변환
1. JSON 파일을 텍스트 에디터로 열기
2. 모든 줄바꿈과 불필요한 공백 제거
3. 한 줄로 만들기

### 방법 2: 온라인 도구 사용
- https://jsonformatter.org/json-minify 에서 JSON을 한 줄로 변환

### 방법 3: 명령어 사용 (터미널에서)
```bash
# jq 설치되어 있다면
cat firebase-service-account.json | jq -c .

# 또는 간단하게
cat firebase-service-account.json | tr -d '\n\t ' | sed 's/  */ /g'
```

## 2단계: 환경 변수 설정

변환된 JSON 문자열을 다음 명령어에 넣어서 실행:

```bash
# 환경 변수 설정 (JSON 문자열을 여기에 붙여넣기)
export FIREBASE_SERVICE_ACCOUNT_KEY='{"type":"service_account","project_id":"news-letter-da24c",...전체내용...}'
export FIREBASE_PROJECT_ID='news-letter-da24c'

# 확인
echo $FIREBASE_PROJECT_ID
```

## 3단계: .env 파일 생성 (권장)

프로젝트 루트에 `.env` 파일 생성:

```bash
# .env 파일 내용
FIREBASE_SERVICE_ACCOUNT_KEY={"type":"service_account","project_id":"news-letter-da24c",...전체내용...}
FIREBASE_PROJECT_ID=news-letter-da24c
```

## 4단계: 애플리케이션 실행

```bash
# .env 파일 사용하여 실행
set -a && source .env && set +a
./gradlew bootRun

# 또는 환경 변수 직접 설정 후 실행
./gradlew bootRun
```

## 5단계: 확인

애플리케이션 로그에서 다음 메시지 확인:
```
INFO  c.n.a.config.FirebaseConfig - Firebase 초기화 중...
INFO  c.n.a.config.FirebaseConfig - Firebase Admin SDK 초기화 완료 - 프로젝트: news-letter-da24c
```

## 예시

### JSON 파일 원본:
```json
{
  "type": "service_account",
  "project_id": "news-letter-da24c",
  "private_key_id": "abc123",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQ...\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-ftbvc@news-letter-da24c.iam.gserviceaccount.com",
  ...
}
```

### 변환 후 (한 줄):
```json
{"type":"service_account","project_id":"news-letter-da24c","private_key_id":"abc123","private_key":"-----BEGIN PRIVATE KEY-----\nMIIEvQ...\n-----END PRIVATE KEY-----\n","client_email":"firebase-adminsdk-ftbvc@news-letter-da24c.iam.gserviceaccount.com",...}
```

### 최종 환경 변수:
```bash
export FIREBASE_SERVICE_ACCOUNT_KEY='{"type":"service_account","project_id":"news-letter-da24c","private_key_id":"abc123","private_key":"-----BEGIN PRIVATE KEY-----\nMIIEvQ...\n-----END PRIVATE KEY-----\n","client_email":"firebase-adminsdk-ftbvc@news-letter-da24c.iam.gserviceaccount.com",...}'
```

## 주의사항

- JSON 문자열에 작은따옴표(')가 있으면 이스케이프 처리 필요
- 큰따옴표는 그대로 유지
- 줄바꿈 문자(\n)는 그대로 유지
- .env 파일은 절대 Git에 커밋하지 말 것!
