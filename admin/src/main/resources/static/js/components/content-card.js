/**
 * ContentCard - Component for displaying content information
 * Supports optional summary and keywords display with custom action buttons
 */
class ContentCard {
    /**
     * Create a new ContentCard instance
     * @param {Object} content - The content data
     * @param {number} content.id - Content ID
     * @param {string} content.title - Content title
     * @param {string} content.newsletterName - Newsletter name
     * @param {string} content.originalUrl - Original URL
     * @param {string} content.publishedAt - Published date
     * @param {string} content.contentText - Content text
     * @param {Array} content.keywords - Array of keywords
     * @param {Object} content.summary - Summary object
     * @param {Object} options - Display options
     * @param {boolean} options.showSummary - Show summary section (default: true)
     * @param {boolean} options.showKeywords - Show keywords section (default: true)
     * @param {boolean} options.showPreview - Show content preview (default: true)
     * @param {number} options.previewLength - Max preview length (default: 200)
     * @param {Array} options.actions - Array of action buttons {text, onClick, className}
     * @param {Function} options.onClick - Callback when card is clicked
     */
    constructor(content, options = {}) {
        this.content = content;
        this.options = {
            showSummary: true,
            showKeywords: true,
            showPreview: true,
            previewLength: 200,
            actions: [],
            onClick: null,
            ...options
        };
    }

    /**
     * Render the content card
     * @returns {HTMLElement} - The card element
     */
    render() {
        const card = document.createElement('div');
        card.className = 'card content-card';
        card.dataset.contentId = this.content.id;

        // Add click handler if provided
        if (this.options.onClick && typeof this.options.onClick === 'function') {
            card.style.cursor = 'pointer';
            card.addEventListener('click', (e) => {
                // Don't trigger if clicking on action buttons
                if (!e.target.closest('.card-actions')) {
                    this.options.onClick(this.content);
                }
            });
        }

        // Create header
        const header = this._createHeader();
        card.appendChild(header);

        // Create metadata
        const meta = this._createMetadata();
        card.appendChild(meta);

        // Create preview if enabled
        if (this.options.showPreview && this.content.contentText) {
            const preview = this._createPreview();
            card.appendChild(preview);
        }

        // Create summary section if enabled and summary exists
        if (this.options.showSummary && this.content.summary) {
            const summary = this._createSummary();
            card.appendChild(summary);
        }

        // Create keywords section if enabled and keywords exist
        if (this.options.showKeywords && this.content.keywords && this.content.keywords.length > 0) {
            const keywords = this._createKeywords();
            card.appendChild(keywords);
        }

        // Create actions if provided
        if (this.options.actions && this.options.actions.length > 0) {
            const actions = this._createActions();
            card.appendChild(actions);
        }

        return card;
    }

    /**
     * Create card header with title
     * @private
     * @returns {HTMLElement}
     */
    _createHeader() {
        const header = document.createElement('div');
        header.className = 'card-header';

        const title = document.createElement('h3');
        title.className = 'card-title';
        title.textContent = this.content.title || 'Untitled';
        header.appendChild(title);

        return header;
    }

    /**
     * Create metadata section
     * @private
     * @returns {HTMLElement}
     */
    _createMetadata() {
        const meta = document.createElement('div');
        meta.className = 'card-subtitle';

        const parts = [];

        if (this.content.newsletterName) {
            parts.push(`📧 ${this.content.newsletterName}`);
        }

        if (this.content.publishedAt) {
            const date = new Date(this.content.publishedAt);
            parts.push(`📅 ${date.toLocaleDateString()}`);
        }

        if (this.content.originalUrl) {
            const url = new URL(this.content.originalUrl);
            parts.push(`🔗 ${url.hostname}`);
        }

        meta.textContent = parts.join(' • ');

        return meta;
    }

    /**
     * Create content preview
     * @private
     * @returns {HTMLElement}
     */
    _createPreview() {
        const preview = document.createElement('div');
        preview.className = 'card-content';

        let text = this.content.contentText || '';
        if (text.length > this.options.previewLength) {
            text = text.substring(0, this.options.previewLength) + '...';
        }

        preview.textContent = text;

        return preview;
    }

