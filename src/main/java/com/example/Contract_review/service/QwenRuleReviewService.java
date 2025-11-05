package com.example.Contract_review.service;

import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.model.ReviewRule;
import com.example.Contract_review.model.RuleMatchResult;
import com.example.Contract_review.model.Clause;
import com.example.Contract_review.qwen.client.QwenClient;
import com.example.Contract_review.qwen.dto.ChatMessage;
import com.example.Contract_review.qwen.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Qwen 规则审查服务
 *
 * 用于将规则审查生成的Prompt传送给Qwen模型，获取JSON格式的审查结果
 * 支持一键式审查工作流
 */
@Slf4j
@Service
public class QwenRuleReviewService {

    @Autowired
    private QwenClient qwenClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRulesService reviewRulesService;

    @Value("${diagnostics.rules.write-keyword-logs:true}")
    private boolean writeKeywordDiagnostics;

    @Value("${rules.default-contract-type:通用合同}")
    private String defaultContractType;

    private static final String QWEN_MODEL = "qwen-max-latest";

    /**
     * 使用Qwen进行合同审查
     *
     * @param prompt 规则审查生成的Prompt
     * @return 审查结果JSON，包含issues数组
     */
    public String reviewContractWithQwen(String prompt) {
        log.info("=== 开始使用Qwen进行合同审查 ===");
        log.debug("Prompt长度: {} 字符", prompt.length());

        try {
            // 构建系统消息和用户消息
            ChatMessage systemMsg = ChatMessage.builder()
                .role("system")
                .content("你是一位资深的法律合同审查专家。你的任务是严格按照用户提供的规则和条款检查清单，" +
                        "对合同进行审查，并返回结构化的JSON格式的审查结果。" +
                        "返回的JSON必须包含'issues'数组，其中每个问题包含：" +
                        "anchorId（锚点ID）、clauseId（条款ID）、severity（严重性：HIGH/MEDIUM/LOW）、" +
                        "category（问题类别）、finding（发现的问题）、suggestion（建议修改）。" +
                        "只返回JSON，不要返回任何其他文本。")
                .build();

            ChatMessage userMsg = ChatMessage.builder()
                .role("user")
                .content(prompt)
                .build();

            List<ChatMessage> messages = Arrays.asList(systemMsg, userMsg);

            log.info("向Qwen发送审查请求，模型: {}", QWEN_MODEL);

            // 调用Qwen API
            ChatResponse response = qwenClient.chat(messages, QWEN_MODEL).block();

            if (response == null) {
                log.error("Qwen返回null响应");
                return buildErrorResponse("Qwen返回null响应");
            }

            String responseContent = response.extractContent();
            log.info("Qwen返回内容长度: {} 字符", responseContent.length());
            log.debug("Qwen原始返回: {}", responseContent);

            // 提取和验证JSON
            String jsonResult = extractJsonFromResponse(responseContent);

            if (jsonResult == null || jsonResult.isEmpty()) {
                log.warn("无法从Qwen返回中提取JSON");
                return buildErrorResponse("无法解析Qwen的返回结果");
            }

            // 验证JSON格式
            try {
                ObjectNode json = (ObjectNode) objectMapper.readTree(jsonResult);

                // 确保包含issues数组
                if (!json.has("issues")) {
                    json.putArray("issues");
                    log.warn("Qwen返回的JSON不包含issues数组，已添加空数组");
                }

                log.info("✓ 审查完成，检出 {} 个问题",
                    json.has("issues") ? json.get("issues").size() : 0);

                return objectMapper.writeValueAsString(json);

            } catch (Exception e) {
                log.error("JSON解析失败: {}", e.getMessage());
                log.debug("尝试解析的JSON内容: {}", jsonResult);

                // 尝试修复常见的JSON格式错误
                String fixedJson = fixJsonFormat(jsonResult);
                return fixedJson;
            }

        } catch (Exception e) {
            log.error("Qwen审查失败", e);
            return buildErrorResponse("Qwen审查失败: " + e.getMessage());
        }
    }

