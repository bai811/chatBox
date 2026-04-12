import React from 'react';

interface Session {
  id: string;
  title: string;
}

interface SidebarProps {
  sessions: Session[];
  activeSessionId: string | null;
  collapsed: boolean;
  onToggle: () => void;
  onNewChat: () => void;
  onSelectSession: (id: string) => void;
  onDeleteSession: (id: string) => void;
  onOpenUpload: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({
  sessions,
  activeSessionId,
  collapsed,
  onToggle,
  onNewChat,
  onSelectSession,
  onDeleteSession,
  onOpenUpload,
}) => {
  return (
    <>
      {/* Toggle button (shown when sidebar collapsed) */}
      {collapsed && (
        <button className="toggle-sidebar-btn" onClick={onToggle} title="展开侧边栏">
          ☰
        </button>
      )}

      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        {/* Header */}
        <div className="sidebar-header">
          <div className="sidebar-brand">
            <div className="sidebar-brand-icon">🧠</div>
            <span className="sidebar-brand-name">智能知识库</span>
          </div>
          <button
            className="toggle-sidebar-btn"
            style={{ position: 'static', background: 'none', border: 'none' }}
            onClick={onToggle}
            title="收起侧边栏"
          >
            ✕
          </button>
        </div>

        {/* New Chat Button */}
        <button className="new-chat-btn" onClick={onNewChat}>
          <span>＋</span>
          <span>新建对话</span>
        </button>

        {/* Session List */}
        <div className="sidebar-section-title">对话历史</div>
        <div className="session-list">
          {sessions.length === 0 && (
            <div style={{ padding: '16px', fontSize: '13px', color: 'var(--text-tertiary)', textAlign: 'center' }}>
              暂无对话记录
            </div>
          )}
          {sessions.map((session) => (
            <div
              key={session.id}
              className={`session-item ${session.id === activeSessionId ? 'active' : ''}`}
              onClick={() => onSelectSession(session.id)}
            >
              <span className="session-icon">💬</span>
              <span className="session-title">{session.title}</span>
              <button
                className="session-delete"
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteSession(session.id);
                }}
                title="删除对话"
              >
                🗑
              </button>
            </div>
          ))}
        </div>

        {/* Footer - Upload Button */}
        <div className="sidebar-footer">
          <button className="upload-btn" onClick={onOpenUpload}>
            <span>📄</span>
            <span>上传知识文档</span>
          </button>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
