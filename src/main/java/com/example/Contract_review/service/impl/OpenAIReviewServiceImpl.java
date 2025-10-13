package com.example.Contract_review.service.impl;

import com.example.Contract_review.config.AIServiceConfig;
import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.AIReviewService;
import com.example.Contract_review.service.ReviewStandardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI GPT审查服务实现
 */
@Service("openaiReviewService")
public class OpenAIReviewServiceImpl implements AIReviewService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIReviewServiceImpl.class);

    @Autowired
    private AIServiceConfig aiServiceConfig;

    @Autowired
    private ReviewStandardService reviewStandardService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String reviewContract(ParseResult parseResult, String contractType) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI服务未配置或不可用");
        }

        logger.info("开始使用OpenAI审查合同: contractType={}", contractType);

        // 生成审查Prompt
        String contractJson = objectMapper.writeValueAsString(parseResult);
        String prompt = reviewStandardService.generateReviewPrompt(contractType, contractJson);

        // 调用OpenAI API
        String reviewResult = callOpenAIAPI(prompt);

        logger.info("OpenAI审查完成，结果长度: {}", reviewResult.length());
        return reviewResult;
    }

    @Override
    public boolean isAvailable() {
        return "openai".equalsIgnoreCase(aiServiceConfig.getProvider())
               && aiServiceConfig.isConfigured();
    }

    @Override
    public String getProviderName() {
        return "OpenAI GPT";
    }

    /**
     * 调用OpenAI API
     */
    private String callOpenAIAPI(String prompt) throws Exception {
        AIServiceConfig.OpenAIConfig config = aiServiceConfig.getOpenai();

        // 重试机制
        int maxRetries = aiServiceConfig.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("调用OpenAI API - 尝试 {}/{}", attempt, maxRetries);

                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", config.getModel());
                requestBody.put("max_tokens", config.getMaxTokens());

                // 构建消息
                Map<String, String> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", prompt);
                requestBody.put("messages", List.of(message));

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(config.getApiKey());

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

                // 发送请求
                ResponseEntity<String> response = restTemplate.postForEntity(
                    config.getApiEndpoint(),
                    request,
                    String.class
                );

                // 解析响应
                String result = extractReviewFromResponse(response.getBody());
                logger.info("OpenAI API调用成功");
                return result;

            } catch (Exception e) {
                lastException = e;
                logger.warn("OpenAI API调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // 等待后重试（指数退避）
                    int waitTime = (int) Math.pow(2, attempt) * 1000; // 2秒, 4秒, 8秒
                    logger.info("等待{}秒后重试...", waitTime / 1000);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("重试被中断", ie);
                    }
                }
            }
        }

        // 所有重试都失败
        String errorMessage = "OpenAI API调用失败（已重试" + maxRetries + "次）";
        if (lastException != null) {
            errorMessage += ": " + lastException.getMessage();

            // 检查是否是网络连接问题
            if (lastException.getMessage().contains("Connection") ||
                lastException.getMessage().contains("timeout") ||
                lastException.getMessage().contains("connect")) {
                errorMessage = "网络连接失败，无法访问OpenAI服务。请检查:\n" +
                              "1. 网络连接是否正常\n" +
                              "2. 是否需要配置代理\n" +
                              "3. API端点URL是否正确: " + config.getApiEndpoint();
            }
        }

        logger.error(errorMessage);
        throw new Exception(errorMessage, lastException);
    }

    /**
     * 从OpenAI响应中提取审查结果
     */
    private String extractReviewFromResponse(String responseBody) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 获取choices数组
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String text = choices.get(0).path("message").path("content").asText();

                // 尝试提取JSON部分
                return extractJsonFromText(text);
            }

            throw new Exception("无法从OpenAI响应中提取审查结果");
        } catch (Exception e) {
            logger.error("解析OpenAI响应失败: {}", responseBody, e);
            throw new Exception("解析OpenAI响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文本中提取JSON
     */
    private String extractJsonFromText(String text) {
        // 尝试找到JSON代码块
        int jsonStart = text.indexOf("```json");
        int jsonEnd = text.lastIndexOf("```");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return text.substring(jsonStart + 7, jsonEnd).trim();
        }

        // 尝试直接找到JSON对象
        jsonStart = text.indexOf("{");
        jsonEnd = text.lastIndexOf("}");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1).trim();
        }

        return text;
    }
}