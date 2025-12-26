// 전역 변수로 현재 정렬 옵션 저장
let currentSortOption = 'publishedAt';

document.addEventListener('DOMContentLoaded', function () {
    // URL에서 id 매개변수 확인
    const urlParams = new URLSearchParams(window.location.search);
    const contentId = urlParams.get('id');

    // Fetch initial data
    fetchCategories();
    fetchReservedKeywords();
    fetchNewsletterNames();

    // Load contents
    if (contentId) {
        showContentDetail(contentId);
        fetchContents();
    } else {
        fetchContents();
    }

    // Setup Integrated Filter Button
    const applyFiltersBtn = document.getElementById('apply-filters-btn');
    if (applyFiltersBtn) {
        applyFiltersBtn.addEventListener('click', function () {
            const button = this;
            const originalText = button.textContent;
            button.textContent = '적용 중...';
            button.disabled = true;

            currentSortOption = document.getElementById('sort-option').value;
            updateSortOptionDisplay();

            const keywordId = document.getElementById('keyword-select').value;
            const categoryId = document.getElementById('category-select').value;

            setTimeout(() => {
                if (keywordId) {
                    fetchContentsByKeyword(keywordId, 0, 10);
                } else if (categoryId) {
                    fetchContentsByCategory(categoryId, 0, 10);
                } else {
                    fetchContents(0, 10);
                }
                button.textContent = originalText;
                button.disabled = false;
            }, 100);
        });
    }


    // Setup add content button
    const addContentBtn = document.getElementById('add-content-trigger-btn');
    if (addContentBtn) {
        addContentBtn.addEventListener('click', openAddContentModal);
    }

    // Setup batch process button
    const batchProcessBtn = document.getElementById('batch-process-trigger-btn');
    if (batchProcessBtn) {
        batchProcessBtn.addEventListener('click', openBatchProcessModal);
    }

    // Setup add content form submission
    document.getElementById('add-content-form').addEventListener('submit', async function (e) {
        e.preventDefault();

        const formData = {
            newsletterSourceId: document.getElementById('modal-newsletter-source-id').value || null,
            newsletterName: document.getElementById('modal-newsletter-name').value,
            title: document.getElementById('modal-content-title').value,
            content: document.getElementById('modal-content-text').value,
            originalUrl: document.getElementById('modal-original-url').value,
            publishedAt: document.getElementById('modal-published-at').value || null
        };

        try {
            const response = await fetch('./api/contents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                alert('콘텐츠가 성공적으로 추가되었습니다!');
                closeAddContentModal();
                fetchContents(); // Refresh list
            } else {
                alert('콘텐츠 추가 실패');
            }
        } catch (error) {
            console.error('Error adding content:', error);
            alert('오류가 발생했습니다: ' + error.message);
        }
    });



    // 추가된 콘텐츠를 저장할 배열
    let addedContents = [];

    // 뉴스레터 목록 버튼 이벤트 설정
    document.getElementById('load-newsletters-btn').addEventListener('click', function () {
        loadNewsletterList();
    });

    // 뉴스레터 목록을 가져와서 모달로 표시하는 함수
    function loadNewsletterList() {
        showLoading();

        // 뉴스레터 목록 가져오기 (중복 제거된 이름 목록)
        fetch('./api/contents/newsletter-names')
            .then(response => response.json())
            .then(data => {
                hideLoading();

                // 모달 생성
                const modalContainer = document.createElement('div');
                modalContainer.className = 'modal-container';
                modalContainer.style.position = 'fixed';
                modalContainer.style.top = '0';
                modalContainer.style.left = '0';
                modalContainer.style.width = '100%';
                modalContainer.style.height = '100%';
                modalContainer.style.backgroundColor = 'rgba(0, 0, 0, 0.7)';
                modalContainer.style.display = 'flex';
                modalContainer.style.justifyContent = 'center';
                modalContainer.style.alignItems = 'center';
                modalContainer.style.zIndex = '1000';

                const modalContent = document.createElement('div');
                modalContent.className = 'modal-content';
                modalContent.style.backgroundColor = 'rgba(26, 26, 46, 0.95)';
                modalContent.style.borderRadius = '20px';
                modalContent.style.padding = '2rem';
                modalContent.style.width = '80%';
                modalContent.style.maxWidth = '600px';
                modalContent.style.maxHeight = '80%';
                modalContent.style.overflowY = 'auto';
                modalContent.style.border = '1px solid rgba(255, 255, 255, 0.1)';
                modalContent.style.boxShadow = '0 8px 32px rgba(0, 0, 0, 0.3)';

                modalContent.innerHTML = `
<h2 style="color: #00d4ff; margin-bottom: 1.5rem;">뉴스레터 목록</h2>
<div style="margin-bottom: 1rem;">
    <input type="text" id="newsletter-search" class="form-control" placeholder="뉴스레터 이름 검색...">
</div>
<div id="newsletter-list" style="max-height: 400px; overflow-y: auto; margin-bottom: 1.5rem;">
    <!-- 뉴스레터 목록이 여기에 표시됩니다 -->
</div>
<div style="display: flex; justify-content: flex-end;">
    <button type="button" id="close-modal-btn" class="btn" style="background: rgba(255, 255, 255, 0.1);">닫기</button>
</div>
`;

                modalContainer.appendChild(modalContent);
                document.body.appendChild(modalContainer);

                // 뉴스레터 목록 표시
                const newsletterList = document.getElementById('newsletter-list');

                if (data.length === 0) {
                    newsletterList.innerHTML = '<p>등록된 뉴스레터가 없습니다</p>';
                } else {
                    // 뉴스레터 이름을 알파벳 순으로 정렬
                    const sortedNewsletters = [...data].sort((a, b) => a.localeCompare(b));

                    sortedNewsletters.forEach(name => {
                        const item = document.createElement('div');
                        item.className = 'content-item';
                        item.style.padding = '0.75rem 1rem';
                        item.style.marginBottom = '0.5rem';
                        item.style.cursor = 'pointer';
                        item.textContent = name;

                        item.addEventListener('click', () => {
                            document.getElementById('newsletter-name').value = name;
                            document.body.removeChild(modalContainer);
                        });

                        newsletterList.appendChild(item);
                    });
                }

                // 검색 기능 설정
                document.getElementById('newsletter-search').addEventListener('input', function () {
                    const searchTerm = this.value.toLowerCase().trim();
                    const items = newsletterList.querySelectorAll('.content-item');

                    items.forEach(item => {
                        const newsletterName = item.textContent.toLowerCase();
                        if (searchTerm === '' || newsletterName.includes(searchTerm)) {
                            item.style.display = 'block';
                        } else {
                            item.style.display = 'none';
                        }
                    });
                });

                // 닫기 버튼 이벤트 설정
                document.getElementById('close-modal-btn').addEventListener('click', function () {
                    document.body.removeChild(modalContainer);
                });
            })
            .catch(error => {
                hideLoading();
                console.error('Error fetching newsletter names:', error);
                alert('뉴스레터 목록을 불러오는 중 오류가 발생했습니다.');
            });
    }

    // Set up add content form submission
    document.getElementById('add-content-form').addEventListener('submit', function (e) {
        e.preventDefault();

        const newsletterSourceId = document.getElementById('newsletter-source-id').value.trim();
        const newsletterName = document.getElementById('newsletter-name').value;
        const title = document.getElementById('content-title').value;
        const originalUrl = document.getElementById('original-url').value;
        const publishedAt = document.getElementById('published-at').value || null;
        const content = document.getElementById('content-text').value;

        if (!newsletterName || !title || !originalUrl || !content) {
            alert('필수 필드를 모두 입력해주세요');
            return;
        }

        // 콘텐츠 객체 생성
        const contentObj = {
            newsletterSourceId: newsletterSourceId || null,
            newsletterName: newsletterName,
            title: title,
            originalUrl: originalUrl,
            publishedAt: publishedAt,
            content: content
        };

        // 콘텐츠를 배열에 추가
        addedContents.push(contentObj);

        // 콘텐츠 목록 업데이트
        updateAddedContentsList();

        // 콘텐츠 폼 초기화 (뉴스레터 정보는 유지)
        document.getElementById('content-title').value = '';
        document.getElementById('original-url').value = '';
        document.getElementById('content-text').value = '';

        // 콘텐츠 제목 입력란에 포커스
        document.getElementById('content-title').focus();

        // 추가된 콘텐츠 목록 섹션 표시
        document.getElementById('added-contents-section').style.display = 'block';
    });

    // 추가된 콘텐츠 목록 초기화 버튼
    document.getElementById('clear-added-contents').addEventListener('click', function () {
        if (confirm('추가된 콘텐츠 목록을 초기화하시겠습니까?')) {
            addedContents = [];
            updateAddedContentsList();
            document.getElementById('added-contents-section').style.display = 'none';
        }
    });

    // 추가된 콘텐츠 목록 업데이트 함수
    function updateAddedContentsList() {
        const container = document.getElementById('added-contents-list');
        container.innerHTML = '';

        if (addedContents.length === 0) {
            container.innerHTML = '<p>추가된 콘텐츠가 없습니다</p>';
            return;
        }

        // 콘텐츠 목록 생성
        addedContents.forEach((content, index) => {
            const item = document.createElement('div');
            item.className = 'content-item';
            item.style.position = 'relative';
            item.style.padding = '1.5rem';
            item.style.marginBottom = '1rem';

            // 제목
            const title = document.createElement('div');
            title.className = 'content-title';
            title.textContent = content.title;
            title.style.fontSize = '1.1rem';
            title.style.fontWeight = '600';
            title.style.marginBottom = '0.5rem';
            title.style.paddingRight = '60px'; // 삭제 버튼 공간 확보

            // URL
            const url = document.createElement('div');
            url.className = 'content-source';
            url.innerHTML = `<strong>URL:</strong> <a href="${content.originalUrl}" target="_blank">${content.originalUrl}</a>`;
            url.style.marginBottom = '0.5rem';

            // 뉴스레터 정보
            const newsletter = document.createElement('div');
            newsletter.className = 'content-source';
            newsletter.innerHTML = `<strong>뉴스레터:</strong> ${content.newsletterName}`;
            if (content.newsletterSourceId) {
                newsletter.innerHTML += ` (ID: ${content.newsletterSourceId})`;
            }
            newsletter.style.marginBottom = '0.5rem';

            // 발행일
            if (content.publishedAt) {
                const publishDate = document.createElement('div');
                publishDate.className = 'content-source';
                publishDate.innerHTML = `<strong>발행일:</strong> ${content.publishedAt}`;
                publishDate.style.marginBottom = '0.5rem';
                item.appendChild(publishDate);
            }

            // 내용 미리보기
            const preview = document.createElement('div');
            preview.className = 'content-preview';
            preview.textContent = content.content.substring(0, 100) + '...';
            preview.style.marginTop = '0.5rem';
            preview.style.color = '#a0aec0';
            preview.style.fontSize = '0.9rem';

            // 삭제 버튼
            const removeBtn = document.createElement('button');
            removeBtn.className = 'btn btn-danger';
            removeBtn.textContent = '삭제';
            removeBtn.style.position = 'absolute';
            removeBtn.style.right = '10px';
            removeBtn.style.top = '10px';
            removeBtn.style.padding = '0.25rem 0.5rem';
            removeBtn.style.fontSize = '0.8rem';

            // 구분선
            const divider = document.createElement('div');
            divider.style.height = '1px';
            divider.style.backgroundColor = 'rgba(255, 255, 255, 0.1)';
            divider.style.margin = '0.5rem 0';

            removeBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                if (confirm('이 콘텐츠를 목록에서 삭제하시겠습니까?')) {
                    addedContents.splice(index, 1);
                    updateAddedContentsList();

                    if (addedContents.length === 0) {
                        document.getElementById('added-contents-section').style.display = 'none';
                    }
                }
            });

            // 순서 번호 표시
            const orderBadge = document.createElement('div');
            orderBadge.style.position = 'absolute';
            orderBadge.style.left = '-10px';
            orderBadge.style.top = '-10px';
            orderBadge.style.backgroundColor = '#00d4ff';
            orderBadge.style.color = '#000';
            orderBadge.style.width = '24px';
            orderBadge.style.height = '24px';
            orderBadge.style.borderRadius = '50%';
            orderBadge.style.display = 'flex';
            orderBadge.style.alignItems = 'center';
            orderBadge.style.justifyContent = 'center';
            orderBadge.style.fontWeight = 'bold';
            orderBadge.style.fontSize = '0.8rem';
            orderBadge.textContent = index + 1;

            item.appendChild(title);
            item.appendChild(url);
            item.appendChild(newsletter);
            item.appendChild(divider);
            item.appendChild(preview);
            item.appendChild(removeBtn);
            item.appendChild(orderBadge);
            container.appendChild(item);
        });

        // 저장 버튼 컨테이너
        const saveButtonContainer = document.createElement('div');
        saveButtonContainer.style.marginTop = '1.5rem';
        saveButtonContainer.style.padding = '1rem';
        saveButtonContainer.style.backgroundColor = 'rgba(0, 0, 0, 0.2)';
        saveButtonContainer.style.borderRadius = '10px';
        saveButtonContainer.style.textAlign = 'center';

        // 저장 정보 표시
        const saveInfo = document.createElement('div');
        saveInfo.style.marginBottom = '1rem';
        saveInfo.style.fontSize = '1.1rem';
        saveInfo.style.fontWeight = '600';
        saveInfo.innerHTML = `<span style="color: #00d4ff;">${addedContents.length}개</span>의 콘텐츠가 추가되었습니다`;
        saveButtonContainer.appendChild(saveInfo);

        // 모든 콘텐츠 저장 버튼
        const saveAllBtn = document.createElement('button');
        saveAllBtn.className = 'btn btn-success';
        saveAllBtn.innerHTML = `<span style="font-size: 1.2rem;">💾</span> 모든 콘텐츠 저장하기`;
        saveAllBtn.style.width = '100%';
        saveAllBtn.style.padding = '0.75rem';
        saveAllBtn.style.fontWeight = '600';
        saveAllBtn.style.fontSize = '1.1rem';

        saveAllBtn.addEventListener('click', function () {
            if (confirm(`${addedContents.length}개의 콘텐츠를 저장하시겠습니까?`)) {
                saveAllContents();
            }
        });

        saveButtonContainer.appendChild(saveAllBtn);

        // 진행 상태 표시 영역 (초기에는 숨김)
        const progressContainer = document.createElement('div');
        progressContainer.id = 'save-progress-container';
        progressContainer.style.display = 'none';
        progressContainer.style.marginTop = '1rem';
        progressContainer.style.padding = '1rem';
        progressContainer.style.backgroundColor = 'rgba(0, 0, 0, 0.3)';
        progressContainer.style.borderRadius = '8px';

        const progressText = document.createElement('div');
        progressText.id = 'save-progress-text';
        progressText.style.marginBottom = '0.5rem';
        progressText.style.textAlign = 'center';
        progressText.textContent = '저장 중...';

        const progressBarOuter = document.createElement('div');
        progressBarOuter.style.width = '100%';
        progressBarOuter.style.height = '10px';
        progressBarOuter.style.backgroundColor = 'rgba(255, 255, 255, 0.1)';
        progressBarOuter.style.borderRadius = '5px';
        progressBarOuter.style.overflow = 'hidden';

        const progressBarInner = document.createElement('div');
        progressBarInner.id = 'save-progress-bar';
        progressBarInner.style.width = '0%';
        progressBarInner.style.height = '100%';
        progressBarInner.style.backgroundColor = '#00d4ff';
        progressBarInner.style.transition = 'width 0.3s ease';

        progressBarOuter.appendChild(progressBarInner);
        progressContainer.appendChild(progressText);
        progressContainer.appendChild(progressBarOuter);
        saveButtonContainer.appendChild(progressContainer);

        container.appendChild(saveButtonContainer);
    }

    // 모든 콘텐츠 저장 함수
    function saveAllContents() {
        if (addedContents.length === 0) {
            alert('저장할 콘텐츠가 없습니다');
            return;
        }

        // 진행 상태 표시
        const totalContents = addedContents.length;
        let processedContents = 0;
        let successCount = 0;
        let failedContents = [];

        // 진행 상태 표시 영역 표시
        const progressContainer = document.getElementById('save-progress-container');
        const progressBar = document.getElementById('save-progress-bar');
        const progressText = document.getElementById('save-progress-text');

        if (progressContainer) {
            progressContainer.style.display = 'block';
            progressBar.style.width = '0%';
            progressText.textContent = `저장 중... (0/${totalContents})`;
        }

        // 각 콘텐츠에 대해 순차적으로 처리
        const processNextContent = (index) => {
            if (index >= addedContents.length) {
                // 모든 콘텐츠 처리 완료
                hideLoading();

                // 진행 상태 표시 영역 업데이트
                if (progressContainer) {
                    progressBar.style.width = '100%';
                    progressText.textContent = `완료: ${successCount}/${totalContents} 저장됨`;

                    // 3초 후에 진행 상태 표시 영역 숨김
                    setTimeout(() => {
                        progressContainer.style.display = 'none';
                    }, 3000);
                }

                // 결과 메시지 표시
                let resultMessage = `총 ${totalContents}개 중 ${successCount}개의 콘텐츠가 저장되었습니다.`;

                if (failedContents.length > 0) {
                    resultMessage += `\n\n저장에 실패한 콘텐츠 (${failedContents.length}개):\n`;
                    failedContents.forEach((title, i) => {
                        if (i < 5) { // 최대 5개까지만 표시
                            resultMessage += `- ${title}\n`;
                        } else if (i === 5) {
                            resultMessage += `... 외 ${failedContents.length - 5}개`;
                        }
                    });
                }

                alert(resultMessage);

                // 성공적으로 저장된 콘텐츠가 있으면 목록 초기화
                if (successCount > 0) {
                    addedContents = [];
                    updateAddedContentsList();
                    document.getElementById('added-contents-section').style.display = 'none';

                    // 콘텐츠 목록 새로고침
                    fetchContents();
                }

                return;
            }

            const contentData = addedContents[index];

            // 진행 상태 표시 업데이트
            if (progressContainer) {
                const percent = Math.floor((index / totalContents) * 100);
                progressBar.style.width = `${percent}% `;
                progressText.textContent = `저장 중... (${index} / ${totalContents})`;
            }

            fetch('./api/contents', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(contentData)
            })
                .then(response => {
                    processedContents++;

                    if (response.ok) {
                        successCount++;
                        return response.json();
                    } else {
                        failedContents.push(contentData.title);
                        throw new Error(`콘텐츠 저장 실패: ${contentData.title} `);
                    }
                })
                .then(() => {
                    // 다음 콘텐츠 처리
                    processNextContent(index + 1);
                })
                .catch(error => {
                    console.error('Error creating content:', error);

                    // 오류가 발생해도 다음 콘텐츠 처리
                    processNextContent(index + 1);
                });
        };

        // 첫 번째 콘텐츠부터 처리 시작
        processNextContent(0);
    }

    // Set up AI processing buttons
    document.getElementById('auto-process-btn').addEventListener('click', function () {
        autoProcessContent(document.getElementById('edit-content-id').value);
    });

    // Set up headline selection
    document.getElementById('headline-select').addEventListener('change', function () {
        const selectedHeadline = this.value;
        if (selectedHeadline) {
            document.getElementById('summary-title').value = selectedHeadline;
        }
    });

    // Set up summary save button
    document.getElementById('save-summary-btn').addEventListener('click', function () {
        saveSummary(document.getElementById('edit-content-id').value);
    });

    // 요약 없음 콘텐츠 관련 이벤트 설정
    setupNoSummaryContentEvents();
});

