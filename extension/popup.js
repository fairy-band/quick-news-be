// API URL 하드코딩
const API_URL = 'http://152.69.225.128';

// ContentProvider 목록 저장
let contentProviders = [];

// 설정 로드
async function loadSettings() {
  const result = await chrome.storage.local.get(['accessToken']);
  if (result.accessToken) {
    document.getElementById('accessToken').value = result.accessToken;
    // 토큰이 있으면 ContentProvider 목록 로드
    await loadContentProviders();
  }
}

// ContentProvider 목록 로드
async function loadContentProviders() {
  try {
    const accessToken = document.getElementById('accessToken').value.trim();
    if (!accessToken) {
      return;
    }

    const response = await fetch(`${API_URL}/api/newsletters/content-providers`, {
      method: 'GET',
      headers: {
        'Access-Token': accessToken
      }
    });

    if (!response.ok) {
      throw new Error(`Failed to load content providers: ${response.status}`);
    }

    contentProviders = await response.json();
    
    const selectEl = document.getElementById('contentProviderSelect');
    selectEl.innerHTML = '<option value="">-- 선택하세요 --</option>';
    
    contentProviders.forEach(provider => {
      const option = document.createElement('option');
      option.value = provider.name;
      option.textContent = `${provider.name} (${provider.type || 'N/A'})`;
      option.dataset.type = provider.type || '';
      selectEl.appendChild(option);
    });
  } catch (error) {
    console.error('Error loading content providers:', error);
    const selectEl = document.getElementById('contentProviderSelect');
    selectEl.innerHTML = '<option value="">로드 실패</option>';
  }
}

// 설정 저장
async function saveSettings() {
  const accessToken = document.getElementById('accessToken').value;
  await chrome.storage.local.set({ accessToken });
}

// 상태 메시지 표시
function showStatus(message, type = 'info') {
  const statusEl = document.getElementById('status');
  statusEl.textContent = message;
  statusEl.className = `status ${type}`;
  statusEl.style.display = 'block';
  
  if (type === 'success' || type === 'error') {
    setTimeout(() => {
      statusEl.style.display = 'none';
    }, 5000);
  }
}

// 페이지 정보 가져오기
async function loadPageInfo() {
  try {
    showStatus('페이지 정보를 가져오는 중...', 'info');
    
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: extractPageContent
    });
    
    if (results && results[0] && results[0].result) {
      const pageData = results[0].result;
      
      document.getElementById('title').value = pageData.title || '';
      document.getElementById('url').value = pageData.url || '';
      document.getElementById('imageUrl').value = pageData.imageUrl || '';
      document.getElementById('content').value = pageData.content || '';
      
      // 오늘 날짜를 기본값으로 설정
      const today = new Date().toISOString().split('T')[0];
      document.getElementById('publishedAt').value = today;
      
      showStatus('페이지 정보를 가져왔습니다', 'success');
    }
  } catch (error) {
    console.error('Error loading page info:', error);
    showStatus('페이지 정보를 가져오는데 실패했습니다', 'error');
  }
}

// 페이지에서 실행될 함수 (콘텐츠 추출)
function extractPageContent() {
  // 메타 태그에서 정보 추출
  const getMetaContent = (name) => {
    const meta = document.querySelector(`meta[name="${name}"], meta[property="${name}"]`);
    return meta ? meta.content : '';
  };
  
  // 상대 경로를 절대 경로로 변환
  const toAbsoluteUrl = (url) => {
    if (!url) return '';
    try {
      return new URL(url, window.location.href).href;
    } catch (e) {
      return url;
    }
  };
  
  // 제목 추출
  const title = document.title || getMetaContent('og:title') || getMetaContent('twitter:title');
  
  // 이미지 URL 추출 및 절대 경로로 변환
  const rawImageUrl = getMetaContent('og:image') || getMetaContent('twitter:image') || '';
  const imageUrl = toAbsoluteUrl(rawImageUrl);
  
  // 본문 추출 - 여러 선택자를 시도하여 가장 적합한 콘텐츠 찾기
  let content = '';
  
  // 1. article 태그 시도
  const article = document.querySelector('article');
  if (article && article.innerText.length > 100) {
    content = article.innerText;
  }
  
  // 2. main 태그 시도
  if (!content) {
    const main = document.querySelector('main');
    if (main && main.innerText.length > 100) {
      content = main.innerText;
    }
  }
  
  // 3. role="main" 속성을 가진 요소 시도
  if (!content) {
    const roleMain = document.querySelector('[role="main"]');
    if (roleMain && roleMain.innerText.length > 100) {
      content = roleMain.innerText;
    }
  }
  
  // 4. 일반적인 콘텐츠 클래스명 시도
  if (!content) {
    const contentSelectors = [
      '.post-content',
      '.article-content',
      '.entry-content',
      '.content',
      '#content',
      '.post-body',
      '.article-body'
    ];
    
    for (const selector of contentSelectors) {
      const element = document.querySelector(selector);
      if (element && element.innerText.length > 100) {
        content = element.innerText;
        break;
      }
    }
  }
  
  // 5. 마지막 수단으로 body 사용
  if (!content) {
    content = document.body.innerText;
  }
  
  // 불필요한 공백 정리
  content = content
    .replace(/\n{3,}/g, '\n\n')  // 3개 이상의 연속 줄바꿈을 2개로
    .trim();
  
  // 너무 긴 콘텐츠는 잘라내기 (100000자 제한 - 약 100KB)
  if (content.length > 100000) {
    content = content.substring(0, 100000) + '\n\n[콘텐츠가 너무 길어 잘렸습니다]';
  }
  
  console.log('Extracted content length:', content.length);
  
  return {
    title: title.trim(),
    content: content,
    url: window.location.href,
    imageUrl: imageUrl.trim()
  };
}

