package com.example.Contract_review.controller;

import com.example.Contract_review.model.ReviewMode;
import com.example.Contract_review.model.UnifiedReviewResult;
import com.example.Contract_review.service.UnifiedReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一审查控制器
 *
 * 提供统一的API接口 /api/unified/review，支持多种审查模式：
 * - reviewMode=rules: 仅规则审查（返回Prompt供用户复制到LLM）
 * - reviewMode=ai: 调用AI进行审查
 * - reviewMode=full: 完整流程（规则审查 + AI审查 + 批注导入）
 *
 * 这个控制器是对其他控制器的统一入口，避免前端调用多个不同的API端点
 */
@RestController
@RequestMapping("/api/unified")
public class UnifiedReviewController {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedReviewController.class);

    @Autowired
    private UnifiedReviewService unifiedReviewService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 统一的审查接口
     *
     * 支持多种审查模式，通过一个端点实现所有审查功能
     *
     * @param file 合同文件
     * @param contractType 合同类型（采购/外包/NDA/通用合同等）
     * @param party 审查立场（A方/B方/null）
     * @param reviewMode 审查模式（rules/ai/full，默认为rules）
     * @param aiProvider AI提供商（qwen/chatgpt/claude，默认为qwen）
     * @return 统一的审查结果
     */
    @PostMapping("/review")
    public ResponseEntity<?> performUnifiedReview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType,
            @RequestParam(value = "party", required = false) String party,
            @RequestParam(value = "reviewMode", defaultValue = "rules") String reviewMode,
            @RequestParam(value = "aiProvider", defaultValue = "qwen") String aiProvider) {

        long startTime = System.currentTimeMillis();

        try {
            logger.info("【统一审查接口】收到请求: file={}, type={}, party={}, mode={}, provider={}",
                       file.getOriginalFilename(), contractType, party, reviewMode, aiProvider);

            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("文件不能为空", null));
            }

            // 解析审查模式
            ReviewMode mode;
            try {
                mode = ReviewMode.fromString(reviewMode);
            } catch (IllegalArgumentException e) {
                logger.warn("无效的审查模式: {}", reviewMode);
                mode = ReviewMode.RULES;
            }

            // 执行统一审查流程
            UnifiedReviewResult result = unifiedReviewService.performReview(
                    file,
                    contractType,
                    party,
                    mode,
                    aiProvider
            );

            // 根据审查模式调整响应
            if (!result.isSuccess()) {
                logger.error("【统一审查失败】{}", result.getError());
                return ResponseEntity.internalServerError()
                        .body(createErrorResponse("审查失败", result.getError()));
            }

            // 构建成功响应
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);

            // 添加统计信息
            if (result.getStatistics() != null) {
                response.set("statistics", objectMapper.valueToTree(result.getStatistics()));
            }

            // 添加匹配结果
            if (result.getMatchResults() != null) {
                response.set("matchResults", objectMapper.valueToTree(result.getMatchResults()));
            }

            // 添加Prompt
            if (result.getPrompt() != null) {
                response.put("prompt", result.getPrompt());
            }

            // 添加缓存ID
            if (result.getParseResultId() != null) {
                response.put("parseResultId", result.getParseResultId());
            }

            // 如果是AI模式，添加AI结果
            if ((mode == ReviewMode.AI || mode == ReviewMode.FULL) && result.getAiResult() != null) {
                response.set("aiResult", result.getAiResult());
            }

            // 添加用户立场
            if (result.getUserStance() != null) {
                response.put("userStance", result.getUserStance());
            }

            // 添加处理耗时
            if (result.getProcessingTime() != null) {
                response.put("processingTime", result.getProcessingTime());
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("【统一审查成功】耗时: {}ms", totalTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("【统一审查异常】", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("审查异常", e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "UP");
        response.put("service", "Unified Review API");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    /**
     * 创建错误响应
     */
    private ObjectNode createErrorResponse(String error, String details) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", false);
        response.put("error", error);
        if (details != null && !details.isEmpty()) {
            response.put("details", details);
        }
        return response;
    }
}
