import { Injectable } from '@angular/core';
import { PlatformDetectionService } from './platform-detection.service';

/**
 * SSR-Safe Event Handling Service
 * 
 * Provides safe event listener management that works in both server and browser environments.
 * All methods return cleanup functions and handle SSR gracefully.
 */
@Injectable({
  providedIn: 'root'
})
export class EventHandlingService {
  constructor(private platformDetection: PlatformDetectionService) {}

  /**
   * Add window event listener with automatic cleanup
   */
  addEventListener<K extends keyof WindowEventMap>(
    type: K,
    listener: (event: WindowEventMap[K]) => void,
    options?: AddEventListenerOptions
  ): () => void {
    const windowObj = this.platformDetection.getWindow();
    
    if (!windowObj) {
      return () => {}; // No-op cleanup function for SSR
    }

    try {
      windowObj.addEventListener(type, listener, options);
      
      return () => {
        try {
          windowObj.removeEventListener(type, listener, options);
        } catch (error) {
          console.warn('Failed to remove event listener:', error);
        }
      };
    } catch (error) {
      console.warn('Failed to add event listener:', error);
      return () => {};
    }
  }

  /**
   * Add document event listener with automatic cleanup
   */
  addDocumentEventListener<K extends keyof DocumentEventMap>(
    type: K,
    listener: (event: DocumentEventMap[K]) => void,
    options?: AddEventListenerOptions
  ): () => void {
    const document = this.platformDetection.getDocument();
    
    if (!document) {
      return () => {}; // No-op cleanup function for SSR
    }

    try {
      document.addEventListener(type, listener, options);
      
      return () => {
        try {
          document.removeEventListener(type, listener, options);
        } catch (error) {
          console.warn('Failed to remove document event listener:', error);
        }
      };
    } catch (error) {
      console.warn('Failed to add document event listener:', error);
      return () => {};
    }
  }

  /**
   * Add element event listener with automatic cleanup
   */
  addElementEventListener<K extends keyof HTMLElementEventMap>(
    element: HTMLElement | null,
    type: K,
    listener: (event: HTMLElementEventMap[K]) => void,
    options?: AddEventListenerOptions
  ): () => void {
    if (!element || !element.addEventListener) {
      return () => {}; // No-op cleanup function
    }

    try {
      element.addEventListener(type, listener, options);
      
      return () => {
        try {
          element.removeEventListener(type, listener, options);
        } catch (error) {
          console.warn('Failed to remove element event listener:', error);
        }
      };
    } catch (error) {
      console.warn('Failed to add element event listener:', error);
      return () => {};
    }
  }

  /**
   * Add scroll listener with throttling
   */
  addScrollListener(
    callback: () => void,
    options?: {
      throttleMs?: number;
      passive?: boolean;
      target?: HTMLElement;
    }
  ): () => void {
    const throttleMs = options?.throttleMs || 16; // ~60fps
    let lastCall = 0;
    let timeoutId: number | undefined;

    const throttledCallback = () => {
      const now = Date.now();
      if (now - lastCall >= throttleMs) {
        lastCall = now;
        callback();
      } else {
        if (timeoutId) clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => {
          lastCall = Date.now();
          callback();
        }, throttleMs - (now - lastCall));
      }
    };

    if (options?.target) {
      return this.addElementEventListener(options.target, 'scroll', throttledCallback, {
        passive: options.passive ?? true
      });
    } else {
      return this.addEventListener('scroll', throttledCallback, {
        passive: options?.passive ?? true
      });
    }
  }

  /**
   * Add resize listener with debouncing
   */
  addResizeListener(
    callback: () => void,
    debounceMs: number = 250
  ): () => void {
    let timeoutId: number | undefined;

    const debouncedCallback = () => {
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = window.setTimeout(callback, debounceMs);
    };

    return this.addEventListener('resize', debouncedCallback);
  }

  /**
   * Add mouse wheel listener
   */
  addWheelListener(
    callback: (event: WheelEvent) => void,
    options?: AddEventListenerOptions
  ): () => void {
    return this.addEventListener('wheel', callback, {
      passive: true,
      ...options
    });
  }

  /**
   * Add visibility change listener
   */
  addVisibilityChangeListener(
    callback: (isVisible: boolean) => void
  ): () => void {
    const document = this.platformDetection.getDocument();
    
    if (!document) {
      return () => {};
    }

    const listener = () => {
      callback(!document.hidden);
    };

    return this.addDocumentEventListener('visibilitychange', listener);
  }

  /**
   * Add online/offline listeners
   */
  addNetworkStatusListeners(
    onOnline: () => void,
    onOffline: () => void
  ): () => void {
    const removeOnline = this.addEventListener('online', onOnline);
    const removeOffline = this.addEventListener('offline', onOffline);

    return () => {
      removeOnline();
      removeOffline();
    };
  }

  /**
   * Add keyboard shortcut listener
   */
  addKeyboardShortcut(
    key: string,
    callback: (event: KeyboardEvent) => void,
    modifiers?: {
      ctrl?: boolean;
      alt?: boolean;
      shift?: boolean;
      meta?: boolean;
    }
  ): () => void {
    const listener = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() !== key.toLowerCase()) return;
      
      const ctrlMatch = modifiers?.ctrl ? event.ctrlKey : !event.ctrlKey;
      const altMatch = modifiers?.alt ? event.altKey : !event.altKey;
      const shiftMatch = modifiers?.shift ? event.shiftKey : !event.shiftKey;
      const metaMatch = modifiers?.meta ? event.metaKey : !event.metaKey;
      
      if (ctrlMatch && altMatch && shiftMatch && metaMatch) {
        event.preventDefault();
        callback(event);
      }
    };

    return this.addDocumentEventListener('keydown', listener);
  }

  /**
   * Create a cleanup manager for multiple event listeners
   */
  createEventListenerManager(): {
    add: <K extends keyof WindowEventMap>(
      type: K,
      listener: (event: WindowEventMap[K]) => void,
      options?: AddEventListenerOptions
    ) => void;
    cleanup: () => void;
  } {
    const cleanupFunctions: (() => void)[] = [];

    return {
      add: <K extends keyof WindowEventMap>(
        type: K,
        listener: (event: WindowEventMap[K]) => void,
        options?: AddEventListenerOptions
      ) => {
        const cleanup = this.addEventListener(type, listener, options);
        cleanupFunctions.push(cleanup);
      },
      cleanup: () => {
        cleanupFunctions.forEach(cleanup => cleanup());
        cleanupFunctions.length = 0;
      }
    };
  }
}