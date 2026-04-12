package com.team.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

        // 步骤5：调用流式大模型生成答案
        return Flux.create(emitter -> {
            StringBuilder fullResponse = new StringBuilder();

            streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    fullResponse.append(token);
                    emitter.next(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    // 步骤6：更新短期记忆
                    chatMemoryService.addMessage(sessionId, "assistant", fullResponse.toString());
                    emitter.complete();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("大模型流式调用失败", error);
                    emitter.error(error);
                }
            });
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

        prompt.append("你是一个团队智能知识库助手。请严格基于以下提供的【知识库参考内容】回答用户的问题。\n");
        prompt.append("要求：\n");
        prompt.append("1. 只能根据知识库内容进行提取和总结。如果提供的知识库内容只有此问题本身而没有具体的答案，或者完全是不相关内容，请直接回复：“抱歉，知识库中未找到相关答案。”\n");
        prompt.append("2. 不要推测、不要编造，也不要对知识库文档本身的内容结构（例如说明这是个测试样例、或描述位于步骤几）进行冗长的分析。\n\n");

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
