import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { LoggerService } from '../infrastructure/logger.service';

export interface ContactFormData {
  name: string;
  email: string;
  subject: string;
  message: string;
  category?: string;
  recaptchaToken?: string;
}

export interface ContactFormResponse {
  success: boolean;
  message: string;
  messageId?: string;
  service?: string;
}

export interface ContactFormError {
  error: string;
  success: false;
  details?: any[];
}

@Injectable({
  providedIn: 'root'
})
export class ContactFormService {
  private http = inject(HttpClient);
  private logger = inject(LoggerService);

  private readonly CONTACT_ENDPOINT = '/contact';

  /**
   * Submit contact form data to the API
   */
  submitContactForm(formData: ContactFormData): Observable<ContactFormResponse> {
    this.logger.debug('Submitting contact form', { formData: { ...formData, recaptchaToken: '[REDACTED]' } }, 'ContactFormService');

    // Prepare the payload with reCAPTCHA token
    const apiPayload = {
      name: formData.name,
      email: formData.email,
      subject: formData.subject,
      message: formData.message,
      recaptchaToken: formData.recaptchaToken
    };

    return this.http.post<ContactFormResponse>(this.CONTACT_ENDPOINT, apiPayload).pipe(
      map(response => {
        this.logger.debug('Contact form submission successful', { response }, 'ContactFormService');
        return response;
      }),
      catchError((error: HttpErrorResponse) => {
        this.logger.error('Contact form submission failed', error, 'ContactFormService');
        return throwError(() => this.handleContactFormError(error));
      })
    );
  }

  /**
   * Handle and format API errors for user display
   */
  private handleContactFormError(error: HttpErrorResponse): ContactFormError {
    // Rate limiting error (429)
    if (error.status === 429) {
      return {
        error: 'Too many contact form submissions. Please try again in a few minutes.',
        success: false
      };
    }

    // Validation errors (400)
    if (error.status === 400) {
      // reCAPTCHA verification errors
      if (error.error?.error && error.error.error.includes('reCAPTCHA')) {
        return {
          error: error.error.error,
          success: false
        };
      }
      
      // Form validation errors
      if (error.error?.details) {
        return {
          error: 'Please check your form inputs and try again.',
          success: false,
          details: error.error.details
        };
      }
    }

    // Server errors (500+)
    if (error.status >= 500) {
      return {
        error: 'Our servers are currently experiencing issues. Please try again later or contact us directly.',
        success: false
      };
    }

    // Network/connection errors
    if (error.status === 0) {
      return {
        error: 'Unable to connect to our servers. Please check your internet connection and try again.',
        success: false
      };
    }

    // Generic API error
    const apiErrorMessage = error.error?.error || error.error?.message || error.message;
    return {
      error: apiErrorMessage || 'An unexpected error occurred. Please try again.',
      success: false
    };
  }

  /**
   * Get user-friendly error message from validation details
   */
  getValidationErrorMessage(details: any[]): string {
    if (!details || details.length === 0) {
      return 'Please check your form inputs.';
    }

    const firstError = details[0];
    return firstError.msg || firstError.message || 'Invalid form data.';
  }
}