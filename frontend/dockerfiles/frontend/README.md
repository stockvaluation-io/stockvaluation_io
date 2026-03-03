# Docker SSR Frontend Configuration

This directory contains separate Docker configurations for production and development environments, designed to integrate with the existing backend Docker orchestration.

## Overview

This configuration provides a comprehensive nginx + Node.js SSR solution with environment-specific optimizations. Two separate Dockerfiles provide production and development configurations with nginx reverse proxy architecture.

## Key Changes from Previous Setup

### Before (Static Nginx)
- Served static files from `/browser` directory
- Used Nginx on port 80 (container) → port 81 (host)
- SPA routing with client-side rendering only

### After (nginx + SSR Node.js)
- nginx reverse proxy on port 81 (exposed)
- Node.js SSR server on port 4000 (internal)
- SEO-optimized with server-side rendering
- Better initial page load performance
- Production-ready nginx configuration with caching and security headers

## Files

- `Dockerfile` - Multi-stage production build with nginx + SSR (optimized, no cache-proxy)
- `Dockerfile.dev` - Development build with nginx + SSR + cache-proxy for local API access
- `conf.d/default.conf` - Production nginx configuration with caching and security headers
- `conf.d/default.dev.conf` - Development nginx configuration with cache-proxy routing
- `README.md` - This documentation

## Integration with Backend Team

### Docker Compose Configuration

The existing docker-compose configuration **requires no changes**:

```yaml
frontend:
  build:
    context: dockerfiles/frontend  # ✅ Same build context
  container_name: frontend         # ✅ Same container name
  image: frontend                  # ✅ Same image name
  restart: on-failure             # ✅ Same restart policy
  logging:                        # ✅ Same logging configuration
    driver: "json-file"
    options:
      max-size: "10m"
      max-file: "5"
  ports:
    - 81:80                       # ✅ Same port mapping
  networks:
    - dcf                         # ✅ Same network
```

### Nginx Proxy Configuration

The main nginx configuration **requires no changes**:

```nginx
location / {
    proxy_pass http://localhost:81;  # ✅ Same proxy target
    # ... existing headers and config
}
```

## Environment Variables

Set these environment variables in the container:

- `NODE_ENV=production` (enables SSR production mode)
- `PORT=4000` (Node.js SSR server internal port)
- `NGINX_PORT=81` (nginx exposed port, default)
- `CACHE_PROXY_PORT=3001` (cache-proxy internal port, development only)

## Health Check

The container includes a health check endpoint:

- **URL**: `http://localhost:81/health`
- **Response**: JSON with status, timestamp, uptime
- **Purpose**: Container orchestration and monitoring

Example response:
```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "uptime": 3600.5,
  "environment": "production",
  "version": "1.0.0"
}
```

## Build Process

### Production Dockerfile
The production `Dockerfile` performs these steps:

1. **Build Stage**: 
   - Installs dependencies
   - Builds both browser and server bundles using `npm run build`
   - Excludes cache-proxy for smaller image size
   
2. **Production Stage**:
   - Creates runtime with Node.js 20 Alpine + nginx
   - Sets up non-root user for security
   - Installs only production dependencies
   - Copies production nginx configuration
   - Configures nginx permissions for non-root user
   - Configures health checks
   - Exposes port 81 (nginx)

### Development Dockerfile.dev
The development `Dockerfile.dev` performs these steps:

1. **Build Stage**: 
   - Installs dependencies
   - Builds both browser and server bundles using `npm run build:dev`
   - Includes cache-proxy for local API access
   
2. **Development Stage**:
   - Creates runtime with Node.js 20 Alpine + nginx
   - Sets up non-root user for security
   - Installs all dependencies (including dev dependencies for cache-proxy)
   - Copies development nginx configuration with cache-proxy routing
   - Configures nginx permissions for non-root user
   - Configures health checks
   - Exposes port 81 (nginx)
   - Sets NODE_ENV=development

## Security Features

- Non-root user (`angular:nodejs`)
- Minimal Alpine Linux base image
- Security updates applied during build
- Proper signal handling with dumb-init
- Production-only dependencies

