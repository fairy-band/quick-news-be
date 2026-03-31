// API URL 하드코딩
const API_URL = 'http://152.69.225.128';

// 설정 로드
async function loadSettings() {
  const result = await chrome.storage.local.get(['accessToken']);
  if (result.accessToken) {
    document.getElementById('accessToken').value = result.accessToken;
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
  
  // 제목 추출
  const title = document.title || getMetaContent('og:title') || getMetaContent('twitter:title');
  
  // 본문 추출 (article 태그 우선, 없으면 body의 텍스트)
  let content = '';
  const article = document.querySelector('article');
  if (article) {
    content = article.innerText;
  } else {
    const mainContent = document.querySelector('main') || document.body;
    content = mainContent.innerText;
  }
  
  // 너무 긴 콘텐츠는 잘라내기 (10000자 제한)
  if (content.length > 10000) {
    content = content.substring(0, 10000) + '...';
  }
  
  return {
    title: title.trim(),
    content: content.trim(),
    url: window.location.href
  };
}

// 콘텐츠 저장
async function saveContent() {
  try {
    await saveSettings();
    
    const accessToken = document.getElementById('accessToken').value.trim();
    const title = document.getElementById('title').value.trim();
    const contentProviderName = document.getElementById('contentProviderName').value.trim();
    const url = document.getElementById('url').value.trim();
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
      showStatus('콘텐츠 제공자를 입력하세요', 'error');
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
    
    // API 요청
    const response = await fetch(`${API_URL}/api/newsletters/contents`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Access-Token': accessToken
      },
      body: JSON.stringify({
        title: title,
        content: content,
        contentProviderName: contentProviderName,
        originalUrl: url,
        publishedAt: publishedAt
      })
    });
    
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`API 요청 실패: ${response.status} - ${errorText}`);
    }
    
    const result = await response.json();
    showStatus(`콘텐츠가 저장되었습니다 (ID: ${result.id})`, 'success');
    
  } catch (error) {
    console.error('Error saving content:', error);
    showStatus(`저장 실패: ${error.message}`, 'error');
  }
}

// 이벤트 리스너
document.addEventListener('DOMContentLoaded', async () => {
  await loadSettings();
  await loadPageInfo();
  
  document.getElementById('loadPageInfo').addEventListener('click', loadPageInfo);
  document.getElementById('saveContent').addEventListener('click', saveContent);
});
