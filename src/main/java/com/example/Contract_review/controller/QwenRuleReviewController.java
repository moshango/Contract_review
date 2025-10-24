package com.example.Contract_review.controller;

import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.service.QwenRuleReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Qwen 规则审查控制器
 *
 * 提供接口用于：
 * 1. 将生成的Prompt发送给Qwen进行审查
 * 2. 获取Qwen的审查结果
 * 3. 检查Qwen服务状态
 */
@Slf4j
@RestController
@RequestMapping("/api/qwen/rule-review")
public class QwenRuleReviewController {

    @Autowired
    private QwenRuleReviewService qwenRuleReviewService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 使用Qwen进行规则审查
     *
     * 这是一键式审查的核心接口：
     * 1. 接收规则审查生成的Prompt
     * 2. 将Prompt发送给Qwen
     * 3. 返回JSON格式的审查结果
     *
     * @param request 包含prompt的请求
     * @return 审查结果JSON
     */
    @PostMapping("/review")
    public ResponseEntity<?> reviewWithQwen(@RequestBody QwenReviewRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("=== 收到Qwen规则审查请求 ===");
            log.debug("Prompt长度: {} 字符", request.getPrompt() != null ? request.getPrompt().length() : 0);

            // 验证Qwen服务
            if (!qwenRuleReviewService.isQwenAvailable()) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "Qwen服务未配置或不可用");
                error.put("hint", "请检查application.properties中的qwen配置");
                return ResponseEntity.badRequest().body(error);
            }

            // 验证Prompt
            if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "Prompt不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            log.info("✓ 参数验证通过，准备发送到Qwen");

            // 调用Qwen进行审查
            String reviewResult = qwenRuleReviewService.reviewContractWithQwen(request.getPrompt());

            // 解析审查结果为ReviewIssue对象
            List<ReviewIssue> issues = qwenRuleReviewService.parseReviewResults(reviewResult);

            // 构建响应
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("issueCount", issues.size());
            response.put("timestamp", System.currentTimeMillis());

            // 【关键修复】添加 parseResultId - 用于后续批注导入
            if (request.getParseResultId() != null && !request.getParseResultId().isEmpty()) {
                response.put("parseResultId", request.getParseResultId());
                log.info("✓ parseResultId 已添加到响应: {}", request.getParseResultId());
            } else {
                log.warn("⚠️ 请求中未包含 parseResultId，后续批注导入可能精度较低");
            }

            // 添加审查结果JSON
            try {
                ObjectNode reviewJson = (ObjectNode) objectMapper.readTree(reviewResult);
                response.set("review", reviewJson);
            } catch (Exception e) {
                log.warn("无法解析审查结果JSON: {}", e.getMessage());
                response.put("review", reviewResult);
            }

            long endTime = System.currentTimeMillis();
            response.put("processingTime", (endTime - startTime) + "ms");

            log.info("✓ Qwen审查完成，耗时: {}ms, 检出 {} 个问题",
                endTime - startTime, issues.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Qwen审查处理失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "审查处理失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 检查Qwen服务状态
     * 用于前端判断是否可以执行一键审查
     *
     * @return 服务状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<?> getQwenStatus() {
        try {
            boolean available = qwenRuleReviewService.isQwenAvailable();
            Map<String, String> config = qwenRuleReviewService.getQwenConfig();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("qwenAvailable", available);
            response.put("timestamp", System.currentTimeMillis());

            ObjectNode configNode = response.putObject("config");
            configNode.put("model", config.getOrDefault("model", "未配置"));
            configNode.put("hasApiKey", !config.getOrDefault("api-key", "").isEmpty());
            configNode.put("hasBaseUrl", !config.getOrDefault("base-url", "").isEmpty());

            ObjectNode endpoints = response.putObject("endpoints");
            endpoints.put("review", "POST /api/qwen/rule-review/review");
            endpoints.put("status", "GET /api/qwen/rule-review/status");
            endpoints.put("config", "GET /api/qwen/rule-review/config");

            if (available) {
                response.put("message", "✓ Qwen服务已就绪");
            } else {
                response.put("message", "✗ Qwen服务未配置");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取Qwen状态失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取状态失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取Qwen配置信息
     * 用于调试和配置检查
     *
     * @return 配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<?> getQwenConfig() {
        try {
            Map<String, String> config = qwenRuleReviewService.getQwenConfig();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);

            ObjectNode configNode = response.putObject("qwen");
            for (Map.Entry<String, String> entry : config.entrySet()) {
                // 隐藏敏感信息
                if (entry.getKey().toLowerCase().contains("key")) {
                    String value = entry.getValue();
                    if (value != null && value.length() > 10) {
                        value = value.substring(0, 7) + "***";
                    }
                    configNode.put(entry.getKey(), value);
                } else {
                    configNode.put(entry.getKey(), entry.getValue());
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取Qwen配置失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Qwen审查请求DTO
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class QwenReviewRequest {
        /**
         * 规则审查生成的Prompt
         */
        private String prompt;

        /**
         * 可选：合同类型
         */
        private String contractType;

        /**
         * 可选：审查立场
         */
        private String stance;

        /**
         * 【关键】可选：parseResultId - 用于后续批注时使用带锚点的文档
         */
        private String parseResultId;
    }
}
