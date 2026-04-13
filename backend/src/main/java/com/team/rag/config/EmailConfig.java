package com.team.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kyb.email") // 读取 kyb.email 开头的配置
public class EmailConfig {
    private String host; // 对应 kyb.email.host
    private Integer port; // 对应 kyb.email.port
    private String from; // 对应 kyb.email.from
    private String fromName; // 对应 kyb.email.from-name
    private String username; // 对应 kyb.email.username
    private String password; // 对应 kyb.email.password
    private Boolean sslEnabled = true; // 默认值
    private Boolean tlsEnabled = true;
    private Boolean enabled = true;
    private Boolean async = true;
    private Integer timeout = 10000;
}