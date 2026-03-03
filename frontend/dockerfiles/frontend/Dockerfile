# Multi-stage Docker build for Angular SSR production deployment
# Production-only: No cache-proxy, uses direct production API endpoints
# Maintains same port configuration (80) for backend team integration

# Stage 1: Build the application
FROM node:20-alpine AS builder

# Set working directory
WORKDIR /app

# Install security updates
RUN apk update && apk upgrade && apk add --no-cache dumb-init

# Copy package files
COPY package*.json ./
COPY package-lock.json ./
COPY angular.json ./
COPY tsconfig*.json ./

# Install dependencies
RUN npm ci && npm cache clean --force

# Copy source code
COPY src/ ./src/
COPY public/ ./public/
COPY docker-entrypoint.sh ./

# Build for production with SSR
RUN npm run build

# Stage 2: Production runtime with nginx
FROM node:20-alpine AS production

# Install security updates, nginx, sudo, and health check tools
RUN apk update && apk upgrade && apk add --no-cache dumb-init curl nginx sudo

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S angular -u 1001 -G nodejs

# Set working directory
WORKDIR /app

# Copy built application from builder stage
COPY --from=builder --chown=angular:nodejs /app/dist/dcf-frontend ./dist/dcf-frontend

# Copy package.json for runtime dependencies info
COPY --from=builder --chown=angular:nodejs /app/package*.json ./

# Copy entrypoint script
COPY --from=builder /app/docker-entrypoint.sh ./
RUN chmod +x docker-entrypoint.sh && chown angular:nodejs docker-entrypoint.sh

# Install runtime dependencies (production only)
RUN npm ci --only=production --silent && \
    npm cache clean --force && \
    chown -R angular:nodejs /app

# Copy nginx configuration
COPY dockerfiles/frontend/nginx.conf /etc/nginx/nginx.conf
COPY dockerfiles/frontend/conf.d/ /etc/nginx/conf.d/
RUN rm -rf /etc/nginx/conf.d/default.dev.conf

# Create nginx directories and set permissions
RUN mkdir -p /var/run/nginx /var/log/nginx /var/cache/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx /var/log/nginx /var/cache/nginx /etc/nginx/conf.d && \
    echo "angular ALL=(ALL) NOPASSWD: /usr/sbin/nginx" >> /etc/sudoers

# Switch to non-root user
USER angular

# Set environment variables
ENV NODE_ENV=production
ENV PORT=4000
ENV NGINX_PORT=81

# Expose port 81 (nginx) - maps to host port 81 via docker-compose
EXPOSE 81

# Health check endpoint for container orchestration
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:81/health || exit 1

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start using entrypoint script (production mode only)
CMD ["/app/docker-entrypoint.sh"]