    /**
     * 从Qwen返回的文本中提取JSON
     * 处理多种格式：
     * 1. 纯JSON
     * 2. 包含```json...```的代码块
     * 3. 包含说明文字的JSON
     *
     * @param response Qwen返回的完整文本
     * @return 提取的JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        response = response.trim();

        // 尝试方式1: 提取```json...```代码块
        Pattern jsonBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher jsonBlockMatcher = jsonBlockPattern.matcher(response);
        if (jsonBlockMatcher.find()) {
            String jsonContent = jsonBlockMatcher.group(1).trim();
            log.debug("从代码块中提取JSON");
            return jsonContent;
        }

        // 尝试方式2: 查找第一个 { 和最后一个 }
        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            String jsonContent = response.substring(firstBrace, lastBrace + 1);
            log.debug("从大括号中提取JSON");
            return jsonContent;
        }

        // 尝试方式3: 直接作为JSON
        if (response.startsWith("{") && response.endsWith("}")) {
            log.debug("响应本身就是JSON");
            return response;
        }

        log.warn("无法识别JSON格式");
        return null;
    }

    /**
     * 修复常见的JSON格式错误
     *
     * @param jsonStr 原始JSON字符串
     * @return 修复后的JSON
     */
    private String fixJsonFormat(String jsonStr) {
        try {
            // 移除注释（如果有）
            jsonStr = jsonStr.replaceAll("//.*", "");
            jsonStr = jsonStr.replaceAll("/\\*.*?\\*/", "");

            // 尝试解析修复后的JSON
            ObjectNode json = (ObjectNode) objectMapper.readTree(jsonStr);

            // 确保包含issues数组
            if (!json.has("issues")) {
                json.putArray("issues");
            }

            return objectMapper.writeValueAsString(json);

        } catch (Exception e) {
            log.error("JSON修复失败: {}", e.getMessage());
            return buildErrorResponse("JSON格式错误");
        }
    }

