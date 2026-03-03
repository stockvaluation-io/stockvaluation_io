import { Injectable, signal } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { ChatMessage, ChatSession } from './chat-message.interface';

/**
 * Chat Service
 * Manages chat state, message history, and provides helper methods
 */
@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private messagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  private currentSessionSubject = new BehaviorSubject<ChatSession | null>(null);

  public messages$ = this.messagesSubject.asObservable();
  public currentSession$ = this.currentSessionSubject.asObservable();
  public isLoading = signal(false);

  /**
   * Get current messages
   */
  getMessages(): ChatMessage[] {
    return this.messagesSubject.value;
  }

  /**
   * Add a new message to the chat
   */
  addMessage(
    content: string,
    sender: 'user' | 'ai',
    metadata?: any
  ): ChatMessage {
    const newMessage: ChatMessage = {
      id: this.generateMessageId(),
      content,
      sender,
      timestamp: new Date(),
      metadata,
    };

    const updatedMessages = [...this.messagesSubject.value, newMessage];
    this.messagesSubject.next(updatedMessages);

    // Update session
    this.updateCurrentSession(updatedMessages);

    return newMessage;
  }

  /**
   * Add a user message
   */
  addUserMessage(content: string): ChatMessage {
    return this.addMessage(content, 'user');
  }

  /**
   * Add an AI message
   */
  addAiMessage(content: string, metadata?: any): ChatMessage {
    return this.addMessage(content, 'ai', metadata);
  }

  /**
   * Add a typing indicator
   */
  addTypingIndicator(): ChatMessage {
    const typingMessage: ChatMessage = {
      id: 'typing-indicator',
      content: '',
      sender: 'ai',
      timestamp: new Date(),
      isTyping: true,
    };

    const updatedMessages = [...this.messagesSubject.value, typingMessage];
    this.messagesSubject.next(updatedMessages);

    return typingMessage;
  }

  /**
   * Remove typing indicator
   */
  removeTypingIndicator(): void {
    const updatedMessages = this.messagesSubject.value.filter(
      (msg) => msg.id !== 'typing-indicator'
    );
    this.messagesSubject.next(updatedMessages);
  }

  /**
   * Update an existing message
   */
  updateMessage(messageId: string, updates: Partial<ChatMessage>): void {
    const updatedMessages = this.messagesSubject.value.map((msg) =>
      msg.id === messageId ? { ...msg, ...updates } : msg
    );
    this.messagesSubject.next(updatedMessages);
    this.updateCurrentSession(updatedMessages);
  }

  /**
   * Delete a message by ID
   */
  deleteMessage(messageId: string): void {
    const updatedMessages = this.messagesSubject.value.filter(
      (msg) => msg.id !== messageId
    );
    this.messagesSubject.next(updatedMessages);
    this.updateCurrentSession(updatedMessages);
  }

  /**
   * Clear all messages
   */
  clearMessages(): void {
    this.messagesSubject.next([]);
    this.currentSessionSubject.next(null);
  }

  /**
   * Start a new chat session
   */
  startNewSession(metadata?: any): ChatSession {
    const newSession: ChatSession = {
      id: this.generateSessionId(),
      messages: [],
      createdAt: new Date(),
      updatedAt: new Date(),
      metadata,
    };

    this.currentSessionSubject.next(newSession);
    this.messagesSubject.next([]);

    return newSession;
  }

  /**
   * Get current session
   */
  getCurrentSession(): ChatSession | null {
    return this.currentSessionSubject.value;
  }

  /**
   * Update current session
   */
  private updateCurrentSession(messages: ChatMessage[]): void {
    const currentSession = this.currentSessionSubject.value;
    if (currentSession) {
      const updatedSession: ChatSession = {
        ...currentSession,
        messages,
        updatedAt: new Date(),
      };
      this.currentSessionSubject.next(updatedSession);
    }
  }

  /**
   * Save session to localStorage
   */
  saveSession(session: ChatSession): void {
    try {
      const sessions = this.getSavedSessions();
      const existingIndex = sessions.findIndex((s) => s.id === session.id);

      if (existingIndex >= 0) {
        sessions[existingIndex] = session;
      } else {
        sessions.push(session);
      }

      localStorage.setItem('chat-sessions', JSON.stringify(sessions));
    } catch (error) {
      console.error('Error saving session:', error);
    }
  }

  /**
   * Load session from localStorage
   */
  loadSession(sessionId: string): ChatSession | null {
    try {
      const sessions = this.getSavedSessions();
      const session = sessions.find((s) => s.id === sessionId);

      if (session) {
        // Convert date strings back to Date objects
        session.createdAt = new Date(session.createdAt);
        session.updatedAt = new Date(session.updatedAt);
        session.messages = session.messages.map((msg) => ({
          ...msg,
          timestamp: new Date(msg.timestamp),
        }));

        this.currentSessionSubject.next(session);
        this.messagesSubject.next(session.messages);
      }

      return session || null;
    } catch (error) {
      console.error('Error loading session:', error);
      return null;
    }
  }

  /**
   * Get all saved sessions
   */
  getSavedSessions(): ChatSession[] {
    try {
      const sessionsJson = localStorage.getItem('chat-sessions');
      return sessionsJson ? JSON.parse(sessionsJson) : [];
    } catch (error) {
      console.error('Error getting saved sessions:', error);
      return [];
    }
  }

  /**
   * Delete a saved session
   */
  deleteSavedSession(sessionId: string): void {
    try {
      const sessions = this.getSavedSessions().filter(
        (s) => s.id !== sessionId
      );
      localStorage.setItem('chat-sessions', JSON.stringify(sessions));
    } catch (error) {
      console.error('Error deleting session:', error);
    }
  }

  /**
   * Generate unique message ID
   */
  private generateMessageId(): string {
    return `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Generate unique session ID
   */
  private generateSessionId(): string {
    return `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Format message content for display (can be extended for markdown, etc.)
   */
  formatMessageContent(content: string): string {
    // Basic formatting - can be extended
    return content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') // Bold
      .replace(/\*(.*?)\*/g, '<em>$1</em>') // Italic
      .replace(/`(.*?)`/g, '<code>$1</code>'); // Code
  }

  /**
   * Export chat session as JSON
   */
  exportSession(session: ChatSession): void {
    const dataStr = JSON.stringify(session, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `chat-session-${session.id}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }
}

