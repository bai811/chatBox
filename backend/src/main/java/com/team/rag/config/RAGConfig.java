package com.team.rag.config;

import org.springframework.context.annotation.Configuration;

/**
 * RAG核心配置说明：
 * DashScope StreamingChatModel 由 langchain4j-community-dashscope-spring-boot-starter 自动装配
 * 配置项在 application.properties 中：
 *   langchain4j.community.dashscope.streaming-chat-model.api-key
 *   langchain4j.community.dashscope.streaming-chat-model.model-name
 *   langchain4j.community.dashscope.streaming-chat-model.temperature
 * 
 * 自动注入的 Bean 类型为 StreamingChatLanguageModel，可直接通过构造器注入使用
 */
@Configuration
public class RAGConfig {
    // DashScope StreamingChatModel 由 Spring Boot Starter 自动装配，无需手动创建 Bean
}