## Testing Locally

### Production Container
Build and test the production container:

```bash
# Build the production image
npm run docker:build

# Run production container (nginx port 81 maps to localhost:80)
npm run docker:prod

# Test health endpoint through nginx
curl http://localhost/health

# Test direct nginx access
curl http://localhost/
```

### Development Container
Build and test the development container:

```bash
# Build the development image
npm run docker:build:dev

# Run development container (nginx port 81 maps to localhost:80)
npm run docker:dev

# Test health endpoint through nginx
curl http://localhost/health

# Test cache-proxy dashboard through nginx
curl http://localhost/cache/dashboard
```

**Note**: The development container includes nginx + cache-proxy + SSR server, while the production container uses nginx + SSR server with direct production API endpoints.

## Troubleshooting

### Container won't start
- Check logs: `docker logs frontend`
- Verify port 81 is not in use
- Ensure `NODE_ENV=production` is set
- Check nginx configuration syntax: `docker exec -it frontend nginx -t`

### Health check failing
- Verify nginx is listening on port 81: `docker exec -it frontend netstat -ln | grep 81`
- Verify SSR server is listening on port 4000: `docker exec -it frontend netstat -ln | grep 4000`
- Check `/health` endpoint manually through nginx
- Review nginx and application logs for errors

### SSR not working
- **Production**: Confirm `npm run build` completed successfully
- **Development**: Confirm `npm run build:dev` completed successfully
- Check that both `/browser` and `/server` directories exist in build output
- Verify Angular Universal configuration
- Check if nginx is properly proxying to SSR server on port 4000

### Development API issues
- Ensure development container was built with `npm run docker:build:dev`
- Verify cache-proxy is running inside the development container on port 3001
- Check that nginx is routing `/api/` requests to cache-proxy
- Verify that `NODE_ENV=development` is set correctly

### nginx issues
- Check nginx configuration: `docker exec -it frontend nginx -t`
- View nginx logs: `docker exec -it frontend cat /var/log/nginx/error.log`
- Verify nginx process is running: `docker exec -it frontend ps aux | grep nginx`

## Architecture Overview

### Production Setup
```
Internet → nginx (port 81) → Node.js SSR Server (port 4000) → API responses
```

### Development Setup
```
Internet → nginx (port 81) → Node.js SSR Server (port 4000) → Application responses
                        → Cache Proxy (port 3001) → API responses
```

### Service Communication
- **nginx**: Handles all incoming requests, static file caching, security headers
- **SSR Server**: Renders Angular Universal pages, handles application logic
- **Cache Proxy** (dev only): Proxies API calls with caching, provides dashboard

### Benefits
- **nginx**: Production-ready reverse proxy with caching, compression, security
- **SSR**: SEO optimization, faster initial page loads
- **Cache Proxy**: Development API caching, CORS handling, monitoring dashboard

## Performance Notes

- **Initial page load**: Faster due to server-side rendering + nginx caching
- **SEO**: Improved with pre-rendered HTML served by nginx
- **Memory usage**: ~150-300MB typical for nginx + Node.js SSR
- **CPU usage**: Higher than static serving but reasonable for SSR with nginx optimization
- **Caching**: nginx handles static assets, cache-proxy handles API calls (dev)

## Migration Checklist for Backend Team

### Production Deployment
- [ ] Use `dockerfiles/frontend/Dockerfile` for production builds
- [ ] Set `NODE_ENV=production` in container environment
- [ ] Test build and deployment process with `npm run docker:build`
- [ ] Verify health check endpoint responds
- [ ] Monitor performance and resource usage
- [ ] Update any deployment scripts if needed

### Development Setup
- [ ] Use `dockerfiles/frontend/Dockerfile.dev` for development builds
- [ ] Set `NODE_ENV=development` in container environment
- [ ] Test development build process with `npm run docker:build:dev`
- [ ] Verify cache-proxy is accessible for local API calls
- [ ] Update development documentation if needed

## Support

For questions or issues with this Docker configuration, contact the frontend development team.