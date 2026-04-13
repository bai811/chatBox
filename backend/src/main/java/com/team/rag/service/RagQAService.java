package com.team.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import com.team.rag.tools.TeamAssistantTools;
import jakarta.annotation.PostConstruct;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import com.team.rag.bean.ChatMessage;
import com.team.rag.util.BM25Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG问答服务：混合检索（BM25+向量）+ 短期记忆 + Query Rewriting + 大模型答案生成
 * 完整实现选型+功能.md中定义的问答流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQAService {

    private final BM25Retriever bm25Retriever;
    private final EmbeddingModel embeddingModel;
    private final PineconeEmbeddingStore pineconeEmbeddingStore;
    private final StreamingChatLanguageModel streamingChatModel;
    private final ChatMemoryService chatMemoryService;
    private final TeamAssistantTools teamAssistantTools;

    private Assistant assistant;

    interface Assistant {
        TokenStream chat(@UserMessage String prompt);
    }

    @PostConstruct
    public void initAssistant() {
        this.assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .tools(teamAssistantTools)
                .build();
    }

    // 向量检索返回前K条
    @Value("${rag.hybrid-retrieval.vector-top-k}")
    private Integer vectorTopK;
    // 向量检索最小相似度阈值
    @Value("${rag.vector-retrieval.min-score}")
    private Double vectorMinScore;
    // 混合检索最终返回前K条
    @Value("${rag.hybrid-retrieval.final-top-k}")
    private Integer finalTopK;

    /**
     * RAG混合检索问答（流式输出）
     * 完整流程：召回短期记忆 → Query Rewriting → RAG混合检索 → 提示词构建 → 大模型调用 → 更新短期记忆
     */
    public Flux<String> qa(String query, String sessionId) {
        log.info("开始RAG混合检索问答，查询词：{}，会话ID：{}", query, sessionId);

        // 步骤1：召回短期记忆 & 保存用户消息
        chatMemoryService.addMessage(sessionId, "user", query);
        List<ChatMessage> memory = chatMemoryService.getMemory(sessionId);

        // 步骤2：Query Rewriting（上下文聚合+改写）
        String rewrittenQuery = chatMemoryService.rewriteQuery(sessionId, query);
        log.info("Query Rewriting完成");

        // 步骤3：RAG混合检索（BM25 + 向量）
        List<TextSegment> bm25Segments = bm25Retriever.retrieve(query);
        List<TextSegment> vectorSegments = vectorRetrieve(rewrittenQuery);
        List<TextSegment> finalSegments = mergeSegments(bm25Segments, vectorSegments);

        // 步骤4：构建提示词
        String prompt = buildPrompt(query, finalSegments, memory);
        log.info("提示词构建完成，包含{}个RAG片段，{}条历史消息", finalSegments.size(), memory.size());

        // 步骤5：调用流式大模型生成答案（通过 AiServices 支持 Function Calling）
        return Flux.create(emitter -> {
            StringBuilder fullResponse = new StringBuilder();

            assistant.chat(prompt)
                    .onPartialResponse(token -> {
                        fullResponse.append(token);
                        emitter.next(token);
                    })
                    .onCompleteResponse(response -> {
                        // 步骤6：更新短期记忆
                        chatMemoryService.addMessage(sessionId, "assistant", fullResponse.toString());
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("大模型流式调用失败", error);
                        emitter.error(error);
                    })
                    .start();
        });
    }

    /**
     * 向量语义检索
     */
    private List<TextSegment> vectorRetrieve(String query) {
        log.info("开始向量语义检索，返回前{}条，最小相似度：{}", vectorTopK, vectorMinScore);
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(vectorTopK)
                .minScore(vectorMinScore)
                .build();
        return pineconeEmbeddingStore.search(request).matches().stream()
                .map(match -> match.embedded())
                .collect(Collectors.toList());
    }

    /**
     * 合并BM25和向量检索结果，去重并取前K条
     */
    private List<TextSegment> mergeSegments(List<TextSegment> bm25, List<TextSegment> vector) {
        List<TextSegment> all = new ArrayList<>();
        all.addAll(bm25);
        vector.forEach(v -> {
            if (all.stream().noneMatch(a -> a.text().equals(v.text()))) {
                all.add(v);
            }
        });
        return all.stream().limit(finalTopK).collect(Collectors.toList());
    }

    /**
     * 构建大模型提示词：融合短期记忆 + RAG检索内容 + 用户问题
     */
    private String buildPrompt(String query, List<TextSegment> segments, List<ChatMessage> memory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请严格遵循以下规则进行回复：\n");

        prompt.append("1. 【首次问候】请仅在用户发起第一次会话时，主动和用户打个招呼，并简短介绍你是谁以及你能提供哪些帮助。\n");

        prompt.append(
                "2. 【专业解答与加工】作为专业的知识库助手，请以知识库内容为核心依据解答问题。要求结合上下文，用自然、流畅的语言重新组织答案，绝不能生硬地大段复制粘贴原文。提炼出对用户最有价值的核心信息。\n");

        prompt.append(
                "3. 【智能降噪】自动过滤并忽略知识库片段中关于文档结构、操作说明或测试标记的元数据文字（例如“本文档是说明书”、“测试数据”、“上传本文件后即可提问”等内容），只针对用户的核心意图回答。\n");

        prompt.append("4. 【合理延展与边界】如果知识库内容不充分，你可以结合自身的常识进行适度延展解答，使回答更丰满。但【严禁】编造具体的项目数据、未提及的功能或虚构的技术指标。\n");

        prompt.append("5. 【领域限制】你必须遵守职责边界。当被问及与本项目、IT技术、企业知识库无关的其他领域时，请礼貌地表示歉意，并说明你只是该项目的专属助手，无法在其他领域提供帮助。\n");

        prompt.append("6. 【互动风格】请在回答的段落排版中，适当包含一些轻松可爱的Emoji图标和表情，让对话更有温度，但不要过度使用导致阅读困难。\n");

        if (!segments.isEmpty()) {
            prompt.append("【知识库参考内容】\n");
            for (int i = 0; i < segments.size(); i++) {
                prompt.append(String.format("%d、%s\n", i + 1, segments.get(i).text()));
            }
            prompt.append("\n");
        }

        if (memory.size() > 1) {
            prompt.append("【对话历史】\n");
            int start = Math.max(1, memory.size() - 6);
            for (int i = start; i < memory.size() - 1; i++) {
                ChatMessage msg = memory.get(i);
                if (!"system".equals(msg.getRole())) {
                    prompt.append(msg.getRole().equals("user") ? "用户: " : "助手: ");
                    prompt.append(msg.getContent()).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("【当前问题】\n").append(query);
        return prompt.toString();
    }
}