function fetchCategories() {
    fetch('./api/keywords/categories')
        .then(response => response.json())
        .then(data => {
            const select = document.getElementById('category-select');

            data.forEach(category => {
                const option = document.createElement('option');
                option.value = category.id;
                option.textContent = category.name;
                select.appendChild(option);
            });
        })
        .catch(error => console.error('Error fetching categories:', error));
}

function fetchReservedKeywords() {
    // 현재 선택된 콘텐츠 ID 가져오기
    const contentDetailElement = document.getElementById('content-detail');
    const contentId = contentDetailElement ? contentDetailElement.dataset.contentId : null;

    // 현재 콘텐츠의 키워드 목록 가져오기
    let existingKeywords = [];

    // 콘텐츠가 선택되어 있으면 현재 키워드 가져오기
    const fetchExistingKeywords = async () => {
        if (contentId) {
            try {
                const response = await fetch(`./ api / process / content / ${contentId}/keywords?size=1000`);
                const data = await response.json();
                return data.content.map(keyword => keyword.id.toString());
            } catch (error) {
                console.error('Error fetching content keywords:', error);
                return [];
            }
        }
        return [];
    };

    // 모든 키워드 가져오기 (예약 키워드와 후보 키워드 모두)
    fetchExistingKeywords().then(existingKeywordIds => {
        // 전체 예약 키워드 가져오기 (size=1000으로 설정하여 최대한 많은 키워드 가져오기)
        fetch('./api/keywords/reserved?size=1000')
            .then(response => response.json())
            .then(data => {
                const keywordSelect = document.getElementById('keyword-select');
                const keywordTagsContainer = document.getElementById('keyword-tags-container');

                // 키워드 필터 드롭다운 업데이트
                keywordSelect.innerHTML = '<option value="">모든 키워드</option>';
                data.content.forEach(keyword => {
                    const option = document.createElement('option');
                    option.value = keyword.id;
                    option.textContent = keyword.name;
                    keywordSelect.appendChild(option);
                });

                // 키워드 태그 컨테이너 업데이트
                keywordTagsContainer.innerHTML = '';

                // 헤더 추가 (전체 키워드 수 표시)
                const headerDiv = document.createElement('div');
                headerDiv.className = 'keyword-tags-header';
                headerDiv.innerHTML = `
    <div>전체 예약 키워드</div>
    <div class="keyword-count-total">총 ${data.content.length}개</div>
    `;
                keywordTagsContainer.appendChild(headerDiv);

                if (data.content.length === 0) {
                    keywordTagsContainer.innerHTML += '<p>등록된 키워드가 없습니다</p>';
                    return;
                }

                // 키워드를 알파벳 순으로 정렬
                const sortedKeywords = [...data.content].sort((a, b) => a.name.localeCompare(b.name));

                // 키워드 태그 컨테이너에 태그 추가
                const tagsContainer = document.createElement('div');
                tagsContainer.className = 'keyword-tags';
                keywordTagsContainer.appendChild(tagsContainer);

                sortedKeywords.forEach(keyword => {
                    const tag = document.createElement('div');
                    tag.className = 'keyword-tag';

                    // 이미 콘텐츠에 존재하는 키워드인지 확인
                    const isExisting = existingKeywordIds.includes(keyword.id.toString());

                    if (isExisting) {
                        tag.classList.add('existing');
                        tag.title = '이미 추가된 키워드';
                        // 시각적으로 구분할 수 있는 스타일 추가
                        tag.style.backgroundColor = 'rgba(79, 172, 254, 0.3)';
                        tag.style.borderColor = '#4facfe';
                    }

                    tag.dataset.id = keyword.id;
                    tag.dataset.name = keyword.name;
                    tag.textContent = keyword.name;

                    tag.addEventListener('click', () => {
                        // 이미 존재하는 키워드는 선택 불가능하게 설정
                        if (!isExisting) {
                            tag.classList.toggle('selected');
                            updateSelectedCount();
                        } else {
                            alert('이미 추가된 키워드입니다.');
                        }
                    });

                    tagsContainer.appendChild(tag);
                });

                // 검색 기능 설정
                setupKeywordSearch();

                // 선택된 개수 초기화
                updateSelectedCount();
            })
            .catch(error => console.error('Error fetching reserved keywords:', error));
    });
}

function setupKeywordSearch() {
    const searchInput = document.getElementById('keyword-search');
    if (!searchInput) return;

    const tags = document.querySelectorAll('.keyword-tags .keyword-tag');

    searchInput.addEventListener('input', () => {
        const searchTerm = searchInput.value.toLowerCase().trim();
        let visibleCount = 0;

        tags.forEach(tag => {
            const keywordName = tag.dataset.name.toLowerCase();
            if (searchTerm === '' || keywordName.includes(searchTerm)) {
                tag.style.display = 'inline-flex';
                visibleCount++;
            } else {
                tag.style.display = 'none';
            }
        });

        // 검색 결과 수 업데이트
        const countTotal = document.querySelector('.keyword-count-total');
        if (countTotal) {
            const totalCount = tags.length;
            if (searchTerm === '') {
                countTotal.textContent = `총 ${totalCount}개`;
            } else {
                countTotal.textContent = `검색 결과: ${visibleCount}/${totalCount}개`;
            }
        }
    });
}

function updateSelectedCount() {
    const selectedTags = document.querySelectorAll('.keyword-tag.selected');
    const countElement = document.getElementById('selected-count');
    countElement.textContent = selectedTags.length;
}

function fetchContents(page = 0, size = 10) {
    // 정렬 옵션에 따라 API 호출
    const sortOption = document.getElementById('sort-option')?.value || 'publishedAt';
    const newsletterName = document.getElementById('newsletter-filter')?.value || '';

    let url = `./api/contents/sorted?sortOption=${sortOption}&page=${page}&size=${size}`;
    if (newsletterName) {
        url += `&newsletterName=${encodeURIComponent(newsletterName)}`;
    }

    fetch(url)
        .then(response => response.json())
        .then(data => {
            renderContentList(data.content);
            renderPagination(data);
        })
        .catch(error => console.error('Error fetching contents:', error));
}

