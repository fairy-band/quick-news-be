# Newsletter Feeder

Spring Boot + Spring Integration을 사용하여 POP3 프로토콜로 Gmail에서 메일을 읽어오는 멀티 모듈 애플리케이션입니다.

## 프로젝트 구조

```
newsletter-feeder/
├── api/                                    # REST API 모듈
│   └── src/main/kotlin/com/nexters/api/
│       └── ApiApplication.kt
├── batch/                                  # 메일 처리 배치 모듈
│   └── src/main/kotlin/com/nexters/newsletterfeeder/
│       ├── NewsletterFeederApplication.kt  # 메인 애플리케이션
│       ├── config/                         # 설정 클래스
│       │   ├── MailChannelConfig.kt       # Spring Integration 설정
│       │   └── MailProperties.kt          # 메일 속성 설정
│       ├── controller/                     # REST API 컨트롤러
│       │   └── MailController.kt          # 메일 API 엔드포인트
│       ├── dto/                           # 데이터 전송 객체
│       │   ├── EmailMessage.kt            # 이메일 메시지 Sealed Class
│       │   └── EmailCharset.kt            # 이메일 문자 인코딩 처리
│       ├── scheduler/                      # 스케줄러
│       │   └── ScheduledMailReader.kt     # 메일 읽기 스케줄러
│       └── service/                        # 비즈니스 로직
│           ├── MailReader.kt              # 메일 읽기 서비스
│           ├── MailProcessor.kt           # 메일 처리 서비스
│           └── MailChannelHandler.kt      # 메일 채널 핸들러
├── build.gradle.kts                        # 루트 프로젝트 빌드 설정
├── settings.gradle.kts                     # 멀티 모듈 설정
└── README.md
```

## 기술 스택

- **Framework**: Spring Boot 3.2.0
- **Language**: Kotlin
- **Build Tool**: Gradle (Kotlin DSL)
- **Integration**: Spring Integration
- **Mail Protocol**: POP3S (SSL)
- **JDK**: Java 17
- **Architecture**: Multi-module Gradle Project

## 주요 기능

### 📧 메일 처리
- **자동 메일 읽기**: 매일 아침 8시에 자동으로 Gmail에서 메일 읽기
- **다양한 콘텐츠 지원**: Plain Text, HTML, 첨부파일 처리
- **안전한 타입 처리**: Sealed Class를 사용한 EmailMessage 타입 안전성
- **문자 인코딩 지원**: 다양한 문자 인코딩 자동 감지 및 처리

### 🔄 Spring Integration
- **메일 채널**: Spring Integration을 통한 메일 처리 파이프라인
- **Message Source**: POP3 기반 메일 소스 설정
- **Service Activator**: 메일 메시지 자동 처리

### 🌐 REST API
- **상태 확인**: 서비스 상태 모니터링
- **수동 메일 읽기**: API를 통한 수동 메일 읽기 트리거

## 핵심 컴포넌트

### EmailMessage (Sealed Class)
다양한 메일 콘텐츠 타입을 안전하게 처리하는 Sealed Class:

```kotlin
sealed class EmailMessage {
    data class StringContent(...)      // 일반 텍스트 메일
    data class MultipartContent(...)   // 첨부파일이 있는 메일
    data class StreamContent(...)      // 스트림 콘텐츠 메일
    data class UnknownContent(...)     // 알 수 없는 형식의 메일
}
```

### MailReader
메일 읽기 및 Spring Integration 채널로 전송하는 핵심 서비스:

```kotlin
@Service
class MailReader(
    val mailMessageSource: MessageSource<*>,
    val mailChannel: MessageChannel
) {
    fun read() {
        // 메일 읽기 → EmailMessage 변환 → 채널 전송
    }
}
```

### 스케줄러
매일 정해진 시간에 메일을 자동으로 읽는 스케줄러:

```kotlin
@Component
class ScheduledMailReader {
    @Scheduled(cron = "0 0 8 * * *")  // 매일 아침 8시
    fun triggerMorningSchedule() {
        mailReader.read()
    }
}
```

## 설정

### 메일 설정

`batch/src/main/resources/application.yml` 파일:

```yaml
spring:
  application:
    name: newsletter-feeder-batch
  mail:
    host: pop.gmail.com
    port: 995
    username: your-email@gmail.com
    password: your-app-password
    protocol: pop3s
    properties:
      mail:
        pop3s:
          ssl:
            enable: true
          socketFactory:
            class: javax.net.ssl.SSLSocketFactory
            fallback: false
            port: 995
```

