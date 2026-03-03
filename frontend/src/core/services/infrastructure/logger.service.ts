import { Injectable } from '@angular/core';
import { environment } from '../../../env/environment';
import { PlatformDetectionService } from './platform-detection.service';

export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  OFF = 4
}

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  data?: any;
  context?: string;
}

@Injectable({
  providedIn: 'root'
})
export class LoggerService {
  private readonly logLevel: LogLevel;

  constructor(private platformDetection: PlatformDetectionService) {
    // Set log level based on environment
    this.logLevel = environment.production ? LogLevel.WARN : LogLevel.DEBUG;
  }

  debug(message: string, data?: any, context?: string): void {
    this.log(LogLevel.DEBUG, message, data, context);
  }

  info(message: string, data?: any, context?: string): void {
    this.log(LogLevel.INFO, message, data, context);
  }

  warn(message: string, data?: any, context?: string): void {
    this.log(LogLevel.WARN, message, data, context);
  }

  error(message: string, data?: any, context?: string): void {
    this.log(LogLevel.ERROR, message, data, context);
  }

  private log(level: LogLevel, message: string, data?: any, context?: string): void {
    // Skip logging if level is below threshold
    if (level < this.logLevel) {
      return;
    }

    const logEntry: LogEntry = {
      timestamp: new Date().toISOString(),
      level,
      message,
      data,
      context
    };

    // Format the log output
    const formattedMessage = this.formatLogMessage(logEntry);

    // Only output to console if console is available
    if (this.platformDetection.isConsoleAvailable()) {
      // Output to appropriate console method
      switch (level) {
        case LogLevel.DEBUG:
          console.debug(formattedMessage, data);
          break;
        case LogLevel.INFO:
          console.info(formattedMessage, data);
          break;
        case LogLevel.WARN:
          console.warn(formattedMessage, data);
          break;
        case LogLevel.ERROR:
          console.error(formattedMessage, data);
          break;
      }
    }

    // In production, you could send logs to a logging service
    if (environment.production && level >= LogLevel.ERROR) {
      this.sendToLoggingService(logEntry);
    }
  }

  private formatLogMessage(entry: LogEntry): string {
    const levelStr = LogLevel[entry.level].padEnd(5);
    const contextStr = entry.context ? `[${entry.context}]` : '';
    return `[${entry.timestamp}] ${levelStr} ${contextStr} ${entry.message}`;
  }

  private sendToLoggingService(logEntry: LogEntry): void {
    // TODO: Implement integration with external logging service
    // e.g., Sentry, LogRocket, or custom analytics
    // For now, we'll just store critical errors locally
    const localStorage = this.platformDetection.getLocalStorage();
    const navigator = this.platformDetection.getNavigator();
    const window = this.platformDetection.getWindow();
    
    if (!localStorage) {
      return; // Silently fail if localStorage is not available
    }

    try {
      const errors = JSON.parse(localStorage.getItem('app_errors') || '[]');
      errors.push({
        ...logEntry,
        userAgent: navigator?.userAgent || 'Unknown',
        url: window?.location.href || 'Unknown'
      });
      
      // Keep only last 50 errors
      if (errors.length > 50) {
        errors.splice(0, errors.length - 50);
      }
      
      localStorage.setItem('app_errors', JSON.stringify(errors));
    } catch (error) {
      // Silently fail if localStorage is not available
    }
  }

  /**
   * Get stored error logs (useful for debugging)
   */
  getStoredErrors(): LogEntry[] {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return [];
    }

    try {
      return JSON.parse(localStorage.getItem('app_errors') || '[]');
    } catch {
      return [];
    }
  }

  /**
   * Clear stored error logs
   */
  clearStoredErrors(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      localStorage.removeItem('app_errors');
    } catch {
      // Silently fail
    }
  }
}