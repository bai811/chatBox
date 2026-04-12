import React, { useState, useRef, useEffect, useCallback } from 'react';

interface MessageInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
  /** 可由外部注入的文本（用于点击建议卡片） */
  injectedText?: string;
  onInjectedTextConsumed?: () => void;
}

const MessageInput: React.FC<MessageInputProps> = ({
  onSend,
  isLoading,
  injectedText,
  onInjectedTextConsumed,
}) => {
  const [text, setText] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 处理外部注入文本
  useEffect(() => {
    if (injectedText) {
      setText(injectedText);
      onInjectedTextConsumed?.();
      textareaRef.current?.focus();
    }
  }, [injectedText, onInjectedTextConsumed]);

  // 自动调整高度
  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${Math.min(textarea.scrollHeight, 200)}px`;
    }
  }, [text]);

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed || isLoading) return;
    onSend(trimmed);
    setText('');
  }, [text, isLoading, onSend]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend]
  );

  return (
    <div className="input-area">
      <div className="input-container">
        <div className="input-wrapper">
          <textarea
            ref={textareaRef}
            className="message-textarea"
            placeholder="输入你的问题..."
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            disabled={isLoading}
          />
          <button
            className="send-btn"
            onClick={handleSend}
            disabled={!text.trim() || isLoading}
            title="发送消息"
          >
            {isLoading ? '⏳' : '↑'}
          </button>
        </div>
        <div className="input-hint">
          按 Enter 发送，Shift + Enter 换行 · 基于 Qwen-plus + RAG 混合检索
        </div>
      </div>
    </div>
  );
};

export default MessageInput;
