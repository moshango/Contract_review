package com.example.Contract_review.controller;

import com.example.Contract_review.service.AutoReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动化审查控制器
 *
 * 提供一键式自动化合同审查接口
 */
@RestController
@RequestMapping("/auto-review")
public class AutoReviewController {

    private static final Logger logger = LoggerFactory.getLogger(AutoReviewController.class);

    @Autowired
    private AutoReviewService autoReviewService;

    /**
     * 一键自动化审查
     *
     * 上传合同 → AI审查 → 返回带批注的文档
     *
     * @param file 合同文件
     * @param contractType 合同类型 (general/technology/purchase等)
     * @param aiProvider AI提供商 (claude/openai/auto)
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的合同文档
     */
    @PostMapping
    public ResponseEntity<?> autoReview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String contractType,
            @RequestParam(defaultValue = "auto") String aiProvider,
            @RequestParam(defaultValue = "true") boolean cleanupAnchors) {

        try {
            logger.info("收到自动化审查请求: filename={}, contractType={}, aiProvider={}",
                       file.getOriginalFilename(), contractType, aiProvider);

            // 执行自动化审查
            byte[] annotatedDocument = autoReviewService.autoReview(
                file,
                contractType,
                aiProvider,
                cleanupAnchors
            );

            // 构建文件名
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename != null
                ? originalFilename.replace(".docx", "-AI审查完成.docx")
                : "contract-ai-reviewed.docx";

            // 返回带批注的文档
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"" + filename + "\"")
                    .body(annotatedDocument);

        } catch (IllegalStateException e) {
            logger.error("AI服务配置错误", e);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("AI服务未配置", e.getMessage()));

        } catch (Exception e) {
            logger.error("自动化审查失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("自动化审查失败", e.getMessage()));
        }
    }

    /**
     * 详细模式的自动化审查
     *
     * 返回完整的审查过程信息和结果
     *
     * @param file 合同文件
     * @param contractType 合同类型
     * @param aiProvider AI提供商
     * @param cleanupAnchors 是否清理锚点
     * @param returnMode 返回模式 (document/json/both)
     * @return 审查结果详情
     */
    @PostMapping("/detailed")
    public ResponseEntity<?> autoReviewDetailed(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String contractType,
            @RequestParam(defaultValue = "auto") String aiProvider,
            @RequestParam(defaultValue = "true") boolean cleanupAnchors,
            @RequestParam(defaultValue = "document") String returnMode) {

        try {
            logger.info("收到详细模式自动化审查请求: filename={}, returnMode={}",
                       file.getOriginalFilename(), returnMode);

            // 执行详细模式审查
            Map<String, Object> result = autoReviewService.autoReviewWithDetails(
                file,
                contractType,
                aiProvider,
                cleanupAnchors
            );

            // 根据返回模式处理响应
            if ("document".equalsIgnoreCase(returnMode)) {
                // 仅返回文档
                byte[] annotatedDocument = (byte[]) result.get("annotatedDocument");
                String filename = file.getOriginalFilename().replace(".docx", "-AI审查完成.docx");

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                               "attachment; filename=\"" + filename + "\"")
                        .body(annotatedDocument);

            } else if ("json".equalsIgnoreCase(returnMode)) {
                // 仅返回JSON信息（不包含文档字节）
                result.remove("annotatedDocument");
                return ResponseEntity.ok(result);

            } else {
                // both模式：返回JSON信息，文档通过另一个端点获取
                // 移除大对象，避免JSON过大
                result.put("documentAvailable", true);
                result.put("documentDownloadHint", "文档已生成，使用document模式下载");
                result.remove("annotatedDocument");
                return ResponseEntity.ok(result);
            }

        } catch (IllegalStateException e) {
            logger.error("AI服务配置错误", e);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("AI服务未配置", e.getMessage()));

        } catch (Exception e) {
            logger.error("详细模式自动化审查失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("自动化审查失败", e.getMessage()));
        }
    }

    /**
     * 检查AI服务配置状态
     *
     * @return AI服务配置信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkStatus() {
        try {
            Map<String, Object> status = autoReviewService.checkAIServiceStatus();
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("检查AI服务状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("检查服务状态失败", e.getMessage()));
        }
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("message", message);
        response.put("status", "failed");
        return response;
    }
}