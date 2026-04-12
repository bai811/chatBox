# 前端架构与 UI 设计规范

本文档为参与知识库前端的开发者提供项目全景设计导向、规范约束和技术核心难点的通盘梳理。

## 1. 技术栈大纲

- **核心视图框架**: React 18
- **应用脚手架与构建**: Vite 
  - (由于完全规避了传统 Webpack 的长构建链路，使得我们在开发编译时做到了毫秒级启动，并在部署时具备强大的 ESM 切块表现)
- **TypeScript 强类型保证**: 定义完整的 Response、Document、Chat 流式等类型系统规范。

## 2. 界面视觉呈现学与动效学设计

在本项目的设计灵魂里，“现代科技感”与“深邃沉浸体验”是非常优先的核心指标。我们屏弃了泛滥而生硬的原生 UI 样式组，重新定制构建了整个组件网。

### 2.1 高级深色模式与配色系统 (CSS Token)

在 `index.css` 主题系统中全面定义：
- **Dark Mode 贯彻始终**: 使用色值如 `#0f172a` (深沉墨蓝) 为底层色，赋予应用轻量化的极客厚度与质感。
- **Glassmorphism 化 (琉璃材质)**: 使用 `backdrop-filter: blur` 对主侧边栏、对话背景面板执行毛玻璃效果，在交互拖拽时保证层级感和视觉透气度。

### 2.2 动效细化处理

- 每一个 Button（不管它是提问区还是侧边新文档管理），均附带微交互的 Hover 流光闪烁效果和 Active 点击形变效果 (`transform: scale(0.98)`)，避免页面变得沉闷呆滞。
- 文本出现带有打字机效果并由 AI 输出时，会提供一个细腻舒缓的渲染滚动轴跟踪算法处理，即实时保持聊天区域聚焦在文档的最底部。

## 3. 面向 RAG 系统的 Server-Sent Events (SSE) 直播流设计

SSE（流式输出）是项目在前端体验最核心的代码壁垒之一。由于大模型问答涉及数秒乃至十几秒的长尾思考期。如果我们用普通的 Fetch/XHR，会让用户误以为应用卡死。

### 3.1 消费 `text/event-stream` 设计模式

我们在底层抛弃了普通的 `axios.post`，转而使用 Fetch API 自带的 `body.getReader()` 进行事件流解码：

```typescript
// 伪代码架构：
const response = await fetch('/api/rag/qa', { method: 'POST', body });
const reader = response.body.getReader();
const decoder = new TextDecoder('utf-8');

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    // 解码获取一个个小切片的 token
    const token = decoder.decode(value, { stream: true });
    // 将 Token 流式压入状态树进而导致 React 组件微幅 Re-render
    setChatText((prev) => prev + token);
}
```

## 4. Markdown 富文本渲染核心

AI 生成的内容通常充斥着大量的 Markdown 标记规范（例如表格、代码块等），前端对此做了深度的结构解析与再组网。

依靠 `react-markdown` 生态链，附带 `remark-gfm` 插件成功解析了 GitHub 风味的增强功能表格以及通过 `react-syntax-highlighter` 呈现代码级别的智能调色解析，真正做到了从问答机器人蜕变成智能程序员的助手呈现级别。

## 5. 组件与目录构建约束

在日后的开发中必须恪守：
1. **单一职责原则**: 即便是类似“上传文件”这样的流程分支，也应当提炼其变为 `<UploadModal />` 组件，严禁在主流程里堆砌 HTML 结构。
2. **不允许过度引包**: 非必要场景不允许盲目叠加三方包，任何关于防抖、日期的轻量处理提倡在 `utils/` 下自己手写提炼。