// 콘텐츠 저장
async function saveContent() {
  try {
    await saveSettings();
    
    const accessToken = document.getElementById('accessToken').value.trim();
    const title = document.getElementById('title').value.trim();
    const useCustomProvider = document.getElementById('useCustomProvider').checked;
    const contentProviderName = useCustomProvider 
      ? document.getElementById('contentProviderName').value.trim()
      : document.getElementById('contentProviderSelect').value.trim();
    const contentProviderType = document.getElementById('contentProviderType').value;
    const url = document.getElementById('url').value.trim();
    const imageUrl = document.getElementById('imageUrl').value.trim();
    const content = document.getElementById('content').value.trim();
    const publishedAt = document.getElementById('publishedAt').value;
    
    // 유효성 검사
    if (!accessToken) {
      showStatus('Access Token을 입력하세요', 'error');
      return;
    }
    if (!title) {
      showStatus('제목을 입력하세요', 'error');
      return;
    }
    if (!contentProviderName) {
      showStatus('콘텐츠 제공자를 선택하거나 입력하세요', 'error');
      return;
    }
    if (!content) {
      showStatus('본문 내용이 없습니다. "페이지 정보 가져오기"를 먼저 클릭하세요', 'error');
      return;
    }
    if (!publishedAt) {
      showStatus('발행 날짜를 입력하세요', 'error');
      return;
    }
    
    showStatus('콘텐츠를 저장하는 중...', 'info');
    
    // API 요청 바디 구성
    const requestBody = {
      title: title,
      content: content,
      contentProviderName: contentProviderName,
      contentProviderType: contentProviderType,
      originalUrl: url,
      publishedAt: publishedAt
    };
    
    // 이미지 URL이 있으면 추가
    if (imageUrl) {
      requestBody.imageUrl = imageUrl;
    }
    
    // API 요청
    const response = await fetch(`${API_URL}/api/newsletters/contents`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Access-Token': accessToken
      },
      body: JSON.stringify(requestBody)
    });
    
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`API 요청 실패: ${response.status} - ${errorText}`);
    }
    
    const result = await response.json();
    showStatus(`콘텐츠가 저장되었습니다 (ID: ${result.id})`, 'success');
    
    // 폼 초기화 (선택사항)
    // document.getElementById('content').value = '';
    
  } catch (error) {
    console.error('Error saving content:', error);
    showStatus(`저장 실패: ${error.message}`, 'error');
  }
}

// 이벤트 리스너
document.addEventListener('DOMContentLoaded', async () => {
  await loadSettings();
  await loadPageInfo();
  
  // ContentProvider 선택 변경 시 타입 자동 설정
  document.getElementById('contentProviderSelect').addEventListener('change', (e) => {
    const selectedOption = e.target.options[e.target.selectedIndex];
    const type = selectedOption.dataset.type;
    if (type) {
      document.getElementById('contentProviderType').value = type;
    }
  });
  
  // 커스텀 제공자 체크박스 토글
  document.getElementById('useCustomProvider').addEventListener('change', (e) => {
    const customInput = document.getElementById('contentProviderName');
    const selectEl = document.getElementById('contentProviderSelect');
    
    if (e.target.checked) {
      customInput.style.display = 'block';
      selectEl.style.display = 'none';
    } else {
      customInput.style.display = 'none';
      selectEl.style.display = 'block';
    }
  });
  
  // Access Token 변경 시 ContentProvider 목록 다시 로드
  document.getElementById('accessToken').addEventListener('blur', async () => {
    await loadContentProviders();
  });
  
  document.getElementById('loadPageInfo').addEventListener('click', loadPageInfo);
  document.getElementById('saveContent').addEventListener('click', saveContent);
});
