import { ChatService } from './chat.service';
import { NotebookChatService } from './notebook-chat.service';


describe('NotebookChatService', () => {
  let chatService: ChatService;
  let service: NotebookChatService;
  let markdownRenderer: jasmine.SpyObj<any>;

  beforeEach(() => {
    chatService = new ChatService();
    markdownRenderer = jasmine.createSpyObj('MarkdownRendererService', ['render']);
    markdownRenderer.render.and.callFake((content: string) => content);
    service = new NotebookChatService(chatService, markdownRenderer);
  });

  it('emits tool plans and adds an approval message to chat', () => {
    let observedPlan: any;
    let observedAssistantEvent: any;
    service.toolPlan$.subscribe(plan => {
      observedPlan = plan;
    });
    service.assistantEvent$.subscribe(event => {
      observedAssistantEvent = event;
    });

    chatService.addTypingIndicator();
    chatService.isLoading.set(true);
    (service as any).currentSessionId = 'session-1';

    (service as any).handleSSEEvent({
      type: 'tool_plan',
      cell_id: 'cell-1',
      tool_id: 'tool-1',
      tool_name: 'dcf_recalculator',
      tool_input: { wacc: 10 },
      plan: 'I can run `dcf_recalculator`.',
      awaiting_response: true,
    });

    const messages = chatService.getMessages();
    const firstMessage = messages[0]!;
    const metadata = firstMessage.metadata as { tool_approval: boolean; tool_name: string };
    expect(messages.length).toBe(1);
    expect(firstMessage.sender).toBe('ai');
    expect(firstMessage.content).toBe('I can run `dcf_recalculator`.');
    expect(metadata.tool_approval).toBeTrue();
    expect(metadata.tool_name).toBe('dcf_recalculator');
    expect(chatService.isLoading()).toBeFalse();
    expect(observedPlan.tool_name).toBe('dcf_recalculator');
    expect(observedPlan.tool_input.wacc).toBe(10);
    expect(observedPlan.session_id).toBe('session-1');
    expect(observedAssistantEvent.type).toBe('tool_plan');
    expect(observedAssistantEvent.cellId).toBe('cell-1');
    expect(observedAssistantEvent.toolName).toBe('dcf_recalculator');
  });

  it('emits tool results from SSE payloads', () => {
    let observedResult: any;
    service.toolResult$.subscribe(result => {
      observedResult = result;
    });

    (service as any).handleSSEEvent({
      type: 'tool_result',
      tool_name: 'python_interpreter',
      status: 'success',
      data: {
        result: [
          { label: '-5%', upside: 8.0 },
          { label: 'base', upside: 13.0 },
          { label: '+5%', upside: 18.0 },
        ],
      },
    });

    expect(observedResult.tool_name).toBe('python_interpreter');
    expect(observedResult.success).toBeTrue();
    expect(observedResult.result.result.length).toBe(3);
    expect(chatService.getMessages().length).toBe(0);
  });

  it('syncs final cell content into chat on cell_complete even without stream chunks', () => {
    let observedAssistantEvent: any;
    service.assistantEvent$.subscribe(event => {
      observedAssistantEvent = event;
    });

    chatService.addTypingIndicator();
    chatService.isLoading.set(true);

    (service as any).handleSSEEvent({
      type: 'cell_complete',
      cell: {
        id: 'cell-42',
        ai_output: {
          content: 'Final tool-backed answer.',
          rewritten_query: 'Use current valuation context only.',
          tool_results: [
            {
              tool_name: 'valuation_loader',
              status: 'success',
            },
          ],
        },
      },
    });

    const messages = chatService.getMessages();
    expect(messages.length).toBe(1);
    expect(messages[0].sender).toBe('ai');
    expect(messages[0].content).toBe('Final tool-backed answer.');
    expect(messages[0].metadata?.['cell_id']).toBe('cell-42');
    expect(messages[0].metadata?.['rewritten_query']).toBe('Use current valuation context only.');
    expect(messages[0].metadata?.['tool_results'].length).toBe(1);
    expect(chatService.isLoading()).toBeFalse();
    expect(observedAssistantEvent.type).toBe('assistant_complete');
    expect(observedAssistantEvent.cellId).toBe('cell-42');
    expect(observedAssistantEvent.text).toBe('Final tool-backed answer.');
  });

  it('emits assistant start, delta, done, and error events from provider-neutral SSE payloads', () => {
    const observedEvents: any[] = [];
    service.assistantEvent$.subscribe(event => {
      observedEvents.push(event);
    });

    (service as any).handleSSEEvent({
      type: 'cell_start',
      cell_id: 'cell-stream-1',
      sequence_number: 7,
    });
    (service as any).handleSSEEvent({
      type: 'stream',
      cell_id: 'cell-stream-1',
      chunk: 'Hello',
    });
    (service as any).handleSSEEvent({
      type: 'done',
      status: 'complete',
    });
    (service as any).handleSSEEvent({
      type: 'error',
      cell_id: 'cell-error-1',
      error: 'Provider failed',
    });

    expect(observedEvents.map(event => event.type)).toEqual([
      'assistant_start',
      'assistant_delta',
      'done',
      'error',
    ]);
    expect(observedEvents[0].cellId).toBe('cell-stream-1');
    expect(observedEvents[0].sequenceNumber).toBe(7);
    expect(observedEvents[1].text).toBe('Hello');
    expect(observedEvents[2].status).toBe('complete');
    expect(observedEvents[3].message).toBe('Provider failed');
  });
});
