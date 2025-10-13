package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.ContractAnnotateService;
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
    private ChatGPTWebReviewServiceImpl chatgptWebReviewService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 步骤1: 解析合同并生成ChatGPT提示
     */
    @PostMapping("/generate-prompt")
    public ResponseEntity<?> generatePrompt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType) {

        try {
            logger.info("为ChatGPT生成提示: filename={}, contractType={}",
                       file.getOriginalFilename(), contractType);

            // 解析合同
            ParseResult parseResult = contractParseService.parseContract(file, "generate");

            // 生成ChatGPT提示
            String promptResponse = chatgptWebReviewService.reviewContract(parseResult, contractType);
            JsonNode responseJson = objectMapper.readTree(promptResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("filename", file.getOriginalFilename());
            result.put("clauseCount", parseResult.getClauses().size());
            result.put("contractType", contractType);
            result.put("chatgptPrompt", responseJson.get("prompt").asText());
            result.put("instructions", responseJson.get("instructions"));
            result.put("parseResult", parseResult); // 保存解析结果供后续使用

            logger.info("ChatGPT提示生成成功");
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
     */
    @PostMapping("/import-result")
    public ResponseEntity<?> importResult(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatgptResponse") String chatgptResponse,
            @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

        try {
            logger.info("导入ChatGPT审查结果: filename={}", file.getOriginalFilename());

            // 清理ChatGPT响应（移除markdown代码块标记）
            String cleanResponse = cleanChatGPTResponse(chatgptResponse);

            // 验证JSON格式
            JsonNode reviewJson = objectMapper.readTree(cleanResponse);
            if (!reviewJson.has("issues")) {
                throw new IllegalArgumentException("ChatGPT响应缺少必需的'issues'字段");
            }

            // 批注合同
            byte[] annotatedDocument = contractAnnotateService.annotateContract(
                file, cleanResponse, "preferAnchor", cleanupAnchors);

            // 生成文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            String annotatedFilename = originalFilename.replace(fileExtension,
                                                              "_ChatGPT审查" + fileExtension);

            logger.info("ChatGPT审查结果导入成功，生成批注文档");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                       "attachment; filename=\"" + annotatedFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(annotatedDocument));

        } catch (Exception e) {
            logger.error("导入ChatGPT审查结果失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", "导入失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 一键流程：生成提示 + 等待用户操作 + 导入结果
     */
    @PostMapping("/workflow")
    public ResponseEntity<?> workflow(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType,
            @RequestParam(value = "step", defaultValue = "1") String step,
            @RequestParam(value = "chatgptResponse", required = false) String chatgptResponse,
            @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

        try {
            if ("1".equals(step)) {
                // 步骤1: 生成提示
                return generatePrompt(file, contractType);
            } else if ("2".equals(step)) {
                // 步骤2: 导入结果
                if (chatgptResponse == null || chatgptResponse.trim().isEmpty()) {
                    Map<String, String> error = new HashMap<>();
                    error.put("success", "false");
                    error.put("error", "请提供ChatGPT的审查结果");
                    return ResponseEntity.badRequest().body(error);
                }
                return importResult(file, chatgptResponse, cleanupAnchors);
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
     * 获取ChatGPT集成状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", true);
        status.put("providerName", "ChatGPT 网页版");
        status.put("url", "https://chatgpt.com/");
        status.put("description", "使用ChatGPT网页版进行合同审查");
        status.put("workflow", new String[]{
            "1. 上传合同文件生成ChatGPT提示",
            "2. 复制提示到 https://chatgpt.com/",
            "3. 获取ChatGPT审查结果",
            "4. 导入结果生成批注文档"
        });

        return ResponseEntity.ok(status);
    }
}