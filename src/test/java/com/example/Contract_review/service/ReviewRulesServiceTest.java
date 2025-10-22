package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReviewRulesService 单元测试
 */
public class ReviewRulesServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(ReviewRulesServiceTest.class);

    private ReviewRulesService reviewRulesService;

    @BeforeEach
    public void setUp() {
        reviewRulesService = new ReviewRulesService();
        // 设置文件路径
        String projectRoot = System.getProperty("user.dir");
        String rulesPath = projectRoot + "/src/main/resources/review-rules/rules.xlsx";
        logger.info("Rules file path: {}", rulesPath);
    }

    @Test
    public void testLoadRules() {
        logger.info("=== 测试加载规则 ===");
        List<ReviewRule> rules = reviewRulesService.loadRules();

        logger.info("✓ 加载规则成功，共 {} 条", rules.size());
        assertTrue(rules.size() > 0, "规则数量应大于0");

        // 打印规则摘要
        for (ReviewRule rule : rules) {
            logger.info("  - Rule: id={}, risk={}, keywords={}",
                rule.getId(), rule.getRisk(), rule.getKeywords());
        }
    }

    @Test
    public void testFilterByContractType() {
        logger.info("=== 测试按合同类型过滤 ===");
        List<ReviewRule> rules = reviewRulesService.filterByContractType("采购");

        logger.info("✓ 过滤完成，采购合同适用规则数: {}", rules.size());
        assertTrue(rules.size() > 0, "采购合同应有适用规则");

        // 验证所有规则都适用于采购合同
        for (ReviewRule rule : rules) {
            assertTrue(rule.applicableToContractType("采购"),
                "规则 " + rule.getId() + " 应适用于采购合同");
        }
    }

    @Test
    public void testRuleMatching() {
        logger.info("=== 测试规则匹配 ===");
        List<ReviewRule> allRules = reviewRulesService.loadRules();

        // 测试包含"付款"的条款
        String clauseText = "甲方应在货物交付后30天内付款，付款方式为银行转账。";
        List<ReviewRule> matchedRules = allRules.stream()
            .filter(rule -> rule.matches(clauseText))
            .toList();

        logger.info("✓ 匹配完成，匹配规则数: {}", matchedRules.size());
        assertTrue(matchedRules.size() > 0, "应该匹配到付款相关的规则");

        // 打印匹配的规则
        for (ReviewRule rule : matchedRules) {
            logger.info("  - 匹配规则: id={}, risk={}, keywords={}",
                rule.getId(), rule.getRisk(), rule.getKeywords());
        }
    }

    @Test
    public void testRulesCache() {
        logger.info("=== 测试规则缓存 ===");

        // 第一次加载
        long start1 = System.currentTimeMillis();
        List<ReviewRule> rules1 = reviewRulesService.loadRules();
        long time1 = System.currentTimeMillis() - start1;

        // 第二次加载（应使用缓存）
        long start2 = System.currentTimeMillis();
        List<ReviewRule> rules2 = reviewRulesService.loadRules();
        long time2 = System.currentTimeMillis() - start2;

        logger.info("✓ 第一次加载耗时: {}ms，第二次加载耗时: {}ms", time1, time2);
        logger.info("  规则数: {}", rules1.size());

        assertEquals(rules1.size(), rules2.size(), "缓存的规则数应相同");
        assertTrue(time2 < time1, "第二次加载应更快（使用缓存）");
    }

    @Test
    public void testRulesContent() {
        logger.info("=== 测试规则内容 ===");
        List<ReviewRule> rules = reviewRulesService.loadRules();

        // 检查是否有付款、保密、违约等常见规则
        boolean hasPay = rules.stream().anyMatch(r ->
            r.getKeywords() != null && r.getKeywords().contains("付款"));
        boolean hasSecret = rules.stream().anyMatch(r ->
            r.getKeywords() != null && r.getKeywords().contains("保密"));
        boolean hasLiability = rules.stream().anyMatch(r ->
            r.getKeywords() != null && r.getKeywords().contains("违约"));

        logger.info("✓ 检查规则类型:");
        logger.info("  - 付款规则: {}", hasPay ? "✓ 存在" : "✗ 不存在");
        logger.info("  - 保密规则: {}", hasSecret ? "✓ 存在" : "✗ 不存在");
        logger.info("  - 违约规则: {}", hasLiability ? "✓ 存在" : "✗ 不存在");

        assertTrue(hasPay || hasSecret || hasLiability, "应该包含常见的审查规则");
    }

    @Test
    public void testPromptGeneration() {
        logger.info("=== 测试 Prompt 生成 ===");
        List<ReviewRule> rules = reviewRulesService.loadRules();

        // 验证规则包含 checklist 和建议
        for (ReviewRule rule : rules.stream().limit(3).toList()) {
            logger.info("规则 {}: ", rule.getId());
            logger.info("  - Checklist: {}", rule.getChecklist() != null ? "✓" : "✗");
            logger.info("  - Suggest A: {}", rule.getSuggestA() != null ? "✓" : "✗");
            logger.info("  - Suggest B: {}", rule.getSuggestB() != null ? "✓" : "✗");

            assertTrue(rule.getChecklist() != null && !rule.getChecklist().isEmpty(),
                "规则应包含检查清单");
        }
    }
}
