package com.example.Contract_review.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;

/**
 * AI服务错误处理器
 */
public class AIServiceErrorHandler implements ResponseErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(AIServiceErrorHandler.class);

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        int rawStatusCode = response.getStatusCode().value();
        return (rawStatusCode >= 400 && rawStatusCode < 600);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        String statusText = response.getStatusText();

        logger.error("AI服务调用失败: HTTP {} - {}", statusCode, statusText);

        // 根据不同的错误码提供更友好的错误信息
        if (statusCode == 401) {
            throw new IOException("API密钥无效或已过期，请检查配置");
        } else if (statusCode == 403) {
            throw new IOException("API访问被拒绝，请检查API密钥权限");
        } else if (statusCode == 429) {
            throw new IOException("API调用频率超限，请稍后重试");
        } else if (statusCode == 500) {
            throw new IOException("AI服务内部错误，请稍后重试");
        } else if (statusCode == 503) {
            throw new IOException("AI服务暂时不可用，请稍后重试");
        } else {
            throw new IOException("AI服务调用失败: " + statusCode + " - " + statusText);
        }
    }
}