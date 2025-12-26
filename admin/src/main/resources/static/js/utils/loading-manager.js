/**
 * LoadingManager - Utility for managing loading states
 * Provides consistent loading spinner display across components
 */
class LoadingManager {
    /**
     * Show loading spinner in a container
     * @param {string|HTMLElement} container - Container element or ID
     * @param {string} [message='데이터를 불러오는 중...'] - Loading message
     */
    static showLoading(container, message = '데이터를 불러오는 중...') {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            console.error('Container not found:', container);
            return;
        }

        element.innerHTML = `
            <div class="loading">
                <div class="spinner"></div>
                <p>${message}</p>
            </div>
        `;
    }

    /**
     * Show error message in a container
     * @param {string|HTMLElement} container - Container element or ID
     * @param {string} [message='데이터를 불러오는 중 오류가 발생했습니다.'] - Error message
     * @param {Function} [onRetry] - Optional retry callback
     */
    static showError(container, message = '데이터를 불러오는 중 오류가 발생했습니다.', onRetry = null) {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            console.error('Container not found:', container);
            return;
        }

        const retryButton = onRetry 
            ? `<button class="retry-btn" style="
                margin-top: 1rem; 
                padding: 0.75rem 1.5rem; 
                background: rgba(0, 212, 255, 0.2); 
                border: 1px solid #00d4ff; 
                border-radius: 8px; 
                color: #00d4ff; 
                cursor: pointer; 
                transition: all 0.3s ease;
                font-weight: 600;
                font-size: 0.95rem;
            ">🔄 다시 시도</button>`
            : '';

        element.innerHTML = `
            <div style="
                text-align: center; 
                padding: 2rem; 
                color: #f87171;
                background: rgba(248, 113, 113, 0.05);
                border-radius: 12px;
                border: 1px solid rgba(248, 113, 113, 0.2);
            ">
                <div style="font-size: 3rem; margin-bottom: 1rem; animation: shake 0.5s;">⚠️</div>
                <p style="font-size: 1rem; font-weight: 500; margin-bottom: 0.5rem;">오류가 발생했습니다</p>
                <p style="font-size: 0.9rem; color: #a0aec0; margin-bottom: 0.5rem;">${message}</p>
                ${retryButton}
            </div>
        `;

        // Add shake animation if not already present
        if (!document.querySelector('#error-animations')) {
            const style = document.createElement('style');
            style.id = 'error-animations';
            style.textContent = `
                @keyframes shake {
                    0%, 100% { transform: translateX(0); }
                    10%, 30%, 50%, 70%, 90% { transform: translateX(-5px); }
                    20%, 40%, 60%, 80% { transform: translateX(5px); }
                }
            `;
            document.head.appendChild(style);
        }

        if (onRetry) {
            const btn = element.querySelector('.retry-btn');
            if (btn) {
                btn.addEventListener('click', () => {
                    // Disable button during retry
                    btn.disabled = true;
                    btn.style.opacity = '0.6';
                    btn.style.cursor = 'not-allowed';
                    btn.textContent = '재시도 중...';
                    
                    // Call retry function
                    onRetry();
                });
                btn.addEventListener('mouseenter', function() {
                    if (!this.disabled) {
                        this.style.background = 'rgba(0, 212, 255, 0.3)';
                        this.style.transform = 'translateY(-2px)';
                        this.style.boxShadow = '0 4px 12px rgba(0, 212, 255, 0.3)';
                    }
                });
                btn.addEventListener('mouseleave', function() {
                    if (!this.disabled) {
                        this.style.background = 'rgba(0, 212, 255, 0.2)';
                        this.style.transform = 'translateY(0)';
                        this.style.boxShadow = 'none';
                    }
                });
            }
        }
    }

    /**
     * Show empty state message in a container
     * @param {string|HTMLElement} container - Container element or ID
     * @param {string} [message='데이터가 없습니다.'] - Empty state message
     */
    static showEmpty(container, message = '데이터가 없습니다.') {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            console.error('Container not found:', container);
            return;
        }

        element.innerHTML = `
            <div style="text-align: center; padding: 2rem; color: #a0aec0;">
                <div style="font-size: 2rem; margin-bottom: 1rem;">📭</div>
                <p>${message}</p>
            </div>
        `;
    }

    /**
     * Clear container content
     * @param {string|HTMLElement} container - Container element or ID
     */
    static clear(container) {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            console.error('Container not found:', container);
            return;
        }

        element.innerHTML = '';
    }

    /**
     * Check if container is currently showing loading state
     * @param {string|HTMLElement} container - Container element or ID
     * @returns {boolean} True if loading state is shown
     */
    static isLoading(container) {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            return false;
        }

        return element.querySelector('.loading') !== null;
    }

    /**
     * Create a loading overlay for the entire page
     * @param {string} [message='처리 중...'] - Loading message
     * @returns {HTMLElement} Overlay element
     */
    static createOverlay(message = '처리 중...') {
        const overlay = document.createElement('div');
        overlay.id = 'loading-overlay';
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.8);
            backdrop-filter: blur(5px);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        `;
        
        overlay.innerHTML = `
            <div style="text-align: center; color: #ffffff;">
                <div class="spinner" style="margin: 0 auto 1rem;"></div>
                <p style="font-size: 1.1rem;">${message}</p>
            </div>
        `;
        
        document.body.appendChild(overlay);
        return overlay;
    }

    /**
     * Remove loading overlay
     */
    static removeOverlay() {
        const overlay = document.getElementById('loading-overlay');
        if (overlay) {
            overlay.remove();
        }
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = LoadingManager;
}
