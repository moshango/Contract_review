package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.model.ReviewRule;
import com.example.Contract_review.model.RuleMatchResult;
import com.example.Contract_review.model.Clause;
import com.example.Contract_review.model.ReviewStance;
import com.example.Contract_review.model.PartyExtractionRequest;
import com.example.Contract_review.model.PartyExtractionResponse;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.ReviewRulesService;
import com.example.Contract_review.service.ParseResultCache;
import com.example.Contract_review.service.ReviewStanceService;
import com.example.Contract_review.service.PartyExtractionService;
import com.example.Contract_review.util.PromptGenerator;
import com.example.Contract_review.util.PromptGeneratorNew;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * API 审查控制器
 *
 * 提供 /api/review 接口用于：
 * 1. 解析合同并按规则匹配条款
 * 2. 为 LLM 生成审查 prompt
 * 3. 返回结构化的审查指导信息
 */
@RestController
@RequestMapping("/api/review")
public class ApiReviewController {

    private static final Logger logger = LoggerFactory.getLogger(ApiReviewController.class);

    @Autowired
    private ContractParseService contractParseService;

    @Autowired
    private ReviewRulesService reviewRulesService;

    @Autowired
    private ParseResultCache parseResultCache;

    @Autowired
    private ReviewStanceService reviewStanceService;

