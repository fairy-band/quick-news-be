/**
 * app.js — 워크쓰루 카드 뷰어 인터랙션
 * - 카드 그리드 렌더링
 * - 모달 열기/닫기 + 레이어 네비게이션
 * - 2개 이후 paywall 표시
 * - 면접 질문 버튼 → TBD
 * - paywall CTA / TBD CTA → 이메일 수집 모달
 * - 이메일 제출 → 로그 파일(localStorage fallback + 다운로드)
 */

// ===== State =====
let currentWalkthrough = null;
let currentLayerIndex = 0;
const FREE_LAYER_LIMIT = 2;

// ===== DOM References =====
const grid = document.getElementById('walkthroughGrid');
const modalOverlay = document.getElementById('modalOverlay');
const modalClose = document.getElementById('modalClose');
const modalHeader = document.getElementById('modalHeader');
const modalBadge = document.getElementById('modalBadge');
const layerIndicator = document.getElementById('layerIndicator');
const modalTitle = document.getElementById('modalTitle');
const modalDesc = document.getElementById('modalDesc');
const modalBody = document.getElementById('modalBody');
const navPrev = document.getElementById('navPrev');
const navNext = document.getElementById('navNext');
const layerDots = document.getElementById('layerDots');
const modalActions = document.getElementById('modalActions');
const btnInterview = document.getElementById('btnInterview');

const paywallOverlay = document.getElementById('paywallOverlay');
const paywallCta = document.getElementById('paywallCta');
const paywallDismiss = document.getElementById('paywallDismiss');

const tbdOverlay = document.getElementById('tbdOverlay');
const tbdCta = document.getElementById('tbdCta');
const tbdDismiss = document.getElementById('tbdDismiss');

const emailOverlay = document.getElementById('emailOverlay');
const emailClose = document.getElementById('emailClose');
const emailForm = document.getElementById('emailForm');
const emailSubmit = document.getElementById('emailSubmit');
const successToast = document.getElementById('successToast');

// ===== Tag Config =====
const TAG_MAP = {
  db: { label: 'DB', cls: 'tag-db' },
  be: { label: 'BE', cls: 'tag-be' },
  os: { label: 'OS', cls: 'tag-os' },
  net: { label: 'Net', cls: 'tag-net' },
  devops: { label: 'DevOps', cls: 'tag-devops' },
  perf: { label: 'Perf', cls: 'tag-perf' },
};

// ===== Render Cards =====
function renderCards() {
  grid.innerHTML = '';
  WALKTHROUGHS.forEach((wt, idx) => {
    const card = document.createElement('div');
    card.className = 'wt-card';
    card.style.animationDelay = `${idx * 0.08}s`;
    card.setAttribute('data-id', wt.id);

    const tagsHTML = wt.tags
      .map(t => {
        const tag = TAG_MAP[t];
        return tag ? `<span class="card-tag ${tag.cls}">${tag.label}</span>` : '';
      })
      .join('');

    card.innerHTML = `
      <div class="card-header">
        <div class="card-icon">${wt.icon}</div>
        <div class="card-meta">
          <div class="card-tags">${tagsHTML}</div>
          <span style="font-size:0.78rem;color:var(--text-muted)">${wt.subtitle}</span>
        </div>
      </div>
      <h3 class="card-title">${wt.title}</h3>
      <p class="card-desc">${wt.desc}</p>
      <div class="card-footer">
        <div class="card-layers">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1L13 4.5L7 8L1 4.5L7 1Z" stroke="currentColor" stroke-width="1.2"/>
            <path d="M1 7L7 10.5L13 7" stroke="currentColor" stroke-width="1.2"/>
            <path d="M1 9.5L7 13L13 9.5" stroke="currentColor" stroke-width="1.2"/>
          </svg>
          ${wt.totalLayers} Layers
        </div>
        <div class="card-arrow">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M3 7h8M8 4l3 3-3 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    `;

    card.addEventListener('click', () => openModal(wt));

    // entrance animation
    card.style.opacity = '0';
    card.style.transform = 'translateY(20px)';
    requestAnimationFrame(() => {
      setTimeout(() => {
        card.style.transition = 'opacity 0.5s ease-out, transform 0.5s ease-out';
        card.style.opacity = '1';
        card.style.transform = 'translateY(0)';
      }, idx * 80);
    });

    grid.appendChild(card);
  });
}

