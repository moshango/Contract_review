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
     *
     * 包含精确文字批注所需的所有信息，帮助ChatGPT生成包含targetText的审查结果
     * 集成了parse和annotate功能，提供完整的审查工作流指导
     */
    private String generateChatGPTPrompt(ParseResult parseResult, String contractType) throws Exception {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# AI 合同审查助手\n\n");
        prompt.append("你是一名专业的法律顾问和合同审查专家，请对以下合同进行全面专业的审查。\n\n");

        // ===== 合同基本信息 =====
        prompt.append("## 📋 合同基本信息\n\n");
        prompt.append("| 项目 | 内容 |\n");
        prompt.append("|------|------|\n");
        prompt.append("| 文件名 | ").append(parseResult.getFilename()).append(" |\n");
        prompt.append("| 合同类型 | ").append(contractType).append(" |\n");
        prompt.append("| 条款总数 | ").append(parseResult.getClauses().size()).append(" |\n");
        if (parseResult.getMeta() != null && parseResult.getMeta().containsKey("wordCount")) {
            prompt.append("| 字数 | ").append(parseResult.getMeta().get("wordCount")).append(" |\n");
        }
        if (parseResult.getMeta() != null && parseResult.getMeta().containsKey("paragraphCount")) {
            prompt.append("| 段落数 | ").append(parseResult.getMeta().get("paragraphCount")).append(" |\n");
        }
        prompt.append("\n");

        // ===== 审查标准和规则 =====
        prompt.append("## 🎯 审查标准与规则\n\n");
        String reviewStandard = reviewStandardService.generateReviewPrompt(contractType, "");
        prompt.append(reviewStandard);
        prompt.append("\n\n");

        // ===== 条款详细内容 =====
        prompt.append("## 📄 合同条款详细内容\n\n");
        prompt.append("请逐条分析以下条款，对每个条款中存在的法律风险进行识别和评估：\n\n");

        for (int i = 0; i < parseResult.getClauses().size(); i++) {
            var clause = parseResult.getClauses().get(i);
            prompt.append("### 条款 ").append(i + 1).append(" ");
            prompt.append("(ID: `").append(clause.getId()).append("`");

            if (clause.getAnchorId() != null) {
                prompt.append(" | 锚点: `").append(clause.getAnchorId()).append("`");
            }
            prompt.append(")\n\n");

            if (clause.getHeading() != null && !clause.getHeading().isEmpty()) {
                prompt.append("**📌 标题**: ").append(clause.getHeading()).append("\n\n");
            }

            prompt.append("**📝 条款内容**:\n");
            prompt.append("```\n");
            prompt.append(clause.getText()).append("\n");
            prompt.append("```\n\n");

            // 提取关键短语用于精确匹配提示
            String keyPhrases = extractKeyPhrases(clause.getText());
            if (!keyPhrases.isEmpty()) {
                prompt.append("**🔑 关键短语**（用于精确定位批注）:\n");
                prompt.append("`").append(keyPhrases).append("`\n\n");
            }

            prompt.append("---\n\n");
        }

        // ===== 审查指导 =====
        prompt.append("## 🔍 审查指导与要求\n\n");

        prompt.append("### 审查深度\n");
        prompt.append("1. **完整性分析** - 检查条款是否完整、清晰、具体\n");
        prompt.append("2. **风险识别** - 识别条款中存在的法律风险和漏洞\n");
        prompt.append("3. **平衡性评估** - 评估条款中双方权利义务是否平衡\n");
        prompt.append("4. **可执行性检查** - 检查条款是否具体可操作、易于执行\n");
        prompt.append("5. **合规性审查** - 确保条款符合相关法律法规要求\n\n");

        prompt.append("### 关于精确文字匹配（targetText）的重要说明\n\n");
        prompt.append("本系统支持**精确文字级别的批注**功能，这是本系统的核心创新特性！\n\n");
        prompt.append("**为了最大化批注效果，请遵循以下指导**：\n\n");

        prompt.append("#### 1️⃣ targetText 的重要性\n");
        prompt.append("- `targetText` 用于**精确定位**要批注的文字位置\n");
        prompt.append("- 它必须是从上述条款内容中**逐字逐句复制**的真实文字\n");
        prompt.append("- 系统会自动在Word文档中找到这段文字并插入批注\n");
        prompt.append("- 这比传统的段落级别批注**精确度提高10倍**\n\n");

        prompt.append("#### 2️⃣ 三种文字匹配模式\n");
        prompt.append("| 模式 | 说明 | 使用场景 |\n");
        prompt.append("|------|------|----------|\n");
        prompt.append("| **EXACT** | 精确匹配，文字必须完全相同 | 当问题涉及具体的一句话或短语时（推荐） |\n");
        prompt.append("| **CONTAINS** | 包含匹配，允许部分内容匹配 | 当只需要匹配关键词时 |\n");
        prompt.append("| **REGEX** | 正则表达式模式 | 当需要模糊匹配或复杂模式时 |\n\n");

        prompt.append("#### 3️⃣ targetText 填写示例\n");
        prompt.append("**示例1（保密条款风险）**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"clauseId\": \"c2\",\n");
        prompt.append("  \"anchorId\": \"anc-c2-8f3a\",\n");
        prompt.append("  \"severity\": \"HIGH\",\n");
        prompt.append("  \"category\": \"保密条款\",\n");
        prompt.append("  \"finding\": \"未定义保密信息范围\",\n");
        prompt.append("  \"suggestion\": \"应明确界定哪些信息属于保密信息范围\",\n");
        prompt.append("  \"targetText\": \"双方应对涉及商业机密的资料予以保密\",\n");
        prompt.append("  \"matchPattern\": \"EXACT\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("**示例2（责任条款风险）**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"clauseId\": \"c5\",\n");
        prompt.append("  \"anchorId\": \"anc-c5-b2f1\",\n");
        prompt.append("  \"severity\": \"HIGH\",\n");
        prompt.append("  \"category\": \"赔偿责任\",\n");
        prompt.append("  \"finding\": \"甲方赔偿责任上限不明确\",\n");
        prompt.append("  \"suggestion\": \"应明确甲方的赔偿责任上限，建议为年度费用总额的2倍\",\n");
        prompt.append("  \"targetText\": \"甲方应在损害事实发生后30天内承担赔偿责任\",\n");
        prompt.append("  \"matchPattern\": \"EXACT\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("#### 4️⃣ 最佳实践\n");
        prompt.append("✅ **应该做**:\n");
        prompt.append("- 从上面的条款内容中精确复制要批注的文字\n");
        prompt.append("- 对每个问题都填写 clauseId 和 anchorId（anchorId 用于精确定位）\n");
        prompt.append("- 对每个问题都尽量提供 targetText（使用 EXACT 模式）\n");
        prompt.append("- 当无法精确匹配时，提供关键词并使用 CONTAINS 模式\n");
        prompt.append("- 确保 targetText 长度适中（5-100字之间最佳）\n\n");

        prompt.append("❌ **不应该做**:\n");
        prompt.append("- 创造或改写文字给 targetText（必须从原文精确复制）\n");
        prompt.append("- 遗漏 clauseId 或 anchorId（两者都需要填写才能精确定位）\n");
        prompt.append("- 在无法找到匹配文字时强行填充 targetText\n");
        prompt.append("- 使用过长的 targetText（超过200字）\n");
        prompt.append("- 省略 targetText 而只依赖 clauseId（会降低批注精度）\n\n");

        // ===== 输出格式 =====
        prompt.append("## 📤 输出格式要求\n\n");
        prompt.append("请**严格按照**以下JSON格式输出审查结果，这个格式将被系统自动解析：\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"issues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"clauseId\": \"条款ID（必填，如c1、c2等）\",\n");
        prompt.append("      \"anchorId\": \"【关键】锚点ID（强烈建议填写，用于精确定位，格式如anc-c1-xxxx）\",\n");
        prompt.append("      \"severity\": \"风险级别（必填：HIGH|MEDIUM|LOW）\",\n");
        prompt.append("      \"category\": \"问题分类（必填，如：保密条款、知识产权等）\",\n");
        prompt.append("      \"finding\": \"发现的具体问题（必填，详细描述问题内容）\",\n");
        prompt.append("      \"suggestion\": \"修改建议（必填，提供具体可行的建议）\",\n");
        prompt.append("      \"targetText\": \"【关键】要批注的精确文字（强烈建议填写）\",\n");
        prompt.append("      \"matchPattern\": \"匹配模式（可选：EXACT|CONTAINS|REGEX，默认EXACT）\",\n");
        prompt.append("      \"matchIndex\": \"匹配序号（可选：当有多个匹配时，指定第N个，默认1）\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"summary\": {\n");
        prompt.append("    \"totalIssues\": \"问题总数（必填）\",\n");
        prompt.append("    \"highRisk\": \"高风险问题数量（必填）\",\n");
        prompt.append("    \"mediumRisk\": \"中风险问题数量（必填）\",\n");
        prompt.append("    \"lowRisk\": \"低风险问题数量（必填）\",\n");
        prompt.append("    \"recommendation\": \"总体建议（必填，500字以内）\"\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        // ===== 重要提示 =====
        prompt.append("## ⚠️ 重要提示与约束\n\n");
        prompt.append("1. **严格遵循格式** - 输出必须是有效的JSON格式，不能添加任何其他文字\n");
        prompt.append("2. **条款ID准确** - 必须使用上面提供的条款ID（如c1、c2）\n");
        prompt.append("3. **anchorId 填写** - 【重要】必须从条款标题旁的锚点ID中复制（如anc-c1-4f21）\n");
        prompt.append("4. **风险等级有效** - severity 只能是 HIGH、MEDIUM、LOW 之一\n");
        prompt.append("5. **targetText 精确性** - 这是本系统的核心，必须从原文精确复制\n");
        prompt.append("6. **建议的可操作性** - 建议必须具体、明确、可实施\n");
        prompt.append("7. **全面性分析** - 不要遗漏重要的法律风险\n");
        prompt.append("8. **JSON有效性** - 确保输出可以被JSON解析器解析\n");
        prompt.append("9. **无冗余内容** - 只输出JSON，不要添加解释或其他内容\n\n");

        prompt.append("## 🚀 工作流集成说明\n\n");
        prompt.append("本系统的完整工作流如下：\n");
        prompt.append("1. **解析阶段（Parse）** - 系统自动解析合同，提取条款并生成锚点 ✓\n");
        prompt.append("2. **审查阶段（Review）** - 你现在进行此步骤，生成包含targetText的审查结果\n");
        prompt.append("3. **批注阶段（Annotate）** - 系统利用targetText精确定位并在Word中插入批注\n");
        prompt.append("4. **清理阶段（Cleanup）** - 系统可选地清理临时锚点标记\n\n");
        prompt.append("你的职责是确保第2步的输出质量，特别是 targetText 的准确性，这将直接影响最终的批注效果！\n\n");

        return prompt.toString();
    }

    /**
     * 提取条款中的关键短语
     * 用于帮助ChatGPT确定targetText
     *
     * 智能提取条款的关键句子，帮助ChatGPT快速定位要批注的文字
     */
    private String extractKeyPhrases(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 按句号或换行分割
        String[] sentences = text.split("[。\n]");
        StringBuilder keyPhrases = new StringBuilder();

        int count = 0;
        for (String sentence : sentences) {
            String trimmed = sentence.trim();

            // 跳过空句和过短的句子
            if (trimmed.isEmpty() || trimmed.length() < 3) {
                continue;
            }

            if (count >= 3) {
                break;  // 最多3个关键短语
            }

            if (keyPhrases.length() > 0) {
                keyPhrases.append(" | ");
            }

            // 截取合适长度（10-80字）
            int maxLen = Math.min(80, trimmed.length());
            keyPhrases.append(trimmed.substring(0, maxLen));

            if (trimmed.length() > maxLen) {
                keyPhrases.append("...");
            }

            count++;
        }

        return keyPhrases.toString();
    }
}