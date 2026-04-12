package com.team.rag.controller;

import com.team.rag.bean.RagQo;
import com.team.rag.service.ChatMemoryService;
import com.team.rag.service.RagQAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG问答接口：混合检索+大模型问答+会话管理
 */
@Tag(name = "RAG问答", description = "团队RAG知识库混合检索问答接口（流式输出）")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagQAController {

    private final RagQAService ragQAService;
    private final ChatMemoryService chatMemoryService;

    @Operation(summary = "RAG混合检索问答（流式输出）")
    @PostMapping(value = "/qa", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=utf-8")
    public Flux<String> qa(@RequestBody RagQo qo) {
        String sessionId = qo.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = chatMemoryService.createSession();
        }
        return ragQAService.qa(qo.getQuery(), sessionId);
    }

    @Operation(summary = "创建新会话")
    @PostMapping("/session")
    public Map<String, String> createSession() {
        String sessionId = chatMemoryService.createSession();
        return Map.of("sessionId", sessionId);
    }

    @Operation(summary = "获取会话历史")
    @GetMapping("/session/{sessionId}/messages")
    public List<?> getMessages(@PathVariable String sessionId) {
        return chatMemoryService.getMemory(sessionId);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/session/{sessionId}")
    public Map<String, String> deleteSession(@PathVariable String sessionId) {
        chatMemoryService.deleteSession(sessionId);
        return Map.of("message", "会话已删除");
    }

    @Operation(summary = "获取所有会话ID")
    @GetMapping("/sessions")
    public List<String> getAllSessions() {
        return chatMemoryService.getAllSessionIds();
    }
}
