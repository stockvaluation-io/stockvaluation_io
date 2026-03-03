export const environment = {
    production: true,
    basePath: 'http://localhost:8081/api/v1/',  // Local Java valuation-service (docker-compose.local.yml)
    agentBasePath: 'http://localhost:5001/api-s/', // Local valuation-agent (ticker-first orchestration)
    contentBaseUrl: '/assets/content',

    // API endpoints
    chatBackendUrl: '',                            // Default to proxied /bullbeargpt path (nginx/dev proxy)
    dashboardApiUrl: '',                           // Legacy dashboard API disabled in local-first mode

    authMode: 'local' as const,
    features: {
        legacyBullbeargpt: true,
    },

    // Server-side configuration
    server: {
        assetBasePath: undefined // Will use default Angular Universal path
    },

    // reCAPTCHA configuration
    recaptcha: {
        v3SiteKey: '' // Disabled in local-first mode
    },

    // Global logo configuration - change these values to update logos across the entire application
    logo: {
        version: 'v1',                                                  // 'legacy' = old SVG logo, 'v1' = new logo system, future: 'v2', 'v3', etc.
        variant: 'horizontal' as 'horizontal' | 'stacked',             // 'horizontal' = logo + text inline, 'stacked' = logo above text
        theme: 'dark' as 'light' | 'dark',                            // 'light' = light theme logo, 'dark' = dark theme logo  
        useOptimized: true                                             // true = prefer WebP format, false = use PNG
    },

    // Footer configuration
    footer: {
        showQuotes: false,                                              // true = show rotating quotes, false = hide quotes section
        quoteRotationInterval: 30000                                   // Interval in milliseconds (30000 = 30 seconds)
    },

    // Valuation configuration - centralized valuation thresholds and labels
    valuation: {
        thresholds: {
            significantlyUndervalued: -20,    // Stock trading 20%+ below fair value
            undervalued: -10,                 // Stock trading 10-20% below fair value
            fairValue: 10,                    // Stock trading within ±10% of fair value
            overvalued: 20                    // Stock trading 20%+ above fair value
        },
        labels: {
            significantlyUndervalued: 'Significantly Undervalued',
            undervalued: 'Undervalued',
            fairValue: 'Fair Value',
            overvalued: 'Overvalued',
            significantlyOvervalued: 'Significantly Overvalued'
        }
    },

    // Centralized asset configuration
    assets: {
        baseUrl: '/assets',
        cdnUrl: undefined, // No CDN in development
        paths: {
            content: '/content',
            logos: '/logo',
            images: '/images',
            icons: '/icons',
            fonts: '/fonts',
            documents: '/documents',
            data: '/data'
        },
        optimization: {
            enableWebP: false,           // Disable WebP in development
            enableLazyLoading: false,    // Disable lazy loading in development
            compressionLevel: 'low' as 'low' | 'medium' | 'high',
            maxCacheAge: 300000          // 5 minutes cache in development
        },
        fallback: {
            enableFallback: true,
            fallbackCdn: undefined,
            retryAttempts: 2,
            timeoutMs: 10000
        }
    }
};
