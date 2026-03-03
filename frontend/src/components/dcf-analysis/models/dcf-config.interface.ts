export interface DCFConfig {
  title: string;
  subtitle: string;
}

export interface DCFStep {
  id: string;
  title: string;
  description: string;
  isCompleted: boolean;
  isActive: boolean;
  isDisabled: boolean;
}

export interface DCFState {
  selectedCompany: any | null;
  results: any | null;
  isLoading: boolean;
  error: string | null;
}