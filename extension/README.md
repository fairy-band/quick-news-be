# Newsletter Content Saver - Chrome Extension

현재 웹 페이지를 뉴스레터 콘텐츠로 저장하는 크롬 확장 프로그램입니다.

## 기능

- 현재 페이지의 제목, URL, 본문을 자동으로 추출
- API를 통해 콘텐츠 저장
- API URL과 Access Token 저장 기능

## 설치 방법

1. Chrome 브라우저에서 `chrome://extensions/` 접속
2. 우측 상단의 "개발자 모드" 활성화
3. "압축해제된 확장 프로그램을 로드합니다" 클릭
4. 이 `extension` 폴더 선택

## 사용 방법

1. 저장하고 싶은 뉴스레터 페이지로 이동
2. 확장 프로그램 아이콘 클릭
3. 처음 사용 시 Access Token 입력 (한 번만 입력하면 저장됨)
4. "페이지 정보 가져오기" 버튼 클릭하여 제목, URL, 본문 자동 추출
5. 콘텐츠 제공자 이름 입력
6. 필요시 제목, 본문, 발행 날짜 수정 (본문은 직접 편집 가능)
7. "저장" 버튼 클릭

## API 설정

- API URL: `http://152.69.225.128` (하드코딩됨)
- 엔드포인트: `POST /api/newsletters/contents`

### 요청 헤더
- `Content-Type: application/json`
- `Access-Token: {your-access-token}`

### 요청 본문
```json
{
  "title": "콘텐츠 제목",
  "content": "콘텐츠 본문",
  "contentProviderName": "제공자 이름",
  "originalUrl": "https://example.com/article",
  "publishedAt": "2024-03-21"
}
```

## 아이콘 추가

`extension/icons/` 폴더에 다음 크기의 아이콘을 추가하세요:
- icon16.png (16x16)
- icon48.png (48x48)
- icon128.png (128x128)

아이콘이 없어도 확장 프로그램은 작동하지만, 기본 아이콘이 표시됩니다.