    @Autowired
    private PartyExtractionService partyExtractionService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 合同审查接口 - 规则匹配和 Prompt 生成
     *
     * 工作流程：
     * 1. 解析合同文件 → 得到条款列表
     * 2. 加载审查规则 → 按合同类型过滤
     * 3. 为每个条款匹配规则 → 得到匹配结果
     * 4. 为 LLM 生成 prompt → 返回给前端
     *
     * @param file 合同文件（.docx / .doc）
     * @param contractType 合同类型（采购/外包/NDA/通用合同等）
     * @param party 审查立场（"A" 甲方 / "B" 乙方 / 不指定则为中立）
     * @return JSON 结果，包含 prompt、匹配结果、风险统计等
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeContract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType,
            @RequestParam(value = "party", required = false) String party) {

        long startTime = System.currentTimeMillis();
        String filename = file.getOriginalFilename();

        try {
            logger.info("=== 开始合同审查分析 ===");
            logger.info("文件: {}, 类型: {}, 立场: {}", filename, contractType, party != null ? party : "中立");

            // 【新增】设置用户立场
            if (party != null && !party.trim().isEmpty()) {
                reviewStanceService.setStanceByParty(party);
                logger.info("✓ 已设置审查立场: {}", party);
            }

            // 步骤1: 解析合同（生成锚点供后续批注使用）
            logger.info("步骤1: 解析合同并生成带锚点文档...");
            ContractParseService.ParseResultWithDocument parseResultWithDoc = contractParseService.parseContractWithDocument(file, "generate");
            ParseResult parseResult = parseResultWithDoc.getParseResult();
            List<Clause> clauses = parseResult.getClauses();
            byte[] anchoredDocumentBytes = parseResultWithDoc.getDocumentBytes();
            logger.info("✓ 解析完成，共 {} 个条款，锚点已生成，带锚点文档大小: {} 字节",
                       clauses.size(), anchoredDocumentBytes != null ? anchoredDocumentBytes.length : 0);

            // 【新增】保存到缓存并生成 parseResultId
            String parseResultId = null;
            if (anchoredDocumentBytes != null && anchoredDocumentBytes.length > 0) {
                String cacheFilename = parseResultWithDoc.getDocumentFilename() != null ?
                        parseResultWithDoc.getDocumentFilename() : filename;
                parseResultId = parseResultCache.store(parseResult, anchoredDocumentBytes, cacheFilename);
                logger.info("✓ 带锚点文档已保存到缓存，parseResultId: {}", parseResultId);
            }

            // 步骤2: 加载和过滤规则
            logger.info("步骤2: 加载审查规则...");
            List<ReviewRule> allRules = reviewRulesService.loadRules();
            List<ReviewRule> applicableRules = reviewRulesService.filterByContractType(contractType);
            logger.info("✓ 规则加载完成，共 {} 个规则（适用规则: {} 个）",
                allRules.size(), applicableRules.size());

            // 步骤3: 为每个条款匹配规则
            logger.info("步骤3: 匹配条款与规则...");
            List<RuleMatchResult> matchResults = new ArrayList<>();

            // 【新增】获取用户立场
            ReviewStance stance = reviewStanceService.getStance();

            for (Clause clause : clauses) {
                List<ReviewRule> matchedRules = applicableRules.stream()
                    .filter(rule -> rule.matches(clause.getFullText()))
                    // 【新增】根据用户立场过滤规则
                    .filter(rule -> stance.isRuleApplicable(rule))
                    .collect(Collectors.toList());

                if (!matchedRules.isEmpty()) {
                    // 计算最高风险等级
                    String highestRisk = calculateHighestRisk(matchedRules);

                    RuleMatchResult result = RuleMatchResult.builder()
                        .clauseId(clause.getId())
                        .anchorId(clause.getAnchorId())  // 【关键修复】从解析的条款中获取 anchorId
                        .paragraphAnchors(clause.getParagraphAnchors())  // 【新增】传递段落级锚点，用于Prompt显示
                        .clauseHeading(clause.getHeading())
                        .clauseText(clause.getFullText())
                        .matchedRules(matchedRules)
                        .matchCount(matchedRules.size())
                        .highestRisk(highestRisk)
                        .build();

                    matchResults.add(result);
                    logger.debug("条款 {} 匹配 {} 条规则，anchorId: {}, 段落数: {}, 用户立场: {}",
                               clause.getId(), matchedRules.size(), clause.getAnchorId(),
                               clause.getParagraphAnchors() != null ? clause.getParagraphAnchors().size() : 0,
                               stance.getDescription());
                }
            }

            logger.info("✓ 匹配完成，检出 {} 个需要审查的条款", matchResults.size());

            // 步骤4: 生成 Prompt（【关键】现在支持立场相关的建议）
            String prompt = PromptGeneratorNew.generateFullPrompt(matchResults, contractType, stance);
            logger.info("✓ Prompt 生成完成，长度: {} 字符", prompt.length());

            // 步骤5: 生成审查指导和统计信息
            ObjectNode guidance = PromptGenerator.generateReviewGuidance(matchResults);

            // 步骤6: 构建响应
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("filename", filename);
            response.put("contractType", contractType);
            // 【新增】添加用户审查立场
            response.put("userStance", stance.getParty() != null ? stance.getParty() : "Neutral");
            response.put("stanceDescription", stance.getDescription());
            response.put("timestamp", System.currentTimeMillis());

            // 【新增】添加识别的甲乙方信息到响应
            if (parseResult.getPartyA() != null) {
                response.put("partyA", parseResult.getPartyA());
                logger.info("✓ 返回识别的甲方: {}", parseResult.getPartyA());
            }
            if (parseResult.getPartyB() != null) {
                response.put("partyB", parseResult.getPartyB());
                logger.info("✓ 返回识别的乙方: {}", parseResult.getPartyB());
            }

            // 统计信息
            ObjectNode stats = response.putObject("statistics");
            stats.put("totalClauses", clauses.size());
            stats.put("matchedClauses", matchResults.size());
            stats.put("totalRules", allRules.size());
            stats.put("applicableRules", applicableRules.size());
            stats.put("totalMatchedRules", matchResults.stream()
                .mapToInt(RuleMatchResult::getMatchCount).sum());

            // 高风险条款数
            long highRiskCount = matchResults.stream()
                .filter(RuleMatchResult::hasHighRisk).count();
            stats.put("highRiskClauses", highRiskCount);

            // 审查指导
            response.set("guidance", guidance);

            // 【关键】添加 Prompt 到响应，供前端复制到 LLM 使用
            response.put("prompt", prompt);

            // 匹配结果详情（用于前端展示）
            ArrayNode matchResultsArray = response.putArray("matchResults");
            for (RuleMatchResult result : matchResults) {
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("clauseId", result.getClauseId());
                resultNode.put("anchorId", result.getAnchorId());  // 【关键】添加 anchorId 到响应
                resultNode.put("heading", result.getClauseHeading());
                resultNode.put("riskLevel", result.getHighestRisk());
                resultNode.put("matchedRuleCount", result.getMatchCount());

                // 匹配的规则摘要
                ArrayNode rulesArray = resultNode.putArray("matchedRules");
                for (ReviewRule rule : result.getMatchedRules()) {
                    ObjectNode ruleNode = objectMapper.createObjectNode();
                    ruleNode.put("id", rule.getId());
                    ruleNode.put("risk", rule.getRisk());
                    ruleNode.put("keywords", rule.getKeywords());
                    ruleNode.put("checklist", rule.getChecklist());
                    rulesArray.add(ruleNode);
                }

                matchResultsArray.add(resultNode);
            }

            // 【关键修复】包含 parseResultId 供后续批注使用
            if (parseResultId != null && !parseResultId.isEmpty()) {
                response.put("parseResultId", parseResultId);
                response.put("nextStep", "将 prompt 字段的内容复制到 LLM（ChatGPT、Claude等），" +
                    "LLM 将返回 JSON 格式的审查结果，然后可以调用 /chatgpt/import-result?parseResultId=" + parseResultId + " 接口导入结果");
                logger.info("✓ parseResultId 已添加到响应: {}", parseResultId);
            } else {
                response.put("nextStep", "将 prompt 字段的内容复制到 LLM（ChatGPT、Claude等），" +
                    "LLM 将返回 JSON 格式的审查结果，然后可以调用 /api/annotate 接口插入批注");
            }

            long endTime = System.currentTimeMillis();
            response.put("processingTime", endTime - startTime + "ms");

            logger.info("=== 合同审查分析完成 === (耗时: {}ms)", endTime - startTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("合同审查分析失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "审查分析失败: " + e.getMessage());
            error.put("filename", filename);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取审查规则列表
     * 用于前端展示可用的审查规则
     *
     * @param contractType 合同类型（可选）
     * @return 规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<?> getRules(
            @RequestParam(value = "contractType", required = false) String contractType) {

        try {
            logger.info("获取审查规则列表, 合同类型: {}", contractType);

            List<ReviewRule> rules;
            if (contractType != null && !contractType.trim().isEmpty()) {
                rules = reviewRulesService.filterByContractType(contractType);
            } else {
                rules = reviewRulesService.getAllRules();
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("contractType", contractType != null ? contractType : "all");
            response.put("totalRules", rules.size());

            // 风险分布
            ObjectNode distribution = response.putObject("riskDistribution");
            distribution.put("high", rules.stream()
                .filter(r -> "high".equalsIgnoreCase(r.getRisk())).count());
            distribution.put("medium", rules.stream()
                .filter(r -> "medium".equalsIgnoreCase(r.getRisk())).count());
            distribution.put("low", rules.stream()
                .filter(r -> "low".equalsIgnoreCase(r.getRisk())).count());
            distribution.put("blocker", rules.stream()
                .filter(r -> "blocker".equalsIgnoreCase(r.getRisk())).count());

            // 规则列表
            ArrayNode rulesArray = response.putArray("rules");
            for (ReviewRule rule : rules) {
                ObjectNode ruleNode = objectMapper.createObjectNode();
                ruleNode.put("id", rule.getId());
                ruleNode.put("contractTypes", rule.getContractTypes());
                ruleNode.put("partyScope", rule.getPartyScope());
                ruleNode.put("risk", rule.getRisk());
                ruleNode.put("keywords", rule.getKeywords());
                ruleNode.put("regex", rule.getRegex());
                ruleNode.put("checklist", rule.getChecklist());
                ruleNode.put("suggestA", rule.getSuggestA());
                ruleNode.put("suggestB", rule.getSuggestB());
                rulesArray.add(ruleNode);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取规则列表失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取规则失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 重新加载审查规则
     * 用于在修改 rules.xlsx 后刷新内存中的规则
     *
     * @return 重新加载结果
     */
    @PostMapping("/reload-rules")
    public ResponseEntity<?> reloadRules() {
        try {
            logger.info("重新加载审查规则...");
            List<ReviewRule> rules = reviewRulesService.reloadRules();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "规则已重新加载");
            response.put("totalRules", rules.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("重新加载规则失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "重新加载失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取服务状态
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("service", "API Review Service");
        response.put("version", "1.0");
        response.put("rulesLoaded", reviewRulesService.isRulesLoaded());
        response.put("cachedRuleCount", reviewRulesService.getCachedRuleCount());
        response.put("timestamp", System.currentTimeMillis());

        ObjectNode endpoints = response.putObject("endpoints");
        endpoints.put("analyze", "POST /api/review/analyze");
        endpoints.put("rules", "GET /api/review/rules");
        endpoints.put("reloadRules", "POST /api/review/reload-rules");
        endpoints.put("settings", "POST /api/review/settings (设置立场)");
        endpoints.put("status", "GET /api/review/status");

        return ResponseEntity.ok(response);
    }

    /**
     * 审查设置接口 - 配置用户的审查立场
     *
     * 【新增】用户可选择自己的立场（甲方或乙方），
     * 在规则匹配时会根据立场返回对应的建议
     *
     * @param party 审查立场 ("A" 甲方 / "B" 乙方 / 其他或空为中立)
     * @return 当前设置信息
     */
    @PostMapping("/settings")
    public ResponseEntity<?> setReviewSettings(
            @RequestParam(value = "party", required = false) String party) {

        try {
            logger.info("=== 设置审查立场 ===");

            // 设置立场
            if (party != null && !party.trim().isEmpty()) {
                if (!("A".equalsIgnoreCase(party) || "B".equalsIgnoreCase(party))) {
                    // 无效的立场，返回错误
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("success", false);
                    error.put("error", "无效的立场值。应为 'A'（甲方）或 'B'（乙方）");
                    ArrayNode validValues = error.putArray("validValues");
                    validValues.add("A");
                    validValues.add("B");
                    validValues.add("Neutral");
                    return ResponseEntity.badRequest().body(error);
                }
                reviewStanceService.setStanceByParty(party);
                logger.info("✓ 审查立场已设置为: {}", party.toUpperCase());
            } else {
                // 清除立场设置，恢复为中立
                reviewStanceService.setStance(ReviewStance.neutral());
                logger.info("✓ 审查立场已重置为中立");
            }

            // 获取当前立场
            ReviewStance currentStance = reviewStanceService.getStance();

            // 构建响应
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("currentStance", currentStance.getParty() != null ? currentStance.getParty() : "Neutral");
            response.put("stanceDescription", currentStance.getDescription());
            response.put("timestamp", System.currentTimeMillis());

            ObjectNode info = response.putObject("stanceInfo");
            info.put("A", "甲方立场 - 将收到对甲方有利的建议");
            info.put("B", "乙方立场 - 将收到对乙方有利的建议");
            info.put("Neutral", "中立立场 - 只显示通用的中立建议");

            response.put("nextStep", "下次调用 /api/review/analyze 时，系统将使用此立场进行规则匹配");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("设置审查立场失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "设置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取当前审查设置
     *
     * @return 当前的审查立场和设置信息
     */
    @GetMapping("/settings")
    public ResponseEntity<?> getReviewSettings() {

        try {
            ReviewStance currentStance = reviewStanceService.getStance();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("currentStance", currentStance.getParty() != null ? currentStance.getParty() : "Neutral");
            response.put("stanceDescription", currentStance.getDescription());
            response.put("timestamp", System.currentTimeMillis());

            ObjectNode availableOptions = response.putObject("availableOptions");
            availableOptions.put("A", "甲方立场");
            availableOptions.put("B", "乙方立场");
            availableOptions.put("Neutral", "中立立场");

            response.put("info", "可使用 POST /api/review/settings?party=A 或 party=B 来切换立场");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取审查设置失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 计算最高风险等级
     * 优先级：high > blocker > medium > low
     *
     * @param rules 规则列表
     * @return 最高风险等级
     */
    private String calculateHighestRisk(List<ReviewRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "low";
        }

        for (String risk : new String[]{"high", "blocker", "medium", "low"}) {
            if (rules.stream().anyMatch(r -> risk.equalsIgnoreCase(r.getRisk()))) {
                return risk;
            }
        }

        return "low";
    }

    /**
     * 提取合同方信息接口
     *
     * 【新增】用于规则审查的第一步：
     * 1. 接收已解析的合同文本
     * 2. 使用 Qwen 识别甲乙方
     * 3. 返回识别结果，让用户选择立场
     *
     * @param request 包含合同文本和类型的请求
     * @return 包含甲乙方信息和推荐立场的响应
     */
    @PostMapping("/extract-parties")
    public ResponseEntity<?> extractParties(@RequestBody PartyExtractionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("=== 开始提取合同方信息 ===");

            // 验证 Qwen 服务
            if (!partyExtractionService.isQwenAvailable()) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "Qwen服务未配置或不可用");
                error.put("hint", "请检查application.properties中的qwen配置");
                logger.error("Qwen服务不可用");
                return ResponseEntity.badRequest().body(error);
            }

            // 验证输入
            if (request.getContractText() == null || request.getContractText().isEmpty()) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("success", false);
                error.put("error", "合同文本不能为空");
                logger.error("合同文本为空");
                return ResponseEntity.badRequest().body(error);
            }

            logger.info("✓ 参数验证通过，合同文本长度: {} 字符", request.getContractText().length());

            // 调用服务提取合同方
            PartyExtractionResponse partyResponse = partyExtractionService.extractPartyInfoWithQwen(request);

            // 构建响应
            ObjectNode response = objectMapper.createObjectNode();

            if (partyResponse.isSuccess()) {
                response.put("success", true);
                response.put("partyA", partyResponse.getPartyA());
                response.put("partyB", partyResponse.getPartyB());
                response.put("partyARoleName", partyResponse.getPartyARoleName());
                response.put("partyBRoleName", partyResponse.getPartyBRoleName());
                response.put("recommendedStance", partyResponse.getRecommendedStance());
                response.put("stanceReason", partyResponse.getStanceReason());
                response.put("message", "已识别甲乙方信息，请选择您的立场");

                logger.info("✓ 合同方提取成功: A={}, B={}", partyResponse.getPartyA(), partyResponse.getPartyB());
            } else {
                response.put("success", false);
                response.put("error", partyResponse.getErrorMessage());
                logger.error("合同方提取失败: {}", partyResponse.getErrorMessage());
            }

            response.put("processingTime", partyResponse.getProcessingTime() + "ms");
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("合同方提取处理失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "处理失败: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
}
