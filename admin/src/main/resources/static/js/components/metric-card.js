/**
 * MetricCard Component
 * Displays a single metric with icon, title, and value
 */
class MetricCard {
    /**
     * Create a metric card
     * @param {Object} config - Configuration object
     * @param {string} config.icon - Emoji or icon to display
     * @param {string} config.title - Title of the metric
     * @param {number} config.value - Numeric value of the metric
     * @param {string} [config.trend] - Optional trend indicator (up/down/neutral)
     * @param {string} [config.subtitle] - Optional subtitle text
     * @param {string} [config.status] - Optional status (normal/warning/danger)
     */
    constructor(config) {
        this.icon = config.icon;
        this.title = config.title;
        this.value = config.value;
        this.trend = config.trend;
        this.subtitle = config.subtitle;
        this.status = config.status;
    }

    /**
     * Render the metric card as HTML string
     * @returns {string} HTML string for the metric card
     */
    render() {
        const formattedValue = this.formatValue(this.value);
        const trendHtml = this.trend ? this.renderTrend() : '';
        const subtitleHtml = this.subtitle ? `<div class="metric-subtitle">${this.subtitle}</div>` : '';
        const statusClass = this.status ? `metric-card-${this.status}` : '';
        
        return `
            <div class="metric-card ${statusClass}">
                <div class="metric-icon">${this.icon}</div>
                <div class="metric-title">${this.title}</div>
                <div class="metric-value">${formattedValue}</div>
                ${subtitleHtml}
                ${trendHtml}
            </div>
        `;
    }

    /**
     * Format numeric value with locale-specific formatting
     * @param {number} value - Value to format
     * @returns {string} Formatted value
     */
    formatValue(value) {
        if (typeof value !== 'number') {
            return value;
        }
        return value.toLocaleString('ko-KR');
    }

    /**
     * Render trend indicator
     * @returns {string} HTML string for trend indicator
     */
    renderTrend() {
        const trendIcons = {
            up: '📈',
            down: '📉',
            neutral: '➡️'
        };
        const trendColors = {
            up: '#10b981',
            down: '#f87171',
            neutral: '#a0aec0'
        };
        
        const icon = trendIcons[this.trend] || '';
        const color = trendColors[this.trend] || '#a0aec0';
        
        return `
            <div class="metric-trend" style="color: ${color}; font-size: 0.9rem; margin-top: 0.5rem;">
                ${icon}
            </div>
        `;
    }

    /**
     * Create a metric card element and append to container
     * @param {HTMLElement} container - Container element to append to
     */
    appendTo(container) {
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = this.render();
        container.appendChild(tempDiv.firstElementChild);
    }
}

/**
 * MetricCardFactory
 * Factory for creating metric cards from API data
 */
class MetricCardFactory {
    /**
     * Create metric cards from dashboard metrics data
     * @param {Object} metricsData - Metrics data from API
     * @returns {Array<MetricCard>} Array of MetricCard instances
     */
    static createFromMetrics(metricsData) {
        // Determine API usage status
        let apiStatus = 'normal';
        let apiIcon = '🤖';
        if (metricsData.apiUsagePercentage >= 90) {
            apiStatus = 'danger';
            apiIcon = '🔴';
        } else if (metricsData.apiUsagePercentage >= 70) {
            apiStatus = 'warning';
            apiIcon = '🟡';
        } else {
            apiIcon = '🟢';
        }

        return [
            new MetricCard({
                icon: '📄',
                title: '전체 콘텐츠',
                value: metricsData.totalContents
            }),
            new MetricCard({
                icon: '📅',
                title: '오늘 처리된 콘텐츠',
                value: metricsData.todayContents
            }),
            new MetricCard({
                icon: '✅',
                title: '요약 있는 콘텐츠',
                value: metricsData.contentsWithSummary
            }),
            new MetricCard({
                icon: '❌',
                title: '요약 없는 콘텐츠',
                value: metricsData.contentsWithoutSummary
            }),
            new MetricCard({
                icon: '👁️',
                title: '노출 중인 콘텐츠',
                value: metricsData.exposedContents
            }),
            new MetricCard({
                icon: '🔑',
                title: '전체 키워드',
                value: metricsData.totalKeywords
            }),
            new MetricCard({
                icon: '📧',
                title: '활성 뉴스레터 소스',
                value: metricsData.activeNewsletterSources
            }),
            new MetricCard({
                icon: apiIcon,
                title: '오늘 AI API 호출',
                value: metricsData.todayApiCalls,
                subtitle: `남은: ${metricsData.apiCallsRemaining.toLocaleString()} / 전체: ${(metricsData.todayApiCalls + metricsData.apiCallsRemaining).toLocaleString()} (${metricsData.apiUsagePercentage.toFixed(1)}%)`,
                status: apiStatus
            })
        ];
    }

    /**
     * Render all metric cards to a container
     * @param {HTMLElement} container - Container element
     * @param {Object} metricsData - Metrics data from API
     */
    static renderToContainer(container, metricsData) {
        const cards = this.createFromMetrics(metricsData);
        container.innerHTML = '';
        cards.forEach(card => card.appendTo(container));
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { MetricCard, MetricCardFactory };
}
