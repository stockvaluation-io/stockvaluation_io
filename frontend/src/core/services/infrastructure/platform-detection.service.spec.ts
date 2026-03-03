import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { PlatformDetectionService } from './platform-detection.service';

describe('PlatformDetectionService', () => {
  let service: PlatformDetectionService;

  describe('Browser Platform', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          { provide: PLATFORM_ID, useValue: 'browser' }
        ]
      });
      service = TestBed.inject(PlatformDetectionService);
    });

    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should detect browser platform correctly', () => {
      expect(service.isBrowser()).toBe(true);
      expect(service.isServer()).toBe(false);
    });

    it('should provide platform info', () => {
      const info = service.getPlatformInfo();
      expect(info.isBrowser).toBe(true);
      expect(info.isServer).toBe(false);
      expect(info.platformId).toBe('browser');
    });
  });

  describe('Server Platform', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          { provide: PLATFORM_ID, useValue: 'server' }
        ]
      });
      service = TestBed.inject(PlatformDetectionService);
    });

    it('should detect server platform correctly', () => {
      expect(service.isBrowser()).toBe(false);
      expect(service.isServer()).toBe(true);
    });

    it('should return null for browser-only APIs on server', () => {
      expect(service.getWindow()).toBeNull();
      expect(service.getDocument()).toBeNull();
      expect(service.getNavigator()).toBeNull();
      expect(service.getLocalStorage()).toBeNull();
      expect(service.getSessionStorage()).toBeNull();
    });

    it('should return false for browser-only feature checks on server', () => {
      expect(service.isWindowAvailable()).toBe(false);
      expect(service.isDocumentAvailable()).toBe(false);
      expect(service.isNavigatorAvailable()).toBe(false);
      expect(service.isLocalStorageAvailable()).toBe(false);
      expect(service.isSessionStorageAvailable()).toBe(false);
    });

    it('should provide platform info', () => {
      const info = service.getPlatformInfo();
      expect(info.isBrowser).toBe(false);
      expect(info.isServer).toBe(true);
      expect(info.platformId).toBe('server');
    });
  });
});