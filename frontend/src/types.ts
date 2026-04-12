/** 聊天消息类型 */
export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

/** 会话类型 */
export interface Session {
  id: string;
  title: string;
  messages: Message[];
}