// ===== Modal =====
function openModal(wt) {
  currentWalkthrough = wt;
  currentLayerIndex = 0;
  renderLayer();
  modalOverlay.classList.add('active');
  document.body.style.overflow = 'hidden';
}

function closeModal() {
  modalOverlay.classList.remove('active');
  document.body.style.overflow = '';
  currentWalkthrough = null;
}

function renderLayer() {
  const wt = currentWalkthrough;
  if (!wt) return;

  // Badge
  const firstTag = TAG_MAP[wt.tags[0]];
  if (firstTag) {
    modalBadge.textContent = firstTag.label;
    modalBadge.className = `modal-badge ${firstTag.cls}`;
  }

  // Layer indicator
  layerIndicator.textContent = `Layer ${currentLayerIndex} / ${wt.totalLayers - 1}`;

  // Title & desc
  modalTitle.textContent = wt.title;
  modalDesc.textContent = wt.subtitle;

  // Content
  if (currentLayerIndex < FREE_LAYER_LIMIT && currentLayerIndex < wt.layers.length) {
    const layer = wt.layers[currentLayerIndex];
    modalBody.innerHTML = `
      <div class="layer-content">
        <h3>${layer.title}</h3>
        ${layer.content}
      </div>
    `;
  }

  // Dots
  renderDots();

  // Nav buttons
  navPrev.disabled = currentLayerIndex === 0;

  if (currentLayerIndex >= FREE_LAYER_LIMIT - 1) {
    navNext.innerHTML = `
      다음 보기 🔒
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M6 3l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
    `;
  } else {
    navNext.innerHTML = `
      다음 Layer
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M6 3l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
    `;
  }
  navNext.disabled = false;

  // Actions visibility
  modalActions.style.display = 'flex';
}

function renderDots() {
  const wt = currentWalkthrough;
  if (!wt) return;

  layerDots.innerHTML = '';
  for (let i = 0; i < wt.totalLayers; i++) {
    const dot = document.createElement('div');
    dot.className = 'layer-dot';
    if (i === currentLayerIndex) dot.classList.add('active');
    if (i >= FREE_LAYER_LIMIT) dot.classList.add('locked');
    layerDots.appendChild(dot);
  }
}

function goNext() {
  if (currentLayerIndex >= FREE_LAYER_LIMIT - 1) {
    showPaywall();
    return;
  }
  if (currentLayerIndex < currentWalkthrough.layers.length - 1) {
    currentLayerIndex++;
    renderLayer();
    // Scroll modal body to top
    document.querySelector('.modal-container').scrollTop = 0;
  }
}

function goPrev() {
  if (currentLayerIndex > 0) {
    currentLayerIndex--;
    renderLayer();
    document.querySelector('.modal-container').scrollTop = 0;
  }
}

// ===== Paywall =====
function showPaywall() {
  paywallOverlay.classList.add('active');
}

function hidePaywall() {
  paywallOverlay.classList.remove('active');
}

// ===== TBD =====
function showTBD() {
  tbdOverlay.classList.add('active');
}

function hideTBD() {
  tbdOverlay.classList.remove('active');
}

// ===== Email Collection =====
function showEmailModal() {
  // Hide any overlays that triggered this
  hidePaywall();
  hideTBD();
  emailOverlay.classList.add('active');
}

function hideEmailModal() {
  emailOverlay.classList.remove('active');
}

function handleEmailSubmit(e) {
  e.preventDefault();

  const email = document.getElementById('emailInput').value.trim();
  const name = document.getElementById('nameInput').value.trim();
  const role = document.getElementById('roleSelect').value;
  const interest = document.getElementById('interestSelect').value;

  if (!email) return;

  // Show loading
  const submitText = emailSubmit.querySelector('.submit-text');
  const submitLoading = emailSubmit.querySelector('.submit-loading');
  submitText.style.display = 'none';
  submitLoading.style.display = 'flex';
  emailSubmit.disabled = true;

  // Build log entry
  const logEntry = {
    timestamp: new Date().toISOString(),
    email,
    name: name || '(미입력)',
    role: role || '(미선택)',
    interest: interest || '(미선택)',
    currentWalkthrough: currentWalkthrough ? currentWalkthrough.id : '(없음)',
    userAgent: navigator.userAgent,
  };

  // Save to localStorage
  const existingLogs = JSON.parse(localStorage.getItem('email_logs') || '[]');
  existingLogs.push(logEntry);
  localStorage.setItem('email_logs', JSON.stringify(existingLogs));

  // Also save as downloadable log file
  saveLogToFile(logEntry);

  // Simulate network delay
  setTimeout(() => {
    // Reset form
    submitText.style.display = 'inline';
    submitLoading.style.display = 'none';
    emailSubmit.disabled = false;
    emailForm.reset();

    // Hide modal, show toast
    hideEmailModal();
    showToast();
  }, 800);
}

