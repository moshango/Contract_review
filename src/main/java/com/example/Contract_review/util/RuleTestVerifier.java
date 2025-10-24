package com.example.Contract_review.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 规则测试验证工具
 * 用于验证 Rule 3 (违约条款) 修复是否有效
 */
public class RuleTestVerifier {

    public static void main(String[] args) {
        String docPath = "测试合同_综合测试版.docx";
        String rulesPath = "src/main/resources/review-rules/rules.xlsx";

        System.out.println("=".repeat(80));
        System.out.println("🧪 规则验证工具 - Rule 3 (违约条款) 修复效果测试");
        System.out.println("=".repeat(80));

        try {
            // Step 1: 读取规则
            System.out.println("\n📋 Step 1: 读取规则配置");
            System.out.println("-".repeat(80));

            FileInputStream ruleFis = new FileInputStream(rulesPath);
            Workbook workbook = new XSSFWorkbook(ruleFis);
            Sheet sheet = workbook.getSheetAt(0);
            Row rule3Row = sheet.getRow(2); // Rule 3 is row index 2

            String rule3Keywords = getCellValue(rule3Row, 3); // Column D
            String rule3Regex = getCellValue(rule3Row, 4);     // Column E

            System.out.println("✓ Rule 3 (违约条款) 已加载");
            System.out.println("  关键词数: " + rule3Keywords.split(";").length);
            System.out.println("  关键词: " + rule3Keywords);
            System.out.println("  正则: " + rule3Regex);

            workbook.close();
            ruleFis.close();

            // Step 2: 读取测试合同
            System.out.println("\n\n📋 Step 2: 读取测试合同内容");
            System.out.println("-".repeat(80));

            FileInputStream docFis = new FileInputStream(new File(docPath));
            XWPFDocument doc = new XWPFDocument(docFis);

            List<String> allParagraphs = new ArrayList<>();
            System.out.println("✓ 测试合同已加载");

            int paraCount = 0;
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    allParagraphs.add(text);
                    paraCount++;
                }
            }
            System.out.println("  总段落数: " + paraCount);

            doc.close();
            docFis.close();

            // Step 3: 关键词匹配测试
            System.out.println("\n\n📋 Step 3: 关键词匹配分析");
            System.out.println("-".repeat(80));

            String[] keywords = rule3Keywords.split(";");
            Map<String, Integer> keywordMatches = new HashMap<>();

            for (String keyword : keywords) {
                int count = 0;
                for (String paragraph : allParagraphs) {
                    if (paragraph.contains(keyword)) {
                        count++;
                    }
                }
                keywordMatches.put(keyword, count);
            }

            // Display keyword matches
            int matchedCount = 0;
            int unmatchedCount = 0;

            System.out.println("✓ 关键词匹配结果:");
            System.out.println("\n【匹配的关键词】");
            for (Map.Entry<String, Integer> entry : keywordMatches.entrySet()) {
                if (entry.getValue() > 0) {
                    System.out.printf("  ✓ '%s' → %d 次命中\n", entry.getKey(), entry.getValue());
                    matchedCount++;
                }
            }

            System.out.println("\n【未匹配的关键词】");
            for (Map.Entry<String, Integer> entry : keywordMatches.entrySet()) {
                if (entry.getValue() == 0) {
                    System.out.printf("  ✗ '%s' → 0 次命中\n", entry.getKey());
                    unmatchedCount++;
                }
            }

            double keywordHitRate = (double) matchedCount / keywords.length * 100;
            System.out.printf("\n关键词命中率: %.1f%% (%d/%d)\n", keywordHitRate, matchedCount, keywords.length);

            // Step 4: 正则表达式匹配测试
            System.out.println("\n\n📋 Step 4: 正则表达式匹配分析");
            System.out.println("-".repeat(80));

            Pattern pattern = Pattern.compile(rule3Regex);
            int regexMatches = 0;
            List<String> matchedParagraphs = new ArrayList<>();

            for (String paragraph : allParagraphs) {
                if (pattern.matcher(paragraph).find()) {
                    regexMatches++;
                    matchedParagraphs.add(paragraph);
                }
            }

            System.out.println("✓ 正则表达式匹配结果:");
            System.out.printf("  匹配段落: %d 个\n", regexMatches);

            if (!matchedParagraphs.isEmpty()) {
                System.out.println("\n【匹配的段落内容】");
                for (int i = 0; i < matchedParagraphs.size(); i++) {
                    System.out.printf("  %d. %s\n", i + 1, matchedParagraphs.get(i));
                }
            }

            // Step 5: 对比分析
            System.out.println("\n\n📋 Step 5: 修复效果对比分析");
            System.out.println("-".repeat(80));

            System.out.println("修复前 (预期):");
            System.out.println("  • 关键词数: 12");
            System.out.println("  • 包含低频词: 毁约、不履行、处罚、违约金、债务、失效");
            System.out.println("  • 预期关键词命中率: 50% (6/12)");
            System.out.println("  • 问题: 规则中包含实际合同未使用的词汇");

            System.out.println("\n修复后 (实际):");
            System.out.printf("  • 关键词数: %d\n", keywords.length);
            System.out.printf("  • 关键词命中率: %.1f%% (%d/%d)\n", keywordHitRate, matchedCount, keywords.length);
            System.out.printf("  • 正则匹配段落: %d\n", regexMatches);

            double improvement = keywordHitRate - 50.0;
            System.out.printf("  • 改进幅度: +%.1f%%\n", improvement);
            System.out.println("  • 优势: 移除低频词，增加实际使用的组合词");

            // Step 6: 测试结论
            System.out.println("\n\n📋 Step 6: 测试结论");
            System.out.println("-".repeat(80));

            boolean keywordImprovement = keywordHitRate >= 70.0;
            boolean regexWorking = regexMatches > 0;

            System.out.println("✓ 测试项目:");
            System.out.printf("  %s 关键词命中率 >= 70%% (实际: %.1f%%)\n",
                keywordImprovement ? "✓" : "✗", keywordHitRate);
            System.out.printf("  %s 正则表达式成功匹配 (实际: %d 段)\n",
                regexWorking ? "✓" : "✗", regexMatches);

            if (keywordImprovement && regexWorking) {
                System.out.println("\n🎉 修复成功! Rule 3 (违约条款) 现在能够有效匹配文档内容");
                System.out.println("\n建议下一步:");
                System.out.println("  1. 测试其他合同文件确保没有副作用");
                System.out.println("  2. 对其他低性能规则应用相同的优化方法");
                System.out.println("  3. 收集用户反馈验证实际效果");
            } else {
                System.out.println("\n⚠️  修复需要进一步调整");
                if (!keywordImprovement) {
                    System.out.println("  • 关键词命中率仍低于目标，需要增加更多高频词");
                }
                if (!regexWorking) {
                    System.out.println("  • 正则表达式未能匹配任何内容，需要调整模式");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? "" : value;
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return "";
        }
    }
}
