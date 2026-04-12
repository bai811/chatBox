package com.team.rag.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 聊天消息实体（存储在Redis短期记忆中）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {
    /** 角色：user / assistant / system */
    private String role;
    /** 消息内容 */
    private String content;
    /** 时间戳 */
    private Long timestamp;
}
