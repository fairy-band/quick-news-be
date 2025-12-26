/**
 * CacheManager - Handles session storage caching with TTL support
 * 
 * Features:
 * - Cache data with configurable TTL (Time To Live)
 * - Automatic cache invalidation on expiry
 * - Cache key namespacing
 * - Cache statistics and management
 */
class CacheManager {
    constructor(namespace = 'admin') {
        this.namespace = namespace;
        this.defaultTTL = 5 * 60 * 1000; // 5 minutes default
    }

    /**
     * Generate a namespaced cache key
     */
    _getKey(key) {
        return `${this.namespace}:${key}`;
    }

    /**
     * Set a value in cache with TTL
     * @param {string} key - Cache key
     * @param {any} value - Value to cache
     * @param {number} ttl - Time to live in milliseconds (optional)
     */
    set(key, value, ttl = this.defaultTTL) {
        const cacheKey = this._getKey(key);
        const cacheData = {
            value: value,
            timestamp: Date.now(),
            ttl: ttl,
            expiresAt: Date.now() + ttl
        };

        try {
            sessionStorage.setItem(cacheKey, JSON.stringify(cacheData));
            return true;
        } catch (error) {
            console.error('Failed to set cache:', error);
            // Handle quota exceeded error
            if (error.name === 'QuotaExceededError') {
                this.clear();
                try {
                    sessionStorage.setItem(cacheKey, JSON.stringify(cacheData));
                    return true;
                } catch (retryError) {
                    console.error('Failed to set cache after clearing:', retryError);
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * Get a value from cache
     * @param {string} key - Cache key
     * @returns {any|null} - Cached value or null if not found/expired
     */
    get(key) {
        const cacheKey = this._getKey(key);
        
        try {
            const cached = sessionStorage.getItem(cacheKey);
            if (!cached) {
                return null;
            }

            const cacheData = JSON.parse(cached);
            
            // Check if cache has expired
            if (Date.now() > cacheData.expiresAt) {
                this.remove(key);
                return null;
            }

            return cacheData.value;
        } catch (error) {
            console.error('Failed to get cache:', error);
            this.remove(key);
            return null;
        }
    }

    /**
     * Check if a key exists and is not expired
     * @param {string} key - Cache key
     * @returns {boolean}
     */
    has(key) {
        return this.get(key) !== null;
    }

    /**
     * Remove a specific cache entry
     * @param {string} key - Cache key
     */
    remove(key) {
        const cacheKey = this._getKey(key);
        sessionStorage.removeItem(cacheKey);
    }

    /**
     * Clear all cache entries for this namespace
     */
    clear() {
        const keysToRemove = [];
        
        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (key && key.startsWith(`${this.namespace}:`)) {
                keysToRemove.push(key);
            }
        }

        keysToRemove.forEach(key => sessionStorage.removeItem(key));
    }

    /**
     * Clear all expired cache entries
     */
    clearExpired() {
        const keysToRemove = [];
        
        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (key && key.startsWith(`${this.namespace}:`)) {
                try {
                    const cached = sessionStorage.getItem(key);
                    if (cached) {
                        const cacheData = JSON.parse(cached);
                        if (Date.now() > cacheData.expiresAt) {
                            keysToRemove.push(key);
                        }
                    }
                } catch (error) {
                    // Invalid cache entry, remove it
                    keysToRemove.push(key);
                }
            }
        }

        keysToRemove.forEach(key => sessionStorage.removeItem(key));
    }

    /**
     * Get cache statistics
     * @returns {object} - Cache statistics
     */
    getStats() {
        let totalEntries = 0;
        let expiredEntries = 0;
        let totalSize = 0;

        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (key && key.startsWith(`${this.namespace}:`)) {
                totalEntries++;
                const cached = sessionStorage.getItem(key);
                if (cached) {
                    totalSize += cached.length;
                    try {
                        const cacheData = JSON.parse(cached);
                        if (Date.now() > cacheData.expiresAt) {
                            expiredEntries++;
                        }
                    } catch (error) {
                        expiredEntries++;
                    }
                }
            }
        }

        return {
            totalEntries,
            expiredEntries,
            activeEntries: totalEntries - expiredEntries,
            totalSize,
            namespace: this.namespace
        };
    }

    /**
     * Invalidate cache entries matching a pattern
     * @param {string|RegExp} pattern - Pattern to match cache keys
     */
    invalidatePattern(pattern) {
        const keysToRemove = [];
        const regex = pattern instanceof RegExp ? pattern : new RegExp(pattern);

        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (key && key.startsWith(`${this.namespace}:`)) {
                const shortKey = key.substring(`${this.namespace}:`.length);
                if (regex.test(shortKey)) {
                    keysToRemove.push(key);
                }
            }
        }

        keysToRemove.forEach(key => sessionStorage.removeItem(key));
        return keysToRemove.length;
    }

