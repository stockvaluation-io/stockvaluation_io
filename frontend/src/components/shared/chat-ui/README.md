# Chat UI Component

A lightweight, customizable chat interface component for Angular applications, built with PrimeNG and Tailwind CSS.

## Features

✅ **Lightweight** - No heavy dependencies, uses existing PrimeNG components  
✅ **Fully Customizable** - Complete control over styling and behavior  
✅ **SSR Compatible** - Works with Angular Universal/SSR  
✅ **Responsive** - Mobile-friendly design  
✅ **Type-Safe** - Full TypeScript support  
✅ **Accessible** - Keyboard navigation and ARIA support  
✅ **Dark Mode** - Automatic dark mode support

## Components

- `ChatUiComponent` - Main chat interface component
- `ChatService` - State management and helper methods
- `ChatMessage` - TypeScript interfaces for type safety

## Installation

The component is already set up in your project at:
```
src/components/shared/chat-ui/
```

## Quick Start

### 1. Import the Component

```typescript
import { Component } from '@angular/core';
import { ChatUiComponent, ChatMessage, ChatService } from '@components/shared/chat-ui';

@Component({
  selector: 'app-my-chat',
  standalone: true,
  imports: [ChatUiComponent],
  template: `
    <div style="height: 600px;">
      <app-chat-ui
        [messages]="messages"
        [config]="chatConfig"
        [isLoading]="isLoading"
        (messageSent)="onMessageSent($event)"
      />
    </div>
  `,
})
export class MyChatComponent {
  messages: ChatMessage[] = [];
  isLoading = false;
  
  chatConfig = {
    placeholder: 'Ask me anything...',
    maxLength: 2000,
    autoScroll: true,
    showTimestamp: true,
  };

  constructor(private chatService: ChatService) {}

  onMessageSent(message: string) {
    // Add user message
    this.chatService.addUserMessage(message);
    
    // Simulate AI response
    this.isLoading = true;
    setTimeout(() => {
      this.chatService.addAiMessage('This is a sample response!');
      this.isLoading = false;
    }, 1000);
  }
}
```

### 2. Using the Chat Service

```typescript
import { Component, OnInit } from '@angular/core';
import { ChatService } from '@components/shared/chat-ui';

@Component({
  selector: 'app-chat-page',
  template: `
    <app-chat-ui
      [messages]="chatService.getMessages()"
      [isLoading]="chatService.isLoading()"
      (messageSent)="handleMessage($event)"
    />
  `,
})
export class ChatPageComponent implements OnInit {
  constructor(public chatService: ChatService) {}

  ngOnInit() {
    // Start a new session
    this.chatService.startNewSession({
      ticker: 'AAPL',
      context: 'valuation-chat'
    });

    // Add welcome message
    this.chatService.addAiMessage(
      'Hello! I can help you understand this company\'s valuation. What would you like to know?'
    );
  }

  async handleMessage(message: string) {
    // Add user message
    this.chatService.addUserMessage(message);
    
    // Show typing indicator
    this.chatService.addTypingIndicator();
    this.chatService.isLoading.set(true);

    try {
      // Call your API
      const response = await this.yourApiService.chat(message);
      
      // Remove typing indicator
      this.chatService.removeTypingIndicator();
      
      // Add AI response
      this.chatService.addAiMessage(response.content, {
        tokens: response.tokens,
        model: response.model,
      });
    } catch (error) {
      this.chatService.removeTypingIndicator();
      this.chatService.addAiMessage('Sorry, an error occurred.', {
        error: true,
        errorMessage: error.message,
      });
    } finally {
      this.chatService.isLoading.set(false);
    }
  }
}
```

## API Reference

### ChatUiComponent Inputs

| Input | Type | Default | Description |
|-------|------|---------|-------------|
| `messages` | `ChatMessage[]` | `[]` | Array of chat messages to display |
| `config` | `ChatConfig` | See below | Configuration options |
| `isLoading` | `boolean` | `false` | Shows typing indicator when true |
| `disabled` | `boolean` | `false` | Disables input when true |

### ChatUiComponent Outputs

| Output | Type | Description |
|--------|------|-------------|
| `messageSent` | `string` | Emits when user sends a message |
| `messageDeleted` | `string` | Emits message ID when deleted |

