import { Injectable } from '@angular/core';
import { PlatformDetectionService } from './platform-detection.service';

/**
 * SSR-Safe DOM Utilities Service
 * 
 * Provides safe DOM operations that work in both server and browser environments.
 * All methods return null or false when DOM is not available (SSR).
 */
@Injectable({
  providedIn: 'root'
})
export class DomUtilitiesService {
  constructor(private platformDetection: PlatformDetectionService) {}

  /**
   * Safely get element by ID
   */
  getElementById(id: string): HTMLElement | null {
    const document = this.platformDetection.getDocument();
    return document ? document.getElementById(id) : null;
  }

  /**
   * Safely query selector
   */
  querySelector<T extends Element = Element>(selector: string): T | null {
    const document = this.platformDetection.getDocument();
    return document ? document.querySelector<T>(selector) : null;
  }

  /**
   * Safely query all elements
   */
  querySelectorAll<T extends Element = Element>(selector: string): NodeListOf<T> | null {
    const document = this.platformDetection.getDocument();
    return document ? document.querySelectorAll<T>(selector) : null;
  }

  /**
   * Scroll to element with offset calculation
   */
  scrollToElement(
    elementId: string, 
    options?: {
      behavior?: ScrollBehavior;
      offset?: number;
      container?: string;
    }
  ): boolean {
    const element = this.getElementById(elementId);
    if (!element) return false;

    const windowObj = this.platformDetection.getWindow();
    if (!windowObj) return false;

    const offset = options?.offset || 0;
    const container = options?.container ? this.querySelector(options.container) : null;

    if (container) {
      // Scroll within container
      const elementPosition = element.offsetTop - offset;
      container.scrollTo({
        top: Math.max(0, elementPosition),
        behavior: options?.behavior || 'smooth'
      });
    } else {
      // Scroll entire window
      const elementRect = element.getBoundingClientRect();
      const scrollTop = windowObj.scrollY + elementRect.top - offset;
      
      windowObj.scrollTo({
        top: Math.max(0, scrollTop),
        behavior: options?.behavior || 'smooth'
      });
    }

    return true;
  }

  /**
   * Safely get element dimensions
   */
  getElementDimensions(element: HTMLElement): { width: number; height: number } | null {
    if (!element || !element.getBoundingClientRect) {
      return null;
    }

    try {
      const rect = element.getBoundingClientRect();
      return {
        width: rect.width,
        height: rect.height
      };
    } catch {
      return null;
    }
  }

  /**
   * Safely add CSS class
   */
  addClass(element: HTMLElement | null, className: string): boolean {
    if (!element || !element.classList) return false;
    
    element.classList.add(className);
    return true;
  }

  /**
   * Safely remove CSS class
   */
  removeClass(element: HTMLElement | null, className: string): boolean {
    if (!element || !element.classList) return false;
    
    element.classList.remove(className);
    return true;
  }

  /**
   * Safely toggle CSS class
   */
  toggleClass(element: HTMLElement | null, className: string): boolean {
    if (!element || !element.classList) return false;
    
    element.classList.toggle(className);
    return true;
  }

  /**
   * Check if element is visible in viewport
   */
  isElementInViewport(element: HTMLElement, threshold: number = 0): boolean {
    const windowObj = this.platformDetection.getWindow();
    if (!windowObj || !element || !element.getBoundingClientRect) {
      return false;
    }

    try {
      const rect = element.getBoundingClientRect();
      return (
        rect.top >= -threshold &&
        rect.left >= -threshold &&
        rect.bottom <= (windowObj.innerHeight || document.documentElement.clientHeight) + threshold &&
        rect.right <= (windowObj.innerWidth || document.documentElement.clientWidth) + threshold
      );
    } catch {
      return false;
    }
  }

  /**
   * Safely create intersection observer
   */
  createIntersectionObserver(
    callback: IntersectionObserverCallback,
    options?: IntersectionObserverInit
  ): IntersectionObserver | null {
    const windowObj = this.platformDetection.getWindow();
    if (!windowObj || !(windowObj as any).IntersectionObserver) {
      return null;
    }

    try {
      return new (windowObj as any).IntersectionObserver(callback, options);
    } catch {
      return null;
    }
  }
}