/**
 * QuickActionPanel Component
 * Displays quick action buttons for common admin tasks
 */
class QuickActionButton {
    /**
     * Create a quick action button
     * @param {Object} config - Configuration object
     * @param {string} config.icon - Emoji or icon to display
     * @param {string} config.label - Button label text
     * @param {string} config.href - URL to navigate to
     * @param {string} [config.gradient] - Optional gradient colors (default: purple gradient)
     */
    constructor(config) {
        this.icon = config.icon;
        this.label = config.label;
        this.href = config.href;
        this.gradient = config.gradient || 'linear-gradient(45deg, #667eea, #764ba2)';
    }

    /**
     * Render the quick action button as HTML string
     * @returns {string} HTML string for the button
     */
    render() {
        return `
            <a href="${this.href}" class="quick-action-btn" style="background: ${this.gradient};">
                ${this.icon} ${this.label}
            </a>
        `;
    }

    /**
     * Create a button element and append to container
     * @param {HTMLElement} container - Container element to append to
     */
    appendTo(container) {
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = this.render();
        container.appendChild(tempDiv.firstElementChild);
    }
}

/**
 * QuickActionPanel
 * Panel containing multiple quick action buttons
 */
class QuickActionPanel {
    /**
     * Create a quick action panel
     * @param {Array<QuickActionButton>} buttons - Array of QuickActionButton instances
     */
    constructor(buttons) {
        this.buttons = buttons;
    }

    /**
     * Render the panel as HTML string
     * @returns {string} HTML string for the panel
     */
    render() {
        const buttonsHtml = this.buttons.map(btn => btn.render()).join('');
        return `
            <div class="quick-actions">
                ${buttonsHtml}
            </div>
        `;
    }

    /**
     * Render the panel to a container element
     * @param {HTMLElement|string} container - Container element or element ID
     */
    renderTo(container) {
        const element = typeof container === 'string' 
            ? document.getElementById(container) 
            : container;
        
        if (!element) {
            console.error('QuickActionPanel: Container not found');
            return;
        }

        element.innerHTML = this.render();
    }

    /**
     * Add a button to the panel
     * @param {QuickActionButton} button - Button to add
     */
    addButton(button) {
        this.buttons.push(button);
    }

    /**
     * Remove a button by index
     * @param {number} index - Index of button to remove
     */
    removeButton(index) {
        if (index >= 0 && index < this.buttons.length) {
            this.buttons.splice(index, 1);
        }
    }
}

/**
 * QuickActionPanelFactory
 * Factory for creating quick action panels with predefined buttons
 */
class QuickActionPanelFactory {
    /**
     * Create default admin dashboard quick action panel
     * @returns {QuickActionPanel} Panel with default admin actions
     */
    static createDefaultPanel() {
        const buttons = [
            new QuickActionButton({
                icon: '📝',
                label: '요약 없는 콘텐츠',
                href: './contents?filter=noSummary',
                gradient: 'linear-gradient(45deg, #667eea, #764ba2)'
            }),
            new QuickActionButton({
                icon: '🚫',
                label: '노출 안된 콘텐츠',
                href: './contents?filter=notExposed',
                gradient: 'linear-gradient(45deg, #f093fb, #f5576c)'
            }),
            new QuickActionButton({
                icon: '➕',
                label: '새 콘텐츠 추가',
                href: './contents#add-content',
                gradient: 'linear-gradient(45deg, #4facfe, #00f2fe)'
            }),
            new QuickActionButton({
                icon: '🔑',
                label: '키워드 관리',
                href: './keywords',
                gradient: 'linear-gradient(45deg, #43e97b, #38f9d7)'
            })
        ];

        return new QuickActionPanel(buttons);
    }

    /**
     * Create and render default panel to container
     * @param {HTMLElement|string} container - Container element or element ID
     * @returns {QuickActionPanel} Created panel instance
     */
    static renderDefaultPanel(container) {
        const panel = this.createDefaultPanel();
        panel.renderTo(container);
        return panel;
    }

    /**
     * Create custom panel with specified buttons
     * @param {Array<Object>} buttonConfigs - Array of button configuration objects
     * @returns {QuickActionPanel} Panel with custom buttons
     */
    static createCustomPanel(buttonConfigs) {
        const buttons = buttonConfigs.map(config => new QuickActionButton(config));
        return new QuickActionPanel(buttons);
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { QuickActionButton, QuickActionPanel, QuickActionPanelFactory };
}
