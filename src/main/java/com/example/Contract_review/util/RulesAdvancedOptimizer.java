package com.example.Contract_review.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;

/**
 * 规则高级优化工具
 * 基于详细优化报告，执行第一层和第二层优化
 */
public class RulesAdvancedOptimizer {

    // 高级优化字典 - 针对现有规则的增强
    private static final Map<String, OptimizationPlan> OPTIMIZATION_PLANS = new HashMap<>();

    static {
        // 规则 1 - 支付方式 (优先级 1)
        OPTIMIZATION_PLANS.put("支付方式;支付单据;费用预支",
            new OptimizationPlan(
                "支付;付款;款项;周期;结算;方式;方法;转账;汇款;支付周期;付款周期;支付条件;付款条件;支付时间;付款时间",
                "(支付|付款).*?(\\d+).*?(天|个工作日|天内|工作日内)"
            ));

        // 规则 2 - 违约条款 (优先级 1)
        OPTIMIZATION_PLANS.put("违约责任;条款;罚款",
            new OptimizationPlan(
                "违约;毁约;违反;不履行;责任;赔偿;处罚;罚款;违约金;违约方;债务;失效",
                "(违约|毁约|违反).*?(责任|赔偿|罚款|金额|处罚)"
            ));

        // 规则 3 - 保密期限 (优先级 2)
        OPTIMIZATION_PLANS.put("保密;机密;隐私",
            new OptimizationPlan(
                "保密;秘密;机密;隐私;保护;保密期;保密条款;保密信息;秘密信息;机密信息;泄露;披露",
                "保密.*?(\\d+年|\\d+个月|\\d+天|期限)|(\\d+年|\\d+个月|\\d+天).*?保密"
            ));

        // 规则 11 - 融资与投资 (优先级 1)
        OPTIMIZATION_PLANS.put("融资;融资协议",
            new OptimizationPlan(
                "融资;投资;股权;资金;融资方式;融资条件;融资来源;投资额;投资比例;融资安排;融资能力",
                "融资|投资|股权|资本金|融资方式"
            ));

        // 规则 6 - 保障责任 (优先级 2)
        OPTIMIZATION_PLANS.put("保证保障;保障;承诺",
            new OptimizationPlan(
                "保证;保障;承诺;承担;担保;责任;义务;保证义务;保证责任;担保责任",
                "(保证|保障|承诺).*?(责任|义务|条件)|(责任|义务).*?(保证|保障|承诺)"
            ));
    }

    public static class OptimizationPlan {
        public String optimizedKeywords;
        public String optimizedRegex;

        public OptimizationPlan(String keywords, String regex) {
            this.optimizedKeywords = keywords;
            this.optimizedRegex = regex;
        }
    }

    private String filepath;
    private Workbook workbook;
    private Sheet sheet;

    public RulesAdvancedOptimizer(String filepath) {
        this.filepath = filepath;
    }

    public boolean loadWorkbook() {
        try {
            FileInputStream fis = new FileInputStream(filepath);
            workbook = new XSSFWorkbook(fis);
            sheet = workbook.getSheetAt(0);
            System.out.println("✅ 成功加载: " + filepath);
            return true;
        } catch (Exception e) {
            System.err.println("❌ 加载失败: " + e.getMessage());
            return false;
        }
    }

