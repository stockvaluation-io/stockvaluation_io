/**
 * Chat Message Interface
 * Defines the structure for chat messages in the chat UI component
 */

import { FormattedMessage } from './message-formatter.service';

export interface ChatMessage {
  id: string;
  content: string;
  sender: 'user' | 'ai';
  timestamp: Date;
  isTyping?: boolean;
  metadata?: ChatMessageMetadata;
  formatted?: FormattedMessage; // Cached formatted message
}

export interface ChatMessageMetadata {
  tokens?: number;
  model?: string;
  error?: boolean;
  errorMessage?: string;
  // Tool approval metadata
  tool_approval?: boolean;
  tool_id?: string;
  tool_name?: string;
  awaiting_response?: boolean;
  // Enhanced metadata for structured responses
  has_metrics?: boolean;
  has_question?: boolean;
  sections_count?: number;
  suggested_actions?: string[];
  confidence?: 'high' | 'medium' | 'low';
  sources?: string[];
  [key: string]: any;
}

export interface ChatSession {
  id: string;
  messages: ChatMessage[];
  createdAt: Date;
  updatedAt: Date;
  title?: string;
  metadata?: {
    ticker?: string;
    context?: string;
    [key: string]: any;
  };
}

export interface ChatConfig {
  placeholder?: string;
  maxLength?: number;
  autoScroll?: boolean;
  showTimestamp?: boolean;
  allowMultiline?: boolean;
  submitOnEnter?: boolean;
  userAvatar?: string;
  aiAvatar?: string;
  enableTypingIndicator?: boolean;
}

