/**
 * UserCard - Component for displaying user information
 * Supports optional statistics display with custom action buttons
 */
class UserCard {
    /**
     * Create a new UserCard instance
     * @param {Object} user - The user data
     * @param {number} user.id - User ID
     * @param {string} user.deviceToken - Device token
     * @param {string} user.createdAt - Creation date
     * @param {string} user.updatedAt - Last update date
     * @param {Object} options - Display options
     * @param {boolean} options.showDetails - Show detailed information (default: false)
     * @param {boolean} options.showStatistics - Show user statistics (default: false)
     * @param {Object} options.statistics - User statistics data
     * @param {Array} options.actions - Array of action buttons {text, onClick, className}
     * @param {Function} options.onClick - Callback when card is clicked
     */
    constructor(user, options = {}) {
        this.user = user;
        this.options = {
            showDetails: false,
            showStatistics: false,
            statistics: null,
            actions: [],
            onClick: null,
            ...options
        };
    }

    /**
     * Render the user card
     * @returns {HTMLElement} - The card element
     */
    render() {
        const card = document.createElement('div');
        card.className = 'card user-card';
        card.dataset.userId = this.user.id;

        // Add click handler if provided
        if (this.options.onClick && typeof this.options.onClick === 'function') {
            card.style.cursor = 'pointer';
            card.addEventListener('click', (e) => {
                // Don't trigger if clicking on action buttons
                if (!e.target.closest('.card-actions')) {
                    this.options.onClick(this.user);
                }
            });
        }

        // Create header
        const header = this._createHeader();
        card.appendChild(header);

        // Create basic info
        const info = this._createBasicInfo();
        card.appendChild(info);

        // Create detailed info if enabled
        if (this.options.showDetails) {
            const details = this._createDetails();
            card.appendChild(details);
        }

        // Create statistics section if enabled and statistics exist
        if (this.options.showStatistics && this.options.statistics) {
            const stats = this._createStatistics();
            card.appendChild(stats);
        }

        // Create actions if provided
        if (this.options.actions && this.options.actions.length > 0) {
            const actions = this._createActions();
            card.appendChild(actions);
        }

        return card;
    }

    /**
     * Create card header with user ID
     * @private
     * @returns {HTMLElement}
     */
    _createHeader() {
        const header = document.createElement('div');
        header.className = 'card-header';

        const title = document.createElement('h3');
        title.className = 'card-title';
        title.textContent = `User #${this.user.id}`;
        header.appendChild(title);

        return header;
    }

    /**
     * Create basic info section
     * @private
     * @returns {HTMLElement}
     */
    _createBasicInfo() {
        const info = document.createElement('div');
        info.className = 'card-content';

        const items = [];

        // Device token (truncated)
        if (this.user.deviceToken) {
            const tokenDisplay = this._truncateToken(this.user.deviceToken);
            items.push({
                icon: '📱',
                label: 'Device',
                value: tokenDisplay,
                fullValue: this.user.deviceToken
            });
        }

        // Created date
        if (this.user.createdAt) {
            const date = new Date(this.user.createdAt);
            items.push({
                icon: '📅',
                label: 'Joined',
                value: date.toLocaleDateString()
            });
        }

        items.forEach(item => {
            const row = document.createElement('div');
            row.style.marginBottom = 'var(--spacing-sm)';
            row.style.display = 'flex';
            row.style.alignItems = 'center';
            row.style.gap = 'var(--spacing-sm)';

            const icon = document.createElement('span');
            icon.textContent = item.icon;
            row.appendChild(icon);

            const label = document.createElement('span');
            label.className = 'text-muted';
            label.style.minWidth = '60px';
            label.textContent = item.label + ':';
            row.appendChild(label);

            const value = document.createElement('span');
            value.textContent = item.value;
            if (item.fullValue) {
                value.title = item.fullValue;
                value.style.cursor = 'help';
            }
            row.appendChild(value);

            info.appendChild(row);
        });

        return info;
    }

    /**
     * Create detailed info section
     * @private
     * @returns {HTMLElement}
     */
    _createDetails() {
        const details = document.createElement('div');
        details.className = 'card-section';
        details.style.marginTop = 'var(--spacing-md)';
        details.style.padding = 'var(--spacing-md)';
        details.style.background = 'var(--bg-overlay)';
        details.style.borderRadius = 'var(--radius-sm)';

        const label = document.createElement('div');
        label.className = 'text-muted';
        label.style.fontSize = '0.9rem';
        label.style.marginBottom = 'var(--spacing-sm)';
        label.textContent = 'Details';
        details.appendChild(label);

        // Full device token
        if (this.user.deviceToken) {
            const tokenRow = document.createElement('div');
            tokenRow.style.marginBottom = 'var(--spacing-sm)';
            tokenRow.style.fontSize = '0.85rem';
            tokenRow.style.wordBreak = 'break-all';

            const tokenLabel = document.createElement('div');
            tokenLabel.className = 'text-muted';
            tokenLabel.textContent = 'Full Device Token:';
            tokenRow.appendChild(tokenLabel);

            const tokenValue = document.createElement('div');
            tokenValue.style.fontFamily = 'monospace';
            tokenValue.textContent = this.user.deviceToken;
            tokenRow.appendChild(tokenValue);

            details.appendChild(tokenRow);
        }

        // Last updated
        if (this.user.updatedAt) {
            const updatedRow = document.createElement('div');
            updatedRow.style.fontSize = '0.85rem';

            const updatedLabel = document.createElement('span');
            updatedLabel.className = 'text-muted';
            updatedLabel.textContent = 'Last Updated: ';
            updatedRow.appendChild(updatedLabel);

            const updatedValue = document.createElement('span');
            const date = new Date(this.user.updatedAt);
            updatedValue.textContent = date.toLocaleString();
            updatedRow.appendChild(updatedValue);

            details.appendChild(updatedRow);
        }

        return details;
    }

