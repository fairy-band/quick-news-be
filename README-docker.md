# Docker Compose 사용 가이드

## 구성 파일

1. **docker-compose.mongodb.yml** - MongoDB와 Mongo Express만 실행
2. **docker-compose.yml** - API와 Batch 서비스만 실행 (외부 MongoDB 사용)
3. **docker-compose.all.yml** - 모든 서비스 실행 (MongoDB 포함)

## 사용 시나리오

### 1. 개발 환경 - MongoDB만 실행

```bash
# MongoDB와 Mongo Express만 실행
docker-compose -f docker-compose.mongodb.yml up -d

# 로그 확인
docker-compose -f docker-compose.mongodb.yml logs -f

# 중지
docker-compose -f docker-compose.mongodb.yml down
```

- MongoDB: `localhost:27017`
- Mongo Express: `http://localhost:8081`

### 2. 개발 환경 - 애플리케이션만 실행 (로컬 MongoDB 사용)

```bash
# .env 파일 생성 (필요시)
cp .env.example .env

# API와 Batch만 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f api
docker-compose logs -f batch
```

### 3. 전체 스택 실행

```bash
# 모든 서비스 실행
docker-compose -f docker-compose.all.yml up -d

# 상태 확인
docker-compose -f docker-compose.all.yml ps

# 로그 확인
docker-compose -f docker-compose.all.yml logs -f
```

### 4. 프로덕션 환경

```bash
# 프로덕션 환경 변수 설정
cp .env.example .env.prod
# .env.prod 파일 편집

# 외부 MongoDB 사용
docker-compose --env-file .env.prod up -d
```

## 환경 변수 설정

### 기본 .env 파일
```bash
# MongoDB 설정
MONGO_ROOT_USERNAME=admin
MONGO_ROOT_PASSWORD=password123
MONGO_DATABASE=newsletter
MONGO_USERNAME=newsletter
MONGO_PASSWORD=newsletter123

# 외부 MongoDB 사용 시
MONGODB_URI=mongodb://newsletter:newsletter123@localhost:27017/newsletter?authSource=newsletter

# 메일 설정
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

## 유용한 명령어

### 특정 서비스만 재시작
```bash
docker-compose restart api
docker-compose restart batch
```

### 빌드 후 실행
```bash
docker-compose up -d --build
```

### 볼륨 정리
```bash
# MongoDB 데이터 유지하면서 컨테이너만 삭제
docker-compose down

# MongoDB 데이터도 함께 삭제
docker-compose down -v
rm -rf data/mongodb data/mongodb-config
```

### Podman 사용 시
```bash
# MongoDB만
podman-compose -f docker-compose.mongodb.yml up -d

# 전체 스택
podman-compose -f docker-compose.all.yml up -d
```

## 개발 워크플로우

1. **백엔드 개발 시**
   ```bash
   # MongoDB만 실행
   docker-compose -f docker-compose.mongodb.yml up -d
   
   # IDE에서 Spring Boot 애플리케이션 실행
   ./gradlew :api:bootRun
   ./gradlew :batch:bootRun
   ```

2. **전체 통합 테스트**
   ```bash
   # 모든 서비스 실행
   docker-compose -f docker-compose.all.yml up -d
   ```

3. **프론트엔드 개발 시**
   ```bash
   # API 서비스만 필요한 경우
   docker-compose up -d api
   ``` 