package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.qwen.client.QwenClient;
import com.example.Contract_review.qwen.dto.ChatMessage;
import com.example.Contract_review.qwen.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Qwen 规则审查服务
 *
 * 用于将规则审查生成的Prompt传送给Qwen模型，获取JSON格式的审查结果
 * 支持一键式审查工作流
 */
@Slf4j
@Service
public class QwenRuleReviewService {

    @Autowired
    private QwenClient qwenClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String QWEN_MODEL = "qwen-max-latest";
    private static final long TIMEOUT = 60000; // 60 seconds

    /**
     * 使用Qwen进行合同审查
     *
     * @param prompt 规则审查生成的Prompt
     * @return 审查结果JSON，包含issues数组
     */
    public String reviewContractWithQwen(String prompt) {
        log.info("=== 开始使用Qwen进行合同审查 ===");
        log.debug("Prompt长度: {} 字符", prompt.length());

        try {
            // 构建系统消息和用户消息
            ChatMessage systemMsg = ChatMessage.builder()
                .role("system")
                .content("你是一位资深的法律合同审查专家。你的任务是严格按照用户提供的规则和条款检查清单，" +
                        "对合同进行审查，并返回结构化的JSON格式的审查结果。" +
                        "返回的JSON必须包含'issues'数组，其中每个问题包含：" +
                        "anchorId（锚点ID）、clauseId（条款ID）、severity（严重性：HIGH/MEDIUM/LOW）、" +
                        "category（问题类别）、finding（发现的问题）、suggestion（建议修改）。" +
                        "只返回JSON，不要返回任何其他文本。")
                .build();

            ChatMessage userMsg = ChatMessage.builder()
                .role("user")
                .content(prompt)
                .build();

            List<ChatMessage> messages = Arrays.asList(systemMsg, userMsg);

            log.info("向Qwen发送审查请求，模型: {}", QWEN_MODEL);

            // 调用Qwen API
            ChatResponse response = qwenClient.chat(messages, QWEN_MODEL).block();

            if (response == null) {
                log.error("Qwen返回null响应");
                return buildErrorResponse("Qwen返回null响应");
            }

            String responseContent = response.extractContent();
            log.info("Qwen返回内容长度: {} 字符", responseContent.length());
            log.debug("Qwen原始返回: {}", responseContent);

            // 提取和验证JSON
            String jsonResult = extractJsonFromResponse(responseContent);

            if (jsonResult == null || jsonResult.isEmpty()) {
                log.warn("无法从Qwen返回中提取JSON");
                return buildErrorResponse("无法解析Qwen的返回结果");
            }

            // 验证JSON格式
            try {
                ObjectNode json = (ObjectNode) objectMapper.readTree(jsonResult);

                // 确保包含issues数组
                if (!json.has("issues")) {
                    json.putArray("issues");
                    log.warn("Qwen返回的JSON不包含issues数组，已添加空数组");
                }

                log.info("✓ 审查完成，检出 {} 个问题",
                    json.has("issues") ? json.get("issues").size() : 0);

                return objectMapper.writeValueAsString(json);

            } catch (Exception e) {
                log.error("JSON解析失败: {}", e.getMessage());
                log.debug("尝试解析的JSON内容: {}", jsonResult);

                // 尝试修复常见的JSON格式错误
                String fixedJson = fixJsonFormat(jsonResult);
                return fixedJson;
            }

        } catch (Exception e) {
            log.error("Qwen审查失败", e);
            return buildErrorResponse("Qwen审查失败: " + e.getMessage());
        }
    }

    /**
     * 从Qwen返回的文本中提取JSON
     * 处理多种格式：
     * 1. 纯JSON
     * 2. 包含```json...```的代码块
     * 3. 包含说明文字的JSON
     *
     * @param response Qwen返回的完整文本
     * @return 提取的JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        response = response.trim();

        // 尝试方式1: 提取```json...```代码块
        Pattern jsonBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher jsonBlockMatcher = jsonBlockPattern.matcher(response);
        if (jsonBlockMatcher.find()) {
            String jsonContent = jsonBlockMatcher.group(1).trim();
            log.debug("从代码块中提取JSON");
            return jsonContent;
        }

        // 尝试方式2: 查找第一个 { 和最后一个 }
        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            String jsonContent = response.substring(firstBrace, lastBrace + 1);
            log.debug("从大括号中提取JSON");
            return jsonContent;
        }

        // 尝试方式3: 直接作为JSON
        if (response.startsWith("{") && response.endsWith("}")) {
            log.debug("响应本身就是JSON");
            return response;
        }

        log.warn("无法识别JSON格式");
        return null;
    }

    /**
     * 修复常见的JSON格式错误
     *
     * @param jsonStr 原始JSON字符串
     * @return 修复后的JSON
     */
    private String fixJsonFormat(String jsonStr) {
        try {
            // 移除注释（如果有）
            jsonStr = jsonStr.replaceAll("//.*", "");
            jsonStr = jsonStr.replaceAll("/\\*.*?\\*/", "");

            // 尝试解析修复后的JSON
            ObjectNode json = (ObjectNode) objectMapper.readTree(jsonStr);

            // 确保包含issues数组
            if (!json.has("issues")) {
                json.putArray("issues");
            }

            return objectMapper.writeValueAsString(json);

        } catch (Exception e) {
            log.error("JSON修复失败: {}", e.getMessage());
            return buildErrorResponse("JSON格式错误");
        }
    }