    /**
     * Create statistics section
     * @private
     * @returns {HTMLElement}
     */
    _createStatistics() {
        const stats = this.options.statistics;
        const statsSection = document.createElement('div');
        statsSection.className = 'card-section';
        statsSection.style.marginTop = 'var(--spacing-md)';
        statsSection.style.padding = 'var(--spacing-md)';
        statsSection.style.background = 'var(--bg-overlay)';
        statsSection.style.borderRadius = 'var(--radius-sm)';

        const label = document.createElement('div');
        label.className = 'text-muted';
        label.style.fontSize = '0.9rem';
        label.style.marginBottom = 'var(--spacing-md)';
        label.textContent = '📊 Activity Statistics';
        statsSection.appendChild(label);

        const statsGrid = document.createElement('div');
        statsGrid.style.display = 'grid';
        statsGrid.style.gridTemplateColumns = 'repeat(auto-fit, minmax(120px, 1fr))';
        statsGrid.style.gap = 'var(--spacing-md)';

        const statItems = [];

        if (stats.totalDaysActive !== undefined) {
            statItems.push({
                label: 'Days Active',
                value: stats.totalDaysActive,
                icon: '📅'
            });
        }

        if (stats.activityStreak !== undefined) {
            statItems.push({
                label: 'Streak',
                value: stats.activityStreak,
                icon: '🔥'
            });
        }

        if (stats.lastActiveDate) {
            const date = new Date(stats.lastActiveDate);
            statItems.push({
                label: 'Last Active',
                value: date.toLocaleDateString(),
                icon: '🕐'
            });
        }

        if (stats.archiveCount !== undefined) {
            statItems.push({
                label: 'Archives',
                value: stats.archiveCount,
                icon: '📦'
            });
        }

        statItems.forEach(item => {
            const statItem = document.createElement('div');
            statItem.style.textAlign = 'center';
            statItem.style.padding = 'var(--spacing-sm)';
            statItem.style.background = 'rgba(0, 0, 0, 0.2)';
            statItem.style.borderRadius = 'var(--radius-sm)';

            const icon = document.createElement('div');
            icon.style.fontSize = '1.5rem';
            icon.style.marginBottom = 'var(--spacing-xs)';
            icon.textContent = item.icon;
            statItem.appendChild(icon);

            const value = document.createElement('div');
            value.style.fontSize = '1.2rem';
            value.style.fontWeight = '600';
            value.style.marginBottom = 'var(--spacing-xs)';
            value.textContent = item.value;
            statItem.appendChild(value);

            const labelEl = document.createElement('div');
            labelEl.className = 'text-muted';
            labelEl.style.fontSize = '0.8rem';
            labelEl.textContent = item.label;
            statItem.appendChild(labelEl);

            statsGrid.appendChild(statItem);
        });

        statsSection.appendChild(statsGrid);

        return statsSection;
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
                    action.onClick(this.user);
                }
            };
            actionsSection.appendChild(button);
        });

        return actionsSection;
    }

    /**
     * Truncate device token for display
     * @private
     * @param {string} token - Full device token
     * @returns {string} - Truncated token
     */
    _truncateToken(token) {
        if (!token) return '';
        if (token.length <= 20) return token;
        return token.substring(0, 10) + '...' + token.substring(token.length - 10);
    }

    /**
     * Update the card with new user data
     * @param {Object} user - New user data
     */
    update(user) {
        this.user = { ...this.user, ...user };
        // Re-render would require replacing the element in DOM
        // This is a simple implementation - could be enhanced
    }

    /**
     * Static method to render multiple user cards
     * @param {Array} users - Array of user objects
     * @param {Object} options - Display options
     * @returns {DocumentFragment} - Fragment containing all cards
     */
    static renderMultiple(users, options = {}) {
        const fragment = document.createDocumentFragment();

        users.forEach(user => {
            const card = new UserCard(user, options);
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
        card.className = 'card user-card';
        card.style.minHeight = '150px';
        card.style.display = 'flex';
        card.style.alignItems = 'center';
        card.style.justifyContent = 'center';

        const spinner = document.createElement('div');
        spinner.className = 'spinner';
        card.appendChild(spinner);

        const text = document.createElement('div');
        text.textContent = 'Loading users...';
        text.style.marginTop = 'var(--spacing-md)';
        card.appendChild(text);

        return card;
    }

    /**
     * Static method to create an empty state card
     * @param {string} message - Empty state message
     * @returns {HTMLElement} - Empty state card element
     */
    static createEmptyCard(message = 'No users found') {
        const card = document.createElement('div');
        card.className = 'card';
        card.style.textAlign = 'center';
        card.style.padding = 'var(--spacing-xl)';
        card.style.color = 'var(--text-secondary)';

        const icon = document.createElement('div');
        icon.style.fontSize = '3rem';
        icon.style.marginBottom = 'var(--spacing-md)';
        icon.textContent = '👥';
        card.appendChild(icon);

        const text = document.createElement('div');
        text.textContent = message;
        card.appendChild(text);

        return card;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = UserCard;
}
