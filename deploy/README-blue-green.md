# Blue-Green 배포 가이드

이 문서는 Newsletter Feeder 서비스의 Blue-Green 배포 시스템 사용법을 설명합니다.

## 🎯 개요

Blue-Green 배포는 서비스 무중단 배포를 위해 두 개의 동일한 환경(Blue/Green)을 운영하여 점진적으로 트래픽을 전환하는 배포 전략입니다.

### 장점
- **무중단 배포**: 서비스 중단 없이 배포 가능
- **빠른 롤백**: 문제 발생 시 즉시 이전 환경으로 전환
- **배포 검증**: 새 버전을 프로덕션에 적용하기 전에 충분히 테스트 가능
- **리스크 최소화**: 문제 발생 시 영향 범위 최소화

## 📁 파일 구조

```
deploy/
├── docker-compose-blue.yml      # Blue 환경 설정
├── docker-compose-green.yml     # Green 환경 설정
├── docker-compose-nginx.yml     # Nginx 전용 설정
├── nginx-blue-green.conf        # Blue-Green 지원 Nginx 설정
├── active-env.conf              # 현재 활성 환경 설정
├── deploy-blue-green.sh         # Blue-Green 배포 스크립트
├── switch-env.sh                # 환경 전환 유틸리티
├── health-check.sh              # 종합 헬스체크 스크립트
├── rollback.sh                  # 롤백 스크립트
└── README-blue-green.md         # 이 문서
```

## 🚀 배포 방법

### 1. GitHub Actions를 통한 자동 배포

#### 일반 푸시 (자동 배포)
```bash
git push origin main
```
- 기본적으로 `blue-green-gradual` 모드로 배포됩니다.

#### 수동 배포 (배포 전략 선택)
1. GitHub 저장소의 Actions 탭으로 이동
2. "Build and Push Docker Images" 워크플로우 선택
3. "Run workflow" 클릭
4. 배포 전략 선택:
   - `traditional`: 기존 방식 (서비스 중단 발생)
   - `blue-green-gradual`: 점진적 Blue-Green 배포 (권장)
   - `blue-green-immediate`: 즉시 Blue-Green 배포

### 2. 수동 배포

서버에 직접 접속하여 배포하는 경우:

```bash
# 서버 접속
ssh user@your-server

# 배포 디렉토리로 이동
cd ~/deploy

# 점진적 배포 (권장)
./deploy-blue-green.sh gradual

# 즉시 배포
./deploy-blue-green.sh immediate
```

## 🔄 환경 관리

### 현재 상태 확인
```bash
./switch-env.sh status
```

### 환경 전환
```bash
# Blue 환경으로 전환
./switch-env.sh blue

# Green 환경으로 전환
./switch-env.sh green
```

### 환경별 직접 접근 (테스트용)
- Blue 환경: `https://fairy-band.com/blue/api/health`
- Green 환경: `https://fairy-band.com/green/api/health`

## 🏥 헬스체크

### 종합 헬스체크 실행
```bash
# 현재 활성 환경 확인
./health-check.sh

# 특정 환경 확인
./health-check.sh blue
./health-check.sh green

# 외부 접근성 제외하고 확인
./health-check.sh --no-external

# 타임아웃 및 재시도 설정
./health-check.sh --timeout 60 --retries 5
```

### 헬스체크 항목
1. **기본 인프라**: PostgreSQL, MongoDB 연결
2. **서비스 상태**: API, Batch, Admin 서비스
3. **외부 접근성**: 도메인을 통한 외부 접근
4. **부하 테스트**: 간단한 로드 테스트

## 🔙 롤백

### 자동 롤백 (권장)
```bash
# 현재 환경의 반대 환경으로 자동 롤백
./rollback.sh --auto
```

### 특정 환경으로 롤백
```bash
# Blue 환경으로 롤백
./rollback.sh blue

# Green 환경으로 롤백
./rollback.sh green
```

### 응급 복구
```bash
# 사용 가능한 모든 환경을 시도하여 복구
./rollback.sh --emergency
```

## 📊 모니터링

### 배포 상태 확인
```bash
# 현재 배포 상태
./switch-env.sh status

# 롤백 상태 확인
./rollback.sh --status
```

### 로그 확인
```bash
# 각 환경의 로그 확인
docker logs newsletter-api-blue
docker logs newsletter-api-green

# 실시간 로그 모니터링
docker logs -f newsletter-api-blue
```

### 컨테이너 상태 확인
```bash
# Blue 환경 상태
docker compose -f docker-compose-blue.yml ps

# Green 환경 상태
docker compose -f docker-compose-green.yml ps
```

## ⚠️ 주의사항

### 배포 전 체크리스트
- [ ] 데이터베이스 마이그레이션이 완료되었는지 확인
- [ ] 환경 변수 설정이 올바른지 확인
- [ ] SSL 인증서가 유효한지 확인
- [ ] 충분한 디스크 공간이 있는지 확인

### 트러블슈팅

#### 1. 배포 실패 시
```bash
# 로그 확인
docker logs newsletter-api-blue --tail 50

# 헬스체크 실행
./health-check.sh blue

# 필요시 롤백
./rollback.sh --auto
```

#### 2. 환경 전환 실패 시
```bash
# Nginx 상태 확인
docker logs newsletter-nginx --tail 20

# 수동 nginx 리로드
docker exec newsletter-nginx nginx -s reload
```

#### 3. 데이터베이스 연결 문제
```bash
# PostgreSQL 상태 확인
docker exec newsletter-postgres pg_isready

# MongoDB 상태 확인
docker exec newsletter-mongodb mongosh --eval "db.adminCommand('ping')"
```

## 🔧 고급 설정

### 환경별 포트 매핑
- **Blue 환경**:
  - API: 8080
  - Batch: 8082
  - Admin: 8083

- **Green 환경**:
  - API: 8090
  - Batch: 8092
  - Admin: 8093

### Nginx 업스트림 설정
nginx는 `active-env.conf` 파일의 설정에 따라 트래픽을 라우팅합니다:
```nginx
set $active_env blue;  # 또는 green
```

### 배포 모드별 특징

#### Gradual 모드 (권장)
1. 비활성 환경에 새 버전 배포
2. 헬스체크 및 안정성 확인
3. 사용자 확인 후 트래픽 전환 (CI/CD에서는 자동)
4. 이전 환경 정리

#### Immediate 모드
1. 현재 환경 즉시 중단
2. 새 환경으로 즉시 전환
3. 더 빠르지만 위험성 높음

## 📞 지원

문제가 발생하거나 도움이 필요한 경우:
1. 로그 확인 및 헬스체크 실행
2. 이 문서의 트러블슈팅 섹션 참조
3. 필요시 롤백 실행
4. Discord 알림 확인

---

**💡 팁**: 처음 사용하는 경우 개발 환경에서 충분히 테스트한 후 프로덕션에 적용하시기 바랍니다.
