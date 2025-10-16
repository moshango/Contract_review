package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.ContractAnnotateService;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.XmlContractAnnotateService;
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
@RequestMapping("/api")
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);

    @Autowired
    private ContractParseService parseService;

    @Autowired
    private ContractAnnotateService annotateService;

    @Autowired
    private XmlContractAnnotateService xmlAnnotateService;

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
     * 合同批注接口（基于XML操作）
     *
     * POST /annotate-xml
     *
     * @param file 原始合同文件
     * @param review 审查结果JSON(可以是字符串或文件)
     * @param anchorStrategy 锚点定位策略: preferAnchor, anchorOnly, textFallback (默认: preferAnchor)
     * @param cleanupAnchors 是否清理锚点 (默认: false)
     * @return 带批注的文档文件
     */
    @PostMapping("/annotate-xml")
    public ResponseEntity<?> annotateContractWithXml(
            @RequestParam("file") MultipartFile file,
            @RequestParam("review") String review,
            @RequestParam(value = "anchorStrategy", defaultValue = "preferAnchor") String anchorStrategy,
            @RequestParam(value = "cleanupAnchors", defaultValue = "false") boolean cleanupAnchors) {

        logger.info("收到XML批注请求: filename={}, anchorStrategy={}, cleanupAnchors={}",
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
                        .body(errorResponse("XML批注功能仅支持 .docx 格式文件"));
            }

            // 验证审查结果JSON
            if (review == null || review.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("review 参数不能为空"));
            }

            // 验证JSON格式
            if (!xmlAnnotateService.validateReviewJson(review)) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("review JSON格式无效"));
            }

            // 执行XML批注
            byte[] annotatedDoc = xmlAnnotateService.annotateContractWithXml(
                    file, review, anchorStrategy, cleanupAnchors);

            // 返回带批注的文档
            String outputFilename = filename.replace(".docx", "_xml_annotated.docx");
            return buildFileResponse(annotatedDoc, outputFilename);

        } catch (IllegalArgumentException e) {
            logger.error("参数错误", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        } catch (IOException e) {
            logger.error("文件处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("文件处理失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("XML批注失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("XML批注失败: " + e.getMessage()));
        }
    }

    /**
     * 测试批注定位接口
     * 用于调试和测试批注定位功能
     *
     * POST /test-annotation
     */
    @PostMapping("/test-annotation")
    public ResponseEntity<?> testAnnotation(@RequestParam("file") MultipartFile file) {
        logger.info("收到批注定位测试请求: filename={}", file.getOriginalFilename());

        try {
            // 1. 解析文档获取条款
            ParseResult parseResult = parseService.parseContract(file, "generate");

            // 2. 创建测试用的审查结果
            Map<String, Object> testReview = new HashMap<>();
            Map<String, Object>[] issues = new Map[1];
            issues[0] = new HashMap<>();
            issues[0].put("clauseId", "c1");
            issues[0].put("severity", "HIGH");
            issues[0].put("category", "测试类别");
            issues[0].put("finding", "这是一个测试批注");
            issues[0].put("suggestion", "这是测试建议");
            testReview.put("issues", issues);

            String reviewJson = objectMapper.writeValueAsString(testReview);
            logger.info("测试用审查JSON: {}", reviewJson);

            // 3. 执行批注
            byte[] annotatedDoc = annotateService.annotateContract(
                    file, reviewJson, "textFallback", false);

            // 4. 返回结果和调试信息
            Map<String, Object> result = new HashMap<>();
            result.put("parseResult", parseResult);
            result.put("testReviewJson", reviewJson);
            result.put("success", true);
            result.put("message", "测试批注完成");
            result.put("annotatedDocSize", annotatedDoc.length);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("批注定位测试失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("测试失败: " + e.getMessage()));
        }
    }

    /**
     * API使用说明接口
     *
     * GET /api
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> apiInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "AI Contract Review Assistant");
        response.put("version", "1.0.0");
        response.put("description", "AI驱动的合同审查系统");

        Map<String, Object> endpoints = new HashMap<>();

        Map<String, Object> parseEndpoint = new HashMap<>();
        parseEndpoint.put("method", "POST");
        parseEndpoint.put("path", "/api/parse");
        parseEndpoint.put("description", "解析合同文档，提取条款结构");
        parseEndpoint.put("parameters", Map.of(
            "file", "上传的合同文件 (.docx/.doc)",
            "anchors", "锚点模式: none|generate|regenerate (可选，默认: none)",
            "returnMode", "返回模式: json|file|both (可选，默认: json)"
        ));
        endpoints.put("parse", parseEndpoint);

        Map<String, Object> annotateEndpoint = new HashMap<>();
        annotateEndpoint.put("method", "POST");
        annotateEndpoint.put("path", "/api/annotate");
        annotateEndpoint.put("description", "在合同中插入AI审查批注（POI方式）");
        annotateEndpoint.put("parameters", Map.of(
            "file", "原始合同文件 (.docx)",
            "review", "审查结果JSON字符串",
            "anchorStrategy", "锚点定位策略: preferAnchor|anchorOnly|textFallback (可选，默认: preferAnchor)",
            "cleanupAnchors", "是否清理锚点: true|false (可选，默认: false)"
        ));
        endpoints.put("annotate", annotateEndpoint);

        Map<String, Object> annotateXmlEndpoint = new HashMap<>();
        annotateXmlEndpoint.put("method", "POST");
        annotateXmlEndpoint.put("path", "/api/annotate-xml");
        annotateXmlEndpoint.put("description", "在合同中插入AI审查批注（纯XML方式，右侧批注）");
        annotateXmlEndpoint.put("parameters", Map.of(
            "file", "原始合同文件 (.docx)",
            "review", "审查结果JSON字符串",
            "anchorStrategy", "锚点定位策略: preferAnchor|anchorOnly|textFallback (可选，默认: preferAnchor)",
            "cleanupAnchors", "是否清理锚点: true|false (可选，默认: false)"
        ));
        endpoints.put("annotate-xml", annotateXmlEndpoint);

        Map<String, Object> healthEndpoint = new HashMap<>();
        healthEndpoint.put("method", "GET");
        healthEndpoint.put("path", "/api/health");
        healthEndpoint.put("description", "健康检查");
        endpoints.put("health", healthEndpoint);

        response.put("endpoints", endpoints);

        Map<String, String> examples = new HashMap<>();
        examples.put("curl_parse", "curl -X POST -F \"file=@contract.docx\" http://localhost:8080/api/parse");
        examples.put("curl_annotate", "curl -X POST -F \"file=@contract.docx\" -F \"review={\\\"issues\\\":[]}\" http://localhost:8080/api/annotate");
        examples.put("curl_annotate_xml", "curl -X POST -F \"file=@contract.docx\" -F \"review={\\\"issues\\\":[]}\" http://localhost:8080/api/annotate-xml");
        response.put("examples", examples);

        return ResponseEntity.ok(response);
    }

    /**
     * 处理GET方法访问parse端点的错误
     */
    @GetMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseGetError() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "方法不支持");
        error.put("message", "/api/parse 端点仅支持 POST 方法");
        error.put("correct_method", "POST");
        error.put("example", "curl -X POST -F \"file=@contract.docx\" http://localhost:8080/api/parse");
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    /**
     * 处理GET方法访问annotate端点的错误
     */
    @GetMapping("/annotate")
    public ResponseEntity<Map<String, Object>> annotateGetError() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "方法不支持");
        error.put("message", "/api/annotate 端点仅支持 POST 方法");
        error.put("correct_method", "POST");
        error.put("example", "curl -X POST -F \"file=@contract.docx\" -F \"review={\\\"issues\\\":[]}\" http://localhost:8080/api/annotate");
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
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
