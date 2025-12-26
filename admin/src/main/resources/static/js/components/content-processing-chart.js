/**
 * ContentProcessingChart - Component for rendering content processing charts
 * Shows processed vs unprocessed content over time
 * Requires Chart.js library to be loaded
 */
class ContentProcessingChart {
    /**
     * Create a new ContentProcessingChart instance
     * @param {string} containerId - The ID of the container element
     * @param {Object} data - The chart data with labels, processedData, and unprocessedData
     * @param {Object} options - Chart options
     */
    constructor(containerId, data, options = {}) {
        this.containerId = containerId;
        this.data = data;
        this.options = {
            chartType: 'line', // 'line' or 'bar'
            title: '콘텐츠 처리 현황',
            colors: {
                processed: '#00d4ff',
                unprocessed: '#f87171',
                processedBg: 'rgba(0, 212, 255, 0.1)',
                unprocessedBg: 'rgba(248, 113, 113, 0.1)',
                grid: 'rgba(255, 255, 255, 0.1)',
                text: '#ffffff'
            },
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

        // Check if data is empty or all values are zero
        if (!this.data || !this.data.labels || this.data.labels.length === 0 || this._isAllZero()) {
            this._showNoData();
            return;
        }

        // Clear existing content
        this.container.innerHTML = '';

        // Create canvas element
        const canvas = document.createElement('canvas');
        canvas.style.maxHeight = '400px';
        this.container.appendChild(canvas);

        const ctx = canvas.getContext('2d');

        // Prepare datasets
        const datasets = [
            {
                label: '처리됨',
                data: this.data.processedData || [],
                borderColor: this.options.colors.processed,
                backgroundColor: this.options.colors.processedBg,
                borderWidth: 2,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: this.options.colors.processed,
                pointBorderColor: '#fff',
                pointBorderWidth: 2
            },
            {
                label: '미처리',
                data: this.data.unprocessedData || [],
                borderColor: this.options.colors.unprocessed,
                backgroundColor: this.options.colors.unprocessedBg,
                borderWidth: 2,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: this.options.colors.unprocessed,
                pointBorderColor: '#fff',
                pointBorderWidth: 2
            }
        ];

        // Create chart
        this.chart = new Chart(ctx, {
            type: this.options.chartType,
            data: {
                labels: this.data.labels || [],
                datasets: datasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        labels: {
                            color: this.options.colors.text,
                            font: {
                                size: 12,
                                weight: '600'
                            },
                            padding: 15,
                            usePointStyle: true,
                            pointStyle: 'circle'
                        }
                    },
                    title: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.9)',
                        titleColor: this.options.colors.text,
                        bodyColor: this.options.colors.text,
                        borderColor: this.options.colors.processed,
                        borderWidth: 1,
                        padding: 12,
                        displayColors: true,
                        callbacks: {
                            label: function(context) {
                                const label = context.dataset.label || '';
                                const value = context.parsed.y;
                                return `${label}: ${value.toLocaleString()}개`;
                            },
                            footer: function(tooltipItems) {
                                let total = 0;
                                tooltipItems.forEach(item => {
                                    total += item.parsed.y;
                                });
                                return `총합: ${total.toLocaleString()}개`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: '날짜',
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
                            maxRotation: 45,
                            minRotation: 0
                        }
                    },
                    y: {
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
                <div>선택한 기간에 데이터가 없습니다.</div>
            </div>
        `;
    }

    /**
     * Check if all data values are zero
     * @private
     * @returns {boolean}
     */
    _isAllZero() {
        const processedData = this.data.processedData || [];
        const unprocessedData = this.data.unprocessedData || [];
        
        const processedSum = processedData.reduce((sum, val) => sum + val, 0);
        const unprocessedSum = unprocessedData.reduce((sum, val) => sum + val, 0);
        
        return processedSum === 0 && unprocessedSum === 0;
    }

    /**
     * Update chart with new data
     * @param {Object} data - New chart data
     */
    update(data) {
        this.data = data;

        // Check if data is empty or all values are zero
        if (!data || !data.labels || data.labels.length === 0 || this._isAllZero()) {
            if (this.chart) {
                this.chart.destroy();
                this.chart = null;
            }
            this._showNoData();
            return;
        }

        if (this.chart) {
            this.chart.data.labels = data.labels || [];
            this.chart.data.datasets[0].data = data.processedData || [];
            this.chart.data.datasets[1].data = data.unprocessedData || [];
            this.chart.update('active');
        } else {
            this.render();
        }
    }

    /**
     * Switch between line and bar chart
     * @param {string} type - 'line' or 'bar'
     */
    switchChartType(type) {
        if (type !== 'line' && type !== 'bar') {
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
     * @param {Object} data - Chart data
     * @param {Object} options - Chart options
     * @returns {ContentProcessingChart}
     */
    static create(containerId, data, options = {}) {
        const chart = new ContentProcessingChart(containerId, data, options);
        chart.render();
        return chart;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ContentProcessingChart;
}
