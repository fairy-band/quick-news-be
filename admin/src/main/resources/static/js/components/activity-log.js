/**
 * ActivityLog Component
 * Displays recent activities with icons, colors, and timestamps
 */
class ActivityLog {
    /**
     * Create an activity log component
     * @param {string} containerId - ID of the container element
     * @param {Array<Object>} activities - Array of activity objects
     * @param {Object} [options] - Optional configuration
     * @param {boolean} [options.useRelativeTime=true] - Use relative time format
     * @param {number} [options.maxHeight=400] - Maximum height in pixels
     */
    constructor(containerId, activities, options = {}) {
        this.containerId = containerId;
        this.activities = activities;
        this.options = {
            useRelativeTime: options.useRelativeTime !== false,
            maxHeight: options.maxHeight || 400
        };
    }

    /**
     * Get icon for activity type
     * @param {string} type - Activity type
     * @returns {string} Emoji icon
     */
    static getActivityIcon(type) {
        const icons = {
            'CONTENT_CREATED': '📝',
            'SUMMARY_GENERATED': '🤖',
            'KEYWORD_MAPPED': '🔑',
            'CONTENT_EXPOSED': '👁️'
        };
        return icons[type] || '📌';
    }

    /**
     * Get color for activity type
     * @param {string} type - Activity type
     * @returns {string} CSS color value
     */
    static getActivityColor(type) {
        const colors = {
            'CONTENT_CREATED': '#00d4ff',
            'SUMMARY_GENERATED': '#10b981',
            'KEYWORD_MAPPED': '#f59e0b',
            'CONTENT_EXPOSED': '#8b5cf6'
        };
        return colors[type] || '#a0aec0';
    }

    /**
     * Get display text for activity type
     * @param {string} type - Activity type
     * @returns {string} Korean display text
     */
    static getActivityTypeText(type) {
        const texts = {
            'CONTENT_CREATED': '콘텐츠 생성',
            'SUMMARY_GENERATED': '요약 생성',
            'KEYWORD_MAPPED': '키워드 매핑',
            'CONTENT_EXPOSED': '콘텐츠 노출'
        };
        return texts[type] || type;
    }

    /**
     * Format timestamp as relative time (e.g., "5분 전")
     * @param {string} timestamp - ISO timestamp string
     * @returns {string} Formatted relative time
     */
    static formatRelativeTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (minutes < 1) return '방금 전';
        if (minutes < 60) return `${minutes}분 전`;
        if (hours < 24) return `${hours}시간 전`;
        if (days < 7) return `${days}일 전`;
        
        return date.toLocaleDateString('ko-KR');
    }

    /**
     * Format timestamp as absolute time
     * @param {string} timestamp - ISO timestamp string
     * @returns {string} Formatted absolute time
     */
    static formatAbsoluteTime(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleString('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    /**
     * Format timestamp based on options
     * @param {string} timestamp - ISO timestamp string
     * @returns {string} Formatted time
     */
    formatTimestamp(timestamp) {
        if (this.options.useRelativeTime) {
            return ActivityLog.formatRelativeTime(timestamp);
        } else {
            return ActivityLog.formatAbsoluteTime(timestamp);
        }
    }

    /**
     * Render a single activity item
     * @param {Object} activity - Activity object
     * @returns {string} HTML string for activity item
     */
    renderActivityItem(activity) {
        const icon = ActivityLog.getActivityIcon(activity.type);
        const color = ActivityLog.getActivityColor(activity.type);
        const typeText = ActivityLog.getActivityTypeText(activity.type);
        const formattedTime = this.formatTimestamp(activity.timestamp);
        const details = activity.details ? `<div class="activity-details" style="color: #718096; font-size: 0.85rem; margin-top: 0.25rem;">${activity.details}</div>` : '';

        return `
            <div class="activity-item">
                <div class="activity-icon" style="color: ${color};">${icon}</div>
                <div class="activity-content">
                    <div class="activity-type" style="color: ${color};">${typeText}</div>
                    <div class="activity-title">${this.escapeHtml(activity.contentTitle)}</div>
                    ${details}
                    <div class="activity-time">${formattedTime}</div>
                </div>
            </div>
        `;
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} text - Text to escape
     * @returns {string} Escaped text
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Render the complete activity log
     * @returns {string} HTML string for activity log
     */
    render() {
        if (!this.activities || this.activities.length === 0) {
            return '<p style="color: #a0aec0; text-align: center; padding: 2rem;">최근 활동이 없습니다.</p>';
        }

        const activitiesHtml = this.activities
            .map(activity => this.renderActivityItem(activity))
            .join('');

        return activitiesHtml;
    }

    /**
     * Update the activity log with new data
     * @param {Array<Object>} activities - New activities array
     */
    update(activities) {
        this.activities = activities;
        const container = document.getElementById(this.containerId);
        if (container) {
            container.innerHTML = this.render();
        }
    }

    /**
     * Create and render activity log to container
     * @param {string} containerId - ID of container element
     * @param {Array<Object>} activities - Array of activity objects
     * @param {Object} [options] - Optional configuration
     * @returns {ActivityLog} ActivityLog instance
     */
    static create(containerId, activities, options = {}) {
        const activityLog = new ActivityLog(containerId, activities, options);
        const container = document.getElementById(containerId);
        
        if (!container) {
            console.error(`Container with id "${containerId}" not found`);
            return activityLog;
        }

        container.innerHTML = activityLog.render();
        return activityLog;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { ActivityLog };
}
