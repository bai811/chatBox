const API_BASE = 'http://localhost:8080/api';

/** 创建新会话 */
export async function createSession(): Promise<string> {
  const res = await fetch(`${API_BASE}/rag/session`, { method: 'POST' });
  const data = await res.json();
  return data.sessionId;
}

/** 获取所有会话ID */
export async function getAllSessions(): Promise<string[]> {
  const res = await fetch(`${API_BASE}/rag/sessions`);
  return res.json();
}

/** 获取会话历史消息 */
export interface ChatMessageDTO {
  role: string;
  content: string;
  timestamp: number;
}

export async function getSessionMessages(sessionId: string): Promise<ChatMessageDTO[]> {
  const res = await fetch(`${API_BASE}/rag/session/${sessionId}/messages`);
  return res.json();
}

/** 删除会话 */
export async function deleteSession(sessionId: string): Promise<void> {
  await fetch(`${API_BASE}/rag/session/${sessionId}`, { method: 'DELETE' });
}

/** 流式问答 - 返回ReadableStream */
export async function streamChat(
  query: string,
  sessionId: string,
  onToken: (token: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): Promise<void> {
  try {
    const res = await fetch(`${API_BASE}/rag/qa`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, sessionId }),
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }

    const reader = res.body?.getReader();
    if (!reader) {
      throw new Error('Response body is not readable');
    }

    const decoder = new TextDecoder();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      // SSE格式解析：data:xxx\n\n
      const lines = chunk.split('\n');
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5);
          if (data.trim() === '[DONE]') {
            onDone();
            return;
          }
          onToken(data);
        } else if (line.trim() && !line.startsWith(':')) {
          // 非SSE格式，直接作为token
          onToken(line);
        }
      }
    }
    onDone();
  } catch (err) {
    onError(err as Error);
  }
}
