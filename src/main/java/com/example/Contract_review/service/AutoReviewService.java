package com.example.Contract_review.service;

import com.example.Contract_review.config.AIServiceConfig;
import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.util.DocxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 自动化审查流程服务
 *
 * 整合解析、AI审查、批注等功能，提供一站式自动化审查
 */
@Service
public class AutoReviewService {

    private static final Logger logger = LoggerFactory.getLogger(AutoReviewService.class);

    @Autowired
    private ContractParseService contractParseService;

    @Autowired
    private ContractAnnotateService contractAnnotateService;

    @Autowired
    private AIServiceConfig aiServiceConfig;

    @Autowired(required = false)
    private AIReviewService claudeReviewService;

    @Autowired(required = false)
    private AIReviewService openaiReviewService;

    @Autowired(required = false)
    private AIReviewService mockReviewService;

    @Autowired(required = false)
    private AIReviewService chatgptWebReviewService;

    @Autowired
    private DocxUtils docxUtils;

    /**
     * 执行完整的自动化审查流程
     *
     * @param file 合同文件
     * @param contractType 合同类型
     * @param aiProvider AI提供商 (claude/openai/auto)
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的合同文件字节数组
     * @throws Exception 处理失败
     */
    public byte[] autoReview(MultipartFile file, String contractType,
                             String aiProvider, boolean cleanupAnchors) throws Exception {

        logger.info("开始自动化审查流程: filename={}, contractType={}, aiProvider={}",
                   file.getOriginalFilename(), contractType, aiProvider);

        // 步骤1: 解析合同
        logger.info("[步骤1/4] 解析合同文件...");
        ParseResult parseResult = parseContract(file, contractType);
        logger.info("[步骤1/4] 解析完成: 提取{}个条款", parseResult.getClauses().size());

        // 步骤2: AI审查
        logger.info("[步骤2/4] 调用AI进行审查...");
        String reviewJson = performAIReview(parseResult, contractType, aiProvider);
        logger.info("[步骤2/4] AI审查完成");

        // 步骤3: 批注合同
        logger.info("[步骤3/4] 将审查结果批注到合同...");
        byte[] annotatedDocument = annotateContract(file, reviewJson, cleanupAnchors);
        logger.info("[步骤3/4] 批注完成");

        logger.info("[步骤4/4] 自动化审查流程完成");

        return annotatedDocument;
    }

    /**
     * 执行自动化审查并返回详细信息
     *
     * @param file 合同文件
     * @param contractType 合同类型
     * @param aiProvider AI提供商
     * @param cleanupAnchors 是否清理锚点
     * @return 包含解析结果、审查结果和批注文档的详细信息
     * @throws Exception 处理失败
     */
    public Map<String, Object> autoReviewWithDetails(MultipartFile file, String contractType,
                                                      String aiProvider, boolean cleanupAnchors) throws Exception {

        logger.info("开始自动化审查流程(详细模式): filename={}, contractType={}, aiProvider={}",
                   file.getOriginalFilename(), contractType, aiProvider);

        Map<String, Object> result = new HashMap<>();

        // 步骤1: 解析合同
        logger.info("[步骤1/4] 解析合同文件...");
        ParseResult parseResult = parseContract(file, contractType);
        result.put("parseResult", parseResult);
        result.put("clauseCount", parseResult.getClauses().size());
        logger.info("[步骤1/4] 解析完成: 提取{}个条款", parseResult.getClauses().size());

        // 步骤2: AI审查
        logger.info("[步骤2/4] 调用AI进行审查...");
        String reviewJson = performAIReview(parseResult, contractType, aiProvider);
        result.put("reviewJson", reviewJson);
        result.put("aiProvider", getActualProvider(aiProvider));
        logger.info("[步骤2/4] AI审查完成");

        // 步骤3: 批注合同
        logger.info("[步骤3/4] 将审查结果批注到合同...");
        byte[] annotatedDocument = annotateContract(file, reviewJson, cleanupAnchors);
        result.put("annotatedDocument", annotatedDocument);
        result.put("documentSize", annotatedDocument.length);
        logger.info("[步骤3/4] 批注完成");

        logger.info("[步骤4/4] 自动化审查流程完成");

        result.put("status", "success");
        result.put("message", "自动化审查流程完成");
        result.put("filename", file.getOriginalFilename());

        return result;
    }

    /**
     * 解析合同并确保锚点被插入到文档中
     */
    private ParseResult parseContract(MultipartFile file, String contractType) throws Exception {
        // 解析合同并生成锚点
        ParseResult parseResult = contractParseService.parseContract(file, "generate");

        // 确保锚点被插入到实际文档中（这对后续批注定位很重要）
        logger.info("解析结果包含 {} 个条款，生成了 {} 个锚点",
                   parseResult.getClauses().size(),
                   parseResult.getClauses().stream().mapToInt(c -> c.getAnchorId() != null ? 1 : 0).sum());

        return parseResult;
    }

    /**
     * 执行AI审查
     */
    private String performAIReview(ParseResult parseResult, String contractType, String aiProvider) throws Exception {
        AIReviewService reviewService = selectAIService(aiProvider);

        if (reviewService == null || !reviewService.isAvailable()) {
            throw new IllegalStateException("AI服务未配置或不可用，请配置Claude或OpenAI API密钥");
        }

        logger.info("使用AI服务: {}", reviewService.getProviderName());

        return reviewService.reviewContract(parseResult, contractType);
    }

