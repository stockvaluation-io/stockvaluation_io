import { ApplicationConfig, provideZoneChangeDetection, ErrorHandler, SecurityContext } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { provideScReCaptchaSettings } from '@semantic-components/re-captcha';
import { environment } from '../env/environment';
import { GlobalErrorHandler } from '../core/services/infrastructure/global-error-handler.service';
import { errorHandlingInterceptor } from '../core/interceptors/error-handling.interceptor';
import { userIdInterceptor } from '../core/interceptors/user-id.interceptor';
import { provideMarkdown } from 'ngx-markdown';

export const appConfig: ApplicationConfig = {
    providers: [
        provideZoneChangeDetection({ eventCoalescing: true }),
        provideRouter(routes),
        provideAnimations(),
        provideHttpClient(withInterceptors([userIdInterceptor, errorHandlingInterceptor]), withFetch()),
        provideScReCaptchaSettings({
            v3SiteKey: environment.recaptcha.v3SiteKey,
        }),
        provideMarkdown({
            sanitize: SecurityContext.NONE,
        }),
        {
            provide: ErrorHandler,
            useClass: GlobalErrorHandler
        }
    ]
};
