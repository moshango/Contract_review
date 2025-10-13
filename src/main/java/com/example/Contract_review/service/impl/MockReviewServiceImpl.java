package com.example.Contract_review.service.impl;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.AIReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模拟AI审查服务实现
 * 用于测试和演示，不依赖外部API
 */
@Service("mockReviewService")
public class MockReviewServiceImpl implements AIReviewService {

    private static final Logger logger = LoggerFactory.getLogger(MockReviewServiceImpl.class);

    @Override
    public String reviewContract(ParseResult parseResult, String contractType) throws Exception {
        logger.info("开始使用模拟AI审查合同: contractType={}", contractType);

        // 模拟审查延迟
        Thread.sleep(2000);

        // 生成模拟审查结果
        String mockResult = generateMockReview(parseResult, contractType);

        logger.info("模拟AI审查完成，结果长度: {}", mockResult.length());
        return mockResult;
    }

    @Override
    public boolean isAvailable() {
        return true; // 模拟服务总是可用
    }

    @Override
    public String getProviderName() {
        return "模拟AI (测试用)";
    }

    private String generateMockReview(ParseResult parseResult, String contractType) {
        StringBuilder reviewJson = new StringBuilder();
        reviewJson.append("{\n");
        reviewJson.append("  \"issues\": [\n");

        // 为每个条款生成一些模拟问题
        for (int i = 0; i < Math.min(3, parseResult.getClauses().size()); i++) {
            var clause = parseResult.getClauses().get(i);

            if (i > 0) reviewJson.append(",\n");

            reviewJson.append("    {\n");
            reviewJson.append("      \"clauseId\": \"").append(clause.getId()).append("\",\n");

            if (clause.getAnchorId() != null) {
                reviewJson.append("      \"anchorId\": \"").append(clause.getAnchorId()).append("\",\n");
            }

            // 随机选择风险级别
            String[] severities = {"HIGH", "MEDIUM", "LOW"};
            String severity = severities[i % 3];
            reviewJson.append("      \"severity\": \"").append(severity).append("\",\n");

            // 根据条款内容生成问题
            String category, finding, suggestion;
            if (clause.getHeading().contains("保密") || clause.getText().contains("保密")) {
                category = "保密条款";
                finding = "保密信息定义不够明确";
                suggestion = "建议明确定义保密信息的范围，包括技术信息、商业信息等具体类别。";
            } else if (clause.getHeading().contains("责任") || clause.getText().contains("责任")) {
                category = "责任条款";
                finding = "责任限制条款可能对我方不利";
                suggestion = "建议增加对等的责任限制条款，平衡双方责任。";
            } else if (clause.getHeading().contains("终止") || clause.getText().contains("终止")) {
                category = "终止条款";
                finding = "终止条件设置不够合理";
                suggestion = "建议增加具体的终止情形，并明确终止后的处理程序。";
            } else {
                category = "合同条款";
                finding = "条款表述存在歧义";
                suggestion = "建议进一步明确条款的具体执行标准和要求。";
            }

            reviewJson.append("      \"category\": \"").append(category).append("\",\n");
            reviewJson.append("      \"finding\": \"").append(finding).append("\",\n");
            reviewJson.append("      \"suggestion\": \"").append(suggestion).append("\"\n");
            reviewJson.append("    }");
        }

        reviewJson.append("\n  ],\n");
        reviewJson.append("  \"summary\": {\n");
        reviewJson.append("    \"totalIssues\": ").append(Math.min(3, parseResult.getClauses().size())).append(",\n");
        reviewJson.append("    \"highRisk\": 1,\n");
        reviewJson.append("    \"mediumRisk\": 1,\n");
        reviewJson.append("    \"lowRisk\": 1,\n");
        reviewJson.append("    \"recommendation\": \"建议仔细审查上述风险点，并与法务部门协商修改相关条款。\"\n");
        reviewJson.append("  }\n");
        reviewJson.append("}");

        return reviewJson.toString();
    }
}