import { Injectable, Inject, Optional, PLATFORM_ID } from '@angular/core';
import { isPlatformServer } from '@angular/common';
import { RESPONSE_INIT } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class HttpStatusService {
  private isServer: boolean;

  constructor(
    @Inject(PLATFORM_ID) platformId: Object,
    @Optional() @Inject(RESPONSE_INIT) private responseInit: any
  ) {
    this.isServer = isPlatformServer(platformId);
  }

  /**
   * Set HTTP status code for SSR responses
   * @param status HTTP status code (e.g., 404, 500)
   * @param message Optional status message
   */
  setStatus(status: number, message?: string): void {
    if (this.isServer && this.responseInit) {
      this.responseInit.status = status;
      if (message) {
        this.responseInit.statusText = message;
      }
    }
  }

  /**
   * Set 404 Not Found status
   */
  setNotFound(): void {
    this.setStatus(404, 'Not Found');
  }

  /**
   * Set 500 Internal Server Error status
   */
  setServerError(): void {
    this.setStatus(500, 'Internal Server Error');
  }

  /**
   * Check if we're in SSR mode
   */
  isSSR(): boolean {
    return this.isServer;
  }
}