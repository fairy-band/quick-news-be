/**
 * ApiClient - Centralized API communication utility
 * Provides consistent error handling, loading states, response parsing, and caching
 */
class ApiClient {
    /**
     * Make a GET request
     * @param {string} url - The API endpoint URL
     * @param {Object} options - Additional options
     * @param {boolean} options.cache - Enable caching (default: false)
     * @param {number} options.cacheTTL - Cache TTL in milliseconds (default: 5 minutes)
     * @param {boolean} options.forceRefresh - Force refresh cache (default: false)
     * @returns {Promise<any>} - The response data
     */
    static async get(url, options = {}) {
        // Check cache if enabled and cacheManager is available
        if (options.cache && typeof cacheManager !== 'undefined' && !options.forceRefresh) {
            const cacheKey = `api:${url}`;
            const cached = cacheManager.get(cacheKey);
            
            if (cached !== null) {
                console.log(`Cache hit for: ${url}`);
                return cached;
            }
        }

        const data = await this._request(url, {
            method: 'GET',
            ...options
        });

        // Store in cache if enabled and cacheManager is available
        if (options.cache && typeof cacheManager !== 'undefined' && data !== null) {
            const cacheKey = `api:${url}`;
            const ttl = options.cacheTTL || (5 * 60 * 1000); // Default 5 minutes
            cacheManager.set(cacheKey, data, ttl);
            console.log(`Cached response for: ${url} (TTL: ${ttl}ms)`);
        }

        return data;
    }

    /**
     * Make a POST request
     * @param {string} url - The API endpoint URL
     * @param {Object} data - The request body data
     * @param {Object} options - Additional options
     * @returns {Promise<any>} - The response data
     */
    static async post(url, data, options = {}) {
        return this._request(url, {
            method: 'POST',
            body: JSON.stringify(data),
            ...options
        });
    }

    /**
     * Make a PUT request
     * @param {string} url - The API endpoint URL
     * @param {Object} data - The request body data
     * @param {Object} options - Additional options
     * @returns {Promise<any>} - The response data
     */
    static async put(url, data, options = {}) {
        return this._request(url, {
            method: 'PUT',
            body: JSON.stringify(data),
            ...options
        });
    }

    /**
     * Make a DELETE request
     * @param {string} url - The API endpoint URL
     * @param {Object} options - Additional options
     * @returns {Promise<any>} - The response data
     */
    static async delete(url, options = {}) {
        return this._request(url, {
            method: 'DELETE',
            ...options
        });
    }

    /**
     * Internal method to handle all HTTP requests
     * @private
     * @param {string} url - The API endpoint URL
     * @param {Object} options - Fetch options
     * @returns {Promise<any>} - The response data
     */
    static async _request(url, options = {}) {
        // Show loading state if callback provided
        if (options.onLoadingStart) {
            options.onLoadingStart();
        }

        // Extract custom options that should not be passed to fetch
        const { cache, cacheTTL, forceRefresh, onLoadingStart, onLoadingEnd, ...fetchOptions } = options;

        // Set default headers
        const headers = {
            'Content-Type': 'application/json',
            ...fetchOptions.headers
        };

        try {
            const response = await fetch(url, {
                ...fetchOptions,
                headers
            });

            // Parse response
            const data = await this._parseResponse(response);

            // Handle HTTP errors
            if (!response.ok) {
                throw new ApiError(
                    data.message || `HTTP ${response.status}: ${response.statusText}`,
                    response.status,
                    data
                );
            }

            // Hide loading state if callback provided
            if (onLoadingEnd) {
                onLoadingEnd();
            }

            return data;

        } catch (error) {
            // Hide loading state on error
            if (onLoadingEnd) {
                onLoadingEnd();
            }

            // Handle network errors
            if (error instanceof TypeError) {
                throw new ApiError('Network error: Unable to connect to server', 0, null);
            }

            // Re-throw ApiError
            if (error instanceof ApiError) {
                throw error;
            }

            // Wrap unknown errors
            throw new ApiError(error.message || 'An unexpected error occurred', 0, null);
        }
    }

