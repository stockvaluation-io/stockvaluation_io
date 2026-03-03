import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { GlobalErrorHandler, ErrorSeverity, ErrorCategory } from './global-error-handler.service';
import { LoggerService } from './logger.service';
import { DCFNotificationService } from '../../../components/dcf-analysis/services/dcf-notification.service';
import { LoadingStateService } from '../ui/loading-state.service';

describe('GlobalErrorHandler', () => {
  let service: GlobalErrorHandler;
  let loggerService: jasmine.SpyObj<LoggerService>;
  let notificationService: jasmine.SpyObj<DCFNotificationService>;
  let loadingStateService: jasmine.SpyObj<LoadingStateService>;

  beforeEach(() => {
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['error', 'warn', 'info', 'debug']);
    const notificationSpy = jasmine.createSpyObj('DCFNotificationService', ['showError', 'showWarning', 'showInfo']);
    const loadingSpy = jasmine.createSpyObj('LoadingStateService', ['setGlobalLoading', 'setGlobalError']);

    TestBed.configureTestingModule({
      providers: [
        GlobalErrorHandler,
        { provide: LoggerService, useValue: loggerSpy },
        { provide: DCFNotificationService, useValue: notificationSpy },
        { provide: LoadingStateService, useValue: loadingSpy }
      ]
    });

    service = TestBed.inject(GlobalErrorHandler);
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
    notificationService = TestBed.inject(DCFNotificationService) as jasmine.SpyObj<DCFNotificationService>;
    loadingStateService = TestBed.inject(LoadingStateService) as jasmine.SpyObj<LoadingStateService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('handleError', () => {
    it('should handle runtime errors correctly', () => {
      const error = new Error('Test runtime error');
      
      service.handleError(error);
      
      expect(loggerService.error).toHaveBeenCalled();
    });

    it('should handle TypeError with high severity', () => {
      const error = new TypeError('Cannot read property of null');
      
      service.handleErrorWithContext(error, {
        component: 'TestComponent',
        action: 'testAction'
      });
      
      expect(loggerService.error).toHaveBeenCalled();
      expect(notificationService.showError).toHaveBeenCalled();
    });
  });

  describe('handleHttpError', () => {
    it('should handle 500 errors as high severity', () => {
      const httpError = new HttpErrorResponse({
        status: 500,
        statusText: 'Internal Server Error',
        url: '/api/test'
      });
      
      service.handleHttpError(httpError, {
        url: '/api/test',
        action: 'test_api_call'
      });
      
      expect(loggerService.error).toHaveBeenCalled();
      expect(notificationService.showError).toHaveBeenCalled();
    });

    it('should handle 404 errors as low severity', () => {
      const httpError = new HttpErrorResponse({
        status: 404,
        statusText: 'Not Found',
        url: '/api/test'
      });
      
      service.handleHttpError(httpError, {
        url: '/api/test',
        action: 'test_api_call'
      });
      
      expect(loggerService.info).toHaveBeenCalled();
      // 404 errors should not notify user by default
      expect(notificationService.showError).not.toHaveBeenCalled();
    });

    it('should handle network errors (status 0) as critical', () => {
      const httpError = new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
        url: '/api/test'
      });
      
      service.handleHttpError(httpError, {
        url: '/api/test',
        action: 'test_api_call'
      });
      
      expect(loggerService.error).toHaveBeenCalled();
      expect(notificationService.showError).toHaveBeenCalled();
      expect(loadingStateService.setGlobalError).toHaveBeenCalled();
    });
  });

  describe('error categorization', () => {
    it('should categorize ChunkLoadError correctly', () => {
      const error = new Error('Loading chunk 1 failed');
      error.name = 'ChunkLoadError';
      
      service.handleErrorWithContext(error, {
        component: 'TestComponent'
      });
      
      expect(loggerService.error).toHaveBeenCalled();
      // ChunkLoadError should be recoverable
      expect(notificationService.showError).toHaveBeenCalled();
    });

    it('should categorize validation errors correctly', () => {
      const error = new Error('Validation failed');
      error.name = 'ValidationError';
      
      service.handleErrorWithContext(error, {
        component: 'FormComponent',
        action: 'validate_form'
      });
      
      expect(loggerService.info).toHaveBeenCalled();
      // Validation errors are low severity and shouldn't show global notifications
      expect(notificationService.showError).not.toHaveBeenCalled();
    });
  });

  describe('duplicate error prevention', () => {
    it('should prevent duplicate errors from being processed', () => {
      const error = new Error('Duplicate test error');
      
      // First occurrence should be processed
      service.handleErrorWithContext(error, {
        component: 'TestComponent',
        action: 'testAction'
      });
      
      // Immediate duplicate should be ignored
      service.handleErrorWithContext(error, {
        component: 'TestComponent',
        action: 'testAction'
      });
      
      expect(loggerService.error).toHaveBeenCalledTimes(1);
    });
  });

  describe('user-friendly messages', () => {
    it('should provide user-friendly messages for common errors', () => {
      const chunkError = new Error('Loading chunk failed');
      chunkError.name = 'ChunkLoadError';
      
      service.handleErrorWithContext(chunkError, {
        component: 'TestComponent'
      });
      
      expect(notificationService.showError).toHaveBeenCalledWith(
        jasmine.stringMatching(/refresh.*page/i)
      );
    });

    it('should provide appropriate messages for HTTP errors', () => {
      const networkError = new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error'
      });
      
      service.handleHttpError(networkError);
      
      expect(notificationService.showError).toHaveBeenCalledWith(
        jasmine.stringMatching(/connection/i)
      );
    });
  });
});