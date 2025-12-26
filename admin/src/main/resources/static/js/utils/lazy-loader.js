/**
 * LazyLoader - Handles lazy loading of content using Intersection Observer
 * 
 * Features:
 * - Load content as it comes into viewport
 * - Configurable threshold and root margin
 * - Automatic cleanup of observers
 * - Support for batch loading
 */
class LazyLoader {
    constructor(options = {}) {
        this.threshold = options.threshold || 0.1; // 10% visible
        this.rootMargin = options.rootMargin || '50px'; // Load 50px before entering viewport
        this.loadCallback = options.loadCallback || null;
        this.batchSize = options.batchSize || 5; // Load 5 items at a time
        this.loadDelay = options.loadDelay || 100; // Delay between batch loads
        
        this.observer = null;
        this.observedElements = new Set();
        this.loadQueue = [];
        this.isLoading = false;
        
        this._initObserver();
    }

    /**
     * Initialize Intersection Observer
     * @private
     */
    _initObserver() {
        // Check if Intersection Observer is supported
        if (!('IntersectionObserver' in window)) {
            console.warn('IntersectionObserver not supported, falling back to immediate loading');
            return;
        }

        this.observer = new IntersectionObserver(
            (entries) => this._handleIntersection(entries),
            {
                threshold: this.threshold,
                rootMargin: this.rootMargin
            }
        );
    }

    /**
     * Handle intersection events
     * @private
     * @param {IntersectionObserverEntry[]} entries
     */
    _handleIntersection(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const element = entry.target;
                
                // Add to load queue
                if (!this.loadQueue.includes(element)) {
                    this.loadQueue.push(element);
                }
                
                // Stop observing this element
                this.observer.unobserve(element);
                this.observedElements.delete(element);
            }
        });

        // Process load queue
        if (!this.isLoading && this.loadQueue.length > 0) {
            this._processLoadQueue();
        }
    }

    /**
     * Process the load queue in batches
     * @private
     */
    async _processLoadQueue() {
        if (this.isLoading || this.loadQueue.length === 0) {
            return;
        }

        this.isLoading = true;

        while (this.loadQueue.length > 0) {
            // Get next batch
            const batch = this.loadQueue.splice(0, this.batchSize);
            
            // Load batch items
            await Promise.all(
                batch.map(element => this._loadElement(element))
            );

            // Delay before next batch
            if (this.loadQueue.length > 0) {
                await this._delay(this.loadDelay);
            }
        }

        this.isLoading = false;
    }

    /**
     * Load a single element
     * @private
     * @param {HTMLElement} element
     */
    async _loadElement(element) {
        try {
            // Mark as loading
            element.classList.add('lazy-loading');
            element.classList.remove('lazy-pending');

            // Call load callback if provided
            if (this.loadCallback && typeof this.loadCallback === 'function') {
                await this.loadCallback(element);
            }

            // Mark as loaded
            element.classList.remove('lazy-loading');
            element.classList.add('lazy-loaded');
            
            // Dispatch custom event
            element.dispatchEvent(new CustomEvent('lazyloaded', {
                bubbles: true,
                detail: { element }
            }));
        } catch (error) {
            console.error('Failed to lazy load element:', error);
            element.classList.remove('lazy-loading');
            element.classList.add('lazy-error');
            
            // Dispatch error event
            element.dispatchEvent(new CustomEvent('lazyerror', {
                bubbles: true,
                detail: { element, error }
            }));
        }
    }

    /**
     * Delay helper
     * @private
     * @param {number} ms
     */
    _delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    /**
     * Observe an element for lazy loading
     * @param {HTMLElement} element
     */
    observe(element) {
        if (!element) {
            return;
        }

        // If Intersection Observer not supported, load immediately
        if (!this.observer) {
            this._loadElement(element);
            return;
        }

        // Mark as pending
        element.classList.add('lazy-pending');
        
        // Start observing
        this.observer.observe(element);
        this.observedElements.add(element);
    }

    /**
     * Observe multiple elements
     * @param {HTMLElement[]|NodeList} elements
     */
    observeAll(elements) {
        if (!elements) {
            return;
        }

        // Convert NodeList to Array if needed
        const elementsArray = Array.from(elements);
        
        elementsArray.forEach(element => this.observe(element));
    }

    /**
     * Unobserve an element
     * @param {HTMLElement} element
     */
    unobserve(element) {
        if (!element || !this.observer) {
            return;
        }

        this.observer.unobserve(element);
        this.observedElements.delete(element);
        
        // Remove from load queue if present
        const index = this.loadQueue.indexOf(element);
        if (index > -1) {
            this.loadQueue.splice(index, 1);
        }
    }

    /**
     * Unobserve all elements
     */
    unobserveAll() {
        if (!this.observer) {
            return;
        }

        this.observedElements.forEach(element => {
            this.observer.unobserve(element);
        });
        
        this.observedElements.clear();
        this.loadQueue = [];
    }

    /**
     * Disconnect the observer and cleanup
     */
    disconnect() {
        if (this.observer) {
            this.observer.disconnect();
        }
        
        this.observedElements.clear();
        this.loadQueue = [];
        this.isLoading = false;
    }

    /**
     * Force load all pending elements immediately
     */
    async loadAll() {
        // Stop observing
        this.unobserveAll();
        
        // Load all elements in queue
        const elements = [...this.loadQueue];
        this.loadQueue = [];
        
        await Promise.all(
            elements.map(element => this._loadElement(element))
        );
    }

    /**
     * Get statistics about lazy loading
     * @returns {object}
     */
    getStats() {
        return {
            observedElements: this.observedElements.size,
            queuedElements: this.loadQueue.length,
            isLoading: this.isLoading,
            batchSize: this.batchSize
        };
    }
}

/**
 * Create a lazy loader for content cards
 * @param {Function} loadCallback - Callback to load content for an element
 * @returns {LazyLoader}
 */
function createContentCardLazyLoader(loadCallback) {
    return new LazyLoader({
        threshold: 0.1,
        rootMargin: '100px',
        loadCallback: loadCallback,
        batchSize: 5,
        loadDelay: 50
    });
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { LazyLoader, createContentCardLazyLoader };
}
