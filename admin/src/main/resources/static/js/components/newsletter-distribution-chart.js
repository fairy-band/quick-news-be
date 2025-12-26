/**
 * NewsletterDistributionChart - Component for rendering newsletter source distribution
 * Shows content count by newsletter source as a horizontal bar chart
 * Requires Chart.js library to be loaded
 */
class NewsletterDistributionChart {
    /**
     * Create a new NewsletterDistributionChart instance
     * @param {string} containerId - The ID of the container element
     * @param {Array} data - Array of distribution objects with name, count, and percentage
     * @param {Object} options - Chart options
     */
    constructor(containerId, data, options = {}) {
        this.containerId = containerId;
        this.data = data;
        this.options = {
            title: '뉴스레터 소스별 분포',
            maxItems: 10,
            colors: {
                bar: '#00d4ff',
                barHover: '#5a67d8',
                barBg: 'rgba(0, 212, 255, 0.2)',
                grid: 'rgba(255, 255, 255, 0.1)',
                text: '#ffffff'
            },
            onItemClick: null, // Callback function when bar is clicked
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

        // Prepare data (limit to maxItems)
        const limitedData = this.data.slice(0, this.options.maxItems);
        const labels = limitedData.map(item => item.name);
        const counts = limitedData.map(item => item.count);
        const percentages = limitedData.map(item => item.percentage);

        // Create chart
        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: '콘텐츠 수',
                    data: counts,
                    backgroundColor: this.options.colors.barBg,
                    borderColor: this.options.colors.bar,
                    borderWidth: 2,
                    hoverBackgroundColor: this.options.colors.barHover,
                    hoverBorderColor: this.options.colors.barHover,
                    borderRadius: 6,
                    barPercentage: 0.8
                }]
            },
            options: {
                indexAxis: 'y', // Horizontal bar chart
                responsive: true,
                maintainAspectRatio: true,
                onClick: (event, elements) => {
                    if (elements.length > 0 && this.options.onItemClick) {
                        const index = elements[0].index;
                        const item = limitedData[index];
                        this.options.onItemClick(item);
                    }
                },
                plugins: {
                    legend: {
                        display: false
                    },
                    title: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.9)',
                        titleColor: this.options.colors.text,
                        bodyColor: this.options.colors.text,
                        borderColor: this.options.colors.bar,
                        borderWidth: 1,
                        padding: 12,
                        displayColors: false,
                        callbacks: {
                            label: (context) => {
                                const count = context.parsed.x;
                                const percentage = percentages[context.dataIndex];
                                return [
                                    `콘텐츠 수: ${count.toLocaleString()}개`,
                                    `비율: ${percentage.toFixed(1)}%`
                                ];
                            },
                            footer: () => {
                                return '클릭하여 콘텐츠 목록 보기';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: '콘텐츠 수',
                            color: this.options.colors.text,
                            font: {
                                size: 12,
                                weight: '600'
                            }
                        },
                        grid: {
                            color: this.options.colors.grid,
                            drawBorder: false
                        },
                        ticks: {
                            color: this.options.colors.text,
                            callback: function(value) {
                                return value.toLocaleString();
                            }
                        },
                        beginAtZero: true
                    },
                    y: {
                        grid: {
                            display: false,
                            drawBorder: false
                        },
                        ticks: {
                            color: this.options.colors.text,
                            font: {
                                size: 11
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
            const limitedData = data.slice(0, this.options.maxItems);
            this.chart.data.labels = limitedData.map(item => item.name);
            this.chart.data.datasets[0].data = limitedData.map(item => item.count);
            this.chart.update('active');
        } else {
            this.render();
        }
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
     * @returns {NewsletterDistributionChart}
     */
    static create(containerId, data, options = {}) {
        const chart = new NewsletterDistributionChart(containerId, data, options);
        chart.render();
        return chart;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = NewsletterDistributionChart;
}
