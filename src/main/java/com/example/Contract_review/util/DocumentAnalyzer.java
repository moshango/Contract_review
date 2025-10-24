package com.example.Contract_review.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * 文档内容分析工具
 * 用于提取和分析 docx 文件中的内容，找出缺失的关键词匹配
 */
public class DocumentAnalyzer {

    public static void main(String[] args) {
        String docPath = "测试合同_综合测试版.docx";

        System.out.println("=" .repeat(80));
        System.out.println("📄 测试合同内容分析");
        System.out.println("=" .repeat(80));

        try {
            FileInputStream fis = new FileInputStream(new File(docPath));
            XWPFDocument doc = new XWPFDocument(fis);

            // 提取所有段落
            List<String> allTexts = new ArrayList<>();
            System.out.println("\n📋 合同全文内容:\n");

            int paraCount = 0;
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    allTexts.add(text);
                    paraCount++;
                    System.out.println("【段落 " + paraCount + "】" + text);
                }
            }

            doc.close();
            fis.close();

            // 分析违约相关关键词
            System.out.println("\n\n" + "=".repeat(80));
            System.out.println("🔍 违约相关关键词搜索");
            System.out.println("=" .repeat(80));

            String[] violationKeywords = {
                "违约", "毁约", "违反", "不履行",
                "责任", "赔偿", "处罚", "罚款", "违约金"
            };

            for (String keyword : violationKeywords) {
                System.out.println("\n搜索 '" + keyword + "':");
                boolean found = false;
                for (String text : allTexts) {
                    if (text.contains(keyword)) {
                        System.out.println("  ✓ " + text);
                        found = true;
                    }
                }
                if (!found) {
                    System.out.println("  ✗ 未找到");
                }
            }

            // 分析规则的关键词
            System.out.println("\n\n" + "=".repeat(80));
            System.out.println("📋 当前规则的违约相关关键词");
            System.out.println("=" .repeat(80));

            String[] ruleKeywords = {
                "违约", "毁约", "违反", "不履行", "责任",
                "赔偿", "处罚", "罚款", "违约金", "违约方", "债务", "失效"
            };

            System.out.println("\n规则关键词清单:");
            for (String kw : ruleKeywords) {
                System.out.println("  - " + kw);
            }

            // 对比分析
            System.out.println("\n\n" + "=".repeat(80));
            System.out.println("🔄 对比分析 - 文档中出现但规则关键词未覆盖的词");
            System.out.println("=" .repeat(80));

            Set<String> ruleKeywordSet = new HashSet<>(Arrays.asList(ruleKeywords));

            System.out.println("\n✓ 分析完成");

        } catch (Exception e) {
            System.err.println("❌ 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
