/**
 * CategoryDistributionChart - Component for rendering category distribution
 * Shows content distribution by category as a pie or doughnut chart
 * Requires Chart.js library to be loaded
 */
class CategoryDistributionChart {
    /**
     * Create a new CategoryDistributionChart instance
     * @param {string} containerId - The ID of the container element
     * @param {Array} data - Array of distribution objects with name, count, and percentage
     * @param {Object} options - Chart options
     */
    constructor(containerId, data, options = {}) {
        this.containerId = containerId;
        this.data = data;
        this.options = {
            title: '카테고리별 분포',
            chartType: 'doughnut', // 'pie' or 'doughnut'
            colors: [
                '#00d4ff', // Cyan
                '#5a67d8', // Purple
                '#ed64a6', // Pink
                '#48bb78', // Green
                '#f6ad55', // Orange
                '#fc8181', // Red
                '#9f7aea', // Violet
                '#38b2ac', // Teal
                '#ecc94b', // Yellow
                '#4299e1'  // Blue
            ],
            grid: 'rgba(255, 255, 255, 0.1)',
            text: '#ffffff',
            onItemClick: null, // Callback function when segment is clicked
            ...options
        };

        this.chart = null;
        this.container = null;
    }

    /**
     * Render the chart
     */
    render() {
        this.container = document.getElementById(this.containerId);
        
        if (!this.container) {
            console.error(`Container with ID "${this.containerId}" not found`);
            return;
        }

        // Check if Chart.js is loaded
        if (typeof Chart === 'undefined') {
            this._showChartJsError();
            return;
        }

        // Check if data is empty
        if (!this.data || this.data.length === 0) {
            this._showNoData();
            return;
        }

        // Clear existing content
        this.container.innerHTML = '';

        // Create canvas element
        const canvas = document.createElement('canvas');
        canvas.style.maxHeight = '400px';
        canvas.style.cursor = 'pointer';
        this.container.appendChild(canvas);

        const ctx = canvas.getContext('2d');

        // Prepare data
        const labels = this.data.map(item => item.name);
        const counts = this.data.map(item => item.count);
        const percentages = this.data.map(item => item.percentage);
        
        // Assign colors (cycle through if more categories than colors)
        const backgroundColors = this.data.map((_, index) => 
            this.options.colors[index % this.options.colors.length]
        );
        
        const borderColors = backgroundColors.map(color => color);

        // Create chart
        this.chart = new Chart(ctx, {
            type: this.options.chartType,
            data: {
                labels: labels,
                datasets: [{
                    data: counts,
                    backgroundColor: backgroundColors,
                    borderColor: borderColors,
                    borderWidth: 2,
                    hoverOffset: 10
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                onClick: (event, elements) => {
                    if (elements.length > 0 && this.options.onItemClick) {
                        const index = elements[0].index;
                        const item = this.data[index];
                        this.options.onItemClick(item);
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'right',
                        labels: {
                            color: this.options.text,
                            font: {
                                size: 12,
                                weight: '600'
                            },
                            padding: 15,
                            usePointStyle: true,
                            pointStyle: 'circle',
                            generateLabels: (chart) => {
                                const data = chart.data;
                                if (data.labels.length && data.datasets.length) {
                                    return data.labels.map((label, i) => {
                                        const count = counts[i];
                                        const percentage = percentages[i];
                                        return {
                                            text: `${label} (${percentage.toFixed(1)}%)`,
                                            fillStyle: backgroundColors[i],
                                            strokeStyle: borderColors[i],
                                            lineWidth: 2,
                                            hidden: false,
                                            index: i
                                        };
                                    });
                                }
                                return [];
                            }
                        }
                    },
                    title: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.9)',
                        titleColor: this.options.text,
                        bodyColor: this.options.text,
                        borderColor: '#00d4ff',
                        borderWidth: 1,
                        padding: 12,
                        displayColors: true,
                        callbacks: {
                            label: (context) => {
                                const label = context.label || '';
                                const count = context.parsed;
                                const percentage = percentages[context.dataIndex];
                                return [
                                    `${label}`,
                                    `콘텐츠 수: ${count.toLocaleString()}개`,
                                    `비율: ${percentage.toFixed(1)}%`
                                ];
                            },
                            footer: () => {
                                return '클릭하여 콘텐츠 목록 보기';
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Show error when Chart.js is not loaded
     * @private
     */
    _showChartJsError() {
        this.container.innerHTML = `
            <div style="padding: 2rem; text-align: center; color: #a0aec0;">
                <div style="font-size: 2rem; margin-bottom: 1rem;">⚠️</div>
                <div>Chart.js 라이브러리가 로드되지 않았습니다.</div>
                <div style="font-size: 0.9rem; margin-top: 0.5rem; opacity: 0.7;">
                    페이지에 Chart.js를 포함해주세요.
                </div>
            </div>
        `;
    }

    /**
     * Show no data message
     * @private
     */
    _showNoData() {
        this.container.innerHTML = `
            <div style="padding: 2rem; text-align: center; color: #a0aec0;">
                <div style="font-size: 2rem; margin-bottom: 1rem;">📊</div>
                <div>데이터가 없습니다.</div>
            </div>
        `;
    }

    /**
     * Update chart with new data
     * @param {Array} data - New distribution data
     */
    update(data) {
        this.data = data;

        if (!data || data.length === 0) {
            if (this.chart) {
                this.chart.destroy();
                this.chart = null;
            }
            this._showNoData();
            return;
        }

        if (this.chart) {
            const labels = data.map(item => item.name);
            const counts = data.map(item => item.count);
            const backgroundColors = data.map((_, index) => 
                this.options.colors[index % this.options.colors.length]
            );

            this.chart.data.labels = labels;
            this.chart.data.datasets[0].data = counts;
            this.chart.data.datasets[0].backgroundColor = backgroundColors;
            this.chart.data.datasets[0].borderColor = backgroundColors;
            this.chart.update('active');
        } else {
            this.render();
        }
    }

    /**
     * Switch between pie and doughnut chart
     * @param {string} type - 'pie' or 'doughnut'
     */
    switchChartType(type) {
        if (type !== 'pie' && type !== 'doughnut') {
            console.error(`Invalid chart type: ${type}`);
            return;
        }

        this.options.chartType = type;
        
        if (this.chart) {
            this.chart.destroy();
        }
        
        this.render();
    }

    /**
     * Destroy the chart and clean up
     */
    destroy() {
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }
        if (this.container) {
            this.container.innerHTML = '';
        }
    }

    /**
     * Static method to check if Chart.js is loaded
     * @returns {boolean}
     */
    static isChartJsLoaded() {
        return typeof Chart !== 'undefined';
    }

    /**
     * Static factory method to create and render chart
     * @param {string} containerId - Container element ID
     * @param {Array} data - Distribution data
     * @param {Object} options - Chart options
     * @returns {CategoryDistributionChart}
     */
    static create(containerId, data, options = {}) {
        const chart = new CategoryDistributionChart(containerId, data, options);
        chart.render();
        return chart;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CategoryDistributionChart;
}