function renderPagination(pageData) {
    const paginationContainer = document.getElementById('pagination');
    paginationContainer.innerHTML = '';

    if (!pageData.totalPages || pageData.totalPages <= 1) {
        return;
    }

    // 현재 필터 상태 확인
    const categoryId = document.getElementById('category-select').value;
    const keywordId = document.getElementById('keyword-select').value;

    // Previous button
    if (pageData.number > 0) {
        const prevButton = document.createElement('div');
        prevButton.className = 'pagination-item';
        prevButton.textContent = '이전';

        // 필터 상태에 따라 페이지네이션 함수 선택
        if (keywordId) {
            prevButton.addEventListener('click', () => fetchContentsByKeyword(keywordId, pageData.number - 1, pageData.size));
        } else if (categoryId) {
            prevButton.addEventListener('click', () => fetchContentsByCategory(categoryId, pageData.number - 1, pageData.size));
        } else {
            prevButton.addEventListener('click', () => fetchContents(pageData.number - 1, pageData.size));
        }

        paginationContainer.appendChild(prevButton);
    }

    // Page numbers
    const startPage = Math.max(0, pageData.number - 2);
    const endPage = Math.min(pageData.totalPages - 1, pageData.number + 2);

    for (let i = startPage; i <= endPage; i++) {
        const pageButton = document.createElement('div');
        pageButton.className = 'pagination-item';
        if (i === pageData.number) {
            pageButton.classList.add('active');
        }
        pageButton.textContent = i + 1;

        // 필터 상태에 따라 페이지네이션 함수 선택
        if (keywordId) {
            pageButton.addEventListener('click', () => fetchContentsByKeyword(keywordId, i, pageData.size));
        } else if (categoryId) {
            pageButton.addEventListener('click', () => fetchContentsByCategory(categoryId, i, pageData.size));
        } else {
            pageButton.addEventListener('click', () => fetchContents(i, pageData.size));
        }

        paginationContainer.appendChild(pageButton);
    }

    // Next button
    if (pageData.number < pageData.totalPages - 1) {
        const nextButton = document.createElement('div');
        nextButton.className = 'pagination-item';
        nextButton.textContent = '다음';

        // 필터 상태에 따라 페이지네이션 함수 선택
        if (keywordId) {
            nextButton.addEventListener('click', () => fetchContentsByKeyword(keywordId, pageData.number + 1, pageData.size));
        } else if (categoryId) {
            nextButton.addEventListener('click', () => fetchContentsByCategory(categoryId, pageData.number + 1, pageData.size));
        } else {
            nextButton.addEventListener('click', () => fetchContents(pageData.number + 1, pageData.size));
        }

        paginationContainer.appendChild(nextButton);
    }
}

function fetchContentsByCategory(categoryId, page = 0, size = 10) {
    // 정렬 옵션에 따라 API 호출
    const sortOption = document.getElementById('sort-option')?.value || 'publishedAt';
    const newsletterName = document.getElementById('newsletter-filter')?.value || '';

    let url =
        `./api/contents/by-category/${categoryId}/sorted?sortOption=${sortOption}&page=${page}&size=${size}`;
    if (newsletterName) {
        url += `&newsletterName=${encodeURIComponent(newsletterName)}`;
    }

    fetch(url)
        .then(response => response.json())
        .then(data => {
            renderContentList(data.content);
            renderPagination(data);
        })
        .catch(error => console.error('Error fetching contents by category:', error));
}

function fetchContentsByKeyword(keywordId, page = 0, size = 10) {
    // 키워드 ID로 콘텐츠 필터링
    const url = `./api/contents/by-keyword/${keywordId}?page=${page}&size=${size}`;

    fetch(url)
        .then(response => response.json())
        .then(data => {
            renderContentList(data.content);
            renderPagination(data);

            // 필터링 적용 상태 표시
            const keywordSelect = document.getElementById('keyword-select');
            const selectedKeywordText = keywordSelect.options[keywordSelect.selectedIndex].text;

            // 기존 필터 정보 요소 제거
            const existingFilterInfo = document.getElementById('keyword-filter-info');
            if (existingFilterInfo) {
                existingFilterInfo.remove();
            }

            // 필터 정보 요소 추가
            const filterInfoElement = document.createElement('div');
            filterInfoElement.id = 'keyword-filter-info';
            filterInfoElement.style.marginTop = '1rem';
            filterInfoElement.style.padding = '0.5rem';
            filterInfoElement.style.backgroundColor = 'rgba(0, 212, 255, 0.1)';
            filterInfoElement.style.borderRadius = '8px';
            filterInfoElement.style.fontSize = '0.9rem';
            filterInfoElement.style.color = '#00d4ff';
            filterInfoElement.style.display = 'flex';
            filterInfoElement.style.justifyContent = 'space-between';
            filterInfoElement.style.alignItems = 'center';

            filterInfoElement.innerHTML = `
                <span>현재 필터: <strong>${selectedKeywordText}</strong> 키워드 (${data.totalElements}개 콘텐츠)</span>
                <button id="clear-keyword-filter" class="btn"
                    style="padding: 0.25rem 0.5rem; font-size: 0.8rem; background: rgba(255, 255, 255, 0.1);">필터
                    해제</button>
                `;

            // 키워드 필터 섹션에 추가
            const keywordFilterSection = document.querySelector('.section h2').parentElement;
            keywordFilterSection.appendChild(filterInfoElement);

            // 필터 해제 버튼 이벤트 설정
            document.getElementById('clear-keyword-filter').addEventListener('click', function () {
                document.getElementById('keyword-select').value = '';
                fetchContents(0, 10);
                filterInfoElement.remove();
            });
        })
        .catch(error => console.error('Error fetching contents by keyword:', error));
}

function renderContentList(contents) {
    const container = document.getElementById('content-list');
    container.innerHTML = '';

    if (contents.length === 0) {
        container.innerHTML = `
                <div style="text-align: center; padding: 3rem; color: #94a3b8;">
                    <p style="font-size: 1rem;">콘텐츠가 없습니다</p>
                </div>
                `;
        return;
    }

    // Table container
    container.style.display = 'block';
    container.style.marginBottom = '2rem';

    // Bulk action bar
    const bulkBar = document.createElement('div');
    bulkBar.id = 'bulk-action-bar';
    bulkBar.style.cssText = `
                display: none;
                padding: 1rem;
                background: rgba(102, 126, 234, 0.1);
                border: 1px solid rgba(102, 126, 234, 0.3);
                border-radius: 12px;
                margin-bottom: 1rem;
                align-items: center;
                gap: 1rem;
                `;
    bulkBar.innerHTML = `
                <span id="selected-count-display" style="color: #cbd5e1; font-weight: 600;">0개 선택됨</span>
                <button class="btn btn-success" onclick="bulkProcessSelected()" style="padding: 0.5rem 1rem;">일괄
                    처리</button>
                <button class="btn btn-danger" onclick="bulkDeleteSelected()" style="padding: 0.5rem 1rem;">일괄
                    삭제</button>
                <button class="btn" onclick="clearSelection()"
                    style="padding: 0.5rem 1rem; background: rgba(255,255,255,0.1);">선택 해제</button>
                `;
    container.appendChild(bulkBar);

    // Create table
    const table = document.createElement('table');
    table.style.cssText = `
                width: 100%;
                border-collapse: separate;
                border-spacing: 0;
                background: rgba(0, 0, 0, 0.3);
                border-radius: 12px;
                overflow: hidden;
                border: 1px solid rgba(255, 255, 255, 0.12);
                `;

    // Table header
    const thead = document.createElement('thead');
    thead.innerHTML = `
                <tr style="background: rgba(0, 0, 0, 0.5); border-bottom: 2px solid rgba(102, 126, 234, 0.3);">
                    <th style="padding: 1.2rem 1rem; text-align: left; width: 50px;">
                        <input type="checkbox" id="select-all-checkbox"
                            style="cursor: pointer; width: 18px; height: 18px;" />
                    </th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: left; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; letter-spacing: 0.3px;">
                        제목</th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: left; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; width: 150px; letter-spacing: 0.3px;">
                        뉴스레터</th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: left; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; width: 120px; letter-spacing: 0.3px;">
                        발행일</th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: center; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; width: 110px; letter-spacing: 0.3px;">
                        상태</th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: left; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; width: 220px; letter-spacing: 0.3px;">
                        키워드</th>
                    <th
                        style="padding: 1.2rem 1rem; text-align: center; color: #e2e8f0; font-weight: 700; font-size: 0.95rem; width: 180px; letter-spacing: 0.3px;">
                        액션</th>
                </tr>
                `;
    table.appendChild(thead);

    // Table body
    const tbody = document.createElement('tbody');
    contents.forEach((content, index) => {
        renderContentRow(content, content.isExposed, tbody, index);
    });
    table.appendChild(tbody);

    container.appendChild(table);

    // Setup select all checkbox
    document.getElementById('select-all-checkbox').addEventListener('change', function () {
        const checkboxes = document.querySelectorAll('.content-checkbox');
        checkboxes.forEach(cb => cb.checked = this.checked);
        updateBulkActionBar();
    });
}

// 테이블 행 렌더링 (실용적인 어드민 레이아웃)
function renderContentRow(content, isExposed, tbody, index) {
    const row = document.createElement('tr');
    row.dataset.id = content.id;
    row.style.cssText = `
                border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                transition: all 0.2s ease;
                cursor: pointer;
                background: ${index % 2 === 0 ? 'rgba(255, 255, 255, 0.02)' : 'transparent'};
                `;

    row.addEventListener('mouseenter', () => {
        row.style.background = 'rgba(102, 126, 234, 0.12)';
        row.style.borderBottomColor = 'rgba(102, 126, 234, 0.3)';
    });
    row.addEventListener('mouseleave', () => {
        if (!row.classList.contains('selected')) {
            row.style.background = index % 2 === 0 ? 'rgba(255, 255, 255, 0.02)' : 'transparent';
            row.style.borderBottomColor = 'rgba(255, 255, 255, 0.1)';
        }
    });

    // Checkbox cell
    const checkboxCell = document.createElement('td');
    checkboxCell.style.cssText = 'padding: 1.2rem 1rem; text-align: center;';
    checkboxCell.innerHTML = `<input type="checkbox" class="content-checkbox" data-id="${content.id}"
                    style="cursor: pointer; width: 18px; height: 18px;" />`;
    checkboxCell.addEventListener('click', (e) => e.stopPropagation());
    row.appendChild(checkboxCell);

    // Title cell
    const titleCell = document.createElement('td');
    titleCell.style.cssText = 'padding: 1rem; color: #f8fafc; font-weight: 500;';
    const titleDiv = document.createElement('div');
    titleDiv.style.cssText = `max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;`;
    titleDiv.textContent = content.title;
    titleDiv.title = content.title;
    titleCell.appendChild(titleDiv);
    row.appendChild(titleCell);

    // Newsletter cell
    const newsletterCell = document.createElement('td');
    newsletterCell.style.cssText = 'padding: 1.2rem 1rem; color: #e2e8f0; font-size: 0.9rem; font-weight: 500;';
    newsletterCell.textContent = content.newsletterName;
    row.appendChild(newsletterCell);

    // Date cell
    const dateCell = document.createElement('td');
    dateCell.style.cssText = 'padding: 1.2rem 1rem; color: #cbd5e1; font-size: 0.88rem; font-weight: 500;';
    dateCell.textContent = content.publishedAt ? new
        Date(content.publishedAt).toLocaleDateString('ko-KR') : '-';
    row.appendChild(dateCell);

    // Status cell
    const statusCell = document.createElement('td');
    statusCell.style.cssText = 'padding: 1rem; text-align: center;';
    const statusContainer = document.createElement('div');
    statusContainer.style.cssText = 'display: flex; flex-direction: column; gap: 0.3rem; align-items: center;';

    const summaryBadge = document.createElement('span');
    summaryBadge.style.cssText = `
            padding: 0.25rem 0.6rem; border-radius: 12px; font-size: 0.7rem; font-weight: 600; white-space: nowrap;
            ${content.hasSummary ? 'background: linear-gradient(135deg, #43e97b, #38f9d7); color: #0a0a0a;' : 'background: rgba(255, 255, 255, 0.08); color: #94a3b8; border: 1px solid rgba(255, 255, 255, 0.12);'}
        `;
    summaryBadge.textContent = content.hasSummary ? '✓ 요약' : '⊘ 미요약';

    const exposureBadge = document.createElement('span');
    exposureBadge.style.cssText = `
            padding: 0.25rem 0.6rem; border-radius: 12px; font-size: 0.7rem; font-weight: 600; white-space: nowrap;
            ${isExposed ? 'background: linear-gradient(135deg, #f59e0b, #d97706); color: #fff;' : 'background: rgba(255, 255, 255, 0.08); color: #94a3b8; border: 1px solid rgba(255, 255, 255, 0.12);'}
        `;
    exposureBadge.textContent = isExposed ? '👁 노출' : '◉ 미노출';

    statusContainer.appendChild(summaryBadge);
    statusContainer.appendChild(exposureBadge);
    statusCell.appendChild(statusContainer);
    row.appendChild(statusCell);

    // Keywords cell
    const keywordsCell = document.createElement('td');
    keywordsCell.style.cssText = 'padding: 1.2rem 1rem;';
    keywordsCell.innerHTML = '<span style="color: #94a3b8; font-size: 0.85rem;">로딩중...</span>';
    getContentKeywords(content.id, (keywords) => {
        const keywordsContainer = document.createElement('div');
        keywordsContainer.style.cssText = 'display: flex; flex-wrap: wrap; gap: 0.3rem;';
        if (keywords.length > 0) {
            const displayKeywords = keywords.slice(0, 3);
            displayKeywords.forEach(keyword => {
                const badge = document.createElement('span');
                badge.className = 'keyword-badge';
                badge.style.cssText = 'padding: 0.3rem 0.6rem; font-size: 0.75rem; font-weight: 600;';
                badge.textContent = keyword.name;
                keywordsContainer.appendChild(badge);
            });
            if (keywords.length > 3) {
                const more = document.createElement('span');
                more.style.cssText = 'color: #cbd5e1; font-size: 0.75rem; padding: 0.3rem 0.6rem; font-weight: 600;';
                more.textContent = `+${keywords.length - 3}`;
                keywordsContainer.appendChild(more);
            }
        } else {
            keywordsContainer.innerHTML = '<span style="color: #64748b; font-size: 0.75rem;">-</span>';
        }
        keywordsCell.innerHTML = '';
        keywordsCell.appendChild(keywordsContainer);
    });
    row.appendChild(keywordsCell);

    // Actions cell
    const actionsCell = document.createElement('td');
    actionsCell.style.cssText = 'padding: 1.2rem 1rem; text-align: center;';
    actionsCell.addEventListener('click', (e) => e.stopPropagation());
    const actionsContainer = document.createElement('div');
    actionsContainer.style.cssText = 'display: flex; gap: 0.6rem; justify-content: center;';
    const viewBtn = document.createElement('button');
    viewBtn.className = 'btn btn-primary';
    viewBtn.style.cssText = 'padding: 0.5rem 1rem; font-size: 0.85rem; font-weight: 600;';
    viewBtn.textContent = '상세';
    viewBtn.onclick = () => showContentDetail(content.id);
    const processBtn = document.createElement('button');
    processBtn.className = 'btn btn-success';
    processBtn.style.cssText = 'padding: 0.5rem 1rem; font-size: 0.85rem; font-weight: 600;';
    processBtn.textContent = '처리';
    processBtn.onclick = () => {
        document.getElementById('edit-content-id').value = content.id;
        autoProcessContent(content.id);
    };
    actionsContainer.appendChild(viewBtn);
    actionsContainer.appendChild(processBtn);
    actionsCell.appendChild(actionsContainer);
    row.appendChild(actionsCell);

    row.addEventListener('click', () => showContentDetail(content.id));
    tbody.appendChild(row);
    const checkbox = checkboxCell.querySelector('.content-checkbox');
    checkbox.addEventListener('change', updateBulkActionBar);
}