    /**
     * Get or set cache with a factory function
     * @param {string} key - Cache key
     * @param {Function} factory - Function to generate value if not cached
     * @param {number} ttl - Time to live in milliseconds (optional)
     * @returns {Promise<any>} - Cached or generated value
     */
    async getOrSet(key, factory, ttl = this.defaultTTL) {
        const cached = this.get(key);
        if (cached !== null) {
            return cached;
        }

        const value = await factory();
        this.set(key, value, ttl);
        return value;
    }
}

// Create a singleton instance for the admin module
const cacheManagerInstance = new CacheManager('admin');

// Periodically clear expired entries (every 5 minutes)
setInterval(() => {
    cacheManagerInstance.clearExpired();
}, 5 * 60 * 1000);

/**
 * Helper function to get HTTP error messages
 * Used by fetchWithCache
 */
function getHttpErrorMessage(status) {
    const messages = {
        400: '잘못된 요청입니다.',
        401: '인증이 필요합니다.',
        403: '접근 권한이 없습니다.',
        404: '요청한 데이터를 찾을 수 없습니다.',
        408: '요청 시간이 초과되었습니다.',
        429: '너무 많은 요청을 보냈습니다.',
        500: '서버 오류가 발생했습니다.',
        502: '게이트웨이 오류가 발생했습니다.',
        503: '서비스를 일시적으로 사용할 수 없습니다.',
        504: '게이트웨이 시간 초과가 발생했습니다.'
    };
    return messages[status] || `서버 오류 (${status})가 발생했습니다.`;
}

/**
 * Fetch data with caching support
 * @param {string} url - URL to fetch
 * @param {object} options - Options object
 * @param {number} options.ttl - Time to live in milliseconds (default: 5 minutes)
 * @param {string} options.cacheKey - Custom cache key (default: url)
 * @param {object} options.fetchOptions - Additional fetch options
 * @returns {Promise<any>} - Fetched or cached data
 */
async function fetchWithCache(url, options = {}) {
    const {
        ttl = 5 * 60 * 1000,
        cacheKey = url,
        fetchOptions = {}
    } = options;

    // Try to get from cache first
    const cached = cacheManagerInstance.get(cacheKey);
    if (cached !== null) {
        return Promise.resolve(cached);
    }

    // Fetch from server
    try {
        const response = await fetch(url, fetchOptions);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${getHttpErrorMessage(response.status)}`);
        }

        const data = await response.json();
        
        // Store in cache
        cacheManagerInstance.set(cacheKey, data, ttl);
        
        return data;
    } catch (error) {
        // Re-throw the error to be handled by the caller
        throw error;
    }
}

// Export as global object - MUST be at the end and executed immediately
(function() {
    if (typeof window !== 'undefined') {
        window.CacheManager = {
            fetchWithCache: fetchWithCache,
            get: (key) => cacheManagerInstance.get(key),
            set: (key, value, ttl) => cacheManagerInstance.set(key, value, ttl),
            has: (key) => cacheManagerInstance.has(key),
            remove: (key) => cacheManagerInstance.remove(key),
            clear: () => cacheManagerInstance.clear(),
            clearExpired: () => cacheManagerInstance.clearExpired(),
            getStats: () => cacheManagerInstance.getStats(),
            invalidatePattern: (pattern) => cacheManagerInstance.invalidatePattern(pattern),
            getOrSet: (key, factory, ttl) => cacheManagerInstance.getOrSet(key, factory, ttl)
        };
    }
})();
