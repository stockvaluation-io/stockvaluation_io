import { HttpInterceptorFn } from '@angular/common/http';

/**
 * HTTP Interceptor that adds X-User-ID header to all API requests
 * 
 * This ensures that:
 * - All backend endpoints (Java & Python) receive the authenticated user's ID
 * - Valuations are saved with proper user attribution
 * - Chat sessions are linked to the correct user
 * - Anonymous users get a default 'default-user' ID
 * 
 * Usage: Already registered in app.config.ts
 */
export const userIdInterceptor: HttpInterceptorFn = (req, next) => {
  // Use a default anonymous ID since auth is removed
  const userId = 'anonymous_user';

  // Only add header for API requests (not for external resources)
  const isApiRequest = req.url.includes('/api/') || req.url.includes('/api-s/');

  if (isApiRequest && userId) {
    // Clone request and add X-User-ID header
    const modifiedReq = req.clone({
      setHeaders: {
        'X-User-ID': userId
      }
    });

    return next(modifiedReq);
  }

  // Pass through unchanged if not an API request
  return next(req);
};