### ChatConfig Options

```typescript
interface ChatConfig {
  placeholder?: string;          // Default: 'Type your message...'
  maxLength?: number;            // Default: 4000
  autoScroll?: boolean;          // Default: true
  showTimestamp?: boolean;       // Default: true
  allowMultiline?: boolean;      // Default: true
  submitOnEnter?: boolean;       // Default: true (Shift+Enter for new line)
  userAvatar?: string;           // Custom user avatar URL
  aiAvatar?: string;             // Custom AI avatar URL
  enableTypingIndicator?: boolean; // Default: true
}
```

### ChatMessage Interface

```typescript
interface ChatMessage {
  id: string;
  content: string;
  sender: 'user' | 'ai';
  timestamp: Date;
  isTyping?: boolean;
  metadata?: {
    tokens?: number;
    model?: string;
    error?: boolean;
    errorMessage?: string;
    [key: string]: any;
  };
}
```

### ChatService Methods

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `addUserMessage` | `content: string` | `ChatMessage` | Add user message |
| `addAiMessage` | `content: string, metadata?: any` | `ChatMessage` | Add AI message |
| `addTypingIndicator` | - | `ChatMessage` | Show typing indicator |
| `removeTypingIndicator` | - | `void` | Hide typing indicator |
| `clearMessages` | - | `void` | Clear all messages |
| `startNewSession` | `metadata?: any` | `ChatSession` | Start new session |
| `saveSession` | `session: ChatSession` | `void` | Save to localStorage |
| `loadSession` | `sessionId: string` | `ChatSession \| null` | Load from localStorage |
| `exportSession` | `session: ChatSession` | `void` | Export as JSON |

## Styling Customization

The component uses SCSS and can be customized by overriding CSS variables or classes:

```scss
// In your component's SCSS file
::ng-deep .chat-ui-container {
  // Change bubble colors
  .user-message .message-bubble {
    background: linear-gradient(135deg, #your-color-1, #your-color-2);
  }

  // Change avatar colors
  .message-avatar {
    background: linear-gradient(135deg, #your-color-3, #your-color-4);
  }
}
```

## Advanced Usage

### Integration with Backend API

```typescript
import { HttpClient } from '@angular/common/http';
import { ChatService } from '@components/shared/chat-ui';

export class ChatApiService {
  constructor(
    private http: HttpClient,
    private chatService: ChatService
  ) {}

  async sendMessage(message: string, ticker: string) {
    this.chatService.addUserMessage(message);
    this.chatService.addTypingIndicator();
    this.chatService.isLoading.set(true);

    try {
      const response = await this.http.post<any>('/api/chat', {
        message,
        ticker,
        history: this.chatService.getMessages()
          .filter(m => !m.isTyping)
          .slice(-10) // Last 10 messages for context
      }).toPromise();

      this.chatService.removeTypingIndicator();
      this.chatService.addAiMessage(response.content, {
        tokens: response.usage?.tokens,
        model: response.model,
      });

      // Save session
      const session = this.chatService.getCurrentSession();
      if (session) {
        this.chatService.saveSession(session);
      }
    } catch (error) {
      this.chatService.removeTypingIndicator();
      this.chatService.addAiMessage('Sorry, something went wrong.', {
        error: true,
        errorMessage: error.message,
      });
    } finally {
      this.chatService.isLoading.set(false);
    }
  }
}
```

### Session Management

```typescript
// List all saved sessions
const sessions = this.chatService.getSavedSessions();

// Load a specific session
this.chatService.loadSession('session-id-here');

// Delete a session
this.chatService.deleteSavedSession('session-id-here');

// Export current session
const currentSession = this.chatService.getCurrentSession();
if (currentSession) {
  this.chatService.exportSession(currentSession);
}
```

## Browser Support

- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)
- Mobile browsers (iOS Safari, Chrome Mobile)

## Performance

- Virtual scrolling for large message lists (coming soon)
- Lazy loading of message history
- Optimized change detection using signals
- Minimal re-renders with OnPush strategy

## Accessibility

- Keyboard navigation (Enter to send, Shift+Enter for new line)
- ARIA labels for screen readers
- Focus management
- High contrast mode support

## License

MIT

## Support

For issues or questions, please check the documentation or create an issue in the project repository.