    /**
     * Create summary section
     * @private
     * @returns {HTMLElement}
     */
    _createSummary() {
        const summarySection = document.createElement('div');
        summarySection.className = 'card-section';
        summarySection.style.marginTop = 'var(--spacing-md)';
        summarySection.style.padding = 'var(--spacing-md)';
        summarySection.style.background = 'var(--bg-overlay)';
        summarySection.style.borderRadius = 'var(--radius-sm)';

        const label = document.createElement('div');
        label.className = 'text-muted';
        label.style.fontSize = '0.9rem';
        label.style.marginBottom = 'var(--spacing-sm)';
        label.textContent = '📝 Summary';
        summarySection.appendChild(label);

        if (this.content.summary.title) {
            const title = document.createElement('div');
            title.style.fontWeight = '600';
            title.style.marginBottom = 'var(--spacing-xs)';
            title.textContent = this.content.summary.title;
            summarySection.appendChild(title);
        }

        if (this.content.summary.summaryContent) {
            const content = document.createElement('div');
            content.style.fontSize = '0.9rem';
            content.style.lineHeight = '1.5';
            content.textContent = this.content.summary.summaryContent;
            summarySection.appendChild(content);
        }

        return summarySection;
    }

    /**
     * Create keywords section
     * @private
     * @returns {HTMLElement}
     */
    _createKeywords() {
        const keywordsSection = document.createElement('div');
        keywordsSection.className = 'card-section';
        keywordsSection.style.marginTop = 'var(--spacing-md)';

        const label = document.createElement('div');
        label.className = 'text-muted';
        label.style.fontSize = '0.9rem';
        label.style.marginBottom = 'var(--spacing-sm)';
        label.textContent = '🏷️ Keywords';
        keywordsSection.appendChild(label);

        const keywordsContainer = document.createElement('div');
        keywordsContainer.style.display = 'flex';
        keywordsContainer.style.flexWrap = 'wrap';
        keywordsContainer.style.gap = 'var(--spacing-sm)';

        this.content.keywords.forEach(keyword => {
            const badge = document.createElement('span');
            badge.className = 'badge badge-primary';
            badge.textContent = typeof keyword === 'string' ? keyword : keyword.name || keyword.keyword;
            keywordsContainer.appendChild(badge);
        });

        keywordsSection.appendChild(keywordsContainer);

        return keywordsSection;
    }

    /**
     * Create actions section
     * @private
     * @returns {HTMLElement}
     */
    _createActions() {
        const actionsSection = document.createElement('div');
        actionsSection.className = 'card-actions';
        actionsSection.style.marginTop = 'var(--spacing-md)';
        actionsSection.style.paddingTop = 'var(--spacing-md)';
        actionsSection.style.borderTop = '1px solid var(--border-primary)';
        actionsSection.style.display = 'flex';
        actionsSection.style.gap = 'var(--spacing-sm)';
        actionsSection.style.flexWrap = 'wrap';

        this.options.actions.forEach(action => {
            const button = document.createElement('button');
            button.className = `btn btn-sm ${action.className || 'btn-primary'}`;
            button.textContent = action.text;
            button.onclick = (e) => {
                e.stopPropagation(); // Prevent card click
                if (action.onClick && typeof action.onClick === 'function') {
                    action.onClick(this.content);
                }
            };
            actionsSection.appendChild(button);
        });

        return actionsSection;
    }

    /**
     * Update the card with new content data
     * @param {Object} content - New content data
     */
    update(content) {
        this.content = { ...this.content, ...content };
        // Re-render would require replacing the element in DOM
        // This is a simple implementation - could be enhanced
    }

    /**
     * Static method to render multiple content cards
     * @param {Array} contents - Array of content objects
     * @param {Object} options - Display options
     * @returns {DocumentFragment} - Fragment containing all cards
     */
    static renderMultiple(contents, options = {}) {
        const fragment = document.createDocumentFragment();

        contents.forEach(content => {
            const card = new ContentCard(content, options);
            fragment.appendChild(card.render());
        });

        return fragment;
    }

    /**
     * Static method to create a loading placeholder card
     * @returns {HTMLElement} - Loading card element
     */
    static createLoadingCard() {
        const card = document.createElement('div');
        card.className = 'card content-card';
        card.style.minHeight = '200px';
        card.style.display = 'flex';
        card.style.alignItems = 'center';
        card.style.justifyContent = 'center';

        const spinner = document.createElement('div');
        spinner.className = 'spinner';
        card.appendChild(spinner);

        const text = document.createElement('div');
        text.textContent = 'Loading...';
        text.style.marginTop = 'var(--spacing-md)';
        card.appendChild(text);

        return card;
    }

    /**
     * Static method to create an empty state card
     * @param {string} message - Empty state message
     * @returns {HTMLElement} - Empty state card element
     */
    static createEmptyCard(message = 'No content available') {
        const card = document.createElement('div');
        card.className = 'card';
        card.style.textAlign = 'center';
        card.style.padding = 'var(--spacing-xl)';
        card.style.color = 'var(--text-secondary)';

        const icon = document.createElement('div');
        icon.style.fontSize = '3rem';
        icon.style.marginBottom = 'var(--spacing-md)';
        icon.textContent = '📭';
        card.appendChild(icon);

        const text = document.createElement('div');
        text.textContent = message;
        card.appendChild(text);

        return card;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ContentCard;
}
