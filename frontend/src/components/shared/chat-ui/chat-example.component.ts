import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatUiComponent } from './chat-ui.component';
import { ChatService } from './chat.service';
import { ChatMessage, ChatConfig } from './chat-message.interface';

/**
 * Chat UI Example Component
 * Demonstrates how to use the ChatUiComponent with the ChatService
 */
@Component({
  selector: 'app-chat-example',
  standalone: true,
  imports: [CommonModule, ChatUiComponent],
  template: `
    <div class="chat-example-container">
      <div class="chat-header">
        <h2>AI Chat Assistant</h2>
        <div class="chat-actions">
          <button (click)="clearChat()" class="action-btn">
            <i class="pi pi-trash"></i> Clear
          </button>
          <button (click)="exportChat()" class="action-btn">
            <i class="pi pi-download"></i> Export
          </button>
        </div>
      </div>

      <div class="chat-wrapper">
        <app-chat-ui
          [messages]="messages()"
          [config]="chatConfig"
          [isLoading]="isLoading()"
          (messageSent)="handleMessageSent($event)"
        />
      </div>

      <div class="chat-info">
        <p>💡 <strong>Tip:</strong> Press Enter to send, Shift+Enter for new line</p>
      </div>
    </div>
  `,
  styles: [`
    .chat-example-container {
      display: flex;
      flex-direction: column;
      max-width: 800px;
      margin: 2rem auto;
      height: calc(100vh - 4rem);
      gap: 1rem;
    }

    .chat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.5rem;
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    }

    .chat-header h2 {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: #1f2937;
    }

    .chat-actions {
      display: flex;
      gap: 0.75rem;
    }

    .action-btn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      background: white;
      color: #6b7280;
      cursor: pointer;
      font-size: 0.875rem;
      transition: all 0.2s;
    }

    .action-btn:hover {
      background: #f9fafb;
      border-color: #d1d5db;
    }

    .chat-wrapper {
      flex: 1;
      min-height: 0;
    }

    .chat-info {
      padding: 1rem 1.5rem;
      background: #eff6ff;
      border-radius: 12px;
      color: #1e40af;
      font-size: 0.875rem;
    }

    .chat-info p {
      margin: 0;
    }

    @media (prefers-color-scheme: dark) {
      .chat-header,
      .action-btn {
        background: #1a1a1a;
        color: #e5e7eb;
      }

      .chat-header h2 {
        color: #e5e7eb;
      }

      .action-btn {
        border-color: #2d2d2d;
      }

      .action-btn:hover {
        background: #2d2d2d;
      }

      .chat-info {
        background: #1e3a5f;
        color: #93c5fd;
      }
    }
  `],
})
export class ChatExampleComponent implements OnInit {
  messages = signal<ChatMessage[]>([]);
  isLoading = signal(false);

  chatConfig: ChatConfig = {
    placeholder: 'Ask me anything about this company...',
    maxLength: 2000,
    autoScroll: true,
    showTimestamp: true,
    allowMultiline: true,
    submitOnEnter: true,
    enableTypingIndicator: true,
  };

  constructor(public chatService: ChatService) {}

  ngOnInit(): void {
    // Start a new session
    this.chatService.startNewSession({
      context: 'example-chat',
      ticker: 'DEMO',
    });

    // Add welcome message
    const welcomeMessage = this.chatService.addAiMessage(
      'Hello! I\'m your AI assistant. I can help you understand company valuations, financial metrics, and more. What would you like to know?'
    );

    // Subscribe to messages
    this.chatService.messages$.subscribe((messages) => {
      this.messages.set(messages);
    });

    // Add some example messages after a delay (for demo purposes)
    setTimeout(() => {
      this.addExampleMessages();
    }, 1000);
  }

  handleMessageSent(message: string): void {
    // Add user message
    this.chatService.addUserMessage(message);

    // Simulate AI processing
    this.simulateAiResponse(message);
  }

