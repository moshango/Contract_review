package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewRule;
import com.example.Contract_review.model.ReviewStandard;
import com.example.Contract_review.model.ReviewTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 审查标准管理服务
 *
 * 负责管理审查标准、规则和模板
 */
@Service
public class ReviewStandardService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewStandardService.class);

    @Autowired
    private ObjectMapper objectMapper;

    // 内存存储(生产环境应使用数据库)
    private final Map<String, ReviewStandard> standardsMap = new ConcurrentHashMap<>();
    private final Map<String, ReviewTemplate> templatesMap = new ConcurrentHashMap<>();

    /**
     * 系统启动时加载默认的审查标准
     */
    @PostConstruct
    public void initializeDefaultStandards() {
        try {
            loadDefaultStandards();
            loadDefaultTemplates();
            logger.info("审查标准初始化完成: 标准数={}, 模板数={}",
                       standardsMap.size(), templatesMap.size());
        } catch (Exception e) {
            logger.error("初始化审查标准失败", e);
        }
    }

    /**
     * 获取所有审查标准
     */
    public List<ReviewStandard> getAllStandards() {
        return new ArrayList<>(standardsMap.values());
    }

    /**
     * 根据合同类型获取审查标准
     */
    public ReviewStandard getStandardByContractType(String contractType) {
        return standardsMap.values().stream()
                .filter(standard -> standard.getContractType().equals(contractType) && standard.isEnabled())
                .findFirst()
                .orElse(getDefaultStandard());
    }

    /**
     * 获取默认审查标准
     */
    public ReviewStandard getDefaultStandard() {
        return standardsMap.get("default");
    }

    /**
     * 获取所有审查模板
     */
    public List<ReviewTemplate> getAllTemplates() {
        return new ArrayList<>(templatesMap.values());
    }

    /**
     * 根据合同类型获取审查模板
     */
    public ReviewTemplate getTemplateByContractType(String contractType) {
        return templatesMap.values().stream()
                .filter(template -> template.getContractType().equals(contractType))
                .findFirst()
                .orElse(getDefaultTemplate());
    }

    /**
     * 获取默认审查模板
     */
    public ReviewTemplate getDefaultTemplate() {
        return templatesMap.get("default");
    }

    /**
     * 生成审查Prompt
     */
    public String generateReviewPrompt(String contractType, String contractJson) {
        ReviewTemplate template = getTemplateByContractType(contractType);
        ReviewStandard standard = getStandardByContractType(contractType);

        String prompt = template.getPromptTemplate();

        // 替换占位符
        prompt = prompt.replace("{CONTRACT_JSON}", contractJson);
        prompt = prompt.replace("{CONTRACT_TYPE}", contractType);
        prompt = prompt.replace("{REVIEW_RULES}", formatRulesForPrompt(standard.getRules()));

        return prompt;
    }

    /**
     * 添加或更新审查标准
     */
    public void saveStandard(ReviewStandard standard) {
        if (standard.getCreatedAt() == null) {
            standard.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        standardsMap.put(standard.getId(), standard);
        logger.info("保存审查标准: {}", standard.getName());
    }

    /**
     * 添加或更新审查模板
     */
    public void saveTemplate(ReviewTemplate template) {
        if (template.getCreatedAt() == null) {
            template.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        templatesMap.put(template.getId(), template);
        logger.info("保存审查模板: {}", template.getName());
    }

    /**
     * 根据条款内容智能推荐适用的审查规则
     */
    public List<ReviewRule> getApplicableRules(String clauseText, String contractType) {
        ReviewStandard standard = getStandardByContractType(contractType);
        if (standard == null) {
            return new ArrayList<>();
        }

        return standard.getRules().stream()
                .filter(rule -> rule.isEnabled())
                .filter(rule -> isRuleApplicable(rule, clauseText))
                .sorted((r1, r2) -> Integer.compare(r2.getWeight(), r1.getWeight()))
                .collect(Collectors.toList());
    }

    /**
     * 加载默认审查标准
     */
    private void loadDefaultStandards() throws IOException {
        // 从resources目录加载默认标准配置
        try (InputStream is = new ClassPathResource("review-standards/default-standards.json").getInputStream()) {
            List<ReviewStandard> standards = objectMapper.readValue(is, new TypeReference<List<ReviewStandard>>() {});
            for (ReviewStandard standard : standards) {
                standardsMap.put(standard.getId(), standard);
            }
        } catch (IOException e) {
            // 如果文件不存在，创建默认标准
            createDefaultStandard();
        }
    }

    /**
     * 加载默认审查模板
     */
    private void loadDefaultTemplates() throws IOException {
        try (InputStream is = new ClassPathResource("review-templates/default-templates.json").getInputStream()) {
            List<ReviewTemplate> templates = objectMapper.readValue(is, new TypeReference<List<ReviewTemplate>>() {});
            for (ReviewTemplate template : templates) {
                templatesMap.put(template.getId(), template);
            }
        } catch (IOException e) {
            // 如果文件不存在，创建默认模板
            createDefaultTemplate();
        }
    }

    /**
     * 创建默认审查标准
     */
    private void createDefaultStandard() {
        ReviewStandard defaultStandard = ReviewStandard.builder()
                .id("default")
                .name("通用合同审查标准")
                .description("适用于各类合同的通用审查标准")
                .contractType("general")
                .rules(createDefaultRules())
                .version("1.0")
                .enabled(true)
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        standardsMap.put(defaultStandard.getId(), defaultStandard);
    }

    /**
     * 创建默认审查规则
     */
    private List<ReviewRule> createDefaultRules() {
        List<ReviewRule> rules = new ArrayList<>();

        // 高风险规则
        rules.add(ReviewRule.builder()
                .id("rule_confidentiality")
                .name("保密条款检查")
                .description("检查保密条款的完整性和合规性")
                .category("保密条款")
                .severity("HIGH")
                .targetClauses(List.of("保密", "商业秘密", "机密信息"))
                .condition("保密条款缺乏明确定义或违约责任不明确")
                .findingTemplate("保密条款存在风险：{具体问题}")
                .suggestionTemplate("建议：{具体改进建议}")
                .enabled(true)
                .weight(100)
                .build());

        rules.add(ReviewRule.builder()
                .id("rule_intellectual_property")
                .name("知识产权条款检查")
                .description("检查知识产权归属和使用权限")
                .category("知识产权")
                .severity("HIGH")
                .targetClauses(List.of("知识产权", "著作权", "专利权", "商标权"))
                .condition("知识产权归属不明确或权利义务失衡")
                .findingTemplate("知识产权条款存在重大风险：{具体问题}")
                .suggestionTemplate("建议：{具体改进建议}")
                .enabled(true)
                .weight(95)
                .build());

        // 中风险规则
        rules.add(ReviewRule.builder()
                .id("rule_payment")
                .name("付款条款检查")
                .description("检查付款条件和方式的合理性")
                .category("付款条款")
                .severity("MEDIUM")
                .targetClauses(List.of("付款", "支付", "结算", "费用"))
                .condition("付款条件不合理或缺乏保障机制")
                .findingTemplate("付款条款需要改进：{具体问题}")
                .suggestionTemplate("建议：{具体改进建议}")
                .enabled(true)
                .weight(80)
                .build());

        // 低风险规则
        rules.add(ReviewRule.builder()
                .id("rule_communication")
                .name("沟通条款检查")
                .description("检查沟通机制和联系方式")
                .category("沟通条款")
                .severity("LOW")
                .targetClauses(List.of("通知", "联系", "沟通", "报告"))
                .condition("沟通机制不够完善")
                .findingTemplate("沟通条款可以优化：{具体问题}")
                .suggestionTemplate("建议：{具体改进建议}")
                .enabled(true)
                .weight(60)
                .build());

        return rules;
    }

    /**
     * 创建默认审查模板
     */
    private void createDefaultTemplate() {
        String defaultPromptTemplate = """
            请作为资深法务专家，对以下合同进行专业审查。

            ### 合同类型：{CONTRACT_TYPE}

            ### 合同内容：
            {CONTRACT_JSON}

            ### 审查标准：
            {REVIEW_RULES}

            ### 输出要求：
            严格按照以下JSON格式输出审查结果：

            ```json
            {
              "issues": [
                {
                  "clauseId": "条款编号",
                  "severity": "HIGH|MEDIUM|LOW",
                  "category": "问题分类",
                  "finding": "发现的具体问题",
                  "suggestion": "详细的修改建议"
                }
              ]
            }
            ```

            请确保：
            - 严格按照提供的审查标准进行评估
            - 每个问题都要包含具体的修改建议
            - 考虑法律风险和商业合理性
            - 注重双方权利义务平衡
            """;

        ReviewTemplate defaultTemplate = ReviewTemplate.builder()
                .id("default")
                .name("通用合同审查模板")
                .contractType("general")
                .roleDefinition("资深法务专家")
                .reviewFocus("法律风险识别和商业合理性评估")
                .outputFormat("标准JSON格式")
                .promptTemplate(defaultPromptTemplate)
                .isDefault(true)
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        templatesMap.put(defaultTemplate.getId(), defaultTemplate);
    }

    /**
     * 将规则格式化为Prompt文本
     */
    private String formatRulesForPrompt(List<ReviewRule> rules) {
        StringBuilder sb = new StringBuilder();

        Map<String, List<ReviewRule>> rulesBySeverity = rules.stream()
                .collect(Collectors.groupingBy(ReviewRule::getSeverity));

        if (rulesBySeverity.containsKey("HIGH")) {
            sb.append("🔴 高风险检查项（必须修改）：\n");
            for (ReviewRule rule : rulesBySeverity.get("HIGH")) {
                sb.append("- ").append(rule.getName()).append("：").append(rule.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (rulesBySeverity.containsKey("MEDIUM")) {
            sb.append("🟡 中风险检查项（建议修改）：\n");
            for (ReviewRule rule : rulesBySeverity.get("MEDIUM")) {
                sb.append("- ").append(rule.getName()).append("：").append(rule.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (rulesBySeverity.containsKey("LOW")) {
            sb.append("🟢 低风险检查项（可以优化）：\n");
            for (ReviewRule rule : rulesBySeverity.get("LOW")) {
                sb.append("- ").append(rule.getName()).append("：").append(rule.getDescription()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 判断规则是否适用于指定条款
     */
    private boolean isRuleApplicable(ReviewRule rule, String clauseText) {
        if (rule.getTargetClauses() == null || rule.getTargetClauses().isEmpty()) {
            return true; // 通用规则
        }

        String lowerCaseText = clauseText.toLowerCase();
        return rule.getTargetClauses().stream()
                .anyMatch(keyword -> lowerCaseText.contains(keyword.toLowerCase()));
    }
}