function updateBulkActionBar() {
    const checkboxes = document.querySelectorAll('.content-checkbox:checked');
    const bulkBar = document.getElementById('bulk-action-bar');
    const countDisplay = document.getElementById('selected-count-display');
    if (checkboxes.length > 0) {
        bulkBar.style.display = 'flex';
        countDisplay.textContent = `${checkboxes.length}개 선택됨`;
    } else {
        bulkBar.style.display = 'none';
    }
}

function bulkProcessSelected() {
    const selected = Array.from(document.querySelectorAll('.content-checkbox:checked')).map(cb =>
        cb.dataset.id);
    if (selected.length === 0) return;
    if (confirm(`선택된 ${selected.length}개 콘텐츠를 일괄 처리하시겠습니까 ? `)) {
        console.log('Bulk processing:', selected);
        alert('일괄 처리 기능은 구현 중입니다.');
    }
}

function bulkDeleteSelected() {
    const selected = Array.from(document.querySelectorAll('.content-checkbox:checked')).map(cb =>
        cb.dataset.id);
    if (selected.length === 0) return;
    if (confirm(`선택된 ${selected.length}개 콘텐츠를 삭제하시겠습니까 ? 이 작업은 되돌릴 수 없습니다.`)) {
        console.log('Bulk deleting:', selected);
        alert('일괄 삭제 기능은 구현 중입니다.');
    }
}

function clearSelection() {
    document.querySelectorAll('.content-checkbox').forEach(cb => cb.checked = false);
    document.getElementById('select-all-checkbox').checked = false;
    updateBulkActionBar();
}

