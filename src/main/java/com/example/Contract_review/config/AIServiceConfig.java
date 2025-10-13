package com.example.Contract_review.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI服务配置
 *
 * 配置Claude、OpenAI等AI服务的API密钥和参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.service")
public class AIServiceConfig {

    /**
     * 默认使用的AI提供商: claude / openai / none
     */
    private String provider = "none";

    /**
     * Claude配置
     */
    private ClaudeConfig claude = new ClaudeConfig();

    /**
     * OpenAI配置
     */
    private OpenAIConfig openai = new OpenAIConfig();

    /**
     * 请求超时时间(秒)
     */
    private int timeout = 60;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 代理配置
     */
    private ProxyConfig proxy = new ProxyConfig();

    @Data
    public static class ProxyConfig {
        /**
         * 是否启用代理
         */
        private boolean enabled = false;

        /**
         * 代理主机
         */
        private String host;

        /**
         * 代理端口
         */
        private int port = 8080;

        /**
         * 代理类型: HTTP / SOCKS
         */
        private String type = "HTTP";
    }

    @Data
    public static class ClaudeConfig {
        /**
         * Claude API密钥
         */
        private String apiKey;

        /**
         * API端点
         */
        private String apiEndpoint = "https://api.anthropic.com/v1/messages";

        /**
         * 使用的模型
         */
        private String model = "claude-3-5-sonnet-20241022";

        /**
         * 最大tokens数
         */
        private int maxTokens = 4096;
    }

    @Data
    public static class OpenAIConfig {
        /**
         * OpenAI API密钥
         */
        private String apiKey;

        /**
         * API端点
         */
        private String apiEndpoint = "https://api.openai.com/v1/chat/completions";

        /**
         * 使用的模型
         */
        private String model = "gpt-4";

        /**
         * 最大tokens数
         */
        private int maxTokens = 4096;
    }

    /**
     * 检查是否配置了AI服务
     */
    public boolean isConfigured() {
        if ("claude".equalsIgnoreCase(provider)) {
            return claude.getApiKey() != null && !claude.getApiKey().isEmpty();
        } else if ("openai".equalsIgnoreCase(provider)) {
            return openai.getApiKey() != null && !openai.getApiKey().isEmpty();
        }
        return false;
    }
}