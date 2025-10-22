package com.example.Contract_review.controller;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.model.ReviewRule;
import com.example.Contract_review.model.RuleMatchResult;
import com.example.Contract_review.model.Clause;
import com.example.Contract_review.service.ContractParseService;
import com.example.Contract_review.service.ReviewRulesService;
import com.example.Contract_review.util.PromptGenerator;
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
     * @return JSON 结果，包含 prompt、匹配结果、风险统计等
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeContract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", defaultValue = "通用合同") String contractType) {

        long startTime = System.currentTimeMillis();
        String filename = file.getOriginalFilename();

        try {
            logger.info("=== 开始合同审查分析 ===");
            logger.info("文件: {}, 类型: {}", filename, contractType);

            // 步骤1: 解析合同（生成锚点供后续批注使用）
            logger.info("步骤1: 解析合同...");
            ParseResult parseResult = contractParseService.parseContract(file, "generate");
            List<Clause> clauses = parseResult.getClauses();
            logger.info("✓ 解析完成，共 {} 个条款，锚点已生成", clauses.size());

            // 步骤2: 加载和过滤规则
            logger.info("步骤2: 加载审查规则...");
            List<ReviewRule> allRules = reviewRulesService.loadRules();
            List<ReviewRule> applicableRules = reviewRulesService.filterByContractType(contractType);
            logger.info("✓ 规则加载完成，共 {} 个规则（适用规则: {} 个）",
                allRules.size(), applicableRules.size());

            // 步骤3: 为每个条款匹配规则
            logger.info("步骤3: 匹配条款与规则...");
            List<RuleMatchResult> matchResults = new ArrayList<>();

            for (Clause clause : clauses) {
                List<ReviewRule> matchedRules = applicableRules.stream()
                    .filter(rule -> rule.matches(clause.getFullText()))
                    .collect(Collectors.toList());

                if (!matchedRules.isEmpty()) {
                    // 计算最高风险等级
                    String highestRisk = calculateHighestRisk(matchedRules);

                    RuleMatchResult result = RuleMatchResult.builder()
                        .clauseId(clause.getId())
                        .clauseHeading(clause.getHeading())
                        .clauseText(clause.getFullText())
                        .matchedRules(matchedRules)
                        .matchCount(matchedRules.size())
                        .highestRisk(highestRisk)
                        .build();

                    matchResults.add(result);
                    logger.debug("条款 {} 匹配 {} 条规则", clause.getId(), matchedRules.size());
                }
            }

            logger.info("✓ 匹配完成，检出 {} 个需要审查的条款", matchResults.size());

            // 步骤4: 生成 Prompt
            logger.info("步骤4: 生成 LLM Prompt...");
            String prompt = PromptGenerator.generateFullPrompt(matchResults, contractType);
            logger.info("✓ Prompt 生成完成，长度: {} 字符", prompt.length());

            // 步骤5: 生成审查指导和统计信息
            ObjectNode guidance = PromptGenerator.generateReviewGuidance(matchResults);

            // 步骤6: 构建响应
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("filename", filename);
            response.put("contractType", contractType);
            response.put("timestamp", System.currentTimeMillis());

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

            // Prompt 提示
            response.put("prompt", prompt);
            response.put("promptLength", prompt.length());

            // 匹配结果详情（用于前端展示）
            ArrayNode matchResultsArray = response.putArray("matchResults");
            for (RuleMatchResult result : matchResults) {
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("clauseId", result.getClauseId());
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

            // 工作流提示
            response.put("nextStep", "将 prompt 字段的内容复制到 LLM（ChatGPT、Claude等），" +
                "LLM 将返回 JSON 格式的审查结果，然后可以调用 /annotate 接口插入批注");

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
        endpoints.put("status", "GET /api/review/status");

        return ResponseEntity.ok(response);
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
}
