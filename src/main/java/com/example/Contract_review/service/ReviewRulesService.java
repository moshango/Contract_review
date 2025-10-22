package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewRule;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审查规则服务
 *
 * 负责加载、解析和管理 rules.xlsx 中的审查规则
 * 提供规则匹配、过滤等功能
 */
@Service
public class ReviewRulesService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewRulesService.class);

    @Value("${review.rules.path:src/main/resources/review-rules/rules.xlsx}")
    private String rulesFilePath;

    /**
     * 缓存所有加载的规则
     */
    private List<ReviewRule> cachedRules = new ArrayList<>();

    /**
     * 规则加载标志
     */
    private boolean rulesLoaded = false;

    /**
     * 加载规则（初始化时调用）
     * 如果文件不存在，则返回空列表
     *
     * @return 加载的规则列表
     */
    public synchronized List<ReviewRule> loadRules() {
        if (rulesLoaded && !cachedRules.isEmpty()) {
            logger.info("Using cached rules, total: {}", cachedRules.size());
            return cachedRules;
        }

        cachedRules.clear();

        // 尝试找到 rules.xlsx 的实际路径
        String actualPath = findRulesFile();
        if (actualPath == null) {
            logger.warn("Rules file not found at {}, using empty rules", rulesFilePath);
            rulesLoaded = true;
            return cachedRules;
        }

        try {
            cachedRules = loadRulesFromExcel(actualPath);
            rulesLoaded = true;
            logger.info("Successfully loaded {} rules from {}", cachedRules.size(), actualPath);

            // 打印加载的规则摘要
            for (ReviewRule rule : cachedRules) {
                logger.debug("Loaded rule: id={}, risk={}, contractTypes={}, keywords={}",
                    rule.getId(), rule.getRisk(), rule.getContractTypes(), rule.getKeywords());
            }

        } catch (IOException e) {
            logger.error("Failed to load rules from {}", actualPath, e);
            rulesLoaded = true;
        }

        return cachedRules;
    }

    /**
     * 重新加载规则（支持动态更新）
     *
     * @return 重新加载的规则列表
     */
    public synchronized List<ReviewRule> reloadRules() {
        rulesLoaded = false;
        cachedRules.clear();
        return loadRules();
    }

    /**
     * 查找规则文件的实际路径
     * 支持多个可能的位置
     *
     * @return 找到的文件路径，如果不存在则返回 null
     */
    private String findRulesFile() {
        // 尝试的路径列表
        String[] pathCandidates = {
            rulesFilePath,
            "src/main/resources/review-rules/rules.xlsx",
            "resources/review-rules/rules.xlsx",
            System.getProperty("user.dir") + "/src/main/resources/review-rules/rules.xlsx",
            "/opt/app/resources/review-rules/rules.xlsx"
        };

        for (String path : pathCandidates) {
            if (path == null) continue;
            try {
                if (Files.exists(Paths.get(path))) {
                    logger.info("Found rules file at: {}", path);
                    return path;
                }
            } catch (Exception e) {
                logger.debug("Failed to check path {}: {}", path, e.getMessage());
            }
        }

        logger.warn("Rules file not found in any of: {}", Arrays.toString(pathCandidates));
        return null;
    }

    /**
     * 从 Excel 文件加载规则
     *
     * @param filePath Excel 文件路径
     * @return 解析的规则列表
     * @throws IOException 文件读取异常
     */
    private List<ReviewRule> loadRulesFromExcel(String filePath) throws IOException {
        List<ReviewRule> rules = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            // 从第二行开始读取（第一行是表头）
            for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmpty(row)) {
                    continue;
                }

                ReviewRule rule = parseRuleFromRow(row, rowNum);
                if (rule != null) {
                    rule.setId("rule_" + rowNum);
                    rules.add(rule);
                }
            }
        }

        return rules;
    }

    /**
     * 解析单行数据为 ReviewRule 对象
     *
     * @param row Excel 行对象
     * @param rowNum 行号（用于日志）
     * @return 解析的规则对象
     */
    private ReviewRule parseRuleFromRow(Row row, int rowNum) {
        try {
            String contractTypes = getCellValue(row, 0);    // contract_types
            String partyScope = getCellValue(row, 1);       // party_scope
            String risk = getCellValue(row, 2);             // risk
            String keywords = getCellValue(row, 3);         // keywords
            String regex = getCellValue(row, 4);            // regex
            String checklist = getCellValue(row, 5);        // checklist
            String suggestA = getCellValue(row, 6);         // suggest_A
            String suggestB = getCellValue(row, 7);         // suggest_B

            // 验证必需字段
            if (risk == null || risk.trim().isEmpty()) {
                logger.warn("Row {} skipped: risk is empty", rowNum);
                return null;
            }

            if ((keywords == null || keywords.trim().isEmpty()) &&
                (regex == null || regex.trim().isEmpty())) {
                logger.warn("Row {} skipped: both keywords and regex are empty", rowNum);
                return null;
            }

            return ReviewRule.builder()
                .contractTypes(contractTypes)
                .partyScope(partyScope != null ? partyScope : "Neutral")
                .risk(risk)
                .keywords(keywords)
                .regex(regex)
                .checklist(checklist)
                .suggestA(suggestA)
                .suggestB(suggestB)
                .build();

        } catch (Exception e) {
            logger.error("Failed to parse rule from row {}: {}", rowNum, e.getMessage());
            return null;
        }
    }

    /**
     * 获取单元格值（支持字符串和数字）
     *
     * @param row Excel 行
     * @param columnIndex 列索引
     * @return 单元格值，如果为空则返回 null
     */
    private String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : value;
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return null;
        }
    }

    /**
     * 判断行是否为空
     *
     * @param row Excel 行
     * @return 是否为空行
     */
    private boolean isEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取所有规则（确保已加载）
     *
     * @return 规则列表
     */
    public List<ReviewRule> getAllRules() {
        if (!rulesLoaded) {
            loadRules();
        }
        return new ArrayList<>(cachedRules);
    }

    /**
     * 按合同类型过滤规则
     *
     * @param contractType 合同类型
     * @return 适用于该合同类型的规则列表
     */
    public List<ReviewRule> filterByContractType(String contractType) {
        if (!rulesLoaded) {
            loadRules();
        }

        return cachedRules.stream()
            .filter(rule -> rule.applicableToContractType(contractType))
            .collect(Collectors.toList());
    }

    /**
     * 按风险等级过滤规则
     *
     * @param riskLevel 风险等级
     * @return 指定风险等级的规则列表
     */
    public List<ReviewRule> filterByRiskLevel(String riskLevel) {
        if (!rulesLoaded) {
            loadRules();
        }

        return cachedRules.stream()
            .filter(rule -> riskLevel.equalsIgnoreCase(rule.getRisk()))
            .collect(Collectors.toList());
    }

    /**
     * 为条款文本匹配适用的规则
     *
     * @param clauseText 条款文本
     * @param contractType 合同类型
     * @return 匹配的规则列表
     */
    public List<ReviewRule> matchRulesForClause(String clauseText, String contractType) {
        if (!rulesLoaded) {
            loadRules();
        }

        return cachedRules.stream()
            .filter(rule -> rule.applicableToContractType(contractType))
            .filter(rule -> rule.matches(clauseText))
            .collect(Collectors.toList());
    }

    /**
     * 获取缓存的规则数量
     *
     * @return 规则数量
     */
    public int getCachedRuleCount() {
        return cachedRules.size();
    }

    /**
     * 检查规则是否已加载
     *
     * @return 是否已加载
     */
    public boolean isRulesLoaded() {
        return rulesLoaded;
    }
}
