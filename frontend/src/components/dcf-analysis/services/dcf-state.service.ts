import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { DCFState, CompanyData, AnalysisInputs, ValuationResults } from '../models';
import { CompanyDataResponse, DCFValuationResponse } from '../models/api-response.interface';
import { DCFStorageService } from './dcf-storage.service';
import { LoggerService } from '../../../core/services';

type CompanyApiData = CompanyDataResponse | DCFValuationResponse;

@Injectable({
  providedIn: 'root'
})
export class DCFStateService {
  private readonly initialState: DCFState = {
    selectedCompany: null,
    results: null,
    isLoading: false,
    error: null
  };

  // Store raw API data separately for use in calculations
  private companyApiData: CompanyApiData | null = null;

  private stateSubject = new BehaviorSubject<DCFState>(this.initialState);
  public state$ = this.stateSubject.asObservable();

  constructor(
    private storageService: DCFStorageService,
    private logger: LoggerService
  ) {
    // Initialize with default state immediately for fast rendering
    this.stateSubject.next(this.initialState);
    
    // Don't auto-load saved state in constructor
    // Let the container decide when to load based on URL context
  }

  get currentState(): DCFState {
    return this.stateSubject.value;
  }



  setSelectedCompany(company: CompanyData, apiData?: CompanyApiData): void {
    if (apiData) {
      this.companyApiData = apiData;
    }
    this.updateState({
      selectedCompany: company
    });
  }

  getCompanyApiData(): CompanyApiData | null {
    return this.companyApiData;
  }

  setUserInputs(inputs: AnalysisInputs): void {
    // Note: userInputs removed from state - this method is kept for backward compatibility
    // but doesn't update state anymore since userInputs are not part of the simplified state
    this.logger.debug('setUserInputs called but userInputs removed from state', inputs, 'DCFStateService');
  }

  setResults(results: ValuationResults | null): void {
    this.updateState({
      results: results,
      isLoading: false
    });
  }

  setLoading(loading: boolean): void {
    this.updateState({ isLoading: loading });
  }

  setError(error: string | null): void {
    this.updateState({ error, isLoading: false });
  }



  reset(): void {
    this.companyApiData = null;
    this.stateSubject.next(this.initialState);
    this.storageService.clearAllData();
  }

  resetToNewAnalysis(): void {
    // Reset for new analysis
    this.companyApiData = null;
    this.stateSubject.next(this.initialState);
    
    // Clear storage except for analysis type preference
    this.storageService.clearAnalysisData();
  }

  private updateState(partialState: Partial<DCFState>): void {
    const currentState = this.currentState;
    const newState = { ...currentState, ...partialState };
    this.stateSubject.next(newState);
    
    // Only save to localStorage for meaningful state changes (not loading/error states)
    if (this.shouldPersistState(newState, currentState)) {
      this.saveState();
    }
  }

  /**
   * Determine if state changes should be persisted to localStorage
   * Reduces unnecessary localStorage writes for better performance
   */
  private shouldPersistState(newState: DCFState, oldState: DCFState): boolean {
    // Don't persist loading or error states
    if (newState.isLoading || newState.error) {
      return false;
    }
    
    // Don't persist if only loading state changed
    if (oldState.isLoading !== newState.isLoading && 
        oldState.error === newState.error &&
        oldState.selectedCompany === newState.selectedCompany &&
        oldState.results === newState.results) {
      return false;
    }
    
    // Persist meaningful changes: company selection, results
    return (
      oldState.selectedCompany !== newState.selectedCompany ||
      oldState.results !== newState.results
    );
  }


  private validateRestoredState(state: DCFState): DCFState {
    // Clear results if we don't have a selected company
    if (!state.selectedCompany && state.results) {
      return {
        ...state,
        selectedCompany: null,
        results: null
      };
    }
    
    return state;
  }

  loadSavedStateIfAvailable(): boolean {
    try {
      if (!this.storageService.isStorageAvailable()) return false;
      
      const savedState = this.storageService.loadState();
      if (savedState) {
        // Validate state consistency before restoring
        const validatedState = this.validateRestoredState(savedState);
        
        // Reset loading and error states when loading from storage
        const restoredState = {
          ...validatedState,
          isLoading: false,
          error: null
        };
        this.stateSubject.next(restoredState);
        return true;
      }
      return false;
    } catch (error) {
      this.logger.warn('Error loading saved state', error, 'DCFStateService');
      return false;
    }
  }

  private saveState(): void {
    if (!this.storageService.isStorageAvailable()) return;
    
    const currentState = this.currentState;
    // Don't save loading or error states
    const stateToSave = {
      ...currentState,
      isLoading: false,
      error: null
    };
    
    this.storageService.saveState(stateToSave);
  }
}
