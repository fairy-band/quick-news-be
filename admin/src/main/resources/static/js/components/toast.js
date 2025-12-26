/**
 * Toast - Notification component for displaying temporary messages
 * Supports success, error, and info message types with auto-dismiss
 */
class Toast {
    /**
     * Initialize the Toast system
     * Creates the toast container if it doesn't exist
     */
    static init() {
        if (!document.querySelector('.toast-container')) {
            const container = document.createElement('div');
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
    }

    /**
     * Show a toast notification
     * @param {string} message - The message to display
     * @param {string} type - The type of toast ('success', 'error', 'info')
     * @param {Object} options - Additional options
     * @param {string} options.title - Optional title for the toast
     * @param {number} options.duration - Duration in ms before auto-dismiss (default: 5000)
     * @param {boolean} options.dismissible - Whether the toast can be manually dismissed (default: true)
     * @returns {HTMLElement} - The toast element
     */
    static show(message, type = 'info', options = {}) {
        // Ensure container exists
        this.init();

        const {
            title = null,
            duration = 5000,
            dismissible = true
        } = options;

        // Create toast element
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        // Add icon based on type
        const icon = this._getIcon(type);
        const iconElement = document.createElement('div');
        iconElement.className = 'toast-icon';
        iconElement.textContent = icon;
        toast.appendChild(iconElement);

        // Add content
        const contentElement = document.createElement('div');
        contentElement.className = 'toast-content';

        if (title) {
            const titleElement = document.createElement('div');
            titleElement.className = 'toast-title';
            titleElement.textContent = title;
            contentElement.appendChild(titleElement);
        }

        const messageElement = document.createElement('div');
        messageElement.className = 'toast-message';
        messageElement.textContent = message;
        contentElement.appendChild(messageElement);

        toast.appendChild(contentElement);

        // Add close button if dismissible
        if (dismissible) {
            const closeButton = document.createElement('button');
            closeButton.className = 'modal-close';
            closeButton.innerHTML = '×';
            closeButton.onclick = () => this.dismiss(toast);
            toast.appendChild(closeButton);
        }

        // Add to container
        const container = document.querySelector('.toast-container');
        container.appendChild(toast);

        // Auto-dismiss after duration
        if (duration > 0) {
            setTimeout(() => {
                this.dismiss(toast);
            }, duration);
        }

        return toast;
    }

    /**
     * Show a success toast
     * @param {string} message - The message to display
     * @param {Object} options - Additional options
     * @returns {HTMLElement} - The toast element
     */
    static success(message, options = {}) {
        return this.show(message, 'success', {
            title: options.title || 'Success',
            ...options
        });
    }

    /**
     * Show an error toast
     * @param {string} message - The message to display
     * @param {Object} options - Additional options
     * @returns {HTMLElement} - The toast element
     */
    static error(message, options = {}) {
        return this.show(message, 'error', {
            title: options.title || 'Error',
            duration: options.duration || 7000, // Errors stay longer
            ...options
        });
    }

    /**
     * Show an info toast
     * @param {string} message - The message to display
     * @param {Object} options - Additional options
     * @returns {HTMLElement} - The toast element
     */
    static info(message, options = {}) {
        return this.show(message, 'info', {
            title: options.title || 'Info',
            ...options
        });
    }

    /**
     * Dismiss a toast notification
     * @param {HTMLElement} toast - The toast element to dismiss
     */
    static dismiss(toast) {
        if (!toast || !toast.parentElement) return;

        // Add fade-out animation
        toast.style.animation = 'slideOut 0.3s ease';
        
        setTimeout(() => {
            if (toast.parentElement) {
                toast.parentElement.removeChild(toast);
            }
        }, 300);
    }

    /**
     * Dismiss all toast notifications
     */
    static dismissAll() {
        const container = document.querySelector('.toast-container');
        if (container) {
            const toasts = container.querySelectorAll('.toast');
            toasts.forEach(toast => this.dismiss(toast));
        }
    }

    /**
     * Get icon for toast type
     * @private
     * @param {string} type - The toast type
     * @returns {string} - The icon character
     */
    static _getIcon(type) {
        const icons = {
            success: '✓',
            error: '✕',
            info: 'ℹ'
        };
        return icons[type] || icons.info;
    }
}

// Add slideOut animation to CSS if not already present
if (!document.querySelector('#toast-animations')) {
    const style = document.createElement('style');
    style.id = 'toast-animations';
    style.textContent = `
        @keyframes slideOut {
            from {
                transform: translateX(0);
                opacity: 1;
            }
            to {
                transform: translateX(100%);
                opacity: 0;
            }
        }
    `;
    document.head.appendChild(style);
}

// Initialize on page load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => Toast.init());
} else {
    Toast.init();
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = Toast;
}