    /**
     * 构建错误响应
     *
     * @param errorMessage 错误消息
     * @return 包含错误信息的JSON字符串
     */
    private String buildErrorResponse(String errorMessage) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", errorMessage);
            error.putArray("issues");

            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            log.error("构建错误响应失败", e);
            return "{\"success\": false, \"error\": \"系统错误\", \"issues\": []}";
        }
    }

    /**
     * 解析Qwen返回的审查结果为ReviewIssue列表
     *
     * @param reviewJsonStr JSON格式的审查结果
     * @return ReviewIssue列表
     */
    public List<ReviewIssue> parseReviewResults(String reviewJsonStr) {
        List<ReviewIssue> issues = new ArrayList<>();

        try {
            ObjectNode reviewJson = (ObjectNode) objectMapper.readTree(reviewJsonStr);

            if (reviewJson.has("issues") && reviewJson.get("issues").isArray()) {
                ArrayNode issuesArray = (ArrayNode) reviewJson.get("issues");

                for (int i = 0; i < issuesArray.size(); i++) {
                    ObjectNode issueNode = (ObjectNode) issuesArray.get(i);

                    ReviewIssue issue = new ReviewIssue();
                    issue.setAnchorId(getStringValue(issueNode, "anchorId"));
                    issue.setClauseId(getStringValue(issueNode, "clauseId"));
                    issue.setSeverity(getStringValue(issueNode, "severity", "MEDIUM"));
                    issue.setCategory(getStringValue(issueNode, "category", "General"));
                    issue.setFinding(getStringValue(issueNode, "finding"));
                    issue.setSuggestion(getStringValue(issueNode, "suggestion"));

                    // 【恢复精确匹配功能】提取targetText字段用于精确文本匹配
                    String targetText = getStringValue(issueNode, "targetText");
                    if (targetText != null && !targetText.isEmpty()) {
                        issue.setTargetText(targetText);
                        log.debug("提取targetText用于精确定位：{}",
                                 targetText.length() > 50 ? targetText.substring(0, 50) + "..." : targetText);
                    }

                    // 【恢复精确匹配功能】提取matchPattern字段（可选）
                    String matchPattern = getStringValue(issueNode, "matchPattern");
                    if (matchPattern != null && !matchPattern.isEmpty()) {
                        issue.setMatchPattern(matchPattern);
                    }

                    if (issue.getFinding() != null && !issue.getFinding().isEmpty()) {
                        issues.add(issue);
                        log.debug("解析审查问题: anchorId={}, severity={}, category={}, 有精确文本={}",
                            issue.getAnchorId(), issue.getSeverity(), issue.getCategory(),
                            issue.getTargetText() != null ? "✓" : "✗");
                    }
                }
            }

            log.info("✓ 成功解析 {} 个审查问题", issues.size());

        } catch (Exception e) {
            log.error("解析审查结果失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 从JSON对象中获取字符串值
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    private String getStringValue(ObjectNode node, String fieldName, String defaultValue) {
        try {
            if (node.has(fieldName)) {
                String value = node.get(fieldName).asText();
                return value != null && !value.isEmpty() ? value : defaultValue;
            }
        } catch (Exception e) {
            log.debug("获取字段值失败: {}", fieldName);
        }
        return defaultValue;
    }

    /**
     * 从JSON对象中获取字符串值
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @return 字段值或null
     */
    private String getStringValue(ObjectNode node, String fieldName) {
        return getStringValue(node, fieldName, null);
    }

    /**
     * 获取Qwen客户端配置信息
     * 用于检查Qwen服务是否可用
     *
     * @return 配置信息Map
     */
    public java.util.Map<String, String> getQwenConfig() {
        return qwenClient.getConfig();
    }

    /**
     * 生成规则审查Prompt（集成规则匹配）
     *
     * 根据解析的合同和审查立场，先进行规则匹配，再生成适合Qwen的规则审查提示
     *
     * @param parseResult 解析的合同结果
     * @param stance 审查立场（neutral、A方、B方等）
     * @return 生成的Prompt文本
     */
    public String generateRuleReviewPrompt(ParseResult parseResult, String stance) {
        log.info("生成规则审查Prompt，条款数: {}, 立场: {}",
            parseResult.getClauses().size(), stance);

        // 【新增】步骤1：进行规则匹配
        log.info("步骤1: 进行规则匹配...");
        List<RuleMatchResult> matchResults = performRuleMatching(parseResult, stance);
        log.info("✓ 规则匹配完成，匹配到 {} 个条款", matchResults.size());

        StringBuilder prompt = new StringBuilder();

        // 系统角色说明
        prompt.append("你是一位资深的法律合同审查专家。");
        if ("A方".equals(stance) || "甲方".equals(stance)) {
            prompt.append("请从A方（甲方）的角度对合同进行全面审查。\n");
        } else if ("B方".equals(stance) || "乙方".equals(stance)) {
            prompt.append("请从B方（乙方）的角度对合同进行全面审查。\n");
        } else {
            prompt.append("请以中立的角度对合同进行全面、专业的审查。\n");
        }

        // 合同基本信息
        prompt.append("\n## 合同基本信息\n");
        prompt.append("- 文件名: ").append(parseResult.getFilename()).append("\n");
        prompt.append("- 条款总数: ").append(parseResult.getClauses().size()).append("\n");
        prompt.append("- 匹配规则条款数: ").append(matchResults.size()).append("\n");
        if (parseResult.getPartyA() != null) {
            prompt.append("- A方: ").append(parseResult.getPartyA()).append("\n");
        }
        if (parseResult.getPartyB() != null) {
            prompt.append("- B方: ").append(parseResult.getPartyB()).append("\n");
        }

        // 【修改】只显示匹配到规则的条款
        if (matchResults.isEmpty()) {
            prompt.append("\n## 审查结果\n");
            prompt.append("经过规则匹配，未发现需要特别关注的条款。\n");
            prompt.append("建议进行常规的合同审查。\n");
        } else {
            prompt.append("\n## 需要重点审查的条款\n");
            prompt.append("以下条款通过规则匹配识别出潜在风险，请重点审查：\n\n");

            for (int i = 0; i < matchResults.size(); i++) {
                RuleMatchResult matchResult = matchResults.get(i);
                prompt.append("### 条款").append(i + 1).append(" (ID: ").append(matchResult.getClauseId());
                
                if (matchResult.getAnchorId() != null && !matchResult.getAnchorId().isEmpty()) {
                    prompt.append(" | 锚点: ").append(matchResult.getAnchorId());
                }
                prompt.append(")\n\n");

                if (matchResult.getClauseHeading() != null && !matchResult.getClauseHeading().isEmpty()) {
                    prompt.append("**标题**: ").append(matchResult.getClauseHeading()).append("\n\n");
                }

                prompt.append("**内容**:\n").append(matchResult.getClauseText()).append("\n\n");

                // 【新增】显示匹配的规则和检查要点
                prompt.append("**匹配的规则** (共").append(matchResult.getMatchCount()).append("条):\n");
                for (ReviewRule rule : matchResult.getMatchedRules()) {
                    prompt.append("- **风险等级**: ").append(rule.getRisk().toUpperCase()).append("\n");
                    
                    if (rule.getMatchedKeywords() != null && !rule.getMatchedKeywords().isEmpty()) {
                        prompt.append("  **匹配关键词**: ").append(String.join(", ", rule.getMatchedKeywords())).append("\n");
                    }
                    
                    if (rule.getChecklist() != null && !rule.getChecklist().isEmpty()) {
                        prompt.append("  **检查要点**:\n");
                        for (String line : rule.getChecklist().split("\n")) {
                            prompt.append("    ").append(line).append("\n");
                        }
                    }
                    
                    // 根据立场显示建议
                    if ("A方".equals(stance) || "甲方".equals(stance)) {
                        if (rule.getSuggestA() != null && !rule.getSuggestA().isEmpty()) {
                            prompt.append("  **甲方建议**: ").append(rule.getSuggestA()).append("\n");
                        }
                    } else if ("B方".equals(stance) || "乙方".equals(stance)) {
                        if (rule.getSuggestB() != null && !rule.getSuggestB().isEmpty()) {
                            prompt.append("  **乙方建议**: ").append(rule.getSuggestB()).append("\n");
                        }
                    }
                    prompt.append("\n");
                }
                prompt.append("---\n\n");
            }
        }

        // 审查指导
        prompt.append("## 审查指导\n\n");
        prompt.append("请按照以下维度进行审查：\n");
        prompt.append("1. **完整性** - 条款是否完整、清晰、具体\n");
        prompt.append("2. **风险识别** - 识别条款中存在的法律风险和漏洞\n");
        prompt.append("3. **平衡性** - 评估条款中双方权利义务是否平衡\n");
        prompt.append("4. **可执行性** - 检查条款是否具体可操作\n");
        prompt.append("5. **合规性** - 确保条款符合相关法律法规\n\n");

        // 基础错误检查（通用）
        prompt.append("## 基础错误检查（请始终执行，即使未命中规则）\n");
        prompt.append("- **错别字/标点错误**：识别明显的字词错误、标点误用或缺失。\n");
        prompt.append("- **重复或冗余**：相同或近似内容的重复、表述冗长不必要。\n");
        prompt.append("- **前后语义不一致**：同一术语或义务在不同条款前后冲突或矛盾。\n");
        prompt.append("- **偏向性表述**：明显偏向某一方，损害另一方合理权益的措辞。\n");
        prompt.append("- **定义缺失/引用不当**：关键术语未定义或被错误引用。\n");
        prompt.append("- **日期/金额/比例等数值错误**：单位、数值、范围、上下限、四舍五入方式等不一致或不明确。\n\n");

        // 输出格式要求
        prompt.append("## 输出格式要求\n\n");
        prompt.append("必须返回JSON格式的审查结果，包含以下结构：\n");
        prompt.append("{\n");
        prompt.append("  \"issues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"anchorId\": \"锚点ID（如果有）\",\n");
        prompt.append("      \"clauseId\": \"条款ID\",\n");
        prompt.append("      \"severity\": \"HIGH|MEDIUM|LOW\",\n");
        prompt.append("      \"category\": \"问题类别（如：违约条款、保密条款等）\",\n");
        prompt.append("      \"finding\": \"发现的问题描述\",\n");
        prompt.append("      \"targetText\": \"要批注的具体文字（问题所在的精确文本，用于精确定位批注位置）\",\n");
        prompt.append("      \"suggestion\": \"建议修改方案\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("特别说明：\n");
        prompt.append("- anchorId：条款解析时生成的锚点ID，格式为 anc-c{X}-{hash}，请在输出中保留\n");
        prompt.append("- targetText：【重要】问题的精确文本，是从条款内容中抽取的具体字句，\n");
        prompt.append("  用于Word文档中精确定位批注位置。必须是条款内容中的真实文本。\n");
        prompt.append("  例如：如果问题是'条款中缺少具体的赔偿金额'，\n");
        prompt.append("  targetText应该是条款中相关的实际文字，如'赔偿责任'或'甲方应承担'等。\n\n");
        prompt.append("只返回JSON，不要返回任何其他文本。");

        log.debug("Prompt生成完成，长度: {}", prompt.length());

        recordDiagnostics(parseResult, stance, matchResults, prompt.toString());

        return prompt.toString();
    }

    private void recordDiagnostics(ParseResult parseResult, String stance,
                                   List<RuleMatchResult> matchResults, String prompt) {
        try {
            Path baseDir = Paths.get(System.getProperty("user.dir"), "文档中心", "02_实现和修复总结", "维测日志");
            Files.createDirectories(baseDir);

            String safeName = parseResult.getFilename() != null
                    ? parseResult.getFilename().replaceAll("[\\\\/:*?\"<>|]", "_")
                    : "合同";
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path diagFile = baseDir.resolve(String.format("规则匹配维测_%s_%s.txt", safeName, timestamp));

            try (BufferedWriter writer = Files.newBufferedWriter(diagFile, StandardCharsets.UTF_8)) {
                writer.write("=== 规则匹配维测日志 ===\n");
                writer.write("生成时间: " + timestamp + "\n");
                writer.write("立场: " + stance + "\n");
                writer.write("源文件: " + parseResult.getFilename() + "\n");
                writer.write("条款总数: " + parseResult.getClauses().size() + "\n");
                writer.write("匹配条款数: " + matchResults.size() + "\n\n");

                writer.write("=== 规则加载与过滤 ===\n");
                try {
                    int totalRules = reviewRulesService.loadRules().size();
                    writer.write("原始规则数: " + totalRules + "\n");
                    if (defaultContractType == null || defaultContractType.trim().isEmpty() || "ALL".equalsIgnoreCase(defaultContractType.trim())) {
                        writer.write("使用全部规则，无合同类型过滤\n");
                        writer.write("适用规则数: " + totalRules + "\n");
                    } else {
                        writer.write("默认合同类型: " + defaultContractType + "\n");
                        writer.write("适用规则数: " + reviewRulesService.filterByContractType(defaultContractType).size() + "\n");
                    }
                } catch (Exception e) {
                    writer.write("规则统计异常: " + e.getMessage() + "\n");
                }

                if (writeKeywordDiagnostics) {
                    List<ReviewRule> diagnosticRules;
                    if (defaultContractType == null || defaultContractType.trim().isEmpty() || "ALL".equalsIgnoreCase(defaultContractType.trim())) {
                        diagnosticRules = reviewRulesService.loadRules();
                    } else {
                        diagnosticRules = reviewRulesService.filterByContractType(defaultContractType);
                    }

                    writer.write("\n=== 全量条款关键词匹配结果 ===\n");
                    for (Clause clause : parseResult.getClauses()) {
                        writer.write(String.format("[条款ID=%s] %s\n", clause.getId(), clause.getFullText()));
                        for (ReviewRule rule : diagnosticRules) {
                            boolean matched = rule.matches(clause.getFullText());
                            writer.write(String.format("  - 规则ID=%s, 命中=%s, 命中关键字=%s\n",
                                    rule.getId(), matched ? "✓" : "✗",
                                    rule.getMatchedKeywords() != null ? rule.getMatchedKeywords() : "[]"));
                        }
                        writer.write('\n');
                    }
                }

                int idx = 1;
                for (RuleMatchResult matchResult : matchResults) {
                    writer.write(String.format("## 匹配条款 %d\n", idx++));
                    writer.write("条款ID: " + matchResult.getClauseId() + "\n");
                    writer.write("锚点: " + (matchResult.getAnchorId() != null ? matchResult.getAnchorId() : "无") + "\n");
                    writer.write("标题: " + (matchResult.getClauseHeading() != null ? matchResult.getClauseHeading() : "（无）") + "\n");
                    writer.write("内容:\n" + matchResult.getClauseText() + "\n\n");

                    writer.write("匹配规则（数量: " + matchResult.getMatchCount() + "）:\n");
                    for (ReviewRule rule : matchResult.getMatchedRules()) {
                        writer.write(String.format("- 规则ID: %s, 风险: %s\n",
                                rule.getId(), rule.getRisk()));
                        if (rule.getMatchedKeywords() != null && !rule.getMatchedKeywords().isEmpty()) {
                            writer.write("  命中关键词: " + String.join(", ", rule.getMatchedKeywords()) + "\n");
                        }
                        if (rule.getKeywords() != null && !rule.getKeywords().isEmpty()) {
                            writer.write("  规则关键词: " + rule.getKeywords() + "\n");
                        }
                        if (rule.getRegex() != null && !rule.getRegex().isEmpty()) {
                            writer.write("  正则: " + rule.getRegex() + "\n");
                        }
                        if (rule.getChecklist() != null && !rule.getChecklist().isEmpty()) {
                            writer.write("  检查要点:\n");
                            for (String line : rule.getChecklist().split("\\n")) {
                                writer.write("    " + line + "\n");
                            }
                        }
                        writer.write("\n");
                    }
                    writer.write("\n");
                }

                writer.write("=== 生成 Prompt ===\n");
                writer.write(prompt);
                writer.write("\n");
            }

            log.info("【维测】规则匹配与Prompt信息已输出到: {}", diagFile);
            log.debug("【维测】Prompt全文:\n{}", prompt);
        } catch (Exception e) {
            log.warn("写入规则匹配维测日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 对外暴露的规则匹配方法，便于在控制器中决定是否调用 LLM。
     */
    public List<RuleMatchResult> matchRules(ParseResult parseResult, String stance) {
        return performRuleMatching(parseResult, stance);
    }

    /**
     * 执行规则匹配
     *
     * @param parseResult 解析的合同结果
     * @param stance 审查立场
     * @return 匹配结果列表
     */
    private List<RuleMatchResult> performRuleMatching(ParseResult parseResult, String stance) {
        try {
            // 加载规则
            List<ReviewRule> allRules = reviewRulesService.loadRules();
            if (allRules.isEmpty()) {
                log.warn("未加载到任何规则，跳过规则匹配");
                return new ArrayList<>();
            }

            // 按合同类型过滤规则（默认使用通用合同）
            String contractType = defaultContractType;
            List<ReviewRule> applicableRules;
            if (contractType == null || contractType.trim().isEmpty() || "ALL".equalsIgnoreCase(contractType.trim())) {
                applicableRules = allRules;
                log.info("加载了 {} 条规则，使用全部规则参与匹配", allRules.size());
            } else {
                applicableRules = reviewRulesService.filterByContractType(contractType);
                log.info("加载了 {} 条规则，适用合同类型 '{}' 的规则 {} 条", allRules.size(), contractType, applicableRules.size());
            }

            List<RuleMatchResult> matchResults = new ArrayList<>();
            List<Clause> clauses = parseResult.getClauses();

            // 对每个条款进行规则匹配
            for (Clause clause : clauses) {
                List<ReviewRule> matchedRules = applicableRules.stream()
                        .filter(rule -> {
                            boolean matched = rule.matches(clause.getFullText());
                            if (writeKeywordDiagnostics) {
                                recordKeywordDiagnostics(clause, rule);
                            }
                            return matched;
                        })
                        .collect(Collectors.toList());

                if (!matchedRules.isEmpty()) {
                    // 计算最高风险等级
                    String highestRisk = calculateHighestRisk(matchedRules);

                    RuleMatchResult matchResult = RuleMatchResult.builder()
                            .clauseId(clause.getId())
                            .anchorId(clause.getAnchorId())
                            .clauseHeading(clause.getHeading())
                            .clauseText(clause.getFullText())
                            .matchedRules(matchedRules)
                            .matchCount(matchedRules.size())
                            .highestRisk(highestRisk)
                            .build();

                    matchResults.add(matchResult);

                    log.info("【维测】条款 {} 命中 {} 条规则，锚点={}，最高风险={}",
                            clause.getId(), matchedRules.size(), clause.getAnchorId(), highestRisk);
                    String clausePreview = clause.getFullText();
                    if (clausePreview != null && clausePreview.length() > 200) {
                        clausePreview = clausePreview.substring(0, 200) + "...";
                    }
                    log.info("【维测】条款内容预览: {}", clausePreview);
                    for (ReviewRule rule : matchedRules) {
                        List<String> matchedKeywords = rule.getMatchedKeywords();
                        log.info("【维测】规则ID={}, 风险={}, 命中关键词={}",
                                rule.getId(), rule.getRisk(),
                                (matchedKeywords != null && !matchedKeywords.isEmpty()) ? matchedKeywords : "[]");
                    }
                }
            }

            log.info("规则匹配完成: 总条款 {} 个，匹配条款 {} 个，总匹配规则 {} 条",
                    clauses.size(), matchResults.size(),
                    matchResults.stream().mapToInt(RuleMatchResult::getMatchCount).sum());

            return matchResults;

        } catch (Exception e) {
            log.error("规则匹配失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 计算最高风险等级
     *
     * @param matchedRules 匹配的规则列表
     * @return 最高风险等级
     */
    private String calculateHighestRisk(List<ReviewRule> matchedRules) {
        if (matchedRules.isEmpty()) {
            return "low";
        }

        // 风险等级优先级：blocker > high > medium > low
        for (ReviewRule rule : matchedRules) {
            String risk = rule.getRisk().toLowerCase();
            if ("blocker".equals(risk)) {
                return "blocker";
            }
        }
        
        for (ReviewRule rule : matchedRules) {
            String risk = rule.getRisk().toLowerCase();
            if ("high".equals(risk)) {
                return "high";
            }
        }
        
        for (ReviewRule rule : matchedRules) {
            String risk = rule.getRisk().toLowerCase();
            if ("medium".equals(risk)) {
                return "medium";
            }
        }
        
        return "low";
    }

    /**
     * 记录关键词匹配诊断信息
     *
     * @param clause 条款
     * @param rule 规则
     */
    private void recordKeywordDiagnostics(Clause clause, ReviewRule rule) {
        if (writeKeywordDiagnostics && clause.getFullText() != null) {
            String fileNameSafe = clause.getFullText().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (fileNameSafe.length() > 50) {
                fileNameSafe = fileNameSafe.substring(0, 50);
            }
            // 统计匹配数量
            log.debug("【维测】条款 {} 匹配到 {} 条规则", clause.getId(), rule.getMatchedKeywords() != null ? rule.getMatchedKeywords().size() : 0);
        }
    }

    /**
     * 检查Qwen服务是否可用
     *
     * @return true 如果可用，false 否则
     */
    public boolean isQwenAvailable() {
        try {
            java.util.Map<String, String> config = getQwenConfig();
            String apiKey = config.getOrDefault("api-key", "");
            String baseUrl = config.getOrDefault("base-url", "");

            boolean available = !apiKey.isEmpty() && !baseUrl.isEmpty() && !apiKey.equals("sk-");

            if (available) {
                log.info("✓ Qwen服务可用");
            } else {
                log.warn("✗ Qwen服务不可用: API Key 或 Base URL 未配置");
            }

            return available;
        } catch (Exception e) {
            log.warn("无法检查Qwen服务: {}", e.getMessage());
            return false;
        }
    }
}
