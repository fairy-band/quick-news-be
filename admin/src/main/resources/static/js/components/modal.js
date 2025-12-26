/**
 * Modal - Dialog component for displaying content in an overlay
 * Supports keyboard navigation (ESC to close) and custom actions
 */
class Modal {
    /**
     * Create a new Modal instance
     * @param {string} title - The modal title
     * @param {string|HTMLElement} content - The modal content (HTML string or element)
     * @param {Object} options - Additional options
     * @param {Array} options.actions - Array of action buttons {text, onClick, className}
     * @param {boolean} options.closeOnOverlayClick - Close modal when clicking overlay (default: true)
     * @param {boolean} options.closeOnEscape - Close modal when pressing ESC (default: true)
     * @param {Function} options.onClose - Callback when modal is closed
     */
    constructor(title, content, options = {}) {
        this.title = title;
        this.content = content;
        this.options = {
            actions: [],
            closeOnOverlayClick: true,
            closeOnEscape: true,
            onClose: null,
            ...options
        };

        this.overlay = null;
        this.modalElement = null;
        this._boundKeyHandler = null;
    }

    /**
     * Show the modal
     */
    show() {
        // Create overlay if it doesn't exist
        if (!this.overlay) {
            this._createModal();
        }

        // Add to DOM
        document.body.appendChild(this.overlay);

        // Trigger reflow to enable transition
        this.overlay.offsetHeight;

        // Show modal
        this.overlay.classList.add('active');

        // Setup keyboard handler
        if (this.options.closeOnEscape) {
            this._boundKeyHandler = this._handleKeyPress.bind(this);
            document.addEventListener('keydown', this._boundKeyHandler);
        }

        // Prevent body scroll
        document.body.style.overflow = 'hidden';
    }

    /**
     * Hide the modal
     */
    hide() {
        if (!this.overlay) return;

        // Remove active class
        this.overlay.classList.remove('active');

        // Remove from DOM after transition
        setTimeout(() => {
            if (this.overlay && this.overlay.parentElement) {
                this.overlay.parentElement.removeChild(this.overlay);
            }
        }, 300);

        // Remove keyboard handler
        if (this._boundKeyHandler) {
            document.removeEventListener('keydown', this._boundKeyHandler);
            this._boundKeyHandler = null;
        }

        // Restore body scroll
        document.body.style.overflow = '';

        // Call onClose callback
        if (this.options.onClose && typeof this.options.onClose === 'function') {
            this.options.onClose();
        }
    }

    /**
     * Update modal content
     * @param {string|HTMLElement} content - New content
     */
    setContent(content) {
        this.content = content;
        
        if (this.modalElement) {
            const bodyElement = this.modalElement.querySelector('.modal-body');
            if (bodyElement) {
                if (typeof content === 'string') {
                    bodyElement.innerHTML = content;
                } else if (content instanceof HTMLElement) {
                    bodyElement.innerHTML = '';
                    bodyElement.appendChild(content);
                }
            }
        }
    }

    /**
     * Update modal title
     * @param {string} title - New title
     */
    setTitle(title) {
        this.title = title;
        
        if (this.modalElement) {
            const titleElement = this.modalElement.querySelector('.modal-title');
            if (titleElement) {
                titleElement.textContent = title;
            }
        }
    }

    /**
     * Create the modal DOM structure
     * @private
     */
    _createModal() {
        // Create overlay
        this.overlay = document.createElement('div');
        this.overlay.className = 'modal-overlay';

        // Handle overlay click
        if (this.options.closeOnOverlayClick) {
            this.overlay.addEventListener('click', (e) => {
                if (e.target === this.overlay) {
                    this.hide();
                }
            });
        }

        // Create modal
        this.modalElement = document.createElement('div');
        this.modalElement.className = 'modal';

        // Create header
        const header = document.createElement('div');
        header.className = 'modal-header';

        const titleElement = document.createElement('h2');
        titleElement.className = 'modal-title';
        titleElement.textContent = this.title;
        header.appendChild(titleElement);

        const closeButton = document.createElement('button');
        closeButton.className = 'modal-close';
        closeButton.innerHTML = '×';
        closeButton.onclick = () => this.hide();
        header.appendChild(closeButton);

        this.modalElement.appendChild(header);

        // Create body
        const body = document.createElement('div');
        body.className = 'modal-body';

        if (typeof this.content === 'string') {
            body.innerHTML = this.content;
        } else if (this.content instanceof HTMLElement) {
            body.appendChild(this.content);
        }

        this.modalElement.appendChild(body);

        // Create footer with actions
        if (this.options.actions && this.options.actions.length > 0) {
            const footer = document.createElement('div');
            footer.className = 'modal-footer';

            this.options.actions.forEach(action => {
                const button = document.createElement('button');
                button.className = `btn ${action.className || 'btn-primary'}`;
                button.textContent = action.text;
                button.onclick = () => {
                    if (action.onClick && typeof action.onClick === 'function') {
                        action.onClick(this);
                    }
                };
                footer.appendChild(button);
            });

            this.modalElement.appendChild(footer);
        }

        this.overlay.appendChild(this.modalElement);
    }