function saveLogToFile(entry) {
  // Format as log line
  const logLine = `[${entry.timestamp}] email=${entry.email} | name=${entry.name} | role=${entry.role} | interest=${entry.interest} | walkthrough=${entry.currentWalkthrough}\n`;

  // Accumulate in localStorage as a text log
  const existingLog = localStorage.getItem('email_log_text') || '';
  const updatedLog = existingLog + logLine;
  localStorage.setItem('email_log_text', updatedLog);

  // Trigger file download of the log
  downloadLogFile(updatedLog);
}

function downloadLogFile(content) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'email_submissions.log';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function showToast() {
  successToast.classList.add('visible');
  setTimeout(() => {
    successToast.classList.remove('visible');
  }, 3500);
}

// ===== Scroll =====
function scrollToWalkthroughs() {
  document.getElementById('walkthroughs').scrollIntoView({ behavior: 'smooth' });
}

// ===== Keyboard =====
function handleKeydown(e) {
  if (emailOverlay.classList.contains('active')) {
    if (e.key === 'Escape') hideEmailModal();
    return;
  }
  if (paywallOverlay.classList.contains('active')) {
    if (e.key === 'Escape') hidePaywall();
    return;
  }
  if (tbdOverlay.classList.contains('active')) {
    if (e.key === 'Escape') hideTBD();
    return;
  }
  if (modalOverlay.classList.contains('active')) {
    if (e.key === 'Escape') closeModal();
    if (e.key === 'ArrowRight') goNext();
    if (e.key === 'ArrowLeft') goPrev();
    return;
  }
}

// ===== Event Listeners =====
modalClose.addEventListener('click', closeModal);
modalOverlay.addEventListener('click', (e) => {
  if (e.target === modalOverlay) closeModal();
});

navNext.addEventListener('click', goNext);
navPrev.addEventListener('click', goPrev);

// Interview button → TBD
btnInterview.addEventListener('click', showTBD);

// Paywall CTA → email
paywallCta.addEventListener('click', showEmailModal);
paywallDismiss.addEventListener('click', hidePaywall);

// TBD CTA → email
tbdCta.addEventListener('click', showEmailModal);
tbdDismiss.addEventListener('click', hideTBD);

// Email modal
emailClose.addEventListener('click', hideEmailModal);
emailOverlay.addEventListener('click', (e) => {
  if (e.target === emailOverlay) hideEmailModal();
});
emailForm.addEventListener('submit', handleEmailSubmit);

// Keyboard
document.addEventListener('keydown', handleKeydown);

// ===== Intersection Observer for card entrance =====
function setupScrollAnimations() {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.style.opacity = '1';
          entry.target.style.transform = 'translateY(0)';
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.1 }
  );

  document.querySelectorAll('.wt-card').forEach((card) => {
    observer.observe(card);
  });
}

// ===== Touch swipe for modal =====
let touchStartX = 0;
let touchEndX = 0;

function handleTouchStart(e) {
  touchStartX = e.changedTouches[0].screenX;
}

function handleTouchEnd(e) {
  touchEndX = e.changedTouches[0].screenX;
  const diff = touchStartX - touchEndX;
  if (Math.abs(diff) > 60) {
    if (diff > 0) goNext();   // swipe left → next
    else goPrev();            // swipe right → prev
  }
}

const modalContainer = document.querySelector('.modal-container');
modalContainer.addEventListener('touchstart', handleTouchStart, { passive: true });
modalContainer.addEventListener('touchend', handleTouchEnd, { passive: true });

// ===== Init =====
renderCards();

// Delayed animation setup
setTimeout(setupScrollAnimations, 100);

// ===== Admin: View collected emails (for development) =====
// Type viewEmailLogs() in console to see all collected emails
window.viewEmailLogs = function () {
  const logs = JSON.parse(localStorage.getItem('email_logs') || '[]');
  console.table(logs);
  console.log('Raw log text:');
  console.log(localStorage.getItem('email_log_text') || '(empty)');
  return logs;
};
