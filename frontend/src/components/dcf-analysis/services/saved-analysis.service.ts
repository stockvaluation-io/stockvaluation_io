import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

import { CompanyData, ValuationResults } from '../models';
import { LoggerService, PlatformDetectionService } from '../../../core/services';

export interface SavedAnalysis {
  id: string;
  company: CompanyData;
  results: ValuationResults;
  savedAt: Date;
  title: string;
  description?: string;
}

@Injectable({
  providedIn: 'root'
})
export class SavedAnalysisService {
  private readonly STORAGE_KEY = 'stockvaluations_saved_analyses';
  private savedAnalysesSubject = new BehaviorSubject<SavedAnalysis[]>([]);
  
  constructor(
    private logger: LoggerService,
    private platformDetection: PlatformDetectionService
  ) {
    this.loadSavedAnalyses();
  }

  get savedAnalyses$(): Observable<SavedAnalysis[]> {
    return this.savedAnalysesSubject.asObservable();
  }

  get savedAnalyses(): SavedAnalysis[] {
    return this.savedAnalysesSubject.value;
  }

  saveAnalysis(company: CompanyData, results: ValuationResults, title?: string, description?: string): SavedAnalysis {
    const analysis: SavedAnalysis = {
      id: this.generateId(),
      company,
      results,
      savedAt: new Date(),
      title: title || `${company.name} (${company.symbol}) Analysis`,
      description
    };

    const currentAnalyses = this.savedAnalyses;
    const updatedAnalyses = [analysis, ...currentAnalyses];
    
    // Keep only the latest 50 analyses to prevent localStorage from growing too large
    const trimmedAnalyses = updatedAnalyses.slice(0, 50);
    
    this.saveToStorage(trimmedAnalyses);
    this.savedAnalysesSubject.next(trimmedAnalyses);
    
    return analysis;
  }

  deleteAnalysis(id: string): boolean {
    const currentAnalyses = this.savedAnalyses;
    const filteredAnalyses = currentAnalyses.filter(analysis => analysis.id !== id);
    
    if (filteredAnalyses.length !== currentAnalyses.length) {
      this.saveToStorage(filteredAnalyses);
      this.savedAnalysesSubject.next(filteredAnalyses);
      return true;
    }
    
    return false;
  }

  getAnalysis(id: string): SavedAnalysis | undefined {
    return this.savedAnalyses.find(analysis => analysis.id === id);
  }

  updateAnalysis(id: string, updates: Partial<Pick<SavedAnalysis, 'title' | 'description'>>): boolean {
    const currentAnalyses = this.savedAnalyses;
    const analysisIndex = currentAnalyses.findIndex(analysis => analysis.id === id);
    
    if (analysisIndex !== -1) {
      const updatedAnalyses = [...currentAnalyses];
      updatedAnalyses[analysisIndex] = {
        ...updatedAnalyses[analysisIndex],
        ...updates
      };
      
      this.saveToStorage(updatedAnalyses);
      this.savedAnalysesSubject.next(updatedAnalyses);
      return true;
    }
    
    return false;
  }

  clearAllAnalyses(): void {
    this.saveToStorage([]);
    this.savedAnalysesSubject.next([]);
  }

  exportAnalysis(id: string): string | null {
    const analysis = this.getAnalysis(id);
    if (!analysis) {
      return null;
    }
    
    return JSON.stringify(analysis, null, 2);
  }

  importAnalysis(jsonData: string): SavedAnalysis | null {
    try {
      const analysis: SavedAnalysis = JSON.parse(jsonData);
      
      // Validate the imported data structure
      if (!this.isValidAnalysis(analysis)) {
        throw new Error('Invalid analysis data structure');
      }
      
      // Convert savedAt string back to Date if needed
      if (typeof analysis.savedAt === 'string') {
        analysis.savedAt = new Date(analysis.savedAt);
      }
      
      // Generate new ID to avoid conflicts
      analysis.id = this.generateId();
      
      const currentAnalyses = this.savedAnalyses;
      const updatedAnalyses = [analysis, ...currentAnalyses].slice(0, 50);
      
      this.saveToStorage(updatedAnalyses);
      this.savedAnalysesSubject.next(updatedAnalyses);
      
      return analysis;
    } catch (error) {
      this.logger.error('Failed to import analysis', error, 'SavedAnalysisService');
      return null;
    }
  }

  getAnalysesByCompany(symbol: string): SavedAnalysis[] {
    return this.savedAnalyses.filter(analysis => 
      analysis.company.symbol.toLowerCase() === symbol.toLowerCase()
    );
  }

  getAnalysesCount(): number {
    return this.savedAnalyses.length;
  }

  private loadSavedAnalyses(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored) {
        const analyses: SavedAnalysis[] = JSON.parse(stored);
        // Convert savedAt strings back to Date objects
        const processedAnalyses = analyses.map(analysis => ({
          ...analysis,
          savedAt: new Date(analysis.savedAt)
        }));
        this.savedAnalysesSubject.next(processedAnalyses);
      }
    } catch (error) {
      this.logger.error('Failed to load saved analyses from localStorage', error, 'SavedAnalysisService');
      // Clear corrupted data
      localStorage.removeItem(this.STORAGE_KEY);
      this.savedAnalysesSubject.next([]);
    }
  }

  private saveToStorage(analyses: SavedAnalysis[]): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(analyses));
    } catch (error) {
      this.logger.error('Failed to save analyses to localStorage', error, 'SavedAnalysisService');
      // Handle quota exceeded or other localStorage errors
      if (error instanceof DOMException && error.code === DOMException.QUOTA_EXCEEDED_ERR) {
        // Try to free up space by removing older analyses
        const reducedAnalyses = analyses.slice(0, 25);
        try {
          localStorage.setItem(this.STORAGE_KEY, JSON.stringify(reducedAnalyses));
          this.savedAnalysesSubject.next(reducedAnalyses);
        } catch (retryError) {
          this.logger.error('Failed to save even reduced analyses', retryError, 'SavedAnalysisService');
        }
      }
    }
  }

  private generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }

  private isValidAnalysis(analysis: any): analysis is SavedAnalysis {
    return (
      analysis &&
      typeof analysis.id === 'string' &&
      typeof analysis.title === 'string' &&
      analysis.company &&
      typeof analysis.company.name === 'string' &&
      typeof analysis.company.symbol === 'string' &&
      analysis.results &&
      typeof analysis.results.intrinsicValue === 'number' &&
      (analysis.savedAt instanceof Date || typeof analysis.savedAt === 'string')
    );
  }
}