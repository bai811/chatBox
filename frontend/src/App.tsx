import { useState, useCallback, useRef, useEffect } from 'react';
import Sidebar from './components/Sidebar';
import ChatWindow from './components/ChatWindow';
import MessageInput from './components/MessageInput';
import DocumentUpload from './components/DocumentUpload';
import { createSession, deleteSession, streamChat, getAllSessions, getSessionMessages } from './api/chat';
import type { Message, Session } from './types';
import './App.css';

function App() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [injectedText, setInjectedText] = useState<string | undefined>(undefined);
  const msgIdCounter = useRef(0);

  // 初始化加载后台现存的回话信息
  useEffect(() => {
    async function loadHistory() {
      try {
        const sids = await getAllSessions();
        const loadedSessions: Session[] = await Promise.all(
          sids.map(async (sid) => {
            const msgs = await getSessionMessages(sid);
            return {
              id: sid,
              // 使用第一条用户的输入作为标题，如果没有则默认
              title: msgs.length > 1 ? msgs[1].content.slice(0, 20) : '历史对话',
              messages: msgs
                .filter((m) => m.role !== 'system') // 过滤掉系统提示词消息
                .map((m, i) => ({
                  id: `history-${sid}-${i}`,
                  role: m.role as 'user' | 'assistant',
                  content: m.content
                }))
            };
          })
        );
        // 按最后一条消息的顺序可以再这里排序一下，这里简单拼接
        setSessions(loadedSessions);
        if (loadedSessions.length > 0) {
          setActiveSessionId(loadedSessions[0].id);
        }
      } catch (err) {
        console.error('获取历史记录失败:', err);
      }
    }
    loadHistory();
  }, []);

  const activeSession = sessions.find((s) => s.id === activeSessionId) || null;

  /** 生成唯一消息ID */
  const nextMsgId = () => {
    msgIdCounter.current += 1;
    return `msg-${Date.now()}-${msgIdCounter.current}`;
  };

  /** 创建新会话 */
  const handleNewChat = useCallback(async () => {
    try {
      const sessionId = await createSession();
      const newSession: Session = {
        id: sessionId,
        title: '新对话',
        messages: [],
      };
      setSessions((prev) => [newSession, ...prev]);
      setActiveSessionId(sessionId);
    } catch (err) {
      // 如果后端未启动，使用本地ID
      const localId = `local-${Date.now()}`;
      const newSession: Session = {
        id: localId,
        title: '新对话',
        messages: [],
      };
      setSessions((prev) => [newSession, ...prev]);
      setActiveSessionId(localId);
    }
  }, []);

  /** 选择会话 */
  const handleSelectSession = useCallback((id: string) => {
    setActiveSessionId(id);
  }, []);

  /** 删除会话 */
  const handleDeleteSession = useCallback(
    async (id: string) => {
      try {
        await deleteSession(id);
      } catch {
        // ignore if backend is down
      }
      setSessions((prev) => prev.filter((s) => s.id !== id));
      if (activeSessionId === id) {
        setActiveSessionId(null);
      }
    },
    [activeSessionId]
  );

  /** 更新session中的消息 */
  const updateSessionMessages = useCallback(
    (sessionId: string, updater: (messages: Message[]) => Message[]) => {
      setSessions((prev) =>
        prev.map((s) => (s.id === sessionId ? { ...s, messages: updater(s.messages) } : s))
      );
    },
    []
  );

  /** 发送消息 */
  const handleSend = useCallback(
    async (text: string) => {
      let currentSessionId = activeSessionId;

      // 如果没有活跃会话，自动创建新会话
      if (!currentSessionId) {
        try {
          currentSessionId = await createSession();
        } catch {
          currentSessionId = `local-${Date.now()}`;
        }
        const newSession: Session = {
          id: currentSessionId,
          title: text.slice(0, 20) + (text.length > 20 ? '...' : ''),
          messages: [],
        };
        setSessions((prev) => [newSession, ...prev]);
        setActiveSessionId(currentSessionId);
      }

      const sid = currentSessionId;

      // 添加用户消息
      const userMsg: Message = { id: nextMsgId(), role: 'user', content: text };
      updateSessionMessages(sid, (msgs) => [...msgs, userMsg]);

      // 更新标题为第一条消息
      setSessions((prev) =>
        prev.map((s) => {
          if (s.id === sid && s.title === '新对话') {
            return { ...s, title: text.slice(0, 20) + (text.length > 20 ? '...' : '') };
          }
          return s;
        })
      );

      // 创建AI回复占位
      const aiMsgId = nextMsgId();
      const aiMsg: Message = { id: aiMsgId, role: 'assistant', content: '' };
      updateSessionMessages(sid, (msgs) => [...msgs, aiMsg]);
      setIsLoading(true);

      // 流式请求
      await streamChat(
        text,
        sid,
        (token) => {
          updateSessionMessages(sid, (msgs) =>
            msgs.map((m) => (m.id === aiMsgId ? { ...m, content: m.content + token } : m))
          );
        },
        () => {
          setIsLoading(false);
        },
        (err) => {
          console.error('Stream error:', err);
          updateSessionMessages(sid, (msgs) =>
            msgs.map((m) =>
              m.id === aiMsgId
                ? { ...m, content: m.content || `⚠️ 请求失败：${err.message}。请检查后端服务是否已启动。` }
                : m
            )
          );
          setIsLoading(false);
        }
      );
    },
    [activeSessionId, updateSessionMessages]
  );

  /** 点击建议卡片 */
  const handleSuggestionClick = useCallback((text: string) => {
    setInjectedText(text);
  }, []);

  return (
    <div className="app-layout">
      <Sidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
        onNewChat={handleNewChat}
        onSelectSession={handleSelectSession}
        onDeleteSession={handleDeleteSession}
        onOpenUpload={() => setUploadOpen(true)}
      />

      <div className="main-content">
        <div className="chat-header">
          <div className="chat-header-title">
            {!sidebarCollapsed ? null : (
              <button
                className="toggle-sidebar-btn"
                style={{ position: 'static', marginRight: '8px' }}
                onClick={() => setSidebarCollapsed(false)}
              >
                ☰
              </button>
            )}
            <span>团队智能知识库</span>
            <span className="model-badge">Qwen-plus</span>
          </div>
        </div>

        <ChatWindow
          messages={activeSession?.messages || []}
          isLoading={isLoading}
          onSuggestionClick={handleSuggestionClick}
        />

        <MessageInput
          onSend={handleSend}
          isLoading={isLoading}
          injectedText={injectedText}
          onInjectedTextConsumed={() => setInjectedText(undefined)}
        />
      </div>

      <DocumentUpload isOpen={uploadOpen} onClose={() => setUploadOpen(false)} />
    </div>
  );
}

export default App;