function showContentDetail(contentId) {
    // Highlight selected row
    document.querySelectorAll('tr[data-id]').forEach(row => {
        row.style.background = '';
        row.style.borderColor = '';
    });

    const selectedRow = document.querySelector(`tr[data-id="${contentId}"]`);
    if (selectedRow) {
        selectedRow.style.background = 'rgba(102, 126, 234, 0.15)';
        selectedRow.style.borderBottomColor = 'rgba(102, 126, 234, 0.4)';
    }

    // Open sidebar
    openSidebar();

    // Show loading state
    const container = document.getElementById('sidebar-content-container');
    container.innerHTML = `
                    <div style="text-align: center; padding: 3rem;">
                        <div class="spinner"></div>
                        <div style="margin-top: 1rem; color: #cbd5e1;">로딩 중...</div>
                    </div>
                `;

    // Fetch content details
    fetch(`./api/contents/${contentId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Content not found');
            }
            return response.json();
        })
        .then(content => {
            populateSidebar(content);

            // Store in hidden form for compatibility with existing functions
            document.getElementById('edit-content-id').value = content.id;
        })
        .catch(error => {
            console.error('Error fetching content details:', error);
            container.innerHTML = `
            <div style="text-align: center; padding: 2rem; color: #ef4444;">
                    <div style="font-size: 2rem; margin-bottom: 0.5rem;">⚠️</div>
                    <div>콘텐츠를 불러오는 중 오류가 발생했습니다</div>
                </div >
            `;
        });
}

// ===== Sidebar Control Functions =====
function openSidebar() {
    const sidebar = document.getElementById('detail-sidebar');
    sidebar.classList.add('open');
}

function closeSidebar() {
    const sidebar = document.getElementById('detail-sidebar');
    sidebar.classList.remove('open');

    // Clear row selection
    document.querySelectorAll('tr[data-id]').forEach(row => {
        row.style.background = '';
        row.style.borderColor = '';
    });
}

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
                    ` : ''
        }
                    <div class="detail-row">
                        <span class="detail-label">발행일</span>
                        <span class="detail-value">${publishedDateText}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">URL</span>
                        <span class="detail-value">
                            <a href="${content.originalUrl}" target="_blank"
                                style="color: #60a5fa; text-decoration: underline;">열기 ↗</a>
                        </span>
                    </div>
                </div >

                <div class="sidebar-section">
                    <div class="sidebar-section-title">📄 콘텐츠</div>
                    <div
                        style="color: #cbd5e1; font-size: 0.9rem; line-height: 1.6; max-height: 300px; overflow-y: auto; padding: 0.5rem; background: rgba(0,0,0,0.2); border-radius: 8px;">
                        ${content.content}
                    </div>
                </div>

                <div class="sidebar-section">
                    <div class="sidebar-section-title">⚡ 빠른 액션</div>
                    <div class="action-group">
                        <button class="btn btn-primary" onclick="editContentInSidebar(${content.id})">
                            ✏️ 수정
                        </button>
                        <button class="btn btn-success"
                            onclick="document.getElementById('edit-content-id').value = ${content.id}; autoProcessContent(${content.id});">
                            🚀 AI 처리
                        </button>
                    </div>
                    <div class="action-group" style="margin-top: 0.5rem;">
                        <button class="btn btn-danger" onclick="deleteContentFromSidebar(${content.id})">
                            🗑️ 삭제
                        </button>
                    </div>
                </div>

                <div class="sidebar-section">
                    <div class="sidebar-section-title">🏷️ 키워드</div>
                    <div id="sidebar-keywords-container" style="min-height: 50px;">
                        <div style="text-align: center; color: #94a3b8; padding: 1rem;">로딩 중...</div>
                    </div>
                    <div style="margin-top: 1rem; padding: 1rem; background: rgba(0, 0, 0, 0.2); border-radius: 8px;">
                        <div style="font-size: 0.85rem; color: #94a3b8; margin-bottom: 0.5rem;">키워드 추가</div>
                        <div style="display: flex; gap: 0.5rem;">
                            <input type="text" id="manual-keyword-input" class="form-control" 
                                placeholder="키워드 입력..." style="flex: 1; padding: 0.5rem;">
                            <button class="btn btn-primary" onclick="addManualKeyword(${content.id})" 
                                style="padding: 0.5rem 1rem; white-space: nowrap;">
                                ➕ 추가
                            </button>
                        </div>
                    </div>
                </div>

                <div class="sidebar-section">
                    <div class="sidebar-section-title">📝 요약</div>
                    <div id="sidebar-summaries-container" style="min-height: 50px;">
                        <div style="text-align: center; color: #94a3b8; padding: 1rem;">로딩 중...</div>
                    </div>
                    <div style="margin-top: 1rem; padding: 1rem; background: rgba(0, 0, 0, 0.2); border-radius: 8px;">
                        <div style="font-size: 0.85rem; color: #94a3b8; margin-bottom: 0.5rem;">요약 추가</div>
                        <div style="margin-bottom: 0.5rem;">
                            <input type="text" id="manual-summary-title" class="form-control" 
                                placeholder="제목 (선택사항)" style="padding: 0.5rem; margin-bottom: 0.5rem;">
                        </div>
                        <div style="margin-bottom: 0.5rem;">
                            <textarea id="manual-summary-text" class="form-control" 
                                placeholder="요약 내용 입력..." rows="3" style="padding: 0.5rem;"></textarea>
                        </div>
                        <button class="btn btn-success" onclick="addManualSummary(${content.id})" 
                            style="width: 100%; padding: 0.5rem;">
                            ✅ 요약 저장
                        </button>
                    </div>
                </div>
        `;

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
    fetch(`./api/process/content/${contentId}/summaries?size=100`)
        .then(response => response.json())
        .then(data => {
            const container = document.getElementById('sidebar-summaries-container');
            if (data.content && data.content.length > 0) {
                const latestSummary = data.content[0];
                container.innerHTML = `
                <div
                    style="background: rgba(0, 0, 0, 0.2); padding: 1rem; border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.05);">
                    <div style="color: #94a3b8; font-size: 0.75rem; margin-bottom: 0.5rem;">최신 요약</div>
                    <div style="color: #e2e8f0; font-size: 0.9rem; line-height: 1.5;">${latestSummary.summary || '내용 없음'}</div>
                    ${latestSummary.title ? `<div style="margin-top: 0.5rem; color: #94a3b8; font-size: 0.8rem;">제목: ${latestSummary.title}</div>` : ''}
                    <div
                        style="margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid rgba(255, 255, 255, 0.05); font-size: 0.75rem; color: #64748b;">
                        총 ${data.content.length}개의 요약
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

function deleteContentFromSidebar(contentId) {
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

// ===== Modal Control Functions =====
function openAddContentModal() {
    document.getElementById('add-content-modal').classList.add('active');
}

function closeAddContentModal() {
    document.getElementById('add-content-modal').classList.remove('active');
    document.getElementById('add-content-form').reset();
}

// ===== Batch Process Modal Functions =====
function openBatchProcessModal() {
    document.getElementById('batch-process-modal').classList.add('active');
    document.getElementById('batch-empty-state').style.display = 'block';
    document.getElementById('no-summary-contents-section').style.display = 'none';
    
    // 뉴스레터 목록 다시 불러오기
    fetchNewsletterNames();
    
    // 모달이 열릴 때 이벤트 리스너 재설정
    setupNoSummaryContentEvents();
}

function closeBatchProcessModal() {
    document.getElementById('batch-process-modal').classList.remove('active');
}

// Close modal on overlay click
document.addEventListener('click', function (e) {
    if (e.target.classList.contains('modal-overlay')) {
        closeAddContentModal();
    }
});

// Edit content in sidebar
function editContentInSidebar(contentId) {
    fetch(`./api/contents/${contentId}`)
        .then(response => response.json())
        .then(content => {
            const container = document.getElementById('sidebar-content-container');
            container.innerHTML = `
                <div class="sidebar-section">
                    <div class="sidebar-section-title">✏️ 콘텐츠 수정</div>
                    <form id="sidebar-edit-form" style="display: flex; flex-direction: column; gap: 1rem;">
                        <div>
                            <label
                                style="display: block; margin-bottom: 0.5rem; color: #cbd5e1; font-size: 0.85rem;">제목</label>
                            <input type="text" id="sidebar-edit-title" class="form-control"
                                value="${content.title.replace(/" /g, '&quot;')}" required>
                        </div>
                        <div>
                            <label
                                style="display: block; margin-bottom: 0.5rem; color: #cbd5e1; font-size: 0.85rem;">뉴스레터</label>
                            <input type="text" id="sidebar-edit-newsletter" class="form-control"
                                value="${content.newsletterName}" required>
                        </div>
                        <div>
                            <label
                                style="display: block; margin-bottom: 0.5rem; color: #cbd5e1; font-size: 0.85rem;">URL</label>
                            <input type="url" id="sidebar-edit-url" class="form-control"
                                value="${content.originalUrl}" required>
                        </div>
                        <div>
                            <label
                                style="display: block; margin-bottom: 0.5rem; color: #cbd5e1; font-size: 0.85rem;">발행일</label>
                            <input type="date" id="sidebar-edit-date" class="form-control"
                                value="${content.publishedAt || ''}">
                        </div>
                        <div>
                            <label
                                style="display: block; margin-bottom: 0.5rem; color: #cbd5e1; font-size: 0.85rem;">내용</label>
                            <textarea id="sidebar-edit-content" class="form-control" rows="10"
                                required>${content.content}</textarea>
                        </div>
                        <div class="action-group">
                            <button type="button" class="btn" onclick="showContentDetail(${contentId})"
                                style="background: rgba(255, 255, 255, 0.1);">취소</button>
                            <button type="submit" class="btn btn-success">💾 저장</button>
                        </div>
                    </form>
                </div>
                `;

            document.getElementById('sidebar-edit-form').addEventListener('submit', async function (e) {
                e.preventDefault();
                const updateData = {
                    title: document.getElementById('sidebar-edit-title').value,
                    newsletterName: document.getElementById('sidebar-edit-newsletter').value,
                    originalUrl: document.getElementById('sidebar-edit-url').value,
                    publishedAt: document.getElementById('sidebar-edit-date').value || null,
                    content: document.getElementById('sidebar-edit-content').value
                };

                try {
                    const response = await fetch(`./api/contents/${contentId}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(updateData)
                    });

                    if (response.ok) {
                        alert('수정되었습니다!');
                        fetchContents();
                        showContentDetail(contentId);
                    } else {
                        alert('수정 실패');
                    }
                } catch (error) {
                    alert('오류: ' + error);
                }
            });
        })
        .catch(err => console.error('Failed to load content for edit:', err));
}






function autoProcessContent(contentId) {
    if (!contentId) {
        alert('콘텐츠를 선택해주세요.');
        return;
    }

    if (!confirm('자동 처리를 시작하시겠습니까?\n\n이 작업은 다음을 순차적으로 수행합니다:\n• AI 요약 생성\n• 키워드 자동 매칭\n• 노출 컨텐츠 생성\n\n처리 시간이 다소 소요될 수 있습니다.')) {
        return;
    }

    // Clear previous results
    document.getElementById('ai-results').innerHTML = '';
    showLoading();

    // Update loading message for auto-processing
    const loadingElement = document.getElementById('loading');
    const loadingText = loadingElement.querySelector('p');
    loadingText.textContent = 'AI가 전체 콘텐츠를 자동으로 처리하고 있습니다...';

    fetch(`./api/process/content/${contentId}/auto-process`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
        .then(response => response.json())
        .then(data => {
            hideLoading();

            if (data.success) {
                let html = `
                        <div class="result-card">
                            <div class="result-title">🚀 자동 처리 완료!</div>
                            <p style="color: #10b981; font-weight: 600; margin-bottom: 1rem;">${data.message}</p>
                            <div
                                style="background: rgba(16, 185, 129, 0.1); border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 8px; padding: 1rem; margin-top: 1rem;">
                                <h4 style="color: #10b981; margin-bottom: 0.5rem;">✅ 완료된 작업:</h4>
                                <ul style="color: #a0aec0; margin-left: 1rem;">
                                    <li>🧠 AI 요약 자동 생성 및 저장</li>
                                    <li>🏷️ 예약 키워드 자동 매칭 및 할당</li>
                                    <li>🌟 노출 컨텐츠 생성 및 활성화</li>
                                </ul>
                                `;

                if (data.exposureContentId) {
                    html += `
                                <div style="margin-top: 1rem;">
                                    <span style="color: #00d4ff;">📋 생성된 노출 컨텐츠 ID:</span>
                                    <span
                                        style="color: #fff; font-family: monospace;">${data.exposureContentId}</span>
                                </div>
                                `;
                }

                html += `
                            </div>
                        </div>
                        `;

                document.getElementById('ai-results').innerHTML = html;

                // Refresh content keywords and summaries
                fetchContentKeywords(contentId);
                fetchSavedSummaries(contentId);

                // Refresh the main content list to show updated exposure status
                fetchContents();
            } else {
                showError(data.message || '자동 처리에 실패했습니다.');
            }
        })
        .catch(error => {
            hideLoading();
            showError('서버 오류가 발생했습니다: ' + error.message);
        });
}

function showLoading() {
    document.getElementById('loading').style.display = 'block';
}

function hideLoading() {
    document.getElementById('loading').style.display = 'none';
}

function showError(message) {
    const resultsDiv = document.getElementById('ai-results');
    resultsDiv.innerHTML = `<div class="error"
                            style="background: rgba(248, 113, 113, 0.1); border: 1px solid rgba(248, 113, 113, 0.3); color: #fca5a5; padding: 1rem; border-radius: 8px; margin-top: 1rem;">
                            ❌ ${message}</div>`;
}

function saveSummary(contentId) {
    if (!contentId) {
        alert('콘텐츠를 선택해주세요.');
        return;
    }

    const title = document.getElementById('summary-title').value.trim();
    const summary = document.getElementById('summary-text').value.trim();
    const model = document.getElementById('summary-save-section').dataset.model || 'unknown';

    if (!title || !summary) {
        alert('제목과 요약 내용을 모두 입력해주세요.');
        return;
    }

    showLoading();

    fetch(`./api/process/content/${contentId}/summary/save`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            title: title,
            summary: summary,
            model: model
        })
    })
        .then(response => response.json())
        .then(data => {
            hideLoading();

            if (data.success) {
                alert('요약이 성공적으로 저장되었습니다!');

                // Clear form
                document.getElementById('summary-title').value = '';
                document.getElementById('summary-text').value = '';
                document.getElementById('headline-select').selectedIndex = 0;

                // Hide summary save section
                document.getElementById('summary-save-section').style.display = 'none';

                // Refresh saved summaries
                fetchSavedSummaries(contentId);
            } else {
                alert('요약 저장에 실패했습니다: ' + (data.error || '알 수 없는 오류'));
            }
        })
        .catch(error => {
            hideLoading();
            alert('서버 오류가 발생했습니다: ' + error.message);
        });
}

function fetchSavedSummaries(contentId, page = 0, size = 10) {
    fetch(`./api/process/content/${contentId}/summaries?page=${page}&size=${size}`)
        .then(response => response.json())
        .then(data => {
            const container = document.getElementById('saved-summaries-list');
            container.innerHTML = '';

            if (data.content.length === 0) {
                document.getElementById('saved-summaries-section').style.display = 'none';
                return;
            }

            document.getElementById('saved-summaries-section').style.display = 'block';

            data.content.forEach(summary => {
                const summaryCard = document.createElement('div');
                summaryCard.className = 'result-card';

                const summaryDate = new Date(summary.createdAt).toLocaleString('ko-KR', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });

                // Check if exposure content exists for this summary
                checkExposureContentExists(summary.id, (exists, exposureContentId) => {
                    let exposureContentButton = '';
                    let activeStatus = '';

                    if (exists) {
                        activeStatus = `<div
                            style="margin-bottom: 0.5rem; background-color: rgba(16, 185, 129, 0.2); color: #10b981; padding: 0.25rem 0.5rem; border-radius: 4px; display: inline-block; font-size: 0.8rem;">
                            ✓ 현재 활성화된 요약</div>`;
                        exposureContentButton = `
                        <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
                            <button class="btn"
                                style="flex: 1; background: linear-gradient(45deg, #3b82f6, #1d4ed8); color: #fff;"
                                onclick="editExposureContent(${exposureContentId})">
                                노출 컨텐츠 수정
                            </button>
                        </div>
                        `;
                    } else {
                        exposureContentButton = `
                        <button class="btn"
                            style="background: linear-gradient(45deg, #f59e0b, #d97706); color: #fff;"
                            onclick="setActiveSummary(${summary.id})">
                            이 요약을 활성화
                        </button>
                        `;
                    }

                    summaryCard.innerHTML = `
                        <div class="result-title">${summary.title}</div>
                        <div style="font-size: 0.8rem; color: #a0aec0; margin-bottom: 0.5rem;">
                            생성일: ${summaryDate} | 모델: ${summary.model}
                        </div>
                        ${activeStatus}
                        <p>${summary.summary}</p>
                        <div style="margin-top: 1rem;">
                            ${exposureContentButton}
                        </div>
                        `;

                    container.appendChild(summaryCard);
                });
            });

            // Add pagination
            const paginationContainer = document.createElement('div');
            paginationContainer.className = 'pagination';

            if (data.totalPages > 1) {
                // Previous button
                if (data.number > 0) {
                    const prevButton = document.createElement('div');
                    prevButton.className = 'pagination-item';
                    prevButton.textContent = '이전';
                    prevButton.addEventListener('click', () => fetchSavedSummaries(contentId, data.number - 1,
                        data.size));
                    paginationContainer.appendChild(prevButton);
                }

                // Page numbers
                const startPage = Math.max(0, data.number - 2);
                const endPage = Math.min(data.totalPages - 1, data.number + 2);

                for (let i = startPage; i <= endPage; i++) {
                    const pageButton = document.createElement('div');
                    pageButton.className = 'pagination-item'; if (i === data.number) {
                        pageButton.classList.add('active');
                    } pageButton.textContent = i + 1;
                    pageButton.addEventListener('click', () => fetchSavedSummaries(contentId, i, data.size));
                    paginationContainer.appendChild(pageButton);
                }

                // Next button
                if (data.number < data.totalPages - 1) {
                    const nextButton = document.createElement('div');
                    nextButton.className = 'pagination-item'; nextButton.textContent = '다음';
                    nextButton.addEventListener('click', () => fetchSavedSummaries(contentId, data.number
                        + 1, data.size));
                    paginationContainer.appendChild(nextButton);
                }

                document.getElementById('saved-summaries-section').appendChild(paginationContainer);
            }
        })
        .catch(error => {
            console.error('Error fetching saved summaries:', error);
            document.getElementById('saved-summaries-section').style.display = 'none';
        });
}

function checkExposureContentExists(summaryId, callback) {
    fetch(`./api/process/summary/${summaryId}/exposure-content-exists`)
        .then(response => response.json())
        .then(data => {
            // Pass both the existence status and the exposure content ID
            callback(data.exists, data.exposureContentId);
        })
        .catch(error => {
            console.error('Error checking exposure content:', error);
            callback(false, null);
        });
}

function updateExposureContent(exposureContentId, data) {
    showLoading();

    fetch(`./api/recommendations/exposure-contents/${exposureContentId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to update exposure content');
            }
            return response.json();
        })
        .then(() => {
            hideLoading();
            alert('노출 컨텐츠가 성공적으로 수정되었습니다.');

            // Refresh the summary list
            const contentDetailElement = document.getElementById('content-detail');
            const contentId = contentDetailElement ? contentDetailElement.dataset.contentId :
                null;
            if (contentId) {
                fetchSavedSummaries(contentId);
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error updating exposure content:', error);
            alert('노출 컨텐츠 수정 중 오류가 발생했습니다.');
        });
}

function setActiveSummary(summaryId) {
    if (!confirm('이 요약을 활성화하시겠습니까? 기존의 노출 컨텐츠는 삭제되고 이 요약이 노출 컨텐츠로 설정됩니다.')) {
        return;
    }

    showLoading();

    fetch(`./api/recommendations/summaries/${summaryId}/set-active`, {
        method: 'POST'
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to set active summary');
            }
            return response.json();
        })
        .then(() => {
            hideLoading();
            alert('요약이 성공적으로 활성화되었습니다.');

            // Refresh the summary list
            const contentDetailElement = document.getElementById('content-detail');
            const contentId = contentDetailElement ? contentDetailElement.dataset.contentId :
                null;
            if (contentId) {
                fetchSavedSummaries(contentId);
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error setting active summary:', error);
            alert('요약 활성화 중 오류가 발생했습니다.');
        });
}

function createExposureContent(summaryId) {
    // Redirect to the new setActiveSummary function
    setActiveSummary(summaryId);
}

async function displaySuggestedKeywordsForSelection(data) {
    const container = document.getElementById('suggested-keywords-container');
    container.innerHTML = '';

    // Combine all keyword types
    const allKeywords = [
        ...(data.suggestedKeywords || []),
        ...(data.matchedKeywords || []),
        ...(data.provocativeKeywords || [])
    ];

    // Remove duplicates
    const uniqueKeywords = [...new Set(allKeywords)];

    if (uniqueKeywords.length === 0) {
        container.innerHTML = '<p>추가할 수 있는 키워드가 없습니다</p>';
        document.getElementById('add-selected-as-candidates-btn').disabled = true;
        return;
    }

    // 이미 예약 키워드로 존재하는 키워드 목록 가져오기
    let existingReservedKeywords = [];
    try {
        // 항상 최신 예약 키워드 목록을 가져오기 위해 캐시를 무시하는 옵션 추가
        const response = await fetch('./api/keywords/reserved?size=1000', {
            cache: 'no-store' // 캐시를 사용하지 않고 항상 새로운 데이터 요청
        });
        const data = await response.json();
        existingReservedKeywords = data.content.map(keyword => keyword.name.toLowerCase());
    } catch (error) {
        console.error('Error fetching reserved keywords:', error);
    }

    // Create checkboxes for each keyword
    uniqueKeywords.forEach(keyword => {
        const keywordDiv = document.createElement('div');
        keywordDiv.className = 'keyword-checkbox';

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `keyword-${keyword}`;
        checkbox.value = keyword;
        checkbox.style.marginRight = '0.5rem';

        // 이미 예약 키워드로 존재하는지 확인
        const isExistingReserved = existingReservedKeywords.includes(keyword.toLowerCase());

        if (isExistingReserved) {
            // 이미 예약 키워드로 존재하면 체크박스 비활성화
            checkbox.disabled = true;
            keywordDiv.classList.add('existing');
            keywordDiv.title = '이미 예약 키워드로 존재합니다';

            // 이미 존재하는 키워드 표시
            const existingBadge = document.createElement('span');
            existingBadge.textContent = '예약됨';
            existingBadge.className = 'existing-badge';

            const label = document.createElement('label');
            label.htmlFor = `keyword-${keyword}`;
            label.textContent = keyword + ' ';
            label.appendChild(existingBadge);

            keywordDiv.appendChild(checkbox);
            keywordDiv.appendChild(label);
        } else {
            const label = document.createElement('label');
            label.htmlFor = `keyword-${keyword}`;
            label.textContent = keyword;

            keywordDiv.appendChild(checkbox);
            keywordDiv.appendChild(label);
        }

        container.appendChild(keywordDiv);
    });

    // Enable the add button
    document.getElementById('add-selected-as-candidates-btn').disabled = false;
}

// 체크된 항목이 있는지 확인하여 버튼 상태 업데이트
function updateAddButtonsState() {
    const checkedBoxes = document.querySelectorAll('#suggested-keywords-container input[type="checkbox"]:checked:not(:disabled)');
    const hasCheckedItems = checkedBoxes.length > 0;

    document.getElementById('add-selected-as-candidates-btn').disabled =
        !hasCheckedItems;
    document.getElementById('add-selected-as-reserved-btn').disabled = !hasCheckedItems;
}

function addKeywordsToContent(contentId, keywordIds) {
    // 진행 상태 표시
    const totalKeywords = keywordIds.length;
    let processedKeywords = 0;
    let successCount = 0;

    // 각 키워드에 대해 순차적으로 처리
    const processNextKeyword = (index) => {
        if (index >= keywordIds.length) {
            // 모든 키워드 처리 완료
            alert(`총 ${totalKeywords}개 중 ${successCount}개의 키워드가 추가되었습니다.`);

            // 키워드 선택 초기화
            document.querySelectorAll('.keyword-tag.selected').forEach(tag => {
                tag.classList.remove('selected');
            });
            updateSelectedCount();

            // 콘텐츠 키워드 목록 새로고침
            fetchContentKeywords(contentId);

            // 예약 키워드 목록 새로고침 (이미 추가된 키워드 표시를 위해)
            fetchReservedKeywords();

            return;
        }

        const keywordId = keywordIds[index];

        fetch(`./api/contents/${contentId}/keywords`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                keywordId: keywordId
            })
        })
            .then(response => {
                processedKeywords++;

                if (response.ok) {
                    successCount++;
                }

                // 다음 키워드 처리
                processNextKeyword(index + 1);
            })
            .catch(error => {
                console.error('Error adding keyword to content:', error);
                processedKeywords++;

                // 오류가 발생해도 다음 키워드 처리
                processNextKeyword(index + 1);
            });
    };

    // 첫 번째 키워드부터 처리 시작
    processNextKeyword(0);
}

async function addSelectedKeywordsAsCandidates() {
    const checkboxes = document.querySelectorAll('#suggested-keywords-container input[type="checkbox"]:checked:not(:disabled)');

    if (checkboxes.length === 0) {
        alert('추가할 키워드를 하나 이상 선택해주세요.');
        return;
    }

    const selectedKeywords = Array.from(checkboxes).map(checkbox => checkbox.value);

    showLoading();

    try {
        const response = await fetch('./api/process/candidate-keywords', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                keywords: selectedKeywords
            })
        });

        const data = await response.json();
        hideLoading();

        if (data.success) {
            let message = '';

            if (data.addedKeywords.length > 0) {
                message += `다음 키워드가 후보로 추가되었습니다: ${data.addedKeywords.join(', ')}\n`;
            }

            if (data.existingKeywords.length > 0) {
                message += `다음 키워드는 이미 존재합니다: ${data.existingKeywords.join(', ')}\n`;
            }

            if (data.errorKeywords.length > 0) {
                message += `다음 키워드는 추가에 실패했습니다: ${data.errorKeywords.join(', ')}`;
            }

            alert(message);

            // Clear checkboxes
            checkboxes.forEach(checkbox => {
                checkbox.checked = false;
            });

            // 현재 AI 결과에서 키워드 목록을 다시 가져와서 표시
            // 이미 예약 키워드로 추가된 항목을 업데이트하기 위함
            const aiResultsElement = document.getElementById('ai-results');
            const resultCardElement = aiResultsElement.querySelector('.result-card');

            if (resultCardElement) {
                // 현재 표시된 키워드 데이터를 다시 사용하여 displaySuggestedKeywordsForSelection 호출
                const matchedKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(1) .keyword')).map(el => el.textContent);
                const suggestedKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(2) .keyword')).map(el => el.textContent);
                const provocativeKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(3) .keyword')).map(el => el.textContent);

                // 현재 데이터로 키워드 목록 다시 표시
                await displaySuggestedKeywordsForSelection({
                    matchedKeywords,
                    suggestedKeywords,
                    provocativeKeywords
                });
            }
        } else {
            alert('키워드 추가에 실패했습니다.');
        }
    } catch (error) {
        hideLoading();
        alert('서버 오류가 발생했습니다: ' + error.message);
    }
}

// 선택한 키워드를 예약 키워드로 추가하는 함수
async function addKeywordsAsReserved(keywords) {
    showLoading();

    // 각 키워드를 순차적으로 처리
    const processKeywords = async () => {
        const results = {
            success: [],
            error: []
        };

        for (const keyword of keywords) {
            try {
                const response = await fetch('./api/keywords/reserved', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        name: keyword
                    })
                });

                if (response.ok) {
                    results.success.push(keyword);
                } else {
                    results.error.push(keyword);
                }
            } catch (error) {
                console.error(`Error adding reserved keyword ${keyword}:`, error);
                results.error.push(keyword);
            }
        }

        return results;
    };

    try {
        const results = await processKeywords();
        hideLoading();

        let message = '';

        if (results.success.length > 0) {
            message += `다음 키워드가 예약 키워드로 추가되었습니다: ${results.success.join(', ')}\n`;
        }

        if (results.error.length > 0) {
            message += `다음 키워드는 추가에 실패했습니다: ${results.error.join(', ')}`;
        }

        alert(message);

        // 체크박스 선택 해제
        document.querySelectorAll('#suggested-keywords-container input[type="checkbox"]:checked').forEach(checkbox => {
            checkbox.checked = false;
        });

        // 예약 키워드 목록 새로고침
        fetchReservedKeywords();

        // 현재 AI 결과 다시 표시 (이미 예약 키워드로 추가된 항목을 업데이트하기 위함)
        const aiResultsElement = document.getElementById('ai-results');
        const resultCardElement = aiResultsElement.querySelector('.result-card');

        if (resultCardElement) {
            // 현재 표시된 키워드 데이터를 다시 사용하여 displaySuggestedKeywordsForSelection 호출
            const matchedKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(1) .keyword')).map(el => el.textContent);
            const suggestedKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(2) .keyword')).map(el => el.textContent);
            const provocativeKeywords = Array.from(resultCardElement.querySelectorAll('.keyword-group:nth-child(3) .keyword')).map(el => el.textContent);

            // 현재 데이터로 키워드 목록 다시 표시
            await displaySuggestedKeywordsForSelection({
                matchedKeywords,
                suggestedKeywords,
                provocativeKeywords
            });
        }
    } catch (error) {
        hideLoading();
        alert('서버 오류가 발생했습니다: ' + error.message);
    }
}

