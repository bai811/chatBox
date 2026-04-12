package com.team.rag.service;

import com.team.rag.bean.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 短期记忆服务：基于Redis的滑动窗口+摘要机制
 * 管理每个会话的上下文记忆，支持多轮对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 短期记忆的Redis Key前缀 */
    private static final String MEMORY_KEY_PREFIX = "rag:memory:";
    /** 滑动窗口大小（保留最近N轮对话） */
    private static final int WINDOW_SIZE = 10;
    /** 会话过期时间（小时） */
    private static final int SESSION_EXPIRE_HOURS = 24;

    /**
     * 创建新会话，返回sessionId
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String key = MEMORY_KEY_PREFIX + sessionId;
        // 初始化空列表
        redisTemplate.opsForList().rightPush(key, new ChatMessage("system", "你是一个团队智能知识库助手，请基于知识库内容准确回答问题。", System.currentTimeMillis()));
        redisTemplate.expire(key, SESSION_EXPIRE_HOURS, TimeUnit.HOURS);
        log.info("创建新会话：{}", sessionId);
        return sessionId;
    }

    /**
     * 添加消息到短期记忆
     */
    public void addMessage(String sessionId, String role, String content) {
        String key = MEMORY_KEY_PREFIX + sessionId;
        ChatMessage message = new ChatMessage(role, content, System.currentTimeMillis());
        redisTemplate.opsForList().rightPush(key, message);
        redisTemplate.expire(key, SESSION_EXPIRE_HOURS, TimeUnit.HOURS);

        // 滑动窗口：当消息数超过窗口大小的2倍时，裁剪旧消息
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > WINDOW_SIZE * 2) {
            // 保留系统消息（第一条）+ 最近WINDOW_SIZE条
            redisTemplate.opsForList().trim(key, size - WINDOW_SIZE, -1);
            log.info("会话{}触发滑动窗口裁剪，保留最近{}条消息", sessionId, WINDOW_SIZE);
        }
    }

    /**
     * 获取短期记忆（会话历史）
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getMemory(String sessionId) {
        String key = MEMORY_KEY_PREFIX + sessionId;
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = new ArrayList<>();
        for (Object obj : raw) {
            if (obj instanceof ChatMessage) {
                messages.add((ChatMessage) obj);
            }
        }
        return messages;
    }

    /**
     * 构建包含历史上下文的查询（Query Rewriting - 上下文聚合）
     * 将最近的对话历史与当前问题聚合，生成带上下文的查询
     */
    public String rewriteQuery(String sessionId, String currentQuery) {
        List<ChatMessage> memory = getMemory(sessionId);
        if (memory.isEmpty() || memory.size() <= 1) {
            return currentQuery; // 无历史上下文，直接返回原始查询
        }

        // 取最近3轮对话作为上下文
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, memory.size() - 6); // 3轮 = 6条消息
        for (int i = start; i < memory.size(); i++) {
            ChatMessage msg = memory.get(i);
            if (!"system".equals(msg.getRole())) {
                context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        context.append("当前问题: ").append(currentQuery);
        return context.toString();
    }

    /**
     * 获取所有活跃的会话ID列表
     */
    public List<String> getAllSessionIds() {
        var keys = redisTemplate.keys(MEMORY_KEY_PREFIX + "*");
        if (keys == null) return Collections.emptyList();
        return keys.stream()
                .map(k -> k.replace(MEMORY_KEY_PREFIX, ""))
                .toList();
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        String key = MEMORY_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("删除会话：{}", sessionId);
    }
}
