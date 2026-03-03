export interface LoadingStage {
  id: string;
  title: string;
  description: string;
  duration: number; // milliseconds
  icon: string;
}

export interface LoadingProgress {
  currentStage: number;
  stages: LoadingStage[];
  isComplete: boolean;
  hasError: boolean;
}