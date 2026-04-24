import { ChatService } from './chat.service';
import { ChatSidebarComponent } from './chat-sidebar.component';
import { NotebookChatService } from './notebook-chat.service';

describe('ChatSidebarComponent', () => {
  let chatService: ChatService;
  let socketService: NotebookChatService;
  let component: ChatSidebarComponent;

  beforeEach(() => {
    chatService = new ChatService();
    const markdownRenderer = jasmine.createSpyObj('MarkdownRendererService', ['render']);
    markdownRenderer.render.and.callFake((content: string) => content);
    socketService = new NotebookChatService(chatService, markdownRenderer);
    spyOn(socketService, 'sendMessage').and.returnValue(Promise.resolve());

    component = new ChatSidebarComponent(chatService, socketService);
    component.ticker = 'GOOG';
    component.autoConnect = false;
    component.ngOnInit();

    (socketService as any).currentSessionId = 'session-1';
    socketService.isConnected.set(true);
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('renders a tool plan in the active notebook cell', () => {
    component.handleMessage('Say OK only.');

    expect(component.cells().length).toBe(1);
    expect(component.streamingCellId()).toContain('cell_');

    (socketService as any).handleSSEEvent({
      type: 'tool_plan',
      cell_id: 'server-cell-1',
      tool_id: 'tool-1',
      tool_name: 'valuation_loader',
      tool_input: { include: 'current_valuation' },
      plan: 'I can run `valuation_loader` with current valuation context.',
      awaiting_response: true,
    });

    const cell = component.cells()[0];
    expect(cell.id).toBe('server-cell-1');
    expect(cell.user_input).toBe('Say OK only.');
    expect(cell.ai_output?.message).toBe('I can run `valuation_loader` with current valuation context.');
    expect(cell.is_streaming).toBeFalse();
    expect(component.streamingCellId()).toBeNull();
  });

  it('renders cell_complete responses that arrive without stream chunks', () => {
    component.handleMessage('Answer in one sentence.');

    (socketService as any).handleSSEEvent({
      type: 'cell_complete',
      cell_id: 'server-cell-2',
      cell: {
        id: 'server-cell-2',
        session_id: 'session-1',
        sequence_number: 1,
        cell_type: 'reasoning',
        author_type: 'user',
        user_input: 'Answer in one sentence.',
        ai_output: {
          content: 'Growth is the assumption that matters most.',
        },
        created_at: '2026-04-24T00:00:00Z',
      },
    });

    const cell = component.cells()[0];
    expect(cell.id).toBe('server-cell-2');
    expect(cell.user_input).toBe('Answer in one sentence.');
    expect(cell.ai_output?.content).toBe('Growth is the assumption that matters most.');
    expect(cell.is_streaming).toBeFalse();
    expect(component.streamingCellId()).toBeNull();
  });
});
