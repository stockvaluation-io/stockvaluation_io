const PROXY_CONFIG = {
  "/api/*": {
    "target": "https://stockvaluation.io",
    "secure": true,
    "changeOrigin": true,
    "logLevel": "debug",
    "headers": {
      "referer": "https://stockvaluation.io/automated-dcf-analysis",
      "origin": "https://stockvaluation.io"
    },
    "onProxyReq": function(proxyReq, req, res) {
      console.log('Proxying:', req.method, req.url, 'to', proxyReq.path);
      console.log('Headers:', proxyReq.getHeaders());
    },
    "onProxyRes": function(proxyRes, req, res) {
      console.log('Response from proxy:', proxyRes.statusCode, req.url);
    },
    "onError": function(err, req, res) {
      console.log('Proxy error:', err);
    }
  }
};

module.exports = PROXY_CONFIG;