    /**
     * 批注合同
     */
    private byte[] annotateContract(MultipartFile file, String reviewJson, boolean cleanupAnchors) throws Exception {
        // 重要：传递文件的副本，确保锚点信息一致
        // 由于我们在解析阶段可能没有修改原文件，这里需要使用带锚点的版本

        // 先重新解析一遍，确保获得带锚点的文档信息
        ParseResult parseResultForAnnotation = contractParseService.parseContract(file, "generate");

        logger.info("为批注重新解析文档: 条款数={}, 锚点数={}",
                   parseResultForAnnotation.getClauses().size(),
                   parseResultForAnnotation.getClauses().stream().mapToInt(c -> c.getAnchorId() != null ? 1 : 0).sum());

        return contractAnnotateService.annotateContract(
            file,
            reviewJson,
            "textFallback",  // 使用文本回退策略，提高定位成功率
            cleanupAnchors
        );
    }

    /**
     * 选择AI服务
     */
    private AIReviewService selectAIService(String aiProvider) {
        if ("claude".equalsIgnoreCase(aiProvider)) {
            return claudeReviewService;
        } else if ("openai".equalsIgnoreCase(aiProvider)) {
            return openaiReviewService;
        } else if ("mock".equalsIgnoreCase(aiProvider)) {
            return mockReviewService;
        } else if ("chatgpt-web".equalsIgnoreCase(aiProvider)) {
            return chatgptWebReviewService;
        } else if ("auto".equalsIgnoreCase(aiProvider)) {
            // 自动选择：优先使用已配置的服务
            if (claudeReviewService != null && claudeReviewService.isAvailable()) {
                return claudeReviewService;
            } else if (openaiReviewService != null && openaiReviewService.isAvailable()) {
                return openaiReviewService;
            } else if (chatgptWebReviewService != null && chatgptWebReviewService.isAvailable()) {
                return chatgptWebReviewService;
            } else if (mockReviewService != null && mockReviewService.isAvailable()) {
                return mockReviewService;
            }
        } else {
            // 使用配置中的默认提供商
            String defaultProvider = aiServiceConfig.getProvider();
            if ("claude".equalsIgnoreCase(defaultProvider) && claudeReviewService != null) {
                return claudeReviewService;
            } else if ("openai".equalsIgnoreCase(defaultProvider) && openaiReviewService != null) {
                return openaiReviewService;
            } else if ("mock".equalsIgnoreCase(defaultProvider) && mockReviewService != null) {
                return mockReviewService;
            } else if ("chatgpt-web".equalsIgnoreCase(defaultProvider) && chatgptWebReviewService != null) {
                return chatgptWebReviewService;
            }
        }
        return null;
    }

    /**
     * 获取实际使用的AI提供商
     */
    private String getActualProvider(String aiProvider) {
        AIReviewService service = selectAIService(aiProvider);
        return service != null ? service.getProviderName() : "未配置";
    }

    /**
     * 检查AI服务配置状态
     */
    public Map<String, Object> checkAIServiceStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("configuredProvider", aiServiceConfig.getProvider());

        Map<String, Object> claudeStatus = new HashMap<>();
        claudeStatus.put("available", claudeReviewService != null && claudeReviewService.isAvailable());
        claudeStatus.put("configured", aiServiceConfig.getClaude().getApiKey() != null);
        claudeStatus.put("model", aiServiceConfig.getClaude().getModel());
        status.put("claude", claudeStatus);

        Map<String, Object> openaiStatus = new HashMap<>();
        openaiStatus.put("available", openaiReviewService != null && openaiReviewService.isAvailable());
        openaiStatus.put("configured", aiServiceConfig.getOpenai().getApiKey() != null);
        openaiStatus.put("model", aiServiceConfig.getOpenai().getModel());
        status.put("openai", openaiStatus);

        Map<String, Object> mockStatus = new HashMap<>();
        mockStatus.put("available", mockReviewService != null && mockReviewService.isAvailable());
        mockStatus.put("configured", true); // 模拟服务不需要配置
        mockStatus.put("model", "模拟服务 (测试用)");
        status.put("mock", mockStatus);

        Map<String, Object> chatgptWebStatus = new HashMap<>();
        chatgptWebStatus.put("available", chatgptWebReviewService != null && chatgptWebReviewService.isAvailable());
        chatgptWebStatus.put("configured", true); // ChatGPT网页版不需要配置
        chatgptWebStatus.put("model", "ChatGPT 网页版 (https://chatgpt.com/)");
        chatgptWebStatus.put("description", "生成提示复制到ChatGPT网页版");
        status.put("chatgptWeb", chatgptWebStatus);

        boolean anyAvailable = (claudeReviewService != null && claudeReviewService.isAvailable())
                            || (openaiReviewService != null && openaiReviewService.isAvailable())
                            || (mockReviewService != null && mockReviewService.isAvailable())
                            || (chatgptWebReviewService != null && chatgptWebReviewService.isAvailable());
        status.put("autoReviewAvailable", anyAvailable);

        return status;
    }
}