    /**
     * Parse response based on content type
     * @private
     * @param {Response} response - The fetch response
     * @returns {Promise<any>} - Parsed response data
     */
    static async _parseResponse(response) {
        const contentType = response.headers.get('content-type');

        // Handle empty responses
        if (response.status === 204 || !contentType) {
            return null;
        }

        // Parse JSON
        if (contentType.includes('application/json')) {
            return await response.json();
        }

        // Parse text
        if (contentType.includes('text/')) {
            return await response.text();
        }

        // Return blob for other types
        return await response.blob();
    }

    /**
     * Create a loading state manager
     * @param {HTMLElement} element - The element to show/hide during loading
     * @returns {Object} - Object with onLoadingStart and onLoadingEnd callbacks
     */
    static createLoadingState(element) {
        return {
            onLoadingStart: () => {
                if (element) {
                    element.style.display = 'block';
                }
            },
            onLoadingEnd: () => {
                if (element) {
                    element.style.display = 'none';
                }
            }
        };
    }

    /**
     * Build query string from object
     * @param {Object} params - Query parameters
     * @returns {string} - Query string
     */
    static buildQueryString(params) {
        const searchParams = new URLSearchParams();
        
        Object.entries(params).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== '') {
                searchParams.append(key, value);
            }
        });

        const queryString = searchParams.toString();
        return queryString ? `?${queryString}` : '';
    }

    /**
     * Handle API errors with user-friendly messages
     * @param {Error} error - The error to handle
     * @param {Function} toastCallback - Optional callback to show toast notification
     */
    static handleError(error, toastCallback = null) {
        let message = 'An unexpected error occurred';
        
        if (error instanceof ApiError) {
            message = error.message;
            
            // Provide user-friendly messages for common HTTP status codes
            switch (error.status) {
                case 400:
                    message = 'Invalid request. Please check your input.';
                    break;
                case 401:
                    message = 'Unauthorized. Please log in again.';
                    break;
                case 403:
                    message = 'Access denied. You do not have permission.';
                    break;
                case 404:
                    message = 'Resource not found.';
                    break;
                case 409:
                    message = 'Conflict. The resource already exists or is in use.';
                    break;
                case 422:
                    message = 'Validation error. Please check your input.';
                    break;
                case 429:
                    message = 'Too many requests. Please try again later.';
                    break;
                case 500:
                    message = 'Server error. Please try again later.';
                    break;
                case 503:
                    message = 'Service unavailable. Please try again later.';
                    break;
            }
        }

        console.error('API Error:', error);

        // Show toast notification if callback provided
        if (toastCallback && typeof toastCallback === 'function') {
            toastCallback(message, 'error');
        }

        return message;
    }

    /**
     * Invalidate cache for a specific URL or pattern
     * @param {string|RegExp} urlPattern - URL or pattern to invalidate
     */
    static invalidateCache(urlPattern) {
        if (typeof cacheManager === 'undefined') {
            return;
        }

        if (typeof urlPattern === 'string') {
            // Invalidate specific URL
            const cacheKey = `api:${urlPattern}`;
            cacheManager.remove(cacheKey);
            console.log(`Invalidated cache for: ${urlPattern}`);
        } else if (urlPattern instanceof RegExp) {
            // Invalidate pattern
            const pattern = new RegExp(`^api:${urlPattern.source}`);
            const count = cacheManager.invalidatePattern(pattern);
            console.log(`Invalidated ${count} cache entries matching pattern`);
        }
    }

    /**
     * Clear all API cache
     */
    static clearCache() {
        if (typeof cacheManager === 'undefined') {
            return;
        }

        cacheManager.invalidatePattern(/^api:/);
        console.log('Cleared all API cache');
    }
}

/**
 * Custom error class for API errors
 */
class ApiError extends Error {
    constructor(message, status, data) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.data = data;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { ApiClient, ApiError };
}