function editExposureContent(exposureContentId) {
    // Fetch the exposure content details
    fetch(`./api/recommendations/exposure-contents/${exposureContentId}`)
        .then(response => response.json())
        .then(exposureContent => {
            // Create a modal for editing
            const modalContainer = document.createElement('div');
            modalContainer.className = 'modal-container';
            modalContainer.style.position = 'fixed';
            modalContainer.style.top = '0';
            modalContainer.style.left = '0';
            modalContainer.style.width = '100%';
            modalContainer.style.height = '100%';
            modalContainer.style.backgroundColor = 'rgba(0, 0, 0, 0.7)';
            modalContainer.style.display = 'flex';
            modalContainer.style.justifyContent = 'center';
            modalContainer.style.alignItems = 'center';
            modalContainer.style.zIndex = '1000';

            const modalContent = document.createElement('div');
            modalContent.className = 'modal-content';
            modalContent.style.backgroundColor = 'rgba(26, 26, 46, 0.95)';
            modalContent.style.borderRadius = '20px';
            modalContent.style.padding = '2rem';
            modalContent.style.width = '80%';
            modalContent.style.maxWidth = '800px';
            modalContent.style.maxHeight = '90%';
            modalContent.style.overflowY = 'auto';
            modalContent.style.border = '1px solid rgba(255, 255, 255, 0.1)';
            modalContent.style.boxShadow = '0 8px 32px rgba(0, 0, 0, 0.3)';

            modalContent.innerHTML = `
                                <h2 style="color: #00d4ff; margin-bottom: 1.5rem;">노출 컨텐츠 수정</h2>
                                <form id="edit-exposure-form">
                                    <input type="hidden" id="exposure-content-id" value="${exposureContent.id}">
                                    <div style="margin-bottom: 1rem;">
                                        <label for="provocative-keyword">자극적인 키워드</label>
                                        <input type="text" id="provocative-keyword" class="form-control"
                                            value="${exposureContent.provocativeKeyword}" required>
                                    </div>
                                    <div style="margin-bottom: 1rem;">
                                        <label for="provocative-headline">자극적인 헤드라인</label>
                                        <input type="text" id="provocative-headline" class="form-control"
                                            value="${exposureContent.provocativeHeadline}" required>
                                    </div>
                                    <div style="margin-bottom: 1rem;">
                                        <label for="summary-content">요약 내용</label>
                                        <textarea id="summary-content" class="form-control" rows="10"
                                            required>${exposureContent.summaryContent}</textarea>
                                    </div>
                                    <div style="display: flex; gap: 1rem; margin-top: 2rem;">
                                        <button type="submit" class="btn btn-primary" style="flex: 1;">저장</button>
                                        <button type="button" id="cancel-edit-btn" class="btn"
                                            style="flex: 1; background: rgba(255, 255, 255, 0.1);">취소</button>
                                    </div>
                                </form>
                                `;

            modalContainer.appendChild(modalContent);
            document.body.appendChild(modalContainer);

            // Set up form submission
            document.getElementById('edit-exposure-form').addEventListener('submit', function
                (e) {
                e.preventDefault();

                const exposureContentId = document.getElementById('exposure-content-id').value;
                const provocativeKeyword = document.getElementById('provocative-keyword').value;
                const provocativeHeadline = document.getElementById('provocative-headline').value;
                const summaryContent = document.getElementById('summary-content').value;

                updateExposureContent(exposureContentId, {
                    provocativeKeyword,
                    provocativeHeadline,
                    summaryContent
                });

                // Close the modal
                document.body.removeChild(modalContainer);
            });

            // Set up cancel button
            document.getElementById('cancel-edit-btn').addEventListener('click', function () {
                document.body.removeChild(modalContainer);
            });
        })
        .catch(error => {
            console.error('Error fetching exposure content:', error);
            alert('노출 컨텐츠 정보를 불러오는 데 실패했습니다.');
        });
}




// Function to get content keywords
function getContentKeywords(contentId, callback) {
    fetch(`./api/contents/${contentId}/keywords?size=1000`)
        .then(response => response.json())
        .then(keywords => {
            callback(keywords);
        })
        .catch(error => {
            console.error('Error getting content keywords:', error);
            callback([]);
        });
}

// 정렬 옵션에 따라 시각적 표시를 업데이트하는 함수
function updateSortOptionDisplay() {
    // 현재 정렬 옵션에 따라 정렬 기준 표시
    const sortInfoElement = document.createElement('div');
    sortInfoElement.style.marginTop = '1rem';
    sortInfoElement.style.padding = '0.5rem';
    sortInfoElement.style.backgroundColor = 'rgba(0, 0, 0, 0.2)';
    sortInfoElement.style.borderRadius = '8px';
    sortInfoElement.style.fontSize = '0.9rem';
    sortInfoElement.style.color = '#a0aec0';

    let sortDescription = '';
    switch (currentSortOption) {
        case 'noSummary':
            sortDescription = '요약 없음 우선 정렬';
            break;
        case 'notExposed':
            sortDescription = '노출 안함 우선 정렬';
            break;
        case 'exposed':
            sortDescription = '노출 중 우선 정렬';
            break;
        case 'newest':
            sortDescription = '최신순 정렬';
            break;
        case 'oldest':
            sortDescription = '오래된순 정렬';
            break;
        case 'publishedAt':
        default:
            sortDescription = '발행일순 정렬';
            break;
    }

    sortInfoElement.innerHTML = `<span style="color: #00d4ff;">현재 정렬:</span>
                                ${sortDescription}`;

    // 기존 정렬 정보 요소 제거
    const existingSortInfo = document.getElementById('sort-info');
    if (existingSortInfo) {
        existingSortInfo.remove();
    }

    // 새 정렬 정보 요소 추가
    sortInfoElement.id = 'sort-info';
    const sortOptionContainer =
        document.getElementById('sort-option').parentElement.parentElement.parentElement;
    sortOptionContainer.appendChild(sortInfoElement);
}

// 뉴스레터 이름 목록을 가져오는 함수
function fetchNewsletterNames() {
    fetch('./api/contents/newsletter-names')
        .then(response => response.json())
        .then(data => {
            // 정렬
            const sortedNames = data.sort();
            
            // 메인 필터 업데이트
            const newsletterFilter = document.getElementById('newsletter-filter');
            if (newsletterFilter) {
                newsletterFilter.innerHTML = '<option value="">모든 뉴스레터</option>';
                sortedNames.forEach(name => {
                    const option = document.createElement('option');
                    option.value = name;
                    option.textContent = name;
                    newsletterFilter.appendChild(option);
                });
            }

            // 일괄 처리 모달 필터 업데이트
            const noSummaryFilter = document.getElementById('no-summary-newsletter-filter');
            if (noSummaryFilter) {
                noSummaryFilter.innerHTML = '<option value="">모든 뉴스레터</option>';
                sortedNames.forEach(name => {
                    const option = document.createElement('option');
                    option.value = name;
                    option.textContent = name;
                    noSummaryFilter.appendChild(option);
                });
            }

            // 모달의 뉴스레터 목록 버튼용 (있다면)
            const modalNewsletterFilter = document.getElementById('modal-newsletter-name');
            if (modalNewsletterFilter) {
                // 이건 input이므로 옵션을 추가하지 않음
            }

            console.log(`Loaded ${sortedNames.length} newsletter names`);
        })
        .catch(error => console.error('Error fetching newsletter names:', error));
}

