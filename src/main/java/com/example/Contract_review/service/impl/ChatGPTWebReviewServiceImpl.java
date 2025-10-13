package com.example.Contract_review.service.impl;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.service.AIReviewService;
import com.example.Contract_review.service.ReviewStandardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ChatGPT 网页版集成服务
 * 生成适合复制到 https://chatgpt.com/ 的提示文本
 */
@Service("chatgptWebReviewService")
public class ChatGPTWebReviewServiceImpl implements AIReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ChatGPTWebReviewServiceImpl.class);

    @Autowired
    private ReviewStandardService reviewStandardService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String reviewContract(ParseResult parseResult, String contractType) throws Exception {
        logger.info("生成ChatGPT网页版提示: contractType={}", contractType);

        // 生成适合ChatGPT的提示文本
        String prompt = generateChatGPTPrompt(parseResult, contractType);

        logger.info("ChatGPT提示生成完成，长度: {}", prompt.length());

        // 返回一个指导用户如何使用的JSON
        return "{\n" +
               "  \"status\": \"ready_for_chatgpt\",\n" +
               "  \"message\": \"请复制下面的提示到 https://chatgpt.com/\",\n" +
               "  \"prompt\": " + objectMapper.writeValueAsString(prompt) + ",\n" +
               "  \"instructions\": [\n" +
               "    \"1. 访问 https://chatgpt.com/\",\n" +
               "    \"2. 复制上面的 prompt 内容\",\n" +
               "    \"3. 粘贴到ChatGPT对话框\",\n" +
               "    \"4. 等待ChatGPT返回审查结果\",\n" +
               "    \"5. 复制ChatGPT的JSON回复\",\n" +
               "    \"6. 使用系统的'导入审查结果'功能\"\n" +
               "  ]\n" +
               "}";
    }

    @Override
    public boolean isAvailable() {
        return true; // ChatGPT网页版总是可用的
    }

    @Override
    public String getProviderName() {
        return "ChatGPT 网页版";
    }

    /**
     * 生成适合ChatGPT的详细提示
     */
    private String generateChatGPTPrompt(ParseResult parseResult, String contractType) throws Exception {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 合同审查任务\n\n");
        prompt.append("你是一名专业的法律顾问，请帮我审查以下合同内容。\n\n");

        prompt.append("## 合同信息\n");
        prompt.append("- **文件名**: ").append(parseResult.getFilename()).append("\n");
        prompt.append("- **合同类型**: ").append(contractType).append("\n");
        prompt.append("- **条款总数**: ").append(parseResult.getClauses().size()).append("\n\n");

        prompt.append("## 审查标准\n");
        String reviewStandard = reviewStandardService.generateReviewPrompt(contractType, "");
        prompt.append(reviewStandard.substring(0, Math.min(1000, reviewStandard.length())));
        prompt.append("\n\n");

        prompt.append("## 合同条款内容\n\n");

        // 添加每个条款的详细信息
        for (int i = 0; i < parseResult.getClauses().size(); i++) {
            var clause = parseResult.getClauses().get(i);
            prompt.append("### 条款 ").append(i + 1).append(" (ID: ").append(clause.getId()).append(")\n");

            if (clause.getHeading() != null && !clause.getHeading().isEmpty()) {
                prompt.append("**标题**: ").append(clause.getHeading()).append("\n\n");
            }

            prompt.append("**内容**: ").append(clause.getText()).append("\n");

            if (clause.getAnchorId() != null) {
                prompt.append("**锚点ID**: ").append(clause.getAnchorId()).append("\n");
            }

            prompt.append("\n---\n\n");
        }

        prompt.append("## 输出要求\n\n");
        prompt.append("请严格按照以下JSON格式输出审查结果，不要添加任何其他文字说明：\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"issues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"clauseId\": \"条款ID（如c1、c2等）\",\n");
        prompt.append("      \"anchorId\": \"锚点ID（如果有的话）\",\n");
        prompt.append("      \"severity\": \"风险级别：HIGH/MEDIUM/LOW\",\n");
        prompt.append("      \"category\": \"问题类别（如：保密条款、责任条款等）\",\n");
        prompt.append("      \"finding\": \"发现的具体问题\",\n");
        prompt.append("      \"suggestion\": \"修改建议\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"summary\": {\n");
        prompt.append("    \"totalIssues\": \"问题总数\",\n");
        prompt.append("    \"highRisk\": \"高风险问题数量\",\n");
        prompt.append("    \"mediumRisk\": \"中风险问题数量\",\n");
        prompt.append("    \"lowRisk\": \"低风险问题数量\",\n");
        prompt.append("    \"recommendation\": \"总体建议\"\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意事项：\n");
        prompt.append("- 必须严格使用上面提供的条款ID（clauseId）\n");
        prompt.append("- 如果条款有锚点ID，请在审查结果中包含对应的anchorId\n");
        prompt.append("- 风险级别只能是 HIGH、MEDIUM、LOW 之一\n");
        prompt.append("- 请提供具体、实用的法律建议\n");
        prompt.append("- 只输出JSON格式，不要添加任何解释文字\n");

        return prompt.toString();
    }
}