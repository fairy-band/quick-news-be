/**
 * ActivityChart - Component for rendering activity charts
 * Integrates Chart.js for line and bar charts, and custom calendar heatmap
 * Requires Chart.js library to be loaded
 */
class ActivityChart {
    /**
     * Create a new ActivityChart instance
     * @param {string} containerId - The ID of the container element
     * @param {Array|Object} data - The chart data
     * @param {Object} options - Chart options
     * @param {string} options.chartType - Type of chart ('line', 'bar', 'calendar')
     * @param {string} options.title - Chart title
     * @param {string} options.xAxisLabel - X-axis label
     * @param {string} options.yAxisLabel - Y-axis label
     * @param {Object} options.colors - Custom colors
     */
    constructor(containerId, data, options = {}) {
        this.containerId = containerId;
        this.data = data;
        this.options = {
            chartType: 'line',
            title: '',
            xAxisLabel: '',
            yAxisLabel: '',
            colors: {
                primary: '#00d4ff',
                secondary: '#5a67d8',
                background: 'rgba(0, 212, 255, 0.1)',
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

        // Clear existing content
        this.container.innerHTML = '';

        // Render based on chart type
        switch (this.options.chartType) {
            case 'line':
                this._renderLineChart();
                break;
            case 'bar':
                this._renderBarChart();
                break;
            case 'calendar':
                this._renderCalendarHeatmap();
                break;
            default:
                console.error(`Unknown chart type: ${this.options.chartType}`);
        }
    }

    /**
     * Render a line chart using Chart.js
     * @private
     */
    _renderLineChart() {
        if (typeof Chart === 'undefined') {
            this._showChartJsError();
            return;
        }

        const canvas = document.createElement('canvas');
        this.container.appendChild(canvas);

        const ctx = canvas.getContext('2d');

        this.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: this.data.labels || [],
                datasets: [{
                    label: this.options.title || 'Activity',
                    data: this.data.values || [],
                    borderColor: this.options.colors.primary,
                    backgroundColor: this.options.colors.background,
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: this.options.colors.primary,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        display: false
                    },
                    title: {
                        display: !!this.options.title,
                        text: this.options.title,
                        color: this.options.colors.text,
                        font: {
                            size: 16,
                            weight: 'bold'
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleColor: this.options.colors.primary,
                        bodyColor: this.options.colors.text,
                        borderColor: this.options.colors.primary,
                        borderWidth: 1,
                        padding: 12,
                        displayColors: false
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: !!this.options.xAxisLabel,
                            text: this.options.xAxisLabel,
                            color: this.options.colors.text
                        },
                        grid: {
                            color: this.options.colors.grid
                        },
                        ticks: {
                            color: this.options.colors.text
                        }
                    },
                    y: {
                        title: {
                            display: !!this.options.yAxisLabel,
                            text: this.options.yAxisLabel,
                            color: this.options.colors.text
                        },
                        grid: {
                            color: this.options.colors.grid
                        },
                        ticks: {
                            color: this.options.colors.text
                        },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    /**
     * Render a bar chart using Chart.js
     * @private
     */
    _renderBarChart() {
        if (typeof Chart === 'undefined') {
            this._showChartJsError();
            return;
        }

        const canvas = document.createElement('canvas');
        this.container.appendChild(canvas);

        const ctx = canvas.getContext('2d');

        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: this.data.labels || [],
                datasets: [{
                    label: this.options.title || 'Activity',
                    data: this.data.values || [],
                    backgroundColor: this.options.colors.background,
                    borderColor: this.options.colors.primary,
                    borderWidth: 2,
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        display: false
                    },
                    title: {
                        display: !!this.options.title,
                        text: this.options.title,
                        color: this.options.colors.text,
                        font: {
                            size: 16,
                            weight: 'bold'
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleColor: this.options.colors.primary,
                        bodyColor: this.options.colors.text,
                        borderColor: this.options.colors.primary,
                        borderWidth: 1,
                        padding: 12,
                        displayColors: false
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: !!this.options.xAxisLabel,
                            text: this.options.xAxisLabel,
                            color: this.options.colors.text
                        },
                        grid: {
                            display: false
                        },
                        ticks: {
                            color: this.options.colors.text
                        }
                    },
                    y: {
                        title: {
                            display: !!this.options.yAxisLabel,
                            text: this.options.yAxisLabel,
                            color: this.options.colors.text
                        },
                        grid: {
                            color: this.options.colors.grid
                        },
                        ticks: {
                            color: this.options.colors.text
                        },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    /**
     * Render a calendar heatmap (custom implementation)
     * @private
     */
    _renderCalendarHeatmap() {
        // Create title if provided
        if (this.options.title) {
            const title = document.createElement('h3');
            title.style.color = this.options.colors.text;
            title.style.marginBottom = 'var(--spacing-md)';
            title.textContent = this.options.title;
            this.container.appendChild(title);
        }

        // Create calendar grid
        const calendar = document.createElement('div');
        calendar.style.display = 'grid';
        calendar.style.gridTemplateColumns = 'repeat(7, 1fr)';
        calendar.style.gap = '4px';
        calendar.style.maxWidth = '400px';

        // Day labels
        const dayLabels = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
        dayLabels.forEach(day => {
            const label = document.createElement('div');
            label.style.textAlign = 'center';
            label.style.fontSize = '0.75rem';
            label.style.color = this.options.colors.text;
            label.style.opacity = '0.6';
            label.style.marginBottom = '4px';
            label.textContent = day;
            calendar.appendChild(label);
        });

        // Process data - expect array of {date, value} objects
        const dataMap = new Map();
        if (Array.isArray(this.data)) {
            this.data.forEach(item => {
                const dateStr = this._formatDate(new Date(item.date));
                dataMap.set(dateStr, item.value || 0);
            });
        }

        // Get max value for color scaling
        const maxValue = Math.max(...Array.from(dataMap.values()), 1);

        // Generate last 35 days (5 weeks)
        const today = new Date();
        const startDate = new Date(today);
        startDate.setDate(today.getDate() - 34);

        // Find the previous Sunday
        const dayOfWeek = startDate.getDay();
        startDate.setDate(startDate.getDate() - dayOfWeek);

        // Create cells for 35 days
        for (let i = 0; i < 35; i++) {
            const currentDate = new Date(startDate);
            currentDate.setDate(startDate.getDate() + i);

            const dateStr = this._formatDate(currentDate);
            const value = dataMap.get(dateStr) || 0;

            const cell = document.createElement('div');
            cell.style.aspectRatio = '1';
            cell.style.borderRadius = '4px';
            cell.style.cursor = 'pointer';
            cell.style.transition = 'all 0.2s ease';
            cell.style.display = 'flex';
            cell.style.alignItems = 'center';
            cell.style.justifyContent = 'center';
            cell.style.fontSize = '0.7rem';

            // Color based on value
            const intensity = value > 0 ? Math.min(value / maxValue, 1) : 0;
            if (intensity === 0) {
                cell.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                cell.style.border = '1px solid rgba(255, 255, 255, 0.1)';
            } else {
                const alpha = 0.2 + (intensity * 0.8);
                cell.style.backgroundColor = `rgba(0, 212, 255, ${alpha})`;
                cell.style.border = '1px solid rgba(0, 212, 255, 0.5)';
            }

            // Tooltip
            cell.title = `${currentDate.toLocaleDateString()}: ${value} ${value === 1 ? 'activity' : 'activities'}`;

            // Hover effect
            cell.addEventListener('mouseenter', () => {
                cell.style.transform = 'scale(1.1)';
                cell.style.zIndex = '10';
            });

            cell.addEventListener('mouseleave', () => {
                cell.style.transform = 'scale(1)';
                cell.style.zIndex = '1';
            });

            calendar.appendChild(cell);
        }

        this.container.appendChild(calendar);

        // Add legend
        const legend = this._createHeatmapLegend();
        this.container.appendChild(legend);
    }

    /**
     * Create legend for heatmap
     * @private
     * @returns {HTMLElement}
     */
    _createHeatmapLegend() {
        const legend = document.createElement('div');
        legend.style.display = 'flex';
        legend.style.alignItems = 'center';
        legend.style.gap = '8px';
        legend.style.marginTop = 'var(--spacing-md)';
        legend.style.fontSize = '0.75rem';
        legend.style.color = this.options.colors.text;
        legend.style.opacity = '0.7';

        const lessLabel = document.createElement('span');
        lessLabel.textContent = 'Less';
        legend.appendChild(lessLabel);

        // Color boxes
        const intensities = [0, 0.25, 0.5, 0.75, 1];
        intensities.forEach(intensity => {
            const box = document.createElement('div');
            box.style.width = '12px';
            box.style.height = '12px';
            box.style.borderRadius = '2px';
            
            if (intensity === 0) {
                box.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                box.style.border = '1px solid rgba(255, 255, 255, 0.1)';
            } else {
                const alpha = 0.2 + (intensity * 0.8);
                box.style.backgroundColor = `rgba(0, 212, 255, ${alpha})`;
            }
            
            legend.appendChild(box);
        });

        const moreLabel = document.createElement('span');
        moreLabel.textContent = 'More';
        legend.appendChild(moreLabel);

        return legend;
    }

    /**
     * Format date as YYYY-MM-DD
     * @private
     * @param {Date} date
     * @returns {string}
     */
    _formatDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    /**
     * Show error when Chart.js is not loaded
     * @private
     */
    _showChartJsError() {
        this.container.innerHTML = `
            <div style="padding: var(--spacing-xl); text-align: center; color: var(--text-secondary);">
                <div style="font-size: 2rem; margin-bottom: var(--spacing-md);">⚠️</div>
                <div>Chart.js library is required but not loaded.</div>
                <div style="font-size: 0.9rem; margin-top: var(--spacing-sm);">
                    Please include Chart.js in your page.
                </div>
            </div>
        `;
    }

    /**
     * Update chart with new data
     * @param {Array|Object} data - New chart data
     */
    update(data) {
        this.data = data;

        if (this.options.chartType === 'calendar') {
            // Re-render calendar heatmap
            this.render();
        } else if (this.chart) {
            // Update Chart.js chart
            this.chart.data.labels = data.labels || [];
            this.chart.data.datasets[0].data = data.values || [];
            this.chart.update();
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
     * Static method to load Chart.js dynamically
     * @returns {Promise<void>}
     */
    static async loadChartJs() {
        if (this.isChartJsLoaded()) {
            return Promise.resolve();
        }

        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js';
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Failed to load Chart.js'));
            document.head.appendChild(script);
        });
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ActivityChart;
}