// 요약 없음 콘텐츠 관련 이벤트 설정
function setupNoSummaryContentEvents() {
    // 요약 없음 콘텐츠 불러오기 버튼
    const loadBtn = document.getElementById('load-no-summary-contents-btn');
    if (loadBtn) {
        // 기존 이벤트 리스너 제거 후 새로 추가 (중복 방지)
        loadBtn.replaceWith(loadBtn.cloneNode(true));
        document.getElementById('load-no-summary-contents-btn').addEventListener('click',
            function () {
                console.log('Loading no-summary contents...');
                loadNoSummaryContents();
            });
    }

    // 전체 선택 버튼
    const selectAllBtn = document.getElementById('select-all-no-summary-btn');
    if (selectAllBtn) {
        selectAllBtn.replaceWith(selectAllBtn.cloneNode(true));
        document.getElementById('select-all-no-summary-btn').addEventListener('click', function () {
            const checkboxes = document.querySelectorAll('#no-summary-contents-list input[type="checkbox"]');
            checkboxes.forEach(checkbox => {
                checkbox.checked = true;
            });
            updateSelectedNoSummaryCount();
        });
    }

    // 선택 해제 버튼
    const deselectAllBtn = document.getElementById('deselect-all-no-summary-btn');
    if (deselectAllBtn) {
        deselectAllBtn.replaceWith(deselectAllBtn.cloneNode(true));
        document.getElementById('deselect-all-no-summary-btn').addEventListener('click', function () {
            const checkboxes = document.querySelectorAll('#no-summary-contents-list input[type="checkbox"]');
            checkboxes.forEach(checkbox => {
                checkbox.checked = false;
            });
            updateSelectedNoSummaryCount();
        });
    }

    // 일괄 자동 처리 버튼
    const bulkProcessBtn = document.getElementById('bulk-auto-process-btn');
    if (bulkProcessBtn) {
        bulkProcessBtn.replaceWith(bulkProcessBtn.cloneNode(true));
        document.getElementById('bulk-auto-process-btn').addEventListener('click', function () {
            bulkAutoProcessContents();
        });
    }

    // 자동 생성 버튼
    const autoGenBtn = document.getElementById('auto-generate-btn');
    if (autoGenBtn) {
        autoGenBtn.replaceWith(autoGenBtn.cloneNode(true));
        document.getElementById('auto-generate-btn').addEventListener('click', function () {
            toggleAutoGeneration();
        });
    }

    // 뉴스레터 필터 변경 시 자동으로 목록 새로고침
    const filterSelect = document.getElementById('no-summary-newsletter-filter');
    if (filterSelect) {
        filterSelect.replaceWith(filterSelect.cloneNode(true));
        document.getElementById('no-summary-newsletter-filter').addEventListener('change',
            function () {
                const noSummarySection = document.getElementById('no-summary-contents-section');
                if (noSummarySection && noSummarySection.style.display !== 'none') {
                    loadNoSummaryContents();
                }
            });
    }
}

// 요약 없음 콘텐츠 불러오기
function loadNoSummaryContents(page = 0, size = 20) {
    const newsletterName =
        document.getElementById('no-summary-newsletter-filter').value;
    let url = `./api/contents/sorted?sortOption=noSummary&page=${page}&size=${size}`;

    if (newsletterName) {
        url += `&newsletterName=${encodeURIComponent(newsletterName)}`;
    }

    showLoading();

    fetch(url)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            renderNoSummaryContentsList(data.content);
            renderNoSummaryPagination(data);

            // 빈 상태 숨기기
            const emptyState = document.getElementById('batch-empty-state');
            if (emptyState) {
                emptyState.style.display = 'none';
            }

            // 섹션 표시
            document.getElementById('no-summary-contents-section').style.display = 'block';

            // 카운트 정보 업데이트
            document.getElementById('no-summary-count-info').textContent = `총
                                ${data.totalElements}개의 요약 없음 콘텐츠`;

            // 버튼 활성화
            document.getElementById('select-all-no-summary-btn').disabled = false;
            document.getElementById('deselect-all-no-summary-btn').disabled = false;
        })
        .catch(error => {
            hideLoading();
            console.error('Error fetching no-summary contents:', error);
            alert('요약 없음 콘텐츠를 불러오는 중 오류가 발생했습니다.');
        });
}

// 요약 없음 콘텐츠 목록 렌더링
function renderNoSummaryContentsList(contents) {
    const container = document.getElementById('no-summary-contents-list');
    container.innerHTML = '';

    if (contents.length === 0) {
        container.innerHTML = '<p>요약 없음 콘텐츠가 없습니다</p>';
        return;
    }

    contents.forEach(content => {
        const item = document.createElement('div');
        item.className = 'content-item';
        item.style.padding = '1rem';
        item.style.marginBottom = '0.5rem';
        item.style.position = 'relative';
        item.style.borderLeft = '4px solid #f59e0b';
        item.style.backgroundColor = 'rgba(245, 158, 11, 0.05)';

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.value = content.id;
        checkbox.style.position = 'absolute';
        checkbox.style.top = '1rem';
        checkbox.style.right = '1rem';
        checkbox.style.transform = 'scale(1.2)';
        checkbox.addEventListener('change', updateSelectedNoSummaryCount);

        const title = document.createElement('div');
        title.className = 'content-title';
        title.textContent = content.title;
        title.style.paddingRight = '30px';

        const source = document.createElement('div');
        source.className = 'content-source';
        source.textContent = `출처: ${content.newsletterName}`;

        const publishDate = document.createElement('div');
        publishDate.className = 'content-source';
        publishDate.textContent = content.publishedAt
            ? `발행일: ${new Date(content.publishedAt).toLocaleDateString('ko-KR')}`
            : '';

        const preview = document.createElement('div');
        preview.className = 'content-preview';
        preview.textContent = content.content.substring(0, 100) + '...';

        item.appendChild(checkbox);
        item.appendChild(title);
        item.appendChild(source);
        if (content.publishedAt) {
            item.appendChild(publishDate);
        }
        item.appendChild(preview);

        // 클릭 시 상세 페이지로 이동 (체크박스 클릭 제외)
        item.addEventListener('click', function (e) {
            if (e.target.type !== 'checkbox') {
                showContentDetail(content.id);
            }
        });

        container.appendChild(item);
    });

    // 선택된 항목 수 업데이트
    updateSelectedNoSummaryCount();
}

// 요약 없음 콘텐츠 페이지네이션 렌더링
function renderNoSummaryPagination(pageData) {
    const paginationContainer = document.getElementById('no-summary-pagination');
    paginationContainer.innerHTML = '';

    if (!pageData.totalPages || pageData.totalPages <= 1) { return; } // Previous button
    if (pageData.number > 0) {
        const prevButton = document.createElement('div');
        prevButton.className = 'pagination-item';
        prevButton.textContent = '이전';
        prevButton.addEventListener('click', () => loadNoSummaryContents(pageData.number
            - 1, pageData.size));
        paginationContainer.appendChild(prevButton);
    }

    // Page numbers
    const startPage = Math.max(0, pageData.number - 2);
    const endPage = Math.min(pageData.totalPages - 1, pageData.number + 2);

    for (let i = startPage; i <= endPage; i++) {
        const
            pageButton = document.createElement('div');
        pageButton.className = 'pagination-item'; if (i === pageData.number) {
            pageButton.classList.add('active');
        } pageButton.textContent = i + 1;
        pageButton.addEventListener('click', () => loadNoSummaryContents(i,
            pageData.size));
        paginationContainer.appendChild(pageButton);
    }

    // Next button
    if (pageData.number < pageData.totalPages - 1) {
        const
            nextButton = document.createElement('div');
        nextButton.className = 'pagination-item'; nextButton.textContent = '다음';
        nextButton.addEventListener('click', () =>
            loadNoSummaryContents(pageData.number + 1, pageData.size));
        paginationContainer.appendChild(nextButton);
    }
}

// 선택된 요약 없음 콘텐츠 수 업데이트
function updateSelectedNoSummaryCount() {
    const checkboxes = document.querySelectorAll('#no-summary-contents-list input[type="checkbox"]:checked');
    const count = checkboxes.length;

    document.getElementById('selected-no-summary-count-info').textContent =
        `${count}개 선택됨`;

    // 일괄 처리 버튼 상태 업데이트
    document.getElementById('bulk-auto-process-btn').disabled = count === 0;

    // 자동 생성 버튼은 콘텐츠가 있으면 항상 활성화
    const totalContents = document.querySelectorAll('#no-summary-contents-list .content-item').length;
    document.getElementById('auto-generate-btn').disabled = totalContents === 0;
}

// 자동 생성 관련 변수
let autoGenerationInterval = null;
let isAutoGenerating = false;

// 자동 생성 토글
function toggleAutoGeneration() {
    const button = document.getElementById('auto-generate-btn');

    if (isAutoGenerating) {
        // 자동 생성 중지
        stopAutoGeneration();
        button.textContent = '⚡ 자동 생성 시작 (1분마다 20개)';
        button.style.background = 'linear-gradient(45deg, #8b5cf6, #7c3aed)';
    } else {
        // 자동 생성 시작
        if (!confirm('자동 생성을 시작하시겠습니까?\n\n1분마다 20개씩 요약 없음 콘텐츠를 자동으로 처리합니다.\n처리 중에는 다른 작업을 계속 진행할 수 있습니다.')) {
            return;
        }

        startAutoGeneration();
        button.textContent = '⏸️ 자동 생성 중지';
        button.style.background = 'linear-gradient(45deg, #ef4444, #dc2626)';
    }
}

// 자동 생성 시작
function startAutoGeneration() {
    isAutoGenerating = true;

    // 즉시 첫 번째 배치 처리
    processNextBatch();

    // 1분(60000ms)마다 다음 배치 처리
    autoGenerationInterval = setInterval(() => {
        processNextBatch();
    }, 60000);

    // 버튼 상태 업데이트
    updateAutoGenerationButtonState();
}

// 자동 생성 중지
function stopAutoGeneration() {
    isAutoGenerating = false;

    if (autoGenerationInterval) {
        clearInterval(autoGenerationInterval);
        autoGenerationInterval = null;
    }

    // 버튼 상태 업데이트
    updateAutoGenerationButtonState();
}

