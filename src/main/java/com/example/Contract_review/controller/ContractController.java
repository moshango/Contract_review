package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.ContractAnnotateService;
import com.example.Contract_review.service.ContractParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 合同审查接口控制器
 *
 * 提供 /parse 和 /annotate 两个主要接口
 */
@RestController
@RequestMapping
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);

    @Autowired
    private ContractParseService parseService;

    @Autowired
    private ContractAnnotateService annotateService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 合同解析接口
     *
     * POST /parse
     *
     * @param file 上传的合同文件
     * @param anchors 锚点模式: none, generate, regenerate (默认: none)
     * @param returnMode 返回模式: json, file, both (默认: json)
     * @return 解析结果JSON或带锚点的文档文件
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseContract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "anchors", defaultValue = "none") String anchors,
            @RequestParam(value = "returnMode", defaultValue = "json") String returnMode) {

        logger.info("收到解析请求: filename={}, anchors={}, returnMode={}",
                    file.getOriginalFilename(), anchors, returnMode);

        try {
            // 验证文件大小 (50MB限制)
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("文件大小超过50MB限制"));
            }

            // 验证文件格式
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".docx") &&
                                    !filename.toLowerCase().endsWith(".doc"))) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("仅支持 .docx 和 .doc 格式文件"));
            }

            // 根据返回模式处理
            if ("json".equalsIgnoreCase(returnMode)) {
                // 仅返回JSON
                ParseResult result = parseService.parseContract(file, anchors);
                return ResponseEntity.ok(result);

            } else if ("file".equalsIgnoreCase(returnMode)) {
                // 仅返回文档文件
                ContractParseService.ParseResultWithDocument resultWithDoc =
                        parseService.parseContractWithDocument(file, anchors);

                if (resultWithDoc.getDocumentBytes() == null) {
                    return ResponseEntity.badRequest()
                            .body(errorResponse("returnMode=file 需要 anchors=generate 或 regenerate"));
                }

                return buildFileResponse(resultWithDoc.getDocumentBytes(),
                                        "parsed-with-anchors.docx");

            } else if ("both".equalsIgnoreCase(returnMode)) {
                // 返回JSON和文档文件
                ContractParseService.ParseResultWithDocument resultWithDoc =
                        parseService.parseContractWithDocument(file, anchors);

                if (resultWithDoc.getDocumentBytes() == null) {
                    return ResponseEntity.badRequest()
                            .body(errorResponse("returnMode=both 需要 anchors=generate 或 regenerate"));
                }

                // TODO: 实现同时返回JSON和文件的方式
                // 目前优先返回文件,JSON可通过响应头传递或使用multipart
                return buildFileResponse(resultWithDoc.getDocumentBytes(),
                                        "parsed-with-anchors.docx");

            } else {
                return ResponseEntity.badRequest()
                        .body(errorResponse("无效的 returnMode 参数,支持: json, file, both"));
            }

        } catch (IllegalArgumentException e) {
            logger.error("参数错误", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        } catch (IOException e) {
            logger.error("文件处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("文件处理失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("解析失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("解析失败: " + e.getMessage()));
        }
    }

    /**
     * 合同批注接口
     *
     * POST /annotate
     *
     * @param file 原始合同文件
     * @param review 审查结果JSON(可以是字符串或文件)
     * @param anchorStrategy 锚点定位策略: preferAnchor, anchorOnly, textFallback (默认: preferAnchor)
     * @param cleanupAnchors 是否清理锚点 (默认: false)
     * @return 带批注的文档文件
     */
    @PostMapping("/annotate")
    public ResponseEntity<?> annotateContract(
            @RequestParam("file") MultipartFile file,
            @RequestParam("review") String review,
            @RequestParam(value = "anchorStrategy", defaultValue = "preferAnchor") String anchorStrategy,
            @RequestParam(value = "cleanupAnchors", defaultValue = "false") boolean cleanupAnchors) {

        logger.info("收到批注请求: filename={}, anchorStrategy={}, cleanupAnchors={}",
                    file.getOriginalFilename(), anchorStrategy, cleanupAnchors);

        try {
            // 验证文件大小
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("文件大小超过50MB限制"));
            }

            // 验证文件格式
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("批注功能仅支持 .docx 格式文件"));
            }

            // 验证审查结果JSON
            if (review == null || review.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("review 参数不能为空"));
            }

            // 执行批注
            byte[] annotatedDoc = annotateService.annotateContract(
                    file, review, anchorStrategy, cleanupAnchors);

            // 返回带批注的文档
            String outputFilename = filename.replace(".docx", "-annotated.docx");
            return buildFileResponse(annotatedDoc, outputFilename);

        } catch (IllegalArgumentException e) {
            logger.error("参数错误", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        } catch (IOException e) {
            logger.error("文件处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("文件处理失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("批注失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("批注失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     *
     * GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "AI Contract Review Assistant");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    /**
     * 构建文件响应
     */
    private ResponseEntity<ByteArrayResource> buildFileResponse(byte[] data, String filename) {
        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(data.length)
                .body(resource);
    }

    /**
     * 构建错误响应
     */
    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
