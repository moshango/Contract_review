package com.example.Contract_review.util;

import com.example.Contract_review.model.RuleMatchResult;
import com.example.Contract_review.model.ReviewRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Prompt 生成工具
 *
 * 根据规则匹配结果为 LLM 生成审查 prompt
 * 格式化包含 checklist、条款文本、建议等信息
 */
public class PromptGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PromptGenerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为单个条款生成审查 prompt
     * 包含规则匹配信息、检查清单、锚点信息和建议
     *
     * @param result 规则匹配结果
     * @return Prompt 文本
     */
    public static String generateClausePrompt(RuleMatchResult result) {
        if (result == null || result.getMatchedRules().isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();

        // 条款标题和ID
        prompt.append("【条款】").append(result.getClauseId());
        if (result.getClauseHeading() != null && !result.getClauseHeading().isEmpty()) {
            prompt.append(" - ").append(result.getClauseHeading());
        }
        prompt.append("\n\n");

        // 条款文本
        prompt.append("【原文】\n").append(result.getClauseText()).append("\n\n");

        // 匹配的规则和检查清单
        prompt.append("【审查要点】\n");
        for (ReviewRule rule : result.getMatchedRules()) {
            prompt.append("● 风险等级: ").append(rule.getRisk().toUpperCase()).append("\n");

            // 显示匹配的关键词
            if (rule.getMatchedKeywords() != null && !rule.getMatchedKeywords().isEmpty()) {
                prompt.append("  匹配关键词: ").append(String.join(", ", rule.getMatchedKeywords())).append("\n");
            }

            if (rule.getChecklist() != null && !rule.getChecklist().isEmpty()) {
                prompt.append("  检查清单:\n");
                for (String line : rule.getChecklist().split("\n")) {
                    prompt.append("  ").append(line).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请根据上述检查要点对该条款进行审查，提出具体的问题和改进建议。\n");

        return prompt.toString();
    }

    /**
     * 为多个条款生成完整的审查 prompt
     *
     * @param results 多个规则匹配结果
     * @param contractType 合同类型
     * @return 完整 Prompt 文本
     */
    public static String generateFullPrompt(List<RuleMatchResult> results, String contractType) {
        if (results == null || results.isEmpty()) {
            return "未检出需要审查的条款";
        }

        StringBuilder fullPrompt = new StringBuilder();

        fullPrompt.append("您是一位资深的合同法律顾问。请根据以下信息对合同进行专业审查。\n\n");

        fullPrompt.append("【合同信息】\n");
        fullPrompt.append("合同类型: ").append(contractType != null ? contractType : "通用合同").append("\n");
        fullPrompt.append("需要审查的条款数: ").append(results.size()).append("\n\n");

        fullPrompt.append("【审查规则说明】\n");
        fullPrompt.append("系统已通过关键字和规则识别出以下可能存在风险的条款。")
            .append("请针对各条款的检查要点进行深入分析，\n")
            .append("提出具体的法律风险、问题点和改进建议。\n\n");

        fullPrompt.append("【重要说明】\n");
        fullPrompt.append("• 请在审查结果中，尽可能指出需要修改的\"具体文字\"（targetText字段）\n");
        fullPrompt.append("• 这样可以精确定位到合同中的修改位置，提高批注准确性\n");
        fullPrompt.append("• 如无法找到完全相同的文字，请提供尽可能接近的关键词或短语\n\n");

        // 汇总高风险条款
        List<RuleMatchResult> highRiskResults = results.stream()
            .filter(RuleMatchResult::hasHighRisk)
            .toList();

        if (!highRiskResults.isEmpty()) {
            fullPrompt.append("【高风险条款提示】\n");
            fullPrompt.append("以下 ").append(highRiskResults.size()).append(" 个条款包含高风险问题，需要重点关注:\n");
            for (RuleMatchResult result : highRiskResults) {
                fullPrompt.append("- ").append(result.getClauseId()).append(" ");
                if (result.getClauseHeading() != null) {
                    fullPrompt.append(result.getClauseHeading());
                }
                fullPrompt.append(" (").append(result.getHighestRisk()).append(")\n");
            }
            fullPrompt.append("\n");
        }

        // 条款详细信息
        fullPrompt.append("【需要审查的条款列表】\n");
        for (RuleMatchResult result : results) {
            fullPrompt.append(generateClausePrompt(result)).append("\n");
            fullPrompt.append("---\n\n");
        }

        fullPrompt.append("【期望输出格式】\n");
        fullPrompt.append("请按照以下 JSON 格式返回审查结果:\n");
        fullPrompt.append(generateExpectedJsonSchema()).append("\n");

        return fullPrompt.toString();
    }

    /**
     * 生成期望的 JSON 输出格式说明
     *
     * @return JSON 格式示例
     */
    private static String generateExpectedJsonSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode example = mapper.createObjectNode();

        ArrayNode issues = mapper.createArrayNode();
        ObjectNode issue1 = mapper.createObjectNode();
        issue1.put("clauseId", "c1");
        issue1.put("severity", "HIGH");
        issue1.put("category", "付款条款");
        issue1.put("finding", "付款周期不明确，容易产生争议");
        issue1.put("suggestion", "建议明确指定付款周期为30天内，并指定付款方式");
        issue1.put("targetText", "甲方应按时支付");
        issue1.put("matchPattern", "EXACT");
        issues.add(issue1);

        example.set("issues", issues);

        return example.toPrettyString();
    }

    /**
     * 为 prompt 中的内容生成可结构化的审查指导
     *
     * @param results 规则匹配结果列表
     * @return 包含审查指导的 JSON 对象
     */
    public static ObjectNode generateReviewGuidance(List<RuleMatchResult> results) {
        ObjectNode guidance = objectMapper.createObjectNode();

        // 统计信息
        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("totalClauses", results.size());
        stats.put("highRiskCount", results.stream().filter(RuleMatchResult::hasHighRisk).count());
        stats.put("totalMatchedRules", results.stream().mapToInt(RuleMatchResult::getMatchCount).sum());

        // 风险分布
        ObjectNode riskDistribution = objectMapper.createObjectNode();
        riskDistribution.put("high", results.stream()
            .filter(r -> "high".equalsIgnoreCase(r.getHighestRisk())).count());
        riskDistribution.put("medium", results.stream()
            .filter(r -> "medium".equalsIgnoreCase(r.getHighestRisk())).count());
        riskDistribution.put("low", results.stream()
            .filter(r -> "low".equalsIgnoreCase(r.getHighestRisk())).count());

        guidance.set("statistics", stats);
        guidance.set("riskDistribution", riskDistribution);

        // 检查要点总结
        ArrayNode checkpoints = guidance.putArray("checkpoints");
        for (RuleMatchResult result : results) {
            ObjectNode checkpoint = objectMapper.createObjectNode();
            checkpoint.put("clauseId", result.getClauseId());
            checkpoint.put("heading", result.getClauseHeading());
            checkpoint.put("riskLevel", result.getHighestRisk());
            checkpoint.put("checklist", result.getCombinedChecklist());
            checkpoints.add(checkpoint);
        }

        return guidance;
    }
}
