import {
  Component,
  Input,
  Output,
  EventEmitter,
  ViewChild,
  ElementRef,
  AfterViewChecked,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
  signal,
  effect,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextarea } from 'primeng/inputtextarea';
import { ScrollPanelModule } from 'primeng/scrollpanel';
import { MarkdownModule } from 'ngx-markdown';
import { Router } from '@angular/router';
import { ChatMessage, ChatConfig } from './chat-message.interface';
import { ThemeService } from '../../../core/services/infrastructure/theme.service';
import { ChatService } from './chat.service';
import { MessageFormatterService, FormattedMessage, QuickAction } from './message-formatter.service';
import { MetricVisualizationComponent } from './metric-visualization.component';
import { InsightCardComponent } from './insight-card.component';
import { MessageActionsComponent } from './message-actions.component';

@Component({
  selector: 'app-chat-ui',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextarea,
    ScrollPanelModule,
    MarkdownModule,
    MetricVisualizationComponent,
    InsightCardComponent,
    MessageActionsComponent,
  ],
  templateUrl: './chat-ui.component.html',
  styleUrls: ['./chat-ui.component.scss'],
})
export class ChatUiComponent implements OnInit, AfterViewChecked, OnDestroy {
  @ViewChild('scrollContainer') scrollContainer!: ElementRef;
  @ViewChild('messageInput') messageInput!: ElementRef;

  // Inputs
  @Input() messages: ChatMessage[] = [];
  @Input() config: ChatConfig = {};
  @Input() isLoading = false;
  @Input() disabled = false;

  // Outputs
  @Output() messageSent = new EventEmitter<string>();
  @Output() messageDeleted = new EventEmitter<string>();
  @Output() toolApproved = new EventEmitter<ChatMessage>();
  @Output() toolDenied = new EventEmitter<ChatMessage>();

  // Component state
  currentMessage = signal('');
  isTyping = signal(false);
  shouldAutoScroll = true;

  // Theme service
  private themeService = inject(ThemeService);
  currentTheme = this.themeService.currentTheme;
  
  // Chat service for message updates
  private chatService = inject(ChatService);
  
  // Message formatter service
  private messageFormatter = inject(MessageFormatterService);
  
  // Router for navigation
  private router = inject(Router);

  // Default configuration
  defaultConfig: ChatConfig = {
    placeholder: 'Type your message...',
    maxLength: 4000,
    autoScroll: true,
    showTimestamp: true,
    allowMultiline: true,
    submitOnEnter: true,
    enableTypingIndicator: true,
  };

  private scrollTimeout?: ReturnType<typeof setTimeout>;
  private userScrollTimeout?: ReturnType<typeof setTimeout>;
  private previousMessageCount = 0;
  private isUserScrolling = false;

  constructor(private cdr: ChangeDetectorRef) {
    // Effect to handle typing indicator
    effect(() => {
      const message = this.currentMessage();
      this.isTyping.set(message.length > 0);
    });
  }

  ngOnInit(): void {
    // Merge default config with provided config
    this.config = { ...this.defaultConfig, ...this.config };
  }

  ngAfterViewChecked(): void {
    // Only auto-scroll if:
    // 1. Auto-scroll is enabled
    // 2. User hasn't manually scrolled up
    // 3. New messages were added (not just any change detection)
    // 4. User is near the bottom (within threshold)
    const currentMessageCount = this.messages.length;
    const hasNewMessages = currentMessageCount > this.previousMessageCount;
    
    if (hasNewMessages) {
      this.previousMessageCount = currentMessageCount;
      
      // Only auto-scroll if user is near bottom or hasn't scrolled manually
      if (this.shouldAutoScroll && this.getConfig('autoScroll') && !this.isUserScrolling) {
        // Double-check user is at bottom before scrolling
        if (this.isNearBottom()) {
          this.scrollToBottom();
        }
      }
    }
  }

  ngOnDestroy(): void {
    if (this.scrollTimeout) {
      clearTimeout(this.scrollTimeout);
    }
    if (this.userScrollTimeout) {
      clearTimeout(this.userScrollTimeout);
    }
  }

  /**
   * Get configuration value with fallback to default
   */
  getConfig<K extends keyof ChatConfig>(key: K): ChatConfig[K] {
    return this.config[key] ?? this.defaultConfig[key];
  }

  /**
   * Send message
   */
  sendMessage(): void {
    const message = this.currentMessage().trim();
    if (!message || this.disabled || this.isLoading) {
      return;
    }

    this.messageSent.emit(message);
    this.currentMessage.set('');
    this.isTyping.set(false);

    // Reset scroll behavior when user sends a message
    // User sending a message means they want to see the response
    this.shouldAutoScroll = true;
    this.isUserScrolling = false;

    // Focus back on input
    setTimeout(() => {
      this.messageInput?.nativeElement?.focus();
    }, 100);
  }

  /**
   * Handle keyboard events in textarea
   */
  onKeyDown(event: KeyboardEvent): void {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      this.getConfig('submitOnEnter')
    ) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  /**
   * Delete a message by ID
   */
  deleteMessage(messageId: string): void {
    this.messageDeleted.emit(messageId);
  }

  /**
   * Scroll to bottom of chat
   */
  scrollToBottom(): void {
    try {
      const container = this.scrollContainer?.nativeElement;
      if (container) {
        // Clear any existing timeout to avoid multiple scroll attempts
        if (this.scrollTimeout) {
          clearTimeout(this.scrollTimeout);
        }
        
        // Use setTimeout to ensure DOM is fully updated before scrolling
        this.scrollTimeout = setTimeout(() => {
          container.scrollTo({
            top: container.scrollHeight,
            behavior: 'smooth'
          });
        }, 50);
      }
    } catch (err) {
      console.error('Error scrolling to bottom:', err);
    }
  }

