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
     * 默认使用的AI提供商: claude / openai / doubao / mock / chatgpt-web / none
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
     * 豆包配置
     */
    private DouBaoConfig doubao = new DouBaoConfig();

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

    @Data
    public static class DouBaoConfig {
        /**
         * 豆包 API密钥 (Bearer token方式)
         */
        private String apiKey;

        /**
         * 火山引擎 Access Key ID (签名认证方式)
         */
        private String accessKeyId;

        /**
         * 火山引擎 Secret Access Key (签名认证方式)
         */
        private String secretAccessKey;

        /**
         * 火山引擎区域
         */
        private String region = "cn-beijing";

        /**
         * API端点 - 火山引擎豆包大模型API
         */
        private String apiEndpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        /**
         * 使用的模型
         */
        private String model = "ep-20241014152622-vvjrn";

        /**
         * 最大tokens数
         */
        private int maxTokens = 4096;

        /**
         * 检查是否配置了API Key认证
         */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.trim().isEmpty();
        }

        /**
         * 检查是否配置了Access Key签名认证
         */
        public boolean hasAccessKey() {
            return accessKeyId != null && !accessKeyId.trim().isEmpty() &&
                   secretAccessKey != null && !secretAccessKey.trim().isEmpty();
        }

        /**
         * 检查是否有任何有效的认证配置
         */
        public boolean hasValidAuth() {
            return hasApiKey() || hasAccessKey();
        }
    }

    /**
     * 检查是否配置了AI服务
     */
    public boolean isConfigured() {
        if ("claude".equalsIgnoreCase(provider)) {
            return claude.getApiKey() != null && !claude.getApiKey().isEmpty();
        } else if ("openai".equalsIgnoreCase(provider)) {
            return openai.getApiKey() != null && !openai.getApiKey().isEmpty();
        } else if ("doubao".equalsIgnoreCase(provider)) {
            return doubao.hasValidAuth();
        }
        return false;
    }
}