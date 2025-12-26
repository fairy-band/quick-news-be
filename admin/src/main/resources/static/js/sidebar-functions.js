// Sidebar Control Functions

function openSidebar() {
    const sidebar = document.getElementById('detail-sidebar');
    sidebar.classList.add('open');
}

function closeSidebar() {
    const sidebar = document.getElementById('detail-sidebar');
    sidebar.classList.remove('open');

    // Clear selection
    document.querySelectorAll('tr[data-id]').forEach(row => {
        row.style.background = '';
        row.style.borderColor = '';
    });
}

function toggleBatchSection() {
    const area = document.getElementById('batch-processing-area');
    const icon = document.getElementById('batch-toggle-icon');

    if (area.style.display === 'none') {
        area.style.display = 'block';
        icon.textContent = '▲';
    } else {
        area.style.display = 'none';
        icon.textContent = '▼';
    }
}

// Populate sidebar with content details
function populateSidebar(content) {
    const container = document.getElementById('sidebar-content-container');

    const publishedDateText = content.publishedAt
        ? new Date(content.publishedAt).toLocaleDateString('ko-KR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        })
        : '없음';

    container.innerHTML = `
        <!-- Basic Info Section -->
        <div class="sidebar-section">
            <div class="sidebar-section-title">📌 기본 정보</div>
            <div class="detail-row">
                <span class="detail-label">ID</span>
                <span class="detail-value">#${content.id}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">뉴스레터</span>
                <span class="detail-value">${content.newsletterName}</span>
            </div>
            ${content.newsletterSourceId ? `
            <div class="detail-row">
                <span class="detail-label">소스 ID</span>
                <span class="detail-value">${content.newsletterSourceId}</span>
            </div>
            ` : ''}
            <div class="detail-row">
                <span class="detail-label">발행일</span>
                <span class="detail-value">${publishedDateText}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">URL</span>
                <span class="detail-value">
                    <a href="${content.originalUrl}" target="_blank" style="color: #60a5fa; text-decoration: underline;">열기 ↗</a>
                </span>
            </div>
        </div>

        <!-- Content Section -->
        <div class="sidebar-section">
            <div class="sidebar-section-title">📄 콘텐츠</div>
            <div style="color: #cbd5e1; font-size: 0.9rem; line-height: 1.6; max-height: 300px; overflow-y: auto; padding: 0.5rem;">
                ${content.content}
            </div>
        </div>

        <!-- Actions Section -->
        <div class="sidebar-section">
            <div class="sidebar-section-title">⚡ 빠른 액션</div>
            <div class="action-group">
                <button class="btn btn-primary" onclick="editContent(${content.id})">
                    ✏️ 수정
                </button>
                <button class="btn btn-success" onclick="processContent(${content.id})">
                    🚀 AI 처리
                </button>
            </div>
            <div class="action-group" style="margin-top: 0.5rem;">
                <button class="btn btn-danger" onclick="deleteContent(${content.id})">
                    🗑️ 삭제
                </button>
            </div>
        </div>

        <!-- Keywords Section -->
        <div class="sidebar-section">
            <div class="sidebar-section-title">🏷️ 키워드</div>
            <div id="sidebar-keywords-container" style="min-height: 50px;">
                <div style="text-align: center; color: #94a3b8; padding: 1rem;">
                    로딩 중...
                </div>
            </div>
            <button class="btn" style="width: 100%; margin-top: 0.75rem; background: rgba(102, 126, 234, 0.15); border: 1px solid rgba(102, 126, 234, 0.3); color: #cbd5e1;"
                onclick="manageKeywords(${content.id})">
                키워드 관리
            </button>
        </div>

        <!-- Summaries Section -->
        <div class="sidebar-section">
            <div class="sidebar-section-title">📝 요약</div>
            <div id="sidebar-summaries-container" style="min-height: 50px;">
                <div style="text-align: center; color: #94a3b8; padding: 1rem;">
                    로딩 중...
                </div>
            </div>
        </div>
    `;

    // Load keywords and summaries
    loadSidebarKeywords(content.id);
    loadSidebarSummaries(content.id);
}

function loadSidebarKeywords(contentId) {
    fetch(`./api/process/content/${contentId}/keywords?size=100`)
        .then(response => response.json())
        .then(data => {
            const container = document.getElementById('sidebar-keywords-container');
            if (data.content && data.content.length > 0) {
                container.innerHTML = `
                    <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                        ${data.content.map(kw => `
                            <span class="keyword-badge" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;">
                                ${kw.name}
                            </span>
                        `).join('')}
                    </div>
                `;
            } else {
                container.innerHTML = '<div style="text-align: center; color: #64748b; padding: 0.5rem; font-size: 0.85rem;">키워드 없음</div>';
            }
        })
        .catch(err => {
            console.error('Failed to load keywords:', err);
            document.getElementById('sidebar-keywords-container').innerHTML = '<div style="color: #ef4444;">로드 실패</div>';
        });
}

function loadSidebarSummaries(contentId) {
    fetch(`./api/summaries/content/${contentId}`)
        .then(response => response.json())
        .then(data => {
            const container = document.getElementById('sidebar-summaries-container');
            if (data && data.length > 0) {
                const latestSummary = data[0];
                container.innerHTML = `
                    <div style="background: rgba(0, 0, 0, 0.2); padding: 1rem; border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.05);">
                        <div style="color: #94a3b8; font-size: 0.75rem; margin-bottom: 0.5rem;">최신 요약</div>
                        <div style="color: #e2e8f0; font-size: 0.9rem; line-height: 1.5;">${latestSummary.summaryContent || '내용 없음'}</div>
                        <div style="margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid rgba(255, 255, 255, 0.05); font-size: 0.75rem; color: #64748b;">
                            총 ${data.length}개의 요약
                        </div>
                    </div>
                `;
            } else {
                container.innerHTML = '<div style="text-align: center; color: #64748b; padding: 0.5rem; font-size: 0.85rem;">요약 없음</div>';
            }
        })
        .catch(err => {
            console.error('Failed to load summaries:', err);
            document.getElementById('sidebar-summaries-container').innerHTML = '<div style="color: #ef4444;">로드 실패</div>';
        });
}

// Quick action functions(placeholders - implement as needed)
function editContent(contentId) {
    alert('Edit functionality - to be implemented. Content ID: ' + contentId);
}

function processContent(contentId) {
    document.getElementById('edit-content-id').value = contentId;
    autoProcessContent(contentId);
}

function deleteContent(contentId) {
    if (confirm('정말로 이 콘텐츠를 삭제하시겠습니까?')) {
        fetch(`./api/contents/${contentId}`, { method: 'DELETE' })
            .then(response => {
                if (response.ok) {
                    alert('삭제되었습니다.');
                    closeSidebar();
                    fetchContents();
                } else {
                    alert('삭제 실패');
                }
            })
            .catch(err => alert('오류: ' + err));
    }
}

function manageKeywords(contentId) {
    alert('Keyword management - to be implemented. Content ID: ' + contentId);
}
