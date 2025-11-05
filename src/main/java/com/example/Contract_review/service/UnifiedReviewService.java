package com.example.Contract_review.service;

import com.example.Contract_review.model.*;
import com.example.Contract_review.util.PromptGeneratorNew;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 统一审查服务
 *
 * 提供统一的审查工作流程，支持多种审查模式：
 * - RULES: 仅规则审查，返回Prompt供用户使用
 * - AI: 调用AI进行审查
 * - FULL: 完整流程（规则审查 + AI审查 + 批注导入）
 *
 * 通过这个服务消除不同审查流程之间的重复代码
 */
@Service
public class UnifiedReviewService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedReviewService.class);

    @Autowired
    private ContractParseService contractParseService;

    @Autowired
    private ReviewRulesService reviewRulesService;

    @Autowired
    private ParseResultCache parseResultCache;

    @Autowired
    private ReviewStanceService reviewStanceService;

    @Autowired
    private QwenRuleReviewService qwenRuleReviewService;

    @Autowired
    private XmlContractAnnotateService xmlContractAnnotateService;

    @Autowired
    private MinioFileService minioFileService;

    @Autowired
    private ObjectMapper objectMapper;

    // @Autowired
    // private ChatGPTIntegrationService chatGPTIntegrationService;  // 暂不集成，未来扩展

    /**
     * 执行统一的审查流程
     *
     * @param file 合同文件
     * @param contractType 合同类型
     * @param party 审查立场（A/B/null）
     * @param reviewMode 审查模式（RULES/AI/FULL）
     * @param aiProvider AI提供商（qwen/chatgpt/claude）
     * @return 统一的审查结果
     */
    public UnifiedReviewResult performReview(
            MultipartFile file,
            String contractType,
            String party,
            ReviewMode reviewMode,
            String aiProvider) {

        long startTime = System.currentTimeMillis();
        String filename = file.getOriginalFilename();

        logger.info("=== 开始统一审查流程 ===");
        logger.info("文件: {}, 类型: {}, 立场: {}, 模式: {}",
                   filename, contractType, party != null ? party : "中立", reviewMode);

        UnifiedReviewResult result = UnifiedReviewResult.builder()
                .success(false)
                .reviewMode(reviewMode.getValue())
                .build();

        try {
            // 【步骤1】设置审查立场
            if (party != null && !party.trim().isEmpty()) {
                reviewStanceService.setStanceByParty(party);
                logger.info("✓ 已设置审查立场: {}", party);
                result.setUserStance(party);
            }

            // 【步骤2】解析合同（生成带锚点文档）
            logger.info("步骤1: 解析合同并生成带锚点文档...");
            long parseStartTime = System.currentTimeMillis();

            ContractParseService.ParseResultWithDocument parseResultWithDoc =
                    contractParseService.parseContractWithDocument(file, "generate");
            ParseResult parseResult = parseResultWithDoc.getParseResult();
            List<Clause> clauses = parseResult.getClauses();
            byte[] anchoredDocumentBytes = parseResultWithDoc.getDocumentBytes();

            long parseTime = System.currentTimeMillis() - parseStartTime;
            logger.info("✓ 合同解析完成: {} 个条款, 耗时 {}ms", clauses.size(), parseTime);

            if (anchoredDocumentBytes != null && anchoredDocumentBytes.length > 0) {
                String cacheFilename = parseResultWithDoc.getDocumentFilename() != null ?
                        parseResultWithDoc.getDocumentFilename() : filename;
                String parseResultId = parseResultCache.store(parseResult, anchoredDocumentBytes, cacheFilename);
                logger.info("✓ 带锚点文档已缓存，parseResultId={}", parseResultId);
            }

            // 【步骤3】加载规则并匹配
            logger.info("步骤2: 加载规则并进行匹配...");
            long matchStartTime = System.currentTimeMillis();

            List<ReviewRule> allRules = reviewRulesService.loadRules();
            List<ReviewRule> applicableRules = reviewRulesService.filterByContractType(contractType);

            // 获取用户立场
            ReviewStance stance = reviewStanceService.getStance();

            // 手动执行规则匹配
            List<RuleMatchResult> matchResults = new java.util.ArrayList<>();
            for (Clause clause : clauses) {
                List<ReviewRule> matchedRules = applicableRules.stream()
                        .filter(rule -> rule.matches(clause.getFullText()))
                        .filter(rule -> stance.isRuleApplicable(rule))
                        .collect(java.util.stream.Collectors.toList());

                if (!matchedRules.isEmpty()) {
                    String highestRisk = calculateHighestRisk(matchedRules);
                    RuleMatchResult mr = RuleMatchResult.builder()
                            .clauseId(clause.getId())
                            .anchorId(clause.getAnchorId())
                            .clauseHeading(clause.getHeading())
                            .clauseText(clause.getFullText())
                            .matchedRules(matchedRules)
                            .matchCount(matchedRules.size())
                            .highestRisk(highestRisk)
                            .build();
                    matchResults.add(mr);
                }
            }

            long matchTime = System.currentTimeMillis() - matchStartTime;
            logger.info("✓ 规则匹配完成: {} 个规则匹配, 耗时 {}ms",
                       matchResults.stream().mapToInt(RuleMatchResult::getMatchCount).sum(),
                       matchTime);

            // 计算统计信息
            ReviewStatistics statistics = calculateStatistics(
                    clauses,
                    matchResults,
                    allRules,
                    applicableRules,
                    contractType,
                    party,
                    parseTime,
                    matchTime
            );

            result.setStatistics(statistics);
            result.setMatchResults(matchResults);

            // 【步骤4】生成Prompt
            logger.info("步骤3: 为LLM生成Prompt...");
            String prompt = generatePrompt(matchResults, party, contractType);
            result.setPrompt(prompt);

            // 【步骤5】如果需要，调用AI
            if (reviewMode == ReviewMode.AI || reviewMode == ReviewMode.FULL) {
                logger.info("步骤4: 调用AI服务...");

                String aiResult = callAI(prompt, aiProvider);
                if (aiResult != null && !aiResult.isEmpty()) {
                    logger.info("✓ AI审查完成");
                    try {
                        result.setAiResult(objectMapper.readTree(aiResult));
                    } catch (Exception e) {
                        logger.warn("无法解析AI结果JSON: {}", e.getMessage());
                        result.setAiResult(objectMapper.valueToTree(aiResult));
                    }

                    // 【步骤6】如果是完整模式，解析结果并导入批注
                    if (reviewMode == ReviewMode.FULL) {
                        logger.info("步骤5: 解析AI结果并导入批注...");
                        try {
                            // 【关键修复】解析AI结果为ReviewIssue列表
                            List<ReviewIssue> issues = qwenRuleReviewService.parseReviewResults(aiResult);
                            logger.info("✓ 解析出 {} 个审查问题", issues.size());

                            if (issues != null && !issues.isEmpty()) {
                                // 【关键修复】直接传递带锚点文档字节数组和issues列表
                                byte[] annotatedDocBytes = xmlContractAnnotateService.annotateContractWithXml(
                                    anchoredDocumentBytes, issues, "preferAnchor", false);
                                logger.info("✓ 文档批注完成，大小: {} KB", annotatedDocBytes.length / 1024);

                                // 【步骤7】保存带批注的文档到文档中心和MinIO
                                String baseName = filename.replaceAll("\\.(docx|doc)$", "");
                                String outputFilename = baseName + "_统一审查_" +
                                    (party != null ? party : "中立") + ".docx";

                                // 保存到本地文档中心（保持现有逻辑）
                                String projectRoot = System.getProperty("user.dir");
                                String docCenterPath = Paths.get(projectRoot, "文档中心", "已生成的审查报告").toString();

                                // 创建目录
                                Path docCenterDir = Paths.get(docCenterPath);
                                if (!Files.exists(docCenterDir)) {
                                    Files.createDirectories(docCenterDir);
                                    logger.info("✓ 创建文档中心目录: {}", docCenterPath);
                                }

                                // 保存文件到本地（使用中文文件名）
                                Path outputPath = docCenterDir.resolve(outputFilename);
                                Files.write(outputPath, annotatedDocBytes);
                                logger.info("✓ 文档已保存到本地: {}", outputPath);

                                // 【新增】同时保存到MinIO
                                try {
                                    if (minioFileService.isEnabled()) {
                                        String minioObjectName = minioFileService.generateReportObjectName(
                                            filename, "统一审查", party != null ? party : "中立");
                                        String minioUrl = minioFileService.uploadBytes(
                                            annotatedDocBytes, 
                                            minioObjectName,
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        );
                                        result.setAnnotatedDocumentUrl(minioUrl);
                                        logger.info("✓ 文档已保存到MinIO: {}", minioUrl);
                                    } else {
                                        logger.info("MinIO服务未启用，跳过MinIO存储");
                                    }
                                } catch (Exception e) {
                                    logger.warn("MinIO存储失败，但本地保存成功: {}", e.getMessage());
                                    // MinIO失败不影响整体流程
                                }
                            }
                        } catch (Exception e) {
                            logger.error("批注导入失败: {}", e.getMessage(), e);
                            // 不中断流程，继续返回AI结果
                        }
                    }
                }
            }

            result.setSuccess(true);
            long totalTime = System.currentTimeMillis() - startTime;
            result.setProcessingTime(totalTime);

            logger.info("=== 审查流程完成 ===");
            logger.info("总耗时: {}ms", totalTime);

            return result;

        } catch (Exception e) {
            logger.error("统一审查流程失败", e);
            result.setError(e.getMessage());
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 计算审查统计信息
     */
    private ReviewStatistics calculateStatistics(
            List<Clause> clauses,
            List<RuleMatchResult> matchResults,
            List<ReviewRule> allRules,
            List<ReviewRule> applicableRules,
            String contractType,
            String party,
            Long parseTime,
            Long matchTime) {

        long highRiskCount = matchResults.stream()
                .filter(RuleMatchResult::hasHighRisk)
                .count();

        long mediumRiskCount = matchResults.stream()
                .filter(m -> "medium".equalsIgnoreCase(m.getHighestRisk()))
                .count();

        long lowRiskCount = matchResults.stream()
                .filter(m -> "low".equalsIgnoreCase(m.getHighestRisk()))
                .count();

        int totalMatchedRules = matchResults.stream()
                .mapToInt(RuleMatchResult::getMatchCount)
                .sum();

        return ReviewStatistics.builder()
                .totalClauses(clauses.size())
                .matchedClauses(matchResults.size())
                .highRiskClauses((int) highRiskCount)
                .mediumRiskClauses((int) mediumRiskCount)
                .lowRiskClauses((int) lowRiskCount)
                .totalRules(allRules.size())
                .applicableRules(applicableRules.size())
                .totalMatchedRules(totalMatchedRules)
                .parseTime(parseTime)
                .matchTime(matchTime)
                .contractType(contractType)
                .userStance(party != null ? party : "中立")
                .build();
    }

    /**
     * 生成审查Prompt
     */
    private String generatePrompt(
            List<RuleMatchResult> matchResults,
            String party,
            String contractType) {

        ReviewStance stance = party != null && !party.trim().isEmpty()
                ? ReviewStance.fromPartyId(party)
                : ReviewStance.neutral();

        return PromptGeneratorNew.generateFullPrompt(matchResults, contractType, stance);
    }

    /**
     * 计算最高风险等级
     */
    private String calculateHighestRisk(List<ReviewRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "low";
        }

        for (ReviewRule rule : rules) {
            if ("high".equalsIgnoreCase(rule.getRisk())) {
                return "high";
            }
        }

        for (ReviewRule rule : rules) {
            if ("medium".equalsIgnoreCase(rule.getRisk())) {
                return "medium";
            }
        }

        return "low";
    }

    /**
     * 调用AI服务
     */
    private String callAI(String prompt, String aiProvider) {
        if (aiProvider == null || aiProvider.isEmpty()) {
            return null;
        }

        switch (aiProvider.toLowerCase()) {
            case "qwen":
                if (qwenRuleReviewService.isQwenAvailable()) {
                    return qwenRuleReviewService.reviewContractWithQwen(prompt);
                }
                break;

            case "chatgpt":
            case "openai":
                logger.warn("ChatGPT服务暂未集成到统一接口");
                break;

            case "claude":
                logger.warn("Claude服务暂未集成到统一接口");
                break;
        }

        logger.warn("无法调用AI服务: {}", aiProvider);
        return null;
    }
}