  private simulateAiResponse(userMessage: string): void {
    // Show loading state
    this.isLoading.set(true);
    this.chatService.addTypingIndicator();

    // Simulate API call delay
    setTimeout(() => {
      this.chatService.removeTypingIndicator();

      // Generate response based on user input
      const response = this.generateMockResponse(userMessage);

      this.chatService.addAiMessage(response, {
        tokens: Math.floor(Math.random() * 100) + 50,
        model: 'gpt-4',
      });

      this.isLoading.set(false);

      // Save session
      const session = this.chatService.getCurrentSession();
      if (session) {
        this.chatService.saveSession(session);
      }
    }, 1500 + Math.random() * 1000);
  }

  private generateMockResponse(userMessage: string): string {
    const lowerMessage = userMessage.toLowerCase();

    if (lowerMessage.includes('valuation') || lowerMessage.includes('value')) {
      return 'Stock valuation involves determining the intrinsic value of a company using various methods like DCF (Discounted Cash Flow), comparable company analysis, and precedent transactions. The DCF method is particularly robust as it considers future cash flows and discount rates.';
    }

    if (lowerMessage.includes('dcf') || lowerMessage.includes('cash flow')) {
      return 'DCF (Discounted Cash Flow) analysis projects a company\'s future free cash flows and discounts them back to present value using the weighted average cost of capital (WACC). This gives us the enterprise value, from which we can derive the equity value per share.';
    }

    if (lowerMessage.includes('wacc') || lowerMessage.includes('discount')) {
      return 'WACC (Weighted Average Cost of Capital) represents the average rate a company pays to finance its assets. It\'s calculated as the weighted average of the cost of equity and after-tax cost of debt, based on the company\'s capital structure.';
    }

    if (lowerMessage.includes('margin') || lowerMessage.includes('profit')) {
      return 'Operating margin is a key profitability metric that shows what percentage of revenue remains after deducting operating expenses. Higher margins generally indicate better operational efficiency and pricing power.';
    }

    if (lowerMessage.includes('growth') || lowerMessage.includes('revenue')) {
      return 'Revenue growth is a critical driver of company valuation. We analyze both historical growth rates and future projections, considering factors like market size, competitive position, and industry trends to estimate sustainable growth rates.';
    }

    // Default response
    return `That's an interesting question about "${userMessage}". In valuation analysis, we consider multiple factors including financial performance, industry dynamics, competitive advantages, and macroeconomic conditions to arrive at a comprehensive assessment.`;
  }

  private addExampleMessages(): void {
    // Add a sample exchange for demonstration
    const exampleUserMsg = this.chatService.addUserMessage(
      'Can you explain how DCF valuation works?'
    );

    setTimeout(() => {
      const exampleAiMsg = this.chatService.addAiMessage(
        'DCF (Discounted Cash Flow) valuation works by projecting a company\'s future cash flows and discounting them back to their present value. Here are the key steps:\n\n1. **Project Future Cash Flows**: Estimate free cash flows for 5-10 years\n2. **Calculate Terminal Value**: Determine value beyond projection period\n3. **Discount to Present Value**: Apply WACC as discount rate\n4. **Calculate Enterprise Value**: Sum of PV of cash flows\n5. **Derive Equity Value**: Subtract net debt and divide by shares\n\nWould you like me to explain any of these steps in more detail?',
        {
          tokens: 124,
          model: 'gpt-4',
        }
      );
    }, 500);
  }

  clearChat(): void {
    if (confirm('Are you sure you want to clear the chat history?')) {
      this.chatService.clearMessages();

      // Restart with welcome message
      this.chatService.startNewSession({
        context: 'example-chat',
        ticker: 'DEMO',
      });

      this.chatService.addAiMessage(
        'Chat cleared! How can I help you today?'
      );
    }
  }

  exportChat(): void {
    const session = this.chatService.getCurrentSession();
    if (session) {
      this.chatService.exportSession(session);
    }
  }
}