  /**
   * Check if scroll position is near the bottom
   */
  isNearBottom(threshold: number = 150): boolean {
    const container = this.scrollContainer?.nativeElement;
    if (!container) return true; // Default to true if container not available
    
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    return distanceFromBottom <= threshold;
  }

  /**
   * Handle scroll event to detect manual scrolling
   */
  onScroll(): void {
    const container = this.scrollContainer?.nativeElement;
    if (!container) return;

    // Check if user is near bottom (within 150px threshold)
    const isAtBottom = this.isNearBottom(150);

    // Track if user is actively scrolling (not just programmatic scroll)
    // Use a small delay to distinguish user scroll from auto-scroll
    if (this.userScrollTimeout) {
      clearTimeout(this.userScrollTimeout);
    }
    this.isUserScrolling = true;
    
    // Reset user scrolling flag after scroll ends
    this.userScrollTimeout = setTimeout(() => {
      this.isUserScrolling = false;
    }, 150);

    // Disable auto-scroll if user scrolled up significantly
    // Only disable if user is clearly not at bottom (more than 200px away)
    if (!isAtBottom && container.scrollTop > 0) {
      this.shouldAutoScroll = false;
    } else if (isAtBottom) {
      // Re-enable auto-scroll when user scrolls back to bottom
      this.shouldAutoScroll = true;
    }
  }

  /**
   * Format timestamp for display
   */
  formatTimestamp(date: Date): string {
    const now = new Date();
    const messageDate = new Date(date);
    const diffMs = now.getTime() - messageDate.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    return messageDate.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /**
   * Track by function for ngFor optimization
   */
  trackByMessageId(index: number, message: ChatMessage): string {
    return message.id;
  }

  /**
   * Check if this is the last message from a specific sender
   */
  isLastInSequence(index: number, sender: 'user' | 'ai'): boolean {
    if (index === this.messages.length - 1) return true;
    return this.messages[index + 1]?.sender !== sender;
  }

  /**
   * Check if this is the first message from a specific sender
   */
  isFirstInSequence(index: number, sender: 'user' | 'ai'): boolean {
    if (index === 0) return true;
    return this.messages[index - 1]?.sender !== sender;
  }

  /**
   * Handle tool approval button click
   */
  onToolApprove(message: ChatMessage): void {
    console.log('[ChatUI] Tool approved:', message.metadata?.['tool_name']);
    
    // Update message metadata via ChatService to trigger change detection
    this.chatService.updateMessage(message.id, {
      metadata: {
        ...message.metadata,
        ['responded']: true,
        ['awaiting_response']: false,
      }
    });
    
    // Emit event
    this.toolApproved.emit(message);
    
    // Send "yes" as a regular message
    this.messageSent.emit('yes');
  }

  /**
   * Handle tool denial button click
   */
  onToolDeny(message: ChatMessage): void {
    console.log('[ChatUI] Tool denied:', message.metadata?.['tool_name']);
    
    // Update message metadata via ChatService to trigger change detection
    this.chatService.updateMessage(message.id, {
      metadata: {
        ...message.metadata,
        ['responded']: true,
        ['awaiting_response']: false,
      }
    });
    
    // Emit event
    this.toolDenied.emit(message);
    
    // Send "no" as a regular message
    this.messageSent.emit('no');
  }

  /**
   * Get formatted message with enhanced parsing
   */
  getFormattedMessage(message: ChatMessage): FormattedMessage {
    if (message.sender === 'user') {
      return {
        raw: message.content,
        sections: [],
        keyMetrics: [],
        suggestedActions: [],
        hasStructuredContent: false
      };
    }
    
    if (!message.formatted) {
      message.formatted = this.messageFormatter.parseMessage(message.content);
    }
    return message.formatted;
  }

  /**
   * Get enhanced markdown content
   */
  getEnhancedContent(message: ChatMessage): string {
    if (message.sender === 'user') {
      return message.content;
    }
    
    return this.messageFormatter.enhanceMarkdown(message.content);
  }

  /**
   * Handle action button clicks
   */
  onActionClick(action: QuickAction, ticker?: string): void {
    console.log('[ChatUI] Action clicked:', action.action, action.params);
    
    switch (action.action) {
      case 'show_dcf_model':
        // Navigate to DCF analysis or emit message
        if (ticker) {
          this.router.navigate(['/dcf-analysis'], { queryParams: { ticker } });
        } else {
          this.messageSent.emit('Show me the DCF model details');
        }
        break;
        
      case 'open_scenarios':
        this.messageSent.emit('Show me different scenarios for growth');
        break;
        
      case 'compare_companies':
        this.messageSent.emit('Compare this company to its competitors');
        break;
        
      case 'analyze_growth':
        this.messageSent.emit('Analyze the growth assumptions in detail');
        break;
        
      case 'assess_risks':
        this.messageSent.emit('What are the key risks to this analysis?');
        break;
        
      case 'save_thesis':
        this.messageSent.emit('Save my investment thesis from this conversation');
        break;
        
      default:
        console.warn('[ChatUI] Unknown action:', action.action);
    }
  }

  /**
   * Check if message has structured content
   */
  hasStructuredContent(message: ChatMessage): boolean {
    if (message.sender === 'user') return false;
    const formatted = this.getFormattedMessage(message);
    return formatted.hasStructuredContent;
  }
}