    public void analyzeAndOptimize() {
        System.out.println("\n📊 开始分析现有规则...");
        System.out.println("================================================================================");

        int matchedCount = 0;
        int optimizedCount = 0;

        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            String keywords = getCellValue(row, 3);
            String regex = getCellValue(row, 4);
            String risk = getCellValue(row, 2);

            if (keywords == null || keywords.trim().isEmpty()) {
                continue;
            }

            // 根据风险等级和关键词内容进行模式匹配（更灵活的策略）
            String currentKey = keywords.trim();
            boolean shouldOptimize = false;
            OptimizationPlan plan = null;

            // 策略 1: 精确匹配
            if (OPTIMIZATION_PLANS.containsKey(currentKey)) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get(currentKey);
            }
            // 策略 2: 部分匹配（检查是否包含关键词）
            else if (currentKey.contains("支付") && currentKey.length() < 15) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get("支付方式;支付单据;费用预支");
            }
            else if (currentKey.contains("违约") && currentKey.length() < 15) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get("违约责任;条款;罚款");
            }
            else if ((currentKey.contains("保密") || currentKey.contains("机密")) && !currentKey.contains("期")) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get("保密;机密;隐私");
            }
            else if (currentKey.contains("融资") || currentKey.contains("投资")) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get("融资;融资协议");
            }
            else if ((currentKey.contains("保证") || currentKey.contains("保障")) && currentKey.length() < 20) {
                shouldOptimize = true;
                plan = OPTIMIZATION_PLANS.get("保证保障;保障;承诺");
            }

            if (shouldOptimize && plan != null) {
                matchedCount++;

                System.out.println("\n【规则 " + (rowIdx + 1) + "】发现匹配项 (风险: " + (risk != null ? risk : "N/A") + ")");
                System.out.println("   当前关键词: " + keywords);
                System.out.println("   当前正则: " + (regex != null ? regex : "(无)"));

                // 应用优化
                Cell keywordCell = row.getCell(3);
                Cell regexCell = row.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                keywordCell.setCellValue(plan.optimizedKeywords);
                regexCell.setCellValue(plan.optimizedRegex);

                // 高亮显示
                CellStyle yellowStyle = workbook.createCellStyle();
                yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                keywordCell.setCellStyle(yellowStyle);
                regexCell.setCellStyle(yellowStyle);

                int oldCount = keywords.split(";").length;
                int newCount = plan.optimizedKeywords.split(";").length;

                System.out.println("   优化后关键词: " + plan.optimizedKeywords);
                System.out.println("   优化后正则: " + plan.optimizedRegex);
                System.out.printf("   效果: %d 个关键词 → %d 个关键词 (+%d)\n", oldCount, newCount, newCount - oldCount);

                optimizedCount++;
            }
        }

        System.out.println("\n================================================================================");
        System.out.println("📈 优化统计:");
        System.out.println("   匹配优化计划的规则: " + matchedCount + " 条");
        System.out.println("   已应用优化: " + optimizedCount + " 条");
    }

    public boolean saveWorkbook() {
        try {
            FileOutputStream fos = new FileOutputStream(filepath);
            workbook.write(fos);
            fos.close();
            System.out.println("✅ 保存成功: " + filepath);
            return true;
        } catch (Exception e) {
            System.err.println("❌ 保存失败: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            if (workbook != null) {
                workbook.close();
            }
        } catch (Exception e) {
            System.err.println("❌ 关闭失败: " + e.getMessage());
        }
    }

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

    public static void main(String[] args) {
        System.out.println("🚀 规则高级优化工具");
        System.out.println("================================================================================\n");
        System.out.println("本工具根据优化报告自动应用第一层和第二层优化");
        System.out.println("预期提升规则匹配命中率 25-40%\n");

        String filepath = "src/main/resources/review-rules/rules.xlsx";
        RulesAdvancedOptimizer optimizer = new RulesAdvancedOptimizer(filepath);

        if (!optimizer.loadWorkbook()) {
            return;
        }

        optimizer.analyzeAndOptimize();
        optimizer.saveWorkbook();

        System.out.println("\n================================================================================");
        System.out.println("✨ 优化完成！");
        System.out.println("\n📌 后续步骤:");
        System.out.println("   1. 重新加载规则: curl -X POST http://localhost:8080/api/review/reload-rules");
        System.out.println("   2. 上传测试合同进行规则审查");
        System.out.println("   3. 验证匹配规则数量是否增加");
        System.out.println("\n💡 提示:");
        System.out.println("   • 黄色高亮表示已修改的单元格");
        System.out.println("   • 可以进一步优化其他规则（见优化报告的优先级 3）");
        System.out.println("   • 如需回滚，可使用 git 恢复或手动编辑");

        optimizer.close();
    }
}