### Gmail 설정 필요사항

1. **2단계 인증 활성화**
   - [Google 계정 관리](https://myaccount.google.com/) > 보안 > 2단계 인증

2. **앱 비밀번호 생성**
   - 보안 > 앱 비밀번호 > 메일 > 기타 > "Newsletter Feeder"
   - 생성된 16자리 비밀번호를 `password` 값으로 설정

3. **POP3 활성화**
   - Gmail > 설정 > 전달 및 POP/IMAP > POP 다운로드 활성화

## 실행 방법

### 1. 전체 프로젝트 빌드
```bash
./gradlew build
```

### 2. 배치 모듈 실행
```bash
./gradlew :batch:bootRun
```

### 3. API 모듈 실행
```bash
./gradlew :api:bootRun
```

### 4. 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 배치 모듈 테스트만
./gradlew :batch:test
```

## API 엔드포인트

배치 모듈 실행 시 다음 엔드포인트 제공:

- **상태 확인**: `GET http://localhost:8080/api/mail/status`
- **수동 메일 읽기**: `GET http://localhost:8080/api/mail/read`

## 로그 확인

메일 처리 시 다음과 같은 로그가 출력됩니다:

```
08:00:00.000 [scheduling-1] INFO  c.n.n.scheduler.ScheduledMailReader - Starting scheduled email reading at 8:00 AM
08:00:00.100 [scheduling-1] INFO  c.n.n.service.MailReader - Checking for new emails...
08:00:00.200 [scheduling-1] INFO  c.n.n.service.MailReader - Found new email message  
08:00:00.300 [scheduling-1] INFO  c.n.n.service.MailProcessor - Processing email from: sender@example.com
08:00:00.400 [scheduling-1] INFO  c.n.n.service.MailProcessor - Subject: Newsletter Subject
```

## 테스트

### 단위 테스트
모킹 없이 작성된 Kotlin다운 테스트:

```kotlin
@Test
fun `should successfully read and process email message when new email exists`() {
    // given: 테스트용 MimeMessage 생성
    val mimeMessage = MimeMessage(session).apply {
        setFrom(InternetAddress("sender@example.com"))
        subject = "Test Newsletter"
        setText("This is a test newsletter content")
    }
    
    // when: 메일 읽기 실행
    mailReader.read()
    
    // then: Kotlin스러운 검증
    val capturedMessage = requireNotNull(testMessageCaptor.getCapturedMessage()) {
        "No message was captured"
    }
    
    val emailMessage = capturedMessage.payload as? EmailMessage
        ?: error("Expected EmailMessage but got ${capturedMessage.payload?.javaClass?.simpleName}")
    
    with(emailMessage) {
        assertEquals(listOf("sender@example.com"), from)
        assertEquals("Test Newsletter", subject)
        assertEquals("This is a test newsletter content", extractedContent)
        assertNotNull(sentDate)
    }
}
```

## 확장 가능한 기능

- 📊 **데이터베이스 연동**: 메일 내용을 데이터베이스에 저장
- 🔍 **키워드 필터링**: 특정 키워드 기반 메일 분류
- 📈 **메일 분석**: 뉴스레터 내용 분석 및 통계
- 🔔 **알림 시스템**: 중요한 메일 도착 시 알림
- 🔄 **웹훅 연동**: 외부 시스템과의 연동
- 📎 **첨부파일 처리**: 첨부파일 자동 다운로드 및 처리

## 주의사항

- **앱 비밀번호**: Gmail 2단계 인증 활성화 후 앱 비밀번호 사용 필수
- **메일 삭제 방지**: `setShouldDeleteMessages(false)` 설정으로 메일 보존
- **연결 제한**: 너무 자주 메일을 읽으면 Gmail에서 연결 제한 가능
- **SSL 인증서**: POP3S 연결 시 SSL 인증서 자동 신뢰 설정 적용

## 개발 가이드

### 새로운 메일 처리 로직 추가
1. `MailProcessor`에 새로운 처리 로직 추가
2. `EmailMessage` 타입에 따른 분기 처리
3. 필요시 새로운 `EmailMessage` 하위 클래스 생성

### 새로운 스케줄러 추가
1. `ScheduledMailReader`에 새로운 `@Scheduled` 메서드 추가
2. Cron 표현식으로 실행 시간 설정

### 테스트 작성
- 모킹 없이 실제 구현체를 사용한 테스트 작성
- Kotlin스러운 검증 방식 사용 (`requireNotNull`, `with`, `as?`)
- 해피패스 중심의 테스트 케이스 작성