    /**
     * 构建错误响应
     *
     * @param errorMessage 错误消息
     * @return 包含错误信息的JSON字符串
     */
    private String buildErrorResponse(String errorMessage) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", errorMessage);
            error.putArray("issues");

            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            log.error("构建错误响应失败", e);
            return "{\"success\": false, \"error\": \"系统错误\", \"issues\": []}";
        }
    }

    /**
     * 解析Qwen返回的审查结果为ReviewIssue列表
     *
     * @param reviewJsonStr JSON格式的审查结果
     * @return ReviewIssue列表
     */
    public List<ReviewIssue> parseReviewResults(String reviewJsonStr) {
        List<ReviewIssue> issues = new ArrayList<>();

        try {
            ObjectNode reviewJson = (ObjectNode) objectMapper.readTree(reviewJsonStr);

            if (reviewJson.has("issues") && reviewJson.get("issues").isArray()) {
                ArrayNode issuesArray = (ArrayNode) reviewJson.get("issues");

                for (int i = 0; i < issuesArray.size(); i++) {
                    ObjectNode issueNode = (ObjectNode) issuesArray.get(i);

                    ReviewIssue issue = new ReviewIssue();
                    issue.setAnchorId(getStringValue(issueNode, "anchorId"));
                    issue.setClauseId(getStringValue(issueNode, "clauseId"));
                    issue.setSeverity(getStringValue(issueNode, "severity", "MEDIUM"));
                    issue.setCategory(getStringValue(issueNode, "category", "General"));
                    issue.setFinding(getStringValue(issueNode, "finding"));
                    issue.setSuggestion(getStringValue(issueNode, "suggestion"));

                    if (issue.getFinding() != null && !issue.getFinding().isEmpty()) {
                        issues.add(issue);
                        log.debug("解析审查问题: anchorId={}, severity={}, category={}",
                            issue.getAnchorId(), issue.getSeverity(), issue.getCategory());
                    }
                }
            }

            log.info("✓ 成功解析 {} 个审查问题", issues.size());

        } catch (Exception e) {
            log.error("解析审查结果失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 从JSON对象中获取字符串值
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    private String getStringValue(ObjectNode node, String fieldName, String defaultValue) {
        try {
            if (node.has(fieldName)) {
                String value = node.get(fieldName).asText();
                return value != null && !value.isEmpty() ? value : defaultValue;
            }
        } catch (Exception e) {
            log.debug("获取字段值失败: {}", fieldName);
        }
        return defaultValue;
    }

    /**
     * 从JSON对象中获取字符串值
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @return 字段值或null
     */
    private String getStringValue(ObjectNode node, String fieldName) {
        return getStringValue(node, fieldName, null);
    }

    /**
     * 获取Qwen客户端配置信息
     * 用于检查Qwen服务是否可用
     *
     * @return 配置信息Map
     */
    public java.util.Map<String, String> getQwenConfig() {
        return qwenClient.getConfig();
    }

    /**
     * 检查Qwen服务是否可用
     *
     * @return true 如果可用，false 否则
     */
    public boolean isQwenAvailable() {
        try {
            java.util.Map<String, String> config = getQwenConfig();
            String apiKey = config.getOrDefault("api-key", "");
            String baseUrl = config.getOrDefault("base-url", "");

            boolean available = !apiKey.isEmpty() && !baseUrl.isEmpty() && !apiKey.equals("sk-");

            if (available) {
                log.info("✓ Qwen服务可用");
            } else {
                log.warn("✗ Qwen服务不可用: API Key 或 Base URL 未配置");
            }

            return available;
        } catch (Exception e) {
            log.warn("无法检查Qwen服务: {}", e.getMessage());
            return false;
        }
    }
}
