import React, { useState, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownRendererProps {
  content: string;
}

/** 代码块头部组件 */
function CodeBlockHeader({ language, code }: { language: string; code: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback
      const textarea = document.createElement('textarea');
      textarea.value = code;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }, [code]);

  return (
    <div className="code-block-header">
      <span className="code-block-lang">{language || 'text'}</span>
      <button className={`code-copy-btn ${copied ? 'copied' : ''}`} onClick={handleCopy}>
        {copied ? '✓ 已复制' : '⎘ 复制'}
      </button>
    </div>
  );
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content }) => {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        // 自定义代码块渲染
        code({ className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '');
          const codeString = String(children).replace(/\n$/, '');
          const isInline = !match && !codeString.includes('\n');

          if (isInline) {
            return (
              <code className={className} {...props}>
                {children}
              </code>
            );
          }

          return (
            <>
              <CodeBlockHeader language={match?.[1] || ''} code={codeString} />
              <code className={className} {...props}>
                {children}
              </code>
            </>
          );
        },
        // 自定义链接在新窗口打开
        a({ href, children, ...props }) {
          return (
            <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
              {children}
            </a>
          );
        },
        // 表格样式
        table({ children, ...props }) {
          return (
            <div style={{ overflowX: 'auto', margin: '12px 0' }}>
              <table
                style={{
                  borderCollapse: 'collapse',
                  width: '100%',
                  fontSize: '14px',
                }}
                {...props}
              >
                {children}
              </table>
            </div>
          );
        },
        th({ children, ...props }) {
          return (
            <th
              style={{
                borderBottom: '1px solid rgba(255,255,255,0.1)',
                padding: '8px 12px',
                textAlign: 'left',
                fontWeight: 600,
                color: 'var(--text-secondary)',
              }}
              {...props}
            >
              {children}
            </th>
          );
        },
        td({ children, ...props }) {
          return (
            <td
              style={{
                borderBottom: '1px solid rgba(255,255,255,0.06)',
                padding: '8px 12px',
              }}
              {...props}
            >
              {children}
            </td>
          );
        },
      }}
    >
      {content}
    </ReactMarkdown>
  );
};

export default MarkdownRenderer;
