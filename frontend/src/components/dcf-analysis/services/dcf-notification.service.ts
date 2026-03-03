import { Injectable } from '@angular/core';
import { toast } from 'ngx-sonner';
import { PlatformDetectionService } from '../../../core/services';

@Injectable({
  providedIn: 'root'
})
export class DCFNotificationService {

  constructor(private platformDetection: PlatformDetectionService) { }

  /**
   * Show success notification for completed custom DCF calculation
   */
  showCustomCalculationSuccess(companySymbol?: string): void {
    const message = companySymbol 
      ? `${companySymbol} analysis updated with your custom assumptions`
      : 'Analysis updated with your custom assumptions';
    
    toast.success(message, {
      description: 'Your new valuation results are now available below',
      duration: 4000,
      action: {
        label: 'View Results',
        onClick: () => {
          // Scroll to top of results
          const windowObj = this.platformDetection.getWindow();
          if (windowObj) {
            windowObj.scrollTo({ top: 0, behavior: 'smooth' });
          }
        }
      }
    });
  }

  /**
   * Show error notification for failed custom DCF calculation
   */
  showCustomCalculationError(error?: string): void {
    const message = 'Failed to update analysis';
    const description = error || 'There was an error processing your custom assumptions. Please try again.';
    
    toast.error(message, {
      description,
      duration: 6000,
      action: {
        label: 'Retry',
        onClick: () => {
          // The retry action will be handled by the parent component
        }
      }
    });
  }

  /**
   * Show notification when custom calculation is cancelled
   */
  showCustomCalculationCancelled(): void {
    toast.info('Calculation cancelled', {
      description: 'Your previous analysis results have been restored',
      duration: 3000
    });
  }

  /**
   * Show loading notification for custom calculation in progress
   */
  showCustomCalculationLoading(companySymbol?: string): string | number {
    const message = companySymbol 
      ? `Recalculating ${companySymbol} with your assumptions...`
      : 'Recalculating with your assumptions...';
    
    return toast.loading(message, {
      description: 'This may take a few moments'
    });
  }

  /**
   * Dismiss a specific toast by ID
   */
  dismissToast(toastId: string | number): void {
    toast.dismiss(toastId);
  }

  /**
   * Dismiss all toasts
   */
  dismissAll(): void {
    toast.dismiss();
  }

  /**
   * Show general info notification
   */
  showInfo(message: string, description?: string): void {
    toast.info(message, {
      description,
      duration: 4000
    });
  }

  /**
   * Show general success notification
   */
  showSuccess(message: string, description?: string): void {
    toast.success(message, {
      description,
      duration: 4000
    });
  }

  /**
   * Show general warning notification
   */
  showWarning(message: string, description?: string): void {
    toast.warning(message, {
      description,
      duration: 5000
    });
  }

  /**
   * Show general error notification
   */
  showError(message: string, description?: string): void {
    toast.error(message, {
      description,
      duration: 6000
    });
  }
}