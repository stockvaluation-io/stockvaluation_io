import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface LoadingState {
  isLoading: boolean;
  hasError: boolean;
  isEmpty: boolean;
  errorMessage?: string;
  loadingMessage?: string;
}

export interface SectionLoadingState {
  [sectionId: string]: LoadingState;
}

@Injectable({
  providedIn: 'root'
})
export class LoadingStateService {
  private globalStateSubject = new BehaviorSubject<LoadingState>({
    isLoading: false,
    hasError: false,
    isEmpty: false
  });

  private sectionStatesSubject = new BehaviorSubject<SectionLoadingState>({});

  globalState$ = this.globalStateSubject.asObservable();
  sectionStates$ = this.sectionStatesSubject.asObservable();

  // Global state management
  setGlobalLoading(isLoading: boolean, message?: string): void {
    this.globalStateSubject.next({
      ...this.globalStateSubject.value,
      isLoading,
      loadingMessage: message,
      hasError: false,
      isEmpty: false
    });
  }

  setGlobalError(errorMessage: string): void {
    this.globalStateSubject.next({
      ...this.globalStateSubject.value,
      isLoading: false,
      hasError: true,
      isEmpty: false,
      errorMessage
    });
  }

  setGlobalEmpty(): void {
    this.globalStateSubject.next({
      ...this.globalStateSubject.value,
      isLoading: false,
      hasError: false,
      isEmpty: true
    });
  }

  setGlobalSuccess(): void {
    this.globalStateSubject.next({
      isLoading: false,
      hasError: false,
      isEmpty: false
    });
  }

  clearGlobalState(): void {
    this.globalStateSubject.next({
      isLoading: false,
      hasError: false,
      isEmpty: false
    });
  }

  // Section-specific state management
  setSectionLoading(sectionId: string, isLoading: boolean, message?: string): void {
    const currentStates = this.sectionStatesSubject.value;
    this.sectionStatesSubject.next({
      ...currentStates,
      [sectionId]: {
        isLoading,
        hasError: false,
        isEmpty: false,
        loadingMessage: message
      }
    });
  }

  setSectionError(sectionId: string, errorMessage: string): void {
    const currentStates = this.sectionStatesSubject.value;
    this.sectionStatesSubject.next({
      ...currentStates,
      [sectionId]: {
        isLoading: false,
        hasError: true,
        isEmpty: false,
        errorMessage
      }
    });
  }

  setSectionEmpty(sectionId: string): void {
    const currentStates = this.sectionStatesSubject.value;
    this.sectionStatesSubject.next({
      ...currentStates,
      [sectionId]: {
        isLoading: false,
        hasError: false,
        isEmpty: true
      }
    });
  }

  setSectionSuccess(sectionId: string): void {
    const currentStates = this.sectionStatesSubject.value;
    this.sectionStatesSubject.next({
      ...currentStates,
      [sectionId]: {
        isLoading: false,
        hasError: false,
        isEmpty: false
      }
    });
  }

  clearSectionState(sectionId: string): void {
    const currentStates = this.sectionStatesSubject.value;
    const { [sectionId]: removed, ...remainingStates } = currentStates;
    this.sectionStatesSubject.next(remainingStates);
  }

  clearAllSectionStates(): void {
    this.sectionStatesSubject.next({});
  }

  // Getters for current state
  get globalState(): LoadingState {
    return this.globalStateSubject.value;
  }

  get sectionStates(): SectionLoadingState {
    return this.sectionStatesSubject.value;
  }

  getSectionState(sectionId: string): LoadingState {
    return this.sectionStates[sectionId] || {
      isLoading: false,
      hasError: false,
      isEmpty: false
    };
  }

  // Observable for specific section
  getSectionState$(sectionId: string): Observable<LoadingState> {
    return new Observable(observer => {
      const subscription = this.sectionStates$.subscribe(states => {
        observer.next(states[sectionId] || {
          isLoading: false,
          hasError: false,
          isEmpty: false
        });
      });

      return () => subscription.unsubscribe();
    });
  }

  // Utility methods
  isAnySectionLoading(): boolean {
    return Object.values(this.sectionStates).some(state => state.isLoading);
  }

  hasAnySectionError(): boolean {
    return Object.values(this.sectionStates).some(state => state.hasError);
  }

  isAllSectionsEmpty(): boolean {
    const states = Object.values(this.sectionStates);
    return states.length > 0 && states.every(state => state.isEmpty);
  }

  // Simulate loading sequences for different scenarios
  simulateFullAnalysisLoading(): Promise<void> {
    return new Promise((resolve) => {
      this.setGlobalLoading(true, 'Initializing DCF analysis...');
      
      setTimeout(() => {
        this.setGlobalLoading(true, 'Fetching company financial data...');
      }, 1000);
      
      setTimeout(() => {
        this.setGlobalLoading(true, 'Building financial projections...');
      }, 2000);
      
      setTimeout(() => {
        this.setGlobalLoading(true, 'Calculating intrinsic value...');
      }, 3000);
      
      setTimeout(() => {
        this.setGlobalLoading(true, 'Generating risk analysis...');
      }, 4000);
      
      setTimeout(() => {
        this.setGlobalSuccess();
        resolve();
      }, 5000);
    });
  }
}