    /**
     * Handle keyboard events
     * @private
     * @param {KeyboardEvent} e - The keyboard event
     */
    _handleKeyPress(e) {
        if (e.key === 'Escape' || e.keyCode === 27) {
            this.hide();
        }
    }

    /**
     * Static method to quickly show a confirmation dialog
     * @param {string} title - Dialog title
     * @param {string} message - Dialog message
     * @param {Function} onConfirm - Callback when confirmed
     * @param {Object} options - Additional options
     * @returns {Modal} - The modal instance
     */
    static confirm(title, message, onConfirm, options = {}) {
        const modal = new Modal(title, `<p>${message}</p>`, {
            actions: [
                {
                    text: options.cancelText || 'Cancel',
                    className: 'btn-secondary',
                    onClick: (m) => m.hide()
                },
                {
                    text: options.confirmText || 'Confirm',
                    className: options.confirmClassName || 'btn-primary',
                    onClick: (m) => {
                        if (onConfirm && typeof onConfirm === 'function') {
                            onConfirm();
                        }
                        m.hide();
                    }
                }
            ],
            ...options
        });

        modal.show();
        return modal;
    }

    /**
     * Static method to quickly show an alert dialog
     * @param {string} title - Dialog title
     * @param {string} message - Dialog message
     * @param {Object} options - Additional options
     * @returns {Modal} - The modal instance
     */
    static alert(title, message, options = {}) {
        const modal = new Modal(title, `<p>${message}</p>`, {
            actions: [
                {
                    text: options.buttonText || 'OK',
                    className: 'btn-primary',
                    onClick: (m) => m.hide()
                }
            ],
            ...options
        });

        modal.show();
        return modal;
    }

    /**
     * Static method to quickly show a prompt dialog
     * @param {string} title - Dialog title
     * @param {string} message - Dialog message
     * @param {Function} onSubmit - Callback with input value when submitted
     * @param {Object} options - Additional options
     * @returns {Modal} - The modal instance
     */
    static prompt(title, message, onSubmit, options = {}) {
        const inputId = 'modal-prompt-input-' + Date.now();
        const content = `
            <p>${message}</p>
            <div class="form-group">
                <input 
                    type="${options.inputType || 'text'}" 
                    id="${inputId}" 
                    class="form-control" 
                    placeholder="${options.placeholder || ''}"
                    value="${options.defaultValue || ''}"
                />
            </div>
        `;

        const modal = new Modal(title, content, {
            actions: [
                {
                    text: options.cancelText || 'Cancel',
                    className: 'btn-secondary',
                    onClick: (m) => m.hide()
                },
                {
                    text: options.submitText || 'Submit',
                    className: 'btn-primary',
                    onClick: (m) => {
                        const input = document.getElementById(inputId);
                        if (input && onSubmit && typeof onSubmit === 'function') {
                            onSubmit(input.value);
                        }
                        m.hide();
                    }
                }
            ],
            ...options
        });

        modal.show();

        // Focus input after modal is shown
        setTimeout(() => {
            const input = document.getElementById(inputId);
            if (input) {
                input.focus();
                
                // Submit on Enter key
                input.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        if (onSubmit && typeof onSubmit === 'function') {
                            onSubmit(input.value);
                        }
                        modal.hide();
                    }
                });
            }
        }, 100);

        return modal;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = Modal;
}
