package com.team.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.annotation.Configuration;

/**
 * 向量模型配置说明：
 * DashScope EmbeddingModel 由 langchain4j-community-dashscope-spring-boot-starter 自动装配
 * 配置项在 application.properties 中：
 *   langchain4j.community.dashscope.embedding-model.api-key
 *   langchain4j.community.dashscope.embedding-model.model-name
 * 
 * 自动注入的 Bean 类型为 EmbeddingModel，可直接通过构造器注入使用
 */
@Configuration
public class EmbeddingConfig {
    // DashScope EmbeddingModel 由 Spring Boot Starter 自动装配，无需手动创建 Bean
}
