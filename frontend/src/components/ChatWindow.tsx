import React, { useRef, useEffect } from 'react';
import MarkdownRenderer from './MarkdownRenderer';
import type { Message } from '../types';


interface ChatWindowProps {
  messages: Message[];
  isLoading: boolean;
  onSuggestionClick: (text: string) => void;
}

const suggestions = [
  {
    title: '📋 项目规范查询',
    desc: '查询团队开发规范和最佳实践',
    query: '请介绍一下我们团队的开发规范',
  },
  {
    title: '🔧 技术问题排查',
    desc: '针对技术问题快速找到解决方案',
    query: '如何解决Redis缓存穿透问题？',
  },
  {
    title: '📚 新人入门指南',
    desc: '快速了解项目结构和上手流程',
    query: '作为新人，我应该如何快速上手项目？',
  },
  {
    title: '🗂️ 知识库检索',
    desc: '从团队文档中查找特定信息',
    query: '请帮我检索项目中的数据库设计文档',
  },
];

const ChatWindow: React.FC<ChatWindowProps> = ({ messages, isLoading, onSuggestionClick }) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  // 空状态 - 欢迎页
  if (messages.length === 0 && !isLoading) {
    return (
      <div className="chat-window">
        <div className="welcome-container">
          <div className="welcome-icon">🧠</div>
          <h1 className="welcome-title">团队智能知识库</h1>
          <p className="welcome-subtitle">
            基于 RAG 混合检索的智能问答助手，支持语义分块与 BM25+向量混合检索，
            为您提供精准的知识库查询服务。
          </p>
          <div className="welcome-suggestions">
            {suggestions.map((s, i) => (
              <div
                key={i}
                className="suggestion-card"
                onClick={() => onSuggestionClick(s.query)}
              >
                <div className="suggestion-card-title">{s.title}</div>
                <div className="suggestion-card-desc">{s.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="chat-window">
      <div className="chat-messages">
        {messages.map((msg) => (
          <div key={msg.id} className="message">
            <div className={`message-avatar ${msg.role === 'user' ? 'user' : 'ai'}`}>
              {msg.role === 'user' ? '👤' : '🧠'}
            </div>
            <div className="message-body">
              <div className="message-role">
                {msg.role === 'user' ? '你' : '知识库助手'}
              </div>
              <div className="message-content">
                {msg.role === 'assistant' ? (
                  <MarkdownRenderer content={msg.content} />
                ) : (
                  <p>{msg.content}</p>
                )}
              </div>
            </div>
          </div>
        ))}

        {isLoading && messages[messages.length - 1]?.role === 'user' && (
          <div className="message">
            <div className="message-avatar ai">🧠</div>
            <div className="message-body">
              <div className="message-role">知识库助手</div>
              <div className="typing-indicator">
                <div className="typing-dot" />
                <div className="typing-dot" />
                <div className="typing-dot" />
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>
    </div>
  );
};

export default ChatWindow;