// 다음 배치 처리
async function processNextBatch() {
    if (!isAutoGenerating) {
        return;
    }

    // 현재 페이지에서 처리되지 않은 콘텐츠 20개 선택
    const allCheckboxes = document.querySelectorAll('#no-summary-contents-list input[type="checkbox"]:not(:disabled)');
    const checkboxesToProcess = Array.from(allCheckboxes).slice(0, 20);

    if (checkboxesToProcess.length === 0) {
        // 더 이상 처리할 콘텐츠가 없으면 자동 생성 중지
        alert('모든 요약 없음 콘텐츠 처리가 완료되었습니다!');
        stopAutoGeneration();
        document.getElementById('auto-generate-btn').textContent = '⚡ 자동 생성 시작 (1분마다 20개)';
        document.getElementById('auto-generate-btn').style.background = 'linear-gradient(45deg, #8b5cf6, #7c3aed)';

        // 목록 새로고침
        loadNoSummaryContents();
        return;
    }

    const contentIds = checkboxesToProcess.map(checkbox => checkbox.value);

    // 진행 상황 표시
    const progressContainer =
        document.getElementById('bulk-processing-progress');
    const progressBar = document.getElementById('bulk-progress-bar');
    const progressText = document.getElementById('bulk-progress-text');
    const progressDetails =
        document.getElementById('bulk-progress-details');

    progressContainer.style.display = 'block';
    progressBar.style.width = '0%';
    progressText.textContent = `자동 생성 중... (${contentIds.length}개 처리)`;
    progressDetails.textContent = '처리를 시작합니다...';

    // 처리 결과 추적
    const totalContents = contentIds.length;
    let processedContents = 0;
    let successCount = 0;
    let failedContents = [];

    // 각 콘텐츠를 순차적으로 처리
    const processNextContent = async (index) => {
        if (index >= contentIds.length) {
            // 배치 처리 완료
            progressBar.style.width = '100%';
            progressText.textContent = `배치 처리 완료! (성공: ${successCount}/${totalContents})`;
            progressDetails.innerHTML = `
                    <div style="color: #10b981;">✅ 성공: ${successCount}개</div>
                    ${failedContents.length > 0 ? `<div style="color: #f87171;">❌ 실패: ${failedContents.length}개</div>` : ''}
                    ${isAutoGenerating ? '<div style="color: #8b5cf6; margin-top: 0.5rem;">⏰ 다음 배치는 1분 후에 처리됩니다...</div>' : ''}
                `;

            // 3초 후 진행 상황 숨기기 (자동 생성이 계속 중이면 숨기지 않음)
            if (!isAutoGenerating) {
                setTimeout(() => {
                    progressContainer.style.display = 'none';
                }, 3000);
            }

            // 목록 새로고침
            if (successCount > 0) {
                loadNoSummaryContents();
            }

            return;
        }

        const contentId = contentIds[index];

        // 진행률 업데이트
        const percent = Math.floor((index / totalContents) * 100);
        progressBar.style.width = `${percent}%`;
        progressText.textContent = `자동 생성 중... ${index + 1}/${totalContents}`;
        progressDetails.textContent = `현재까지 성공: ${successCount}개, 실패: ${failedContents.length}개`;

        try {
            const response = await
                fetch(`./api/process/content/${contentId}/auto-process`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });

            const data = await response.json();
            processedContents++;

            if (data.success) {
                successCount++;

                // 성공한 콘텐츠는 체크박스 비활성화하고 시각적으로 표시
                const checkbox = document.querySelector(`#no-summary-contents-list
                                            input[value="${contentId}"]`);
                if (checkbox) {
                    checkbox.checked = false;
                    checkbox.disabled = true;
                    const item = checkbox.closest('.content-item');
                    if (item) {
                        item.style.opacity = '0.5';
                        item.style.borderLeft = '4px solid #10b981';
                        item.style.backgroundColor = 'rgba(16, 185, 129, 0.05)';

                        // 성공 표시 추가
                        const successBadge = document.createElement('div');
                        successBadge.style.position = 'absolute';
                        successBadge.style.top = '0.5rem';
                        successBadge.style.left = '0.5rem';
                        successBadge.style.backgroundColor = '#10b981';
                        successBadge.style.color = '#fff';
                        successBadge.style.padding = '0.25rem 0.5rem';
                        successBadge.style.borderRadius = '4px';
                        successBadge.style.fontSize = '0.8rem';
                        successBadge.style.fontWeight = '600';
                        successBadge.textContent = '처리 완료';
                        item.appendChild(successBadge);
                    }
                }
            } else {
                // 실패한 콘텐츠 제목 가져오기
                const failedItem = document.querySelector(`#no-summary-contents-list
                                            input[value="${contentId}"]`)?.closest('.content-item');
                const failedTitle =
                    failedItem?.querySelector('.content-title')?.textContent || `ID:
                                            ${contentId}`;
                failedContents.push(failedTitle);
            }

            // 선택된 항목 수 업데이트
            updateSelectedNoSummaryCount();

            // 다음 콘텐츠 처리 (약간의 지연을 두어 서버 부하 분산)
            setTimeout(() => {
                processNextContent(index + 1);
            }, 500);

        } catch (error) {
            console.error(`Error processing content ${contentId}:`, error);
            processedContents++;

            // 실패한 콘텐츠 제목 가져오기
            const failedItem = document.querySelector(`#no-summary-contents-list
                                            input[value="${contentId}"]`)?.closest('.content-item');
            const failedTitle =
                failedItem?.querySelector('.content-title')?.textContent || `ID:
                                            ${contentId}`;
            failedContents.push(failedTitle);

            // 다음 콘텐츠 처리
            setTimeout(() => {
                processNextContent(index + 1);
            }, 500);
        }
    };

    // 첫 번째 콘텐츠부터 처리 시작
    await processNextContent(0);
}

// 자동 생성 버튼 상태 업데이트
function updateAutoGenerationButtonState() {
    const button = document.getElementById('auto-generate-btn');
    const bulkButton = document.getElementById('bulk-auto-process-btn');
    const loadButton =
        document.getElementById('load-no-summary-contents-btn');
    const selectAllButton =
        document.getElementById('select-all-no-summary-btn');
    const deselectAllButton =
        document.getElementById('deselect-all-no-summary-btn');

    if (isAutoGenerating) {
        // 자동 생성 중에는 다른 버튼들 비활성화
        bulkButton.disabled = true;
        loadButton.disabled = true;
        selectAllButton.disabled = true;
        deselectAllButton.disabled = true;
    } else {
        // 자동 생성 중지 시 버튼들 다시 활성화
        loadButton.disabled = false;
        const totalContents = document.querySelectorAll('#no-summary-contents-list .content-item').length;
        if (totalContents > 0) {
            selectAllButton.disabled = false;
            deselectAllButton.disabled = false;
        }
        updateSelectedNoSummaryCount(); // 일괄 처리 버튼 상태 업데이트
    }
}

// 선택된 콘텐츠들에 대한 일괄 자동 처리
async function bulkAutoProcessContents() {
    const checkboxes = document.querySelectorAll('#no-summary-contents-list input[type="checkbox"]:checked');

    if (checkboxes.length === 0) {
        alert('처리할 콘텐츠를 하나 이상 선택해주세요.');
        return;
    }

    const contentIds = Array.from(checkboxes).map(checkbox =>
        checkbox.value);

    if (!confirm(`선택된 ${contentIds.length}개의 콘텐츠에 대해 일괄 자동 처리를
                                            시작하시겠습니까?\n\n각 콘텐츠에 대해 다음 작업이 수행됩니다:\n• AI 요약 생성\n• 키워드 자동 매칭\n• 노출 컨텐츠
                                            생성\n\n처리 시간이 다소 소요될 수 있습니다.`)) {
        return;
    }

    // 진행 상황 표시
    const progressContainer =
        document.getElementById('bulk-processing-progress');
    const progressBar = document.getElementById('bulk-progress-bar');
    const progressText = document.getElementById('bulk-progress-text');
    const progressDetails =
        document.getElementById('bulk-progress-details');

    progressContainer.style.display = 'block';
    progressBar.style.width = '0%';
    progressText.textContent = '일괄 자동 처리를 시작합니다...';
    progressDetails.textContent = '';

    // 처리 결과 추적
    const totalContents = contentIds.length;
    let processedContents = 0;
    let successCount = 0;
    let failedContents = [];

    // 각 콘텐츠를 순차적으로 처리
    const processNextContent = async (index) => {
        if (index >= contentIds.length) {
            // 모든 콘텐츠 처리 완료
            progressBar.style.width = '100%';
            progressText.textContent = '일괄 처리가 완료되었습니다!';
            progressDetails.innerHTML = `
                                            <div style="color: #10b981;">✅ 성공: ${successCount}개</div>
                                            ${failedContents.length > 0 ? `<div style="color: #f87171;">❌ 실패:
                                                ${failedContents.length}개</div>` : ''}
                                            `;

            // 결과 메시지 표시
            let resultMessage = `총 ${totalContents}개 중 ${successCount}개의 콘텐츠가 성공적으로
                                            처리되었습니다.`;

            if (failedContents.length > 0) {
                resultMessage += `\n\n처리에 실패한 콘텐츠 (${failedContents.length}개):\n`;
                failedContents.forEach((title, i) => {
                    if (i < 5) { resultMessage += `- ${title}\n`; } else if (i === 5) {
                        resultMessage += `... 외 ${failedContents.length - 5}개`;
                    }
                });
            }
            alert(resultMessage);

            // 3초 후 진행 상황 숨기기
            setTimeout(() => {
                progressContainer.style.display = 'none';
            }, 3000);

            // 목록 새로고침
            if (successCount > 0) {
                loadNoSummaryContents();
                fetchContents(); // 메인 콘텐츠 목록도 새로고침
            }

            return;
        }

        const contentId = contentIds[index];

        // 진행률 업데이트
        const percent = Math.floor((index / totalContents) * 100);
        progressBar.style.width = `${percent}%`;
        progressText.textContent = `콘텐츠 ${index + 1}/${totalContents} 처리
                                                중...`;
        progressDetails.textContent = `현재까지 성공: ${successCount}개, 실패:
                                                ${failedContents.length}개`;

        try {
            const response = await
                fetch(`./api/process/content/${contentId}/auto-process`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });

            const data = await response.json();
            processedContents++;

            if (data.success) {
                successCount++;

                // 성공한 콘텐츠는 체크박스 해제하고 시각적으로 표시
                const checkbox = document.querySelector(`#no-summary-contents-list
                                                input[value="${contentId}"]`);
                if (checkbox) {
                    checkbox.checked = false;
                    checkbox.disabled = true;
                    const item = checkbox.closest('.content-item');
                    if (item) {
                        item.style.opacity = '0.5';
                        item.style.borderLeft = '4px solid #10b981';
                        item.style.backgroundColor = 'rgba(16, 185, 129, 0.05)';

                        // 성공 표시 추가
                        const successBadge = document.createElement('div');
                        successBadge.style.position = 'absolute';
                        successBadge.style.top = '0.5rem';
                        successBadge.style.left = '0.5rem';
                        successBadge.style.backgroundColor = '#10b981';
                        successBadge.style.color = '#fff';
                        successBadge.style.padding = '0.25rem 0.5rem';
                        successBadge.style.borderRadius = '4px';
                        successBadge.style.fontSize = '0.8rem';
                        successBadge.style.fontWeight = '600';
                        successBadge.textContent = '처리 완료';
                        item.appendChild(successBadge);
                    }
                }
            } else {
                // 실패한 콘텐츠 제목 가져오기
                const failedItem = document.querySelector(`#no-summary-contents-list
                                                input[value="${contentId}"]`)?.closest('.content-item');
                const failedTitle =
                    failedItem?.querySelector('.content-title')?.textContent || `ID:
                                                ${contentId}`;
                failedContents.push(failedTitle);
            }

            // 선택된 항목 수 업데이트
            updateSelectedNoSummaryCount();

            // 다음 콘텐츠 처리 (약간의 지연을 두어 서버 부하 분산)
            setTimeout(() => {
                processNextContent(index + 1);
            }, 500);

        } catch (error) {
            console.error(`Error processing content ${contentId}:`, error);
            processedContents++;

            // 실패한 콘텐츠 제목 가져오기
            const failedItem = document.querySelector(`#no-summary-contents-list
                                                input[value="${contentId}"]`)?.closest('.content-item');
            const failedTitle =
                failedItem?.querySelector('.content-title')?.textContent || `ID:
                                                ${contentId}`;
            failedContents.push(failedTitle);

            // 다음 콘텐츠 처리
            setTimeout(() => {
                processNextContent(index + 1);
            }, 500);
        }
    };

    // 첫 번째 콘텐츠부터 처리 시작
    await processNextContent(0);
}


// ===== Manual Keyword and Summary Functions =====

/**
 * 수동으로 키워드 추가
 */
function addManualKeyword(contentId) {
    const input = document.getElementById('manual-keyword-input');
    const keywordName = input.value.trim();

    if (!keywordName) {
        alert('키워드를 입력해주세요.');
        return;
    }

    // 먼저 키워드가 예약 키워드로 존재하는지 확인
    fetch(`./api/keywords/reserved/search?name=${encodeURIComponent(keywordName)}`)
        .then(response => response.json())
        .then(keyword => {
            if (keyword && keyword.id) {
                // 예약 키워드가 존재하면 해당 키워드를 콘텐츠에 추가
                return addKeywordToContent(contentId, keyword.id);
            } else {
                // 예약 키워드가 없으면 후보 키워드로 생성 후 콘텐츠에 추가
                return createCandidateKeywordAndAdd(contentId, keywordName);
            }
        })
        .then(() => {
            input.value = '';
            loadSidebarKeywords(contentId);
            alert('키워드가 추가되었습니다.');
        })
        .catch(error => {
            console.error('Error adding keyword:', error);
            alert('키워드 추가 중 오류가 발생했습니다: ' + error.message);
        });
}

/**
 * 키워드를 콘텐츠에 추가
 */
function addKeywordToContent(contentId, keywordId) {
    return fetch(`./api/process/content/${contentId}/keywords`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ keywordId: keywordId })
    }).then(response => {
        if (!response.ok) {
            throw new Error('키워드 추가에 실패했습니다.');
        }
        return response.json();
    });
}

/**
 * 후보 키워드 생성 후 콘텐츠에 추가
 */
function createCandidateKeywordAndAdd(contentId, keywordName) {
    // 1. 후보 키워드 생성
    return fetch('./api/keywords/candidate', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name: keywordName })
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('후보 키워드 생성에 실패했습니다.');
            }
            return response.json();
        })
        .then(candidateKeyword => {
            // 2. 후보 키워드를 예약 키워드로 승격
            return fetch(`./api/keywords/candidate/${candidateKeyword.id}/promote-to-reserved`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('키워드 승격에 실패했습니다.');
            }
            return response.json();
        })
        .then(reservedKeyword => {
            // 3. 예약 키워드를 콘텐츠에 추가
            return addKeywordToContent(contentId, reservedKeyword.id);
        });
}

/**
 * 수동으로 요약 추가
 */
function addManualSummary(contentId) {
    const titleInput = document.getElementById('manual-summary-title');
    const textInput = document.getElementById('manual-summary-text');

    const title = titleInput.value.trim();
    const summary = textInput.value.trim();

    if (!summary) {
        alert('요약 내용을 입력해주세요.');
        return;
    }

    const summaryData = {
        title: title || null,
        summary: summary
    };

    fetch(`./api/process/content/${contentId}/summaries`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(summaryData)
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('요약 저장에 실패했습니다.');
            }
            return response.json();
        })
        .then(() => {
            titleInput.value = '';
            textInput.value = '';
            loadSidebarSummaries(contentId);
            alert('요약이 저장되었습니다.');
        })
        .catch(error => {
            console.error('Error saving summary:', error);
            alert('요약 저장 중 오류가 발생했습니다: ' + error.message);
        });
}
