#!/bin/sh

# Docker entrypoint script for handling development vs production modes with nginx

if [ "$NODE_ENV" = "development" ]; then
    echo "[Docker] Starting development mode with nginx + cache proxy + SSR server..."
    
    # Start nginx in background as root (nginx will drop privileges)
    sudo nginx -g "daemon off;" &
    NGINX_PID=$!
    
    # Wait a moment for nginx to start
    sleep 2
    
    # Start cache proxy in background
    export NODE_ENV=development
    node cache-proxy/server.js &
    PROXY_PID=$!
    
    # Wait a moment for proxy to start
    sleep 2
    
    # Start SSR server on port 4000
    export PORT=4000
    node dist/dcf-frontend/server/server.mjs &
    SSR_PID=$!
    
    # Function to handle shutdown
    shutdown() {
        echo "[Docker] Shutting down all services..."
        sudo kill $NGINX_PID 2>/dev/null
        kill $PROXY_PID $SSR_PID 2>/dev/null
        exit 0
    }
    
    # Set up signal handlers
    trap shutdown SIGTERM SIGINT
    
    # Wait for main process (SSR server)
    wait $SSR_PID
else
    echo "[Docker] Starting production mode with nginx + SSR server..."
    
    # Start nginx in background as root (nginx will drop privileges)
    sudo nginx -g "daemon off;" &
    NGINX_PID=$!
    
    # Wait a moment for nginx to start
    sleep 2
    
    # Start SSR server on port 4000
    export PORT=4000
    node dist/dcf-frontend/server/server.mjs &
    SSR_PID=$!
    
    # Function to handle shutdown
    shutdown() {
        echo "[Docker] Shutting down all services..."
        sudo kill $NGINX_PID 2>/dev/null
        kill $SSR_PID 2>/dev/null
        exit 0
    }
    
    # Set up signal handlers
    trap shutdown SIGTERM SIGINT
    
    # Wait for main process (SSR server)
    wait $SSR_PID
fi