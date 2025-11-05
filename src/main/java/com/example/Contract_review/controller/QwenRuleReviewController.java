package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.ParseResultCache;
import com.example.Contract_review.service.QwenRuleReviewService;
import com.example.Contract_review.service.XmlContractAnnotateService;
import com.example.Contract_review.service.MinioFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Autowired
    private ContractParseService contractParseService;

    @Autowired
    private XmlContractAnnotateService xmlContractAnnotateService;

    @Autowired
    private ParseResultCache parseResultCache;

    @Autowired
    private MinioFileService minioFileService;

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
     * 一键审查接口 - 完整的审查工作流
     *
     * 流程：
     * 1. 接收文件和审查参数
     * 2. 解析合同获取条款
     * 3. 生成规则审查Prompt
     * 4. 调用Qwen进行审查
     * 5. 将审查结果插入文档
     * 6. 保存文档到文档中心
     * 7. 返回带批注的文档
     *
     * @param file 上传的合同文件
     * @param stance 审查立场
     * @return 带审查批注的文档或JSON响应
     */
    @PostMapping("/one-click-review")
    public ResponseEntity<?> oneClickReview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "stance", defaultValue = "neutral") String stance) {

        long startTime = System.currentTimeMillis();

        try {
            log.info("=== 开始一键审查流程 ===");
            log.info("文件: {}, 立场: {}", file.getOriginalFilename(), stance);

            // 步骤1：验证文件
            if (file == null || file.isEmpty()) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "文件不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".docx") &&
                                   !filename.toLowerCase().endsWith(".doc"))) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "仅支持 .docx 和 .doc 格式");
                return ResponseEntity.badRequest().body(error);
            }

            // 【新增】先保存原始上传文件到MinIO的contracts目录，返回originalUrl
            String originalUrl = null;
            try {
                if (minioFileService.isEnabled()) {
                    String originalObjName = minioFileService.generateObjectName(filename, "contracts");
                    originalUrl = minioFileService.uploadBytes(
                        file.getBytes(),
                        originalObjName,
                        file.getContentType() != null ? file.getContentType() : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    );
                    log.info("✓ 原始文件已保存到MinIO: {}", originalUrl);
                } else {
                    log.info("MinIO服务未启用，跳过原始文件上传");
                }
            } catch (Exception ex) {
                log.warn("原始文件上传MinIO失败: {}", ex.getMessage());
            }

            // 步骤2：解析合同（启用锚点）
            log.info("步骤1/6: 正在解析合同...");

            // 【关键修复】改用 parseContractWithDocument() 方法，确保锚点被真正插入到文档中
            // 与规则审查流程一致，避免缓存和字节不同步的问题
            ContractParseService.ParseResultWithDocument parseResultWithDoc =
                contractParseService.parseContractWithDocument(file, "generate");
            ParseResult parseResult = parseResultWithDoc.getParseResult();
            byte[] documentWithAnchorBytes = parseResultWithDoc.getDocumentBytes();

            log.info("✓ 合同解析完成，识别 {} 个条款，带锚点文档大小: {} bytes",
                    parseResult.getClauses().size(),
                    documentWithAnchorBytes != null ? documentWithAnchorBytes.length : 0);

            // 验证文档中是否包含锚点
            if (documentWithAnchorBytes == null || documentWithAnchorBytes.length == 0) {
                log.error("❌ 错误：带锚点文档为空，无法继续");
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "文档解析失败：带锚点文档生成失败");
                return ResponseEntity.status(500).body(error);
            }

            // 步骤2：先进行规则匹配，若无匹配则跳过LLM
            log.info("步骤2/6: 规则匹配...");
            java.util.List<com.example.Contract_review.model.RuleMatchResult> matched =
                qwenRuleReviewService.matchRules(parseResult, stance);
            if (matched == null || matched.isEmpty()) {
                log.info("✓ 规则未命中：跳过LLM，但仍保存带锚点的原文档到MinIO");

                String baseNameNoExt = filename.replaceAll("\\.(docx|doc)$", "");
                String outputFilenameNoHit = baseNameNoExt + "_一键审查_未命中规则.docx";

                // 保存到本地文档中心，便于留痕
                String projectRootNoHit = System.getProperty("user.dir");
                String docCenterPathNoHit = java.nio.file.Paths.get(projectRootNoHit, "文档中心", "已生成的审查报告").toString();
                java.nio.file.Path docCenterDirNoHit = java.nio.file.Paths.get(docCenterPathNoHit);
                if (!java.nio.file.Files.exists(docCenterDirNoHit)) {
                    java.nio.file.Files.createDirectories(docCenterDirNoHit);
                    log.info("✓ 创建文档中心目录: {}", docCenterPathNoHit);
                }
                java.nio.file.Path outputPathNoHit = docCenterDirNoHit.resolve(outputFilenameNoHit);
                java.nio.file.Files.write(outputPathNoHit, documentWithAnchorBytes);
                log.info("✓ 未命中文档已本地保存: {}", outputPathNoHit);

                // 同步保存至 MinIO（reports 目录）
                String minioUrlNoHit = null;
                try {
                    if (minioFileService.isEnabled()) {
                        String objName = minioFileService.generateReportObjectName(filename, "一键审查未命中", stance);
                        minioUrlNoHit = minioFileService.uploadBytes(
                            documentWithAnchorBytes,
                            objName,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        );
                        log.info("✓ 未命中文档已保存到MinIO: {}", minioUrlNoHit);
                    } else {
                        log.info("MinIO服务未启用，跳过MinIO存储（未命中场景）");
                    }
                } catch (Exception e) {
                    log.warn("未命中文档上传MinIO失败: {}", e.getMessage());
                }

                ObjectNode response = objectMapper.createObjectNode();
                response.put("success", true);
                response.put("message", "未命中任何规则，已跳过LLM并保存原文档");
                response.put("issuesCount", 0);
                response.put("processingTime", System.currentTimeMillis() - startTime);
                if (originalUrl != null) {
                    response.put("originalUrl", originalUrl);
                }
                if (minioUrlNoHit != null) {
                    response.put("minioUrl", minioUrlNoHit);
                    response.put("savedToMinio", true);
                } else {
                    response.put("savedToMinio", false);
                }
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            // 生成审查Prompt（仅包含命中条款）
            log.info("步骤3/6: 生成审查Prompt...");
            String prompt = qwenRuleReviewService.generateRuleReviewPrompt(parseResult, stance);
            log.info("✓ Prompt生成完成，长度: {} 字符", prompt.length());

            // 步骤4：调用Qwen进行审查
            log.info("步骤4/6: 正在调用Qwen进行审查...");
            String reviewResult = qwenRuleReviewService.reviewContractWithQwen(prompt);
            List<ReviewIssue> issues = qwenRuleReviewService.parseReviewResults(reviewResult);
            log.info("✓ Qwen审查完成，检出 {} 个问题", issues.size());

            // 【新增诊断】验证 anchorId 是否存在
            int validAnchorCount = 0;
            for (ReviewIssue issue : issues) {
                if (issue.getAnchorId() != null && !issue.getAnchorId().isEmpty()) {
                    validAnchorCount++;
                } else {
                    log.warn("⚠️ Issue缺少anchorId: clauseId={}, finding长度={}",
                            issue.getClauseId(),
                            issue.getFinding() != null ? issue.getFinding().length() : 0);
                }
            }
            log.info("✓ 其中 {} 个问题有有效的anchorId", validAnchorCount);

            // 步骤5：将审查结果插入文档
            // 【关键修复】直接传递文档字节数组和 issues 列表，而不是 JSON 字符串和 MultipartFile 包装器
            // 这样避免了中间层的不确定性，确保带锚点文档被正确使用
            log.info("步骤4/6: 正在将审查结果插入文档...");

            byte[] annotatedDocBytes = xmlContractAnnotateService.annotateContractWithXml(
                documentWithAnchorBytes, issues, "preferAnchor", false);
            log.info("✓ 文档批注完成，大小: {} KB", annotatedDocBytes.length / 1024);

            // 步骤6：保存文档到文档中心
            log.info("步骤5/6: 正在保存文档到文档中心...");
            String baseName = filename.replaceAll("\\.(docx|doc)$", "");
            
            // 添加时间戳避免文件冲突
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String outputFilename = baseName + "_一键审查_" + stance + "_" + timestamp + ".docx";

            // 构建文档中心路径
            String projectRoot = System.getProperty("user.dir");
            String docCenterPath = java.nio.file.Paths.get(projectRoot, "文档中心", "已生成的审查报告").toString();

            // 创建目录
            java.nio.file.Path docCenterDir = java.nio.file.Paths.get(docCenterPath);
            if (!java.nio.file.Files.exists(docCenterDir)) {
                java.nio.file.Files.createDirectories(docCenterDir);
                log.info("✓ 创建文档中心目录: {}", docCenterPath);
            }

            // 保存文件到本地（使用中文文件名，带时间戳避免冲突）
            java.nio.file.Path outputPath = docCenterDir.resolve(outputFilename);
            int retryCount = 0;
            int maxRetries = 3;
            
            while (retryCount < maxRetries) {
                try {
                    java.nio.file.Files.write(outputPath, annotatedDocBytes);
                    log.info("✓ 文档已保存到本地: {}", outputPath);
                    break;
                } catch (java.nio.file.FileSystemException e) {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        log.warn("文件保存失败（尝试 {}/{}），等待后重试: {}", retryCount, maxRetries, e.getMessage());
                        try {
                            Thread.sleep(500); // 等待500ms
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        // 添加随机后缀避免冲突
                        outputFilename = baseName + "_一键审查_" + stance + "_" + timestamp + "_" + retryCount + ".docx";
                        outputPath = docCenterDir.resolve(outputFilename);
                    } else {
                        log.error("文件保存失败，已达到最大重试次数", e);
                        throw e;
                    }
                }
            }

            // 【新增】同时保存到MinIO
            String minioUrl = null;
            try {
                if (minioFileService.isEnabled()) {
                    String minioObjectName = minioFileService.generateReportObjectName(
                        filename, "一键审查", stance);
                    
                    // 【诊断】打印上传前信息
                    log.info("【诊断】准备上传到MinIO: objectName={}, size={}字节", 
                            minioObjectName, annotatedDocBytes.length);
                    
                    // 【诊断】验证上传前文档是否包含批注
                    try {
                        boolean hasCommentsBefore = validateDocHasComments(annotatedDocBytes);
                        log.info("【诊断】上传前文档包含批注: {}", hasCommentsBefore);
                        if (!hasCommentsBefore) {
                            log.error("⚠️ 【严重警告】准备上传的文档不包含批注！这不应该发生！");
                        }
                    } catch (Exception e) {
                        log.warn("【诊断】无法验证上传前文档批注: {}", e.getMessage());
                    }
                    
                    // 【诊断】计算上传前的MD5
                    java.security.MessageDigest md5Before = java.security.MessageDigest.getInstance("MD5");
                    byte[] hashBefore = md5Before.digest(annotatedDocBytes);
                    String md5BeforeStr = bytesToHex(hashBefore);
                    log.info("【诊断】上传前文档MD5: {}", md5BeforeStr);
                    
                    minioUrl = minioFileService.uploadBytes(
                        annotatedDocBytes, 
                        minioObjectName,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    );
                    
                    // 【诊断】下载MinIO中的文件并验证
                    try {
                        java.util.Map<String, Object> minioFileInfo = minioFileService.getFileInfo(minioObjectName);
                        Object minioSize = minioFileInfo.get("size");
                        log.info("【诊断】MinIO文件验证: size={}字节, 本地={}字节, 匹配={}", 
                                minioSize, annotatedDocBytes.length, 
                                minioSize.equals((long)annotatedDocBytes.length));
                        
                        // 【诊断】下载MinIO文件并计算MD5
                        byte[] downloadedBytes = minioFileService.downloadFile(minioObjectName);
                        java.security.MessageDigest md5After = java.security.MessageDigest.getInstance("MD5");
                        byte[] hashAfter = md5After.digest(downloadedBytes);
                        String md5AfterStr = bytesToHex(hashAfter);
                        log.info("【诊断】下载后文档MD5: {}", md5AfterStr);
                        log.info("【诊断】MD5匹配: {}", md5BeforeStr.equals(md5AfterStr));
                        
                        // 【诊断】验证下载后文档是否包含批注
                        try {
                            boolean hasCommentsAfter = validateDocHasComments(downloadedBytes);
                            log.info("【诊断】下载后文档包含批注: {}", hasCommentsAfter);
                            if (!hasCommentsAfter) {
                                log.error("⚠️ 【严重警告】MinIO中的文档不包含批注！文档可能被错误覆盖或上传失败！");
                            }
                        } catch (Exception e) {
                            log.warn("【诊断】无法验证下载后文档批注: {}", e.getMessage());
                        }
                        
                        if (!md5BeforeStr.equals(md5AfterStr)) {
                            log.error("【诊断】⚠️ 文档内容不一致！上传前后MD5不匹配");
                            log.error("【诊断】上传前: {}, 下载后: {}", md5BeforeStr, md5AfterStr);
                        }
                    } catch (Exception verifyEx) {
                        log.warn("【诊断】MinIO文件验证失败: {}", verifyEx.getMessage(), verifyEx);
                    }
                    
                    log.info("✓ 文档已保存到MinIO: {}", minioUrl);
                } else {
                    log.info("MinIO服务未启用，跳过MinIO存储");
                }
            } catch (Exception e) {
                log.warn("MinIO存储失败，但本地保存成功: {}", e.getMessage());
                // MinIO失败不影响整体流程
            }

            // 步骤7：返回结果
            log.info("步骤6/6: 返回审查结果...");
            long endTime = System.currentTimeMillis();

            // 构建JSON响应，包含MinIO URL和成功状态
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "合同审查完成");
            response.put("filename", outputFilename);
            response.put("fileSize", annotatedDocBytes.length);
            response.put("issuesCount", issues.size());
            response.put("processingTime", endTime - startTime);
            if (originalUrl != null) {
                response.put("originalUrl", originalUrl);
            }
            
            if (minioUrl != null) {
                response.put("minioUrl", minioUrl);
                response.put("savedToMinio", true);
            } else {
                response.put("savedToMinio", false);
            }

            log.info("✓ 一键审查完成！总耗时: {}ms, 检出 {} 个问题",
                endTime - startTime, issues.size());
            log.info("✓ 输出文件: {}", outputFilename);
            if (minioUrl != null) {
                log.info("✓ MinIO URL: {}", minioUrl);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);

        } catch (IOException e) {
            log.error("文件操作失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "文件操作失败: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("一键审查失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "一键审查失败: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 辅助方法：字节数组转十六进制
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 验证DOCX文档是否包含批注文件
     */
    private static boolean validateDocHasComments(byte[] docBytes) {
        try {
            java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(docBytes));
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/comments.xml".equals(entry.getName())) {
                    zip.close();
                    return true;
                }
                zip.closeEntry();
            }
            zip.close();
            return false;
        } catch (Exception e) {
            return false;
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
