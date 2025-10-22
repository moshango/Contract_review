package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.ContractAnnotateService;
import com.example.Contract_review.service.XmlContractAnnotateService;
import com.example.Contract_review.service.ParseResultCache;
import com.example.Contract_review.service.impl.ChatGPTWebReviewServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * ChatGPT 网页版集成控制器
 */
@RestController
@RequestMapping("/chatgpt")
public class ChatGPTIntegrationController {

    private static final Logger logger = LoggerFactory.getLogger(ChatGPTIntegrationController.class);

    @Autowired
    private ContractParseService contractParseService;

    @Autowired
    private ContractAnnotateService contractAnnotateService;

    @Autowired
    private XmlContractAnnotateService xmlContractAnnotateService;

    @Autowired
    private ChatGPTWebReviewServiceImpl chatgptWebReviewService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ParseResultCache parseResultCache;

    /**
     * 步骤1: 解析合同并生成ChatGPT提示
     *
     * 此步骤演示了完整的 Parse 阶段功能，自动生成锚点用于精确批注
     *
     * 【修复】现在返回带锚点的文档，确保 Parse 和 Annotate 使用同一个文档
     */
    @PostMapping("/generate-prompt")
    public ResponseEntity<?> generatePrompt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType,
            @RequestParam(value = "anchors", defaultValue = "generate") String anchors) {

        try {
            logger.info("为ChatGPT生成提示: filename={}, contractType={}, anchors={}",
                       file.getOriginalFilename(), contractType, anchors);

            // 【修复】使用 parseContractWithDocument 获取带锚点的文档
            // 这样确保返回的文档包含了生成的锚点
            ContractParseService.ParseResultWithDocument resultWithDoc = null;
            ParseResult parseResult;
            byte[] documentWithAnchorsBytes = null;

            if (file.getOriginalFilename() != null &&
                file.getOriginalFilename().toLowerCase().endsWith(".docx")) {
                // DOCX 文件：可以获取带锚点的文档
                resultWithDoc = contractParseService.parseContractWithDocument(file, anchors);
                parseResult = resultWithDoc.getParseResult();
                documentWithAnchorsBytes = resultWithDoc.getDocumentBytes();
                logger.info("已生成带锚点的 DOCX 文档: size={} 字节",
                    documentWithAnchorsBytes != null ? documentWithAnchorsBytes.length : 0);
            } else {
                // DOC 文件或其他格式：使用原有方法
                parseResult = contractParseService.parseContract(file, anchors);
            }

            // 生成ChatGPT提示
            String promptResponse = chatgptWebReviewService.reviewContract(parseResult, contractType);
            JsonNode responseJson = objectMapper.readTree(promptResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("filename", file.getOriginalFilename());
            result.put("clauseCount", parseResult.getClauses().size());
            result.put("contractType", contractType);
            result.put("anchorsEnabled", "generate".equals(anchors) || "regenerate".equals(anchors));
            result.put("chatgptPrompt", responseJson.get("prompt").asText());
            result.put("instructions", responseJson.get("instructions"));
            result.put("parseResult", parseResult); // 保存解析结果供后续使用

            // 【关键修复】返回带锚点的文档（Base64 编码）
            if (documentWithAnchorsBytes != null && documentWithAnchorsBytes.length > 0) {
                String documentBase64 = java.util.Base64.getEncoder().encodeToString(documentWithAnchorsBytes);
                result.put("documentWithAnchorsBase64", documentBase64);
                result.put("documentWithAnchorsInfo",
                    "本文档包含生成的锚点书签，用于精确批注定位。在步骤2中使用此文档以获得最佳效果。");
                result.put("getDocumentUrl",
                    "提示：也可以调用 POST /chatgpt/get-document-with-anchors 直接下载带锚点的文档");

                // 【完整修复】将 Parse 结果存储到缓存，并返回 parseResultId
                // 这样 /import-result-xml 端点可以通过 parseResultId 获取相同的文档
                String parseResultId = parseResultCache.store(
                    parseResult, documentWithAnchorsBytes, file.getOriginalFilename());

                result.put("parseResultId", parseResultId);
                result.put("parseResultIdUsage",
                    "在步骤2中调用 /chatgpt/import-result-xml 时，建议传递 parseResultId 参数以确保使用同一个带锚点的文档");
            }

            result.put("workflowStep", "1-prompt-generation");
            result.put("nextStep", "/chatgpt/import-result-xml (步骤2：使用带锚点的文档导入ChatGPT审查结果)");

            logger.info("ChatGPT提示生成成功，已启用锚点精确定位");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("生成ChatGPT提示失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", "生成提示失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 步骤2: 处理ChatGPT返回的审查结果并生成批注文档
     *
     * 此步骤演示了完整的 Annotate 阶段功能，使用 XML 方式精确定位批注位置
     * 支持三种匹配模式：EXACT（精确）、CONTAINS（包含）、REGEX（正则）
     *
     * ✨ 已升级为使用XML方式（推荐）
     * 【修复】现在支持 parseResultId 参数以使用缓存的带锚点文档
     *
     * @deprecated 原有的POI方式已废弃，建议使用 /chatgpt/import-result-xml 端点或本端点并传递 parseResultId
     */
    @PostMapping("/import-result")
    public ResponseEntity<?> importResult(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "parseResultId", required = false) String parseResultId,
            @RequestParam("chatgptResponse") String chatgptResponse,
            @RequestParam(value = "anchorStrategy", defaultValue = "preferAnchor") String anchorStrategy,
            @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

        try {
            logger.info("🔍 [/import-result] 请求参数: parseResultId={}, hasFile={}, anchorStrategy={}, cleanupAnchors={}",
                       parseResultId != null ? "✓ " + parseResultId : "✗ NULL",
                       file != null ? "✓ " + file.getOriginalFilename() : "✗ NULL",
                       anchorStrategy, cleanupAnchors);

            // 【关键修复】优先使用缓存的带锚点文档
            byte[] documentToAnnotate = null;
            String sourceInfo = "";
            String originalFilename = "contract.docx";

            if (parseResultId != null && !parseResultId.trim().isEmpty()) {
                logger.info("🔍 [缓存检索] 尝试从缓存中检索parseResultId: {}", parseResultId);
                // 优先方案：使用缓存的带锚点文档
                ParseResultCache.CachedParseResult cached = parseResultCache.retrieve(parseResultId);
                if (cached != null && cached.documentWithAnchorsBytes != null) {
                    documentToAnnotate = cached.documentWithAnchorsBytes;
                    sourceInfo = "缓存的带锚点文档";
                    originalFilename = cached.sourceFilename;
                    logger.info("✅ [缓存命中] 成功使用缓存的带锚点文档: parseResultId={}, 大小={} 字节, 文件名={}",
                               parseResultId, documentToAnnotate.length, originalFilename);
                } else {
                    logger.warn("⚠️ [缓存失败] 缓存不存在或已过期: parseResultId={}", parseResultId);
                    if (file == null) {
                        throw new IllegalArgumentException(
                            "parseResultId 已过期且没有提供 file 参数。请重新调用 /generate-prompt 以获取新的 parseResultId");
                    }
                }
            } else {
                logger.warn("⚠️ [参数缺失] parseResultId 为空，将尝试使用 file 参数");
            }

            // 备选方案：使用用户上传的文件
            if (documentToAnnotate == null && file != null) {
                documentToAnnotate = file.getBytes();
                sourceInfo = "用户上传的文件（不包含锚点）";
                originalFilename = file.getOriginalFilename();
                logger.warn("⚠️ [降级方案] 使用用户上传的文件进行批注，可能不包含锚点。批注定位精度可能降低");
                logger.warn("   建议：请使用 parseResultId 参数以获得最佳效果。工作流程应为：");
                logger.warn("   1. 调用 /chatgpt/generate-prompt 获取 parseResultId");
                logger.warn("   2. 在 ChatGPT 进行审查");
                logger.warn("   3. 调用 /chatgpt/import-result?parseResultId=YOUR_ID 传入审查结果");
            }

            if (documentToAnnotate == null) {
                throw new IllegalArgumentException(
                    "❌ 无法获取文档内容: 既没有有效的 parseResultId，也没有提供 file 参数。" +
                    "请先调用 /chatgpt/generate-prompt 端点以获取 parseResultId，然后在此端点传递该ID");
            }

            // 清理ChatGPT响应（移除markdown代码块标记）
            String cleanResponse = cleanChatGPTResponse(chatgptResponse);

            // 验证JSON格式
            JsonNode reviewJson = objectMapper.readTree(cleanResponse);
            if (!reviewJson.has("issues")) {
                throw new IllegalArgumentException("ChatGPT响应缺少必需的'issues'字段");
            }

            // 统计审查问题
            JsonNode issuesNode = reviewJson.get("issues");
            int totalIssues = issuesNode.size();
            int issuesWithTargetText = 0;
            int issuesWithPreciseMatch = 0;

            for (JsonNode issue : issuesNode) {
                if (issue.has("targetText") && !issue.get("targetText").asText().isEmpty()) {
                    issuesWithTargetText++;
                    // 检查是否指定了精确匹配模式
                    String matchPattern = issue.has("matchPattern") ?
                        issue.get("matchPattern").asText() : "EXACT";
                    if ("EXACT".equalsIgnoreCase(matchPattern) || "CONTAINS".equalsIgnoreCase(matchPattern)) {
                        issuesWithPreciseMatch++;
                    }
                }
            }

            // ✨ 使用XML方式批注合同（精确文字级批注）
            // 创建一个简单的 MultipartFile 包装器来传递 byte[] 数据
            MultipartFile mockFile = new SimpleMultipartFileWrapper(
                originalFilename, documentToAnnotate);

            byte[] annotatedDocument = xmlContractAnnotateService.annotateContractWithXml(
                mockFile, cleanResponse, anchorStrategy, cleanupAnchors);

            // 生成文件名
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            String annotatedFilename = originalFilename.replace(fileExtension,
                                                              "_ChatGPT审查" + fileExtension);

            logger.info("📄 [批注完成] ChatGPT审查结果导入成功: 文档来源={}, 总问题{}个，其中{}个提供了精确文字定位，{}个使用精确匹配模式",
                       sourceInfo, totalIssues, issuesWithTargetText, issuesWithPreciseMatch);

            // 返回批注后的文档
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                       "attachment; filename=\"" + annotatedFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(annotatedDocument));

        } catch (Exception e) {
            logger.error("❌ [导入失败] ChatGPT审查结果导入失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", "导入失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 新增：XML专用导入端点（推荐使用）
     *
     * 使用纯XML操作方式进行批注，提供最高的批注精度和性能
     * 支持精确文字级批注，targetText必须从原文精确复制
     *
     * 【完整修复】支持 parseResultId 参数以确保使用缓存的带锚点文档
     * - 优先方案：传递 parseResultId 参数，系统将使用缓存的带锚点文档
     * - 备选方案：传递 file 参数，系统使用上传的文件
     */
    @PostMapping("/import-result-xml")
    public ResponseEntity<?> importResultXml(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "parseResultId", required = false) String parseResultId,
            @RequestParam("chatgptResponse") String chatgptResponse,
            @RequestParam(value = "anchorStrategy", defaultValue = "preferAnchor") String anchorStrategy,
            @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

        try {
            logger.info("导入ChatGPT审查结果（XML专用端点）: parseResultId={}, hasFile={}, strategy={}, cleanup={}",
                       parseResultId, file != null, anchorStrategy, cleanupAnchors);

            // 【修复关键】优先使用缓存的带锚点文档
            byte[] documentToAnnotate = null;
            String sourceInfo = "";

            if (parseResultId != null && !parseResultId.isEmpty()) {
                // 优先方案：使用缓存的带锚点文档
                ParseResultCache.CachedParseResult cached = parseResultCache.retrieve(parseResultId);
                if (cached != null && cached.documentWithAnchorsBytes != null) {
                    documentToAnnotate = cached.documentWithAnchorsBytes;
                    sourceInfo = "缓存的带锚点文档";
                    logger.info("✅ 使用缓存的带锚点文档: parseResultId={}, 大小={} 字节, 条款数={}",
                               parseResultId, documentToAnnotate.length, cached.parseResult.getClauses().size());
                } else {
                    logger.warn("⚠️ 缓存不存在或已过期: parseResultId={}", parseResultId);
                    if (file == null) {
                        throw new IllegalArgumentException(
                            "parseResultId 已过期且没有提供 file 参数。请重新调用 /generate-prompt 以获取新的 parseResultId");
                    }
                }
            }

            // 备选方案：使用用户上传的文件
            if (documentToAnnotate == null && file != null) {
                documentToAnnotate = file.getBytes();
                sourceInfo = "用户上传的文件";
                logger.warn("⚠️ 使用用户上传的文件，可能不包含锚点。建议使用 parseResultId 参数以获得最佳效果");
            }

            if (documentToAnnotate == null) {
                throw new IllegalArgumentException(
                    "无法获取文档内容: 既没有有效的 parseResultId，也没有提供 file 参数。" +
                    "请使用 parseResultId 参数或上传 file 参数");
            }

            // 清理ChatGPT响应（移除markdown代码块标记）
            String cleanResponse = cleanChatGPTResponse(chatgptResponse);

            // 验证JSON格式
            if (!xmlContractAnnotateService.validateReviewJson(cleanResponse)) {
                throw new IllegalArgumentException("ChatGPT响应JSON格式无效");
            }

            // 获取问题数量统计
            int totalIssues = xmlContractAnnotateService.getIssueCount(cleanResponse);

            // ✨ 使用XML方式批注合同（精确文字级批注）
            // 创建一个简单的 MultipartFile 包装器来传递 byte[] 数据
            String mockFilename = (file != null) ? file.getOriginalFilename() : "contract.docx";
            MultipartFile mockFile = new SimpleMultipartFileWrapper(
                mockFilename, documentToAnnotate);

            byte[] annotatedDocument = xmlContractAnnotateService.annotateContractWithXml(
                mockFile, cleanResponse, anchorStrategy, cleanupAnchors);

            // 生成文件名
            String originalFilename = (file != null) ?
                file.getOriginalFilename() : "contract_annotated.docx";
            String annotatedFilename = xmlContractAnnotateService.buildOutputFilename(originalFilename);

            logger.info("ChatGPT审查结果导入成功（XML专用）: 文档来源={}, 总问题{}个",
                       sourceInfo, totalIssues);

            // 返回批注后的文档
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                       "attachment; filename=\"" + annotatedFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(annotatedDocument));

        } catch (Exception e) {
            logger.error("导入ChatGPT审查结果（XML专用）失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", "导入失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 新增：直接获取带锚点的文档
     *
     * 【工作流程修复】提供便捷方式获取 Parse 阶段生成的带锚点文档
     * 用于 Annotate 阶段，确保使用相同的文档，批注定位准确
     *
     * @param file 合同文件（会被解析并插入锚点）
     * @return 带锚点的 DOCX 文档文件
     */
    @PostMapping("/get-document-with-anchors")
    public ResponseEntity<?> getDocumentWithAnchors(
            @RequestParam("file") MultipartFile file) {

        try {
            logger.info("获取带锚点的文档: filename={}", file.getOriginalFilename());

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
                return ResponseEntity.badRequest()
                    .body("错误：仅支持 .docx 格式文件");
            }

            // 【关键】解析并生成带锚点的文档
            ContractParseService.ParseResultWithDocument resultWithDoc =
                contractParseService.parseContractWithDocument(file, "generate");

            byte[] documentBytes = resultWithDoc.getDocumentBytes();

            if (documentBytes == null || documentBytes.length == 0) {
                return ResponseEntity.badRequest()
                    .body("错误：无法生成带锚点的文档");
            }

            // 生成文件名（带时间戳以避免冲突）
            String outputFilename = filename.replace(".docx", "_with_anchors.docx");

            logger.info("已生成带锚点的文档: filename={}, size={} 字节",
                outputFilename, documentBytes.length);

            // 返回文档文件
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + outputFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(documentBytes));

        } catch (Exception e) {
            logger.error("获取带锚点文档失败", e);
            return ResponseEntity.badRequest()
                .body("获取失败: " + e.getMessage());
        }
    }

    /**
     * 一键流程：生成提示 + 等待用户操作 + 导入结果
     *
     * 【修复】现在支持在同一个会话中自动传递 parseResultId
     */
    @PostMapping("/workflow")
    public ResponseEntity<?> workflow(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType,
            @RequestParam(value = "step", defaultValue = "1") String step,
            @RequestParam(value = "chatgptResponse", required = false) String chatgptResponse,
            @RequestParam(value = "parseResultId", required = false) String parseResultId,
            @RequestParam(value = "anchors", defaultValue = "generate") String anchors,
            @RequestParam(value = "anchorStrategy", defaultValue = "preferAnchor") String anchorStrategy,
            @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

        try {
            if ("1".equals(step)) {
                // 步骤1: 生成提示
                return generatePrompt(file, contractType, anchors);
            } else if ("2".equals(step)) {
                // 步骤2: 导入结果
                if (chatgptResponse == null || chatgptResponse.trim().isEmpty()) {
                    Map<String, String> error = new HashMap<>();
                    error.put("success", "false");
                    error.put("error", "请提供ChatGPT的审查结果");
                    return ResponseEntity.badRequest().body(error);
                }
                // 【关键修复】传递 parseResultId 到 importResult
                return importResult(file, parseResultId, chatgptResponse, anchorStrategy, cleanupAnchors);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("success", "false");
                error.put("error", "无效的步骤参数，应为1或2");
                return ResponseEntity.badRequest().body(error);
            }

        } catch (Exception e) {
            logger.error("ChatGPT工作流程失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", "工作流程失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 清理ChatGPT响应文本
     */
    private String cleanChatGPTResponse(String response) {
        if (response == null) return "";

        // 移除markdown代码块标记
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        }
        if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }

        return response.trim();
    }

    /**
     * 获取ChatGPT集成状态和工作流信息
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", true);
        status.put("providerName", "ChatGPT 网页版（人工智能模型集成）");
        status.put("version", "2.0-Enhanced");
        status.put("url", "https://chatgpt.com/");
        status.put("description", "使用ChatGPT网页版进行AI合同审查，支持精确文字级批注");

        // 工作流步骤详解
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("totalSteps", 4);
        workflow.put("currentCapability", "Parse → Review (ChatGPT) → Annotate → Cleanup");

        Map<String, Object> step1 = new HashMap<>();
        step1.put("step", 1);
        step1.put("name", "解析阶段（Parse）");
        step1.put("endpoint", "POST /chatgpt/generate-prompt");
        step1.put("description", "系统自动解析合同，提取条款，生成锚点用于精确定位");
        step1.put("params", new String[]{"file", "contractType", "anchors"});
        step1.put("features", new String[]{
            "自动生成条款ID（c1, c2, c3...）",
            "生成唯一锚点ID（anc-c1-xxxx）",
            "提取条款关键短语",
            "返回结构化条款JSON"
        });

        Map<String, Object> step2 = new HashMap<>();
        step2.put("step", 2);
        step2.put("name", "审查阶段（Review by ChatGPT）");
        step2.put("description", "用户复制prompt到ChatGPT.com进行审查，ChatGPT返回包含targetText的审查结果");
        step2.put("userAction", "1. 复制prompt到https://chatgpt.com/ 2. 等待ChatGPT审查 3. 复制JSON结果");
        step2.put("expectedOutput", new String[]{
            "issues[].clauseId - 条款ID",
            "issues[].targetText - 要批注的精确文字",
            "issues[].matchPattern - EXACT|CONTAINS|REGEX",
            "issues[].severity - HIGH|MEDIUM|LOW",
            "issues[].suggestion - 具体建议"
        });
        step2.put("targetTextImportance", "✓ 这是本系统的核心特性，必须填写准确");

        Map<String, Object> step3 = new HashMap<>();
        step3.put("step", 3);
        step3.put("name", "批注阶段（Annotate）");
        step3.put("endpoint", "POST /chatgpt/import-result");
        step3.put("description", "系统解析审查结果，使用targetText精确定位，在Word中插入批注");
        step3.put("params", new String[]{"file", "chatgptResponse", "anchorStrategy", "cleanupAnchors"});
        step3.put("anchorStrategies", new String[]{
            "preferAnchor - 优先使用anchorId，次选clauseId",
            "anchorOnly - 仅使用anchorId（最准确）",
            "textFallback - 使用targetText文本匹配"
        });
        step3.put("output", "带有AI审查批注的Word文档（.docx）");

        Map<String, Object> step4 = new HashMap<>();
        step4.put("step", 4);
        step4.put("name", "清理阶段（Cleanup）");
        step4.put("description", "可选：清理临时锚点标记（cleanupAnchors=true时自动执行）");
        step4.put("note", "如需后续增量审查，建议保留锚点（cleanupAnchors=false）");

        workflow.put("step1-parse", step1);
        workflow.put("step2-review", step2);
        workflow.put("step3-annotate", step3);
        workflow.put("step4-cleanup", step4);

        status.put("workflow", workflow);

        // 关键特性
        Map<String, Object> features = new HashMap<>();
        features.put("preciseAnnotation", new String[]{
            "精确文字级批注 - 不是段落级，是具体文字",
            "三种匹配模式 - EXACT(推荐), CONTAINS, REGEX",
            "自动锚点定位 - 支持锚点、条款ID、文字匹配",
            "多条语言支持 - 中英文混合支持"
        });
        features.put("workflowIntegration", new String[]{
            "端到端集成 - Parse + ChatGPT + Annotate + Cleanup",
            "精确定位 - 利用targetText实现精确批注",
            "灵活配置 - 支持多种锚点和匹配策略",
            "错误恢复 - 自动降级处理无法匹配的情况"
        });

        status.put("features", features);

        // 使用建议
        Map<String, String> recommendations = new HashMap<>();
        recommendations.put("prompt优化", "prompt中已包含详细指导，强调targetText的重要性");
        recommendations.put("ChatGPT设置", "建议使用GPT-4以获得更精确的批注定位");
        recommendations.put("targetText填写", "必须从原文精确复制，不能改写（EXACT模式最准确）");
        recommendations.put("批注策略", "优先使用 anchorStrategy=preferAnchor 以获得最佳定位准确度");
        recommendations.put("后续处理", "建议保留锚点以支持后续增量审查");

        status.put("recommendations", recommendations);

        return ResponseEntity.ok(status);
    }

    /**
     * 简单的 MultipartFile 包装器
     * 用于将 byte[] 包装成 MultipartFile 接口
     */
    private static class SimpleMultipartFileWrapper implements MultipartFile {
        private final String filename;
        private final byte[] content;

        public SimpleMultipartFileWrapper(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content != null ? content.length : 0;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public ByteArrayInputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content != null ? content : new byte[0]);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}