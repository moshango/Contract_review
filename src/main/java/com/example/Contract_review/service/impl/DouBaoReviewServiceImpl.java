package com.example.Contract_review.service.impl;

import com.example.Contract_review.model.Clause;
import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.AIReviewService;
import com.example.Contract_review.util.VolcEngineSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆包（字节跳动）AI审查服务实现
 */
@Service("douBaoReviewService")
public class DouBaoReviewServiceImpl implements AIReviewService {

    private static final Logger logger = LoggerFactory.getLogger(DouBaoReviewServiceImpl.class);

    @Value("${doubao.api.url:https://ark.cn-beijing.volces.com/api/v3/chat/completions}")
    private String apiUrl;

    @Value("${doubao.api.key:}")
    private String apiKey;

    @Value("${doubao.model:ep-20241014141450-2mhkd}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DouBaoReviewServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String reviewContract(ParseResult parseResult, String contractType) throws Exception {
        logger.info("开始豆包AI合同审查，条款数量: {}", parseResult.getClauses().size());

        try {
            // 构建请求消息
            String prompt = buildReviewPrompt(parseResult.getClauses(), contractType);
            Map<String, Object> requestBody = buildRequestBody(prompt);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 发送请求
            logger.debug("发送豆包API请求: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                logger.debug("豆包API响应: {}", responseBody);
                return extractReviewResult(responseBody);
            } else {
                logger.error("豆包API请求失败，状态码: {}", response.getStatusCode());
                return createErrorResponse("API请求失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("豆包AI审查过程中发生错误", e);
            return createErrorResponse("AI审查失败: " + e.getMessage());
        }
    }

    /**
     * 构建审查提示词
     */
    private String buildReviewPrompt(List<Clause> clauses, String standards) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("作为资深法务专家，请对以下合同条款进行详细审查分析。\n\n");
        prompt.append("审查标准：\n").append(standards).append("\n\n");
        prompt.append("合同条款：\n");

        for (int i = 0; i < clauses.size(); i++) {
            Clause clause = clauses.get(i);
            prompt.append("条款 ").append(i + 1).append(" (").append(clause.getId()).append(")：")
                  .append(clause.getHeading()).append("\n")
                  .append(clause.getText()).append("\n\n");
        }

        prompt.append("请按照以下JSON格式返回审查结果：\n");
        prompt.append("{\n");
        prompt.append("  \"issues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"clauseId\": \"条款ID\",\n");
        prompt.append("      \"severity\": \"HIGH/MEDIUM/LOW\",\n");
        prompt.append("      \"category\": \"问题类别\",\n");
        prompt.append("      \"finding\": \"发现的问题\",\n");
        prompt.append("      \"suggestion\": \"修改建议\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 构建API请求体
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        requestBody.put("messages", List.of(message));
        requestBody.put("max_tokens", 4000);
        requestBody.put("temperature", 0.1);

        return requestBody;
    }

    /**
     * 提取审查结果
     */
    private String extractReviewResult(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");

                // 提取JSON部分
                String jsonContent = extractJsonFromContent(content);
                logger.info("豆包AI审查完成，结果长度: {}", jsonContent.length());
                return jsonContent;
            }

            logger.warn("豆包API响应格式异常，无法提取内容");
            return createErrorResponse("API响应格式异常");

        } catch (Exception e) {
            logger.error("解析豆包API响应失败", e);
            return createErrorResponse("解析API响应失败: " + e.getMessage());
        }
    }

    /**
     * 从响应内容中提取JSON
     */
    private String extractJsonFromContent(String content) {
        if (content == null) {
            return createErrorResponse("AI响应内容为空");
        }

        // 查找JSON开始和结束位置
        int jsonStart = content.indexOf("{");
        int jsonEnd = content.lastIndexOf("}");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1);
        }

        // 如果没有找到完整JSON，返回原内容
        logger.warn("未找到完整JSON格式，返回原始内容");
        return content;
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(String errorMessage) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", errorMessage);
        errorResponse.put("issues", List.of());

        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"error\":true,\"message\":\"" + errorMessage + "\",\"issues\":[]}";
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String getProviderName() {
        return "doubao";
    }
}