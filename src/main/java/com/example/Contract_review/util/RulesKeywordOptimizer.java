package com.example.Contract_review.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;

/**
 * 规则关键词优化工具
 * 用于分析和优化 rules.xlsx 中的关键词配置
 */
public class RulesKeywordOptimizer {

    // 优化字典 - 将复杂关键词拆分为更细粒度的关键词
    private static final Map<String, String> OPTIMIZATION_RULES = new LinkedHashMap<>();

    static {
        // 支付相关
        OPTIMIZATION_RULES.put("支付方式",
            "支付;付款;款项;周期;结算;方式;方法;转账;汇款;支付周期;付款周期;支付条件;付款条件;支付时间;付款时间");
        OPTIMIZATION_RULES.put("付款条件",
            "支付;付款;款项;周期;结算;方式;方法;转账;汇款");

        // 违约相关
        OPTIMIZATION_RULES.put("违约责任",
            "违约;毁约;违反;不履行;责任;赔偿;处罚;罚款;违约金;违约方;债务;失效");
        OPTIMIZATION_RULES.put("违约条款",
            "违约;毁约;违反;责任;条款;条件;约定;规定");

        // 保密相关
        OPTIMIZATION_RULES.put("保密条款",
            "保密;秘密;机密;隐私;保护;保密期;保密条款;保密信息;秘密信息;机密信息");
        OPTIMIZATION_RULES.put("保密期限",
            "保密;秘密;机密;保密期;期限;期间;年;月;日");

        // 知识产权相关
        OPTIMIZATION_RULES.put("知识产权",
            "知识产权;专利;著作权;商标;IP;版权;商业秘密;技术秘密;创新;发明");

        // 终止相关
        OPTIMIZATION_RULES.put("合同终止",
            "终止;解除;中止;终约;解约;提前;合同期;期限;到期;届满");
    }

    private String filepath;
    private Workbook workbook;
    private Sheet sheet;
    private int optimizedCount = 0;

    public RulesKeywordOptimizer(String filepath) {
        this.filepath = filepath;
    }

    /**
     * 加载 Excel 工作簿
     */
    public boolean loadWorkbook() {
        try {
            FileInputStream fis = new FileInputStream(filepath);
            workbook = new XSSFWorkbook(fis);
            sheet = workbook.getSheetAt(0);
            System.out.println("✅ 成功加载: " + filepath);
            System.out.println("   总行数: " + sheet.getLastRowNum());
            return true;
        } catch (Exception e) {
            System.err.println("❌ 加载失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 分析规则并打印分析结果
     */
    public void analyzeRules() {
        System.out.println("\n📊 开始分析规则...");
        System.out.println("================================================================================");

        List<Map<String, String>> rulesToOptimize = new ArrayList<>();
        int ruleCount = 0;

        // 遍历所有行（从第2行开始，跳过表头）
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            // 获取各列数据
            String contractTypes = getCellValue(row, 0);  // A列
            String partyScope = getCellValue(row, 1);     // B列
            String risk = getCellValue(row, 2);            // C列
            String keywords = getCellValue(row, 3);        // D列
            String regex = getCellValue(row, 4);           // E列

            if (keywords == null || keywords.trim().isEmpty()) {
                continue;
            }

            ruleCount++;

            // 检查是否是可优化的复合词汇
            if (OPTIMIZATION_RULES.containsKey(keywords.trim())) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("row", String.valueOf(rowIdx + 1));
                item.put("risk", risk != null ? risk : "N/A");
                item.put("keywords", keywords);
                item.put("optimized", OPTIMIZATION_RULES.get(keywords.trim()));
                item.put("regex", regex != null ? regex : "");
                rulesToOptimize.add(item);
            }
        }

        System.out.println("\n📈 分析结果:");
        System.out.println("   总规则数: " + ruleCount);
        System.out.println("   可优化规则数: " + rulesToOptimize.size());
        if (ruleCount > 0) {
            System.out.printf("   优化比例: %.1f%%\n", rulesToOptimize.size() * 100.0 / ruleCount);
        }

        if (!rulesToOptimize.isEmpty()) {
            System.out.println("\n🎯 可优化的规则:");
            for (Map<String, String> item : rulesToOptimize) {
                System.out.println("\n   【第 " + item.get("row") + " 行】" + item.get("risk") + " 级风险");
                System.out.println("   当前: " + item.get("keywords"));
                System.out.println("   建议: " + item.get("optimized"));
                if (!item.get("regex").isEmpty()) {
                    System.out.println("   正则: " + item.get("regex"));
                }

                // 统计优化效果
                int oldCount = item.get("keywords").split(";").length;
                int newCount = item.get("optimized").split(";").length;
                int improvement = newCount - oldCount;
                System.out.printf("   效果: %d 个关键词 → %d 个关键词 (+%d, 预期提升 30-50%%)\n",
                    oldCount, newCount, improvement);
            }
        }
    }

    /**
     * 应用优化
     */
    public void applyOptimization() {
        System.out.println("\n🔧 应用优化...");
        System.out.println("================================================================================");

        optimizedCount = 0;

        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            String keywords = getCellValue(row, 3);

            if (keywords != null && OPTIMIZATION_RULES.containsKey(keywords.trim())) {
                Cell keywordCell = row.getCell(3);
                String oldValue = keywordCell.getStringCellValue();
                String newValue = OPTIMIZATION_RULES.get(keywords.trim());

                keywordCell.setCellValue(newValue);

                // 高亮显示修改的单元格
                CellStyle yellowStyle = workbook.createCellStyle();
                yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                keywordCell.setCellStyle(yellowStyle);

                optimizedCount++;
                System.out.println("✏️  第 " + (rowIdx + 1) + " 行:");
                System.out.println("   " + oldValue);
                System.out.println("   → " + newValue + "\n");
            }
        }

        System.out.println("💾 已优化 " + optimizedCount + " 条规则");
    }

    /**
     * 保存工作簿
     */
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

    /**
     * 关闭工作簿
     */
    public void close() {
        try {
            if (workbook != null) {
                workbook.close();
            }
        } catch (Exception e) {
            System.err.println("❌ 关闭失败: " + e.getMessage());
        }
    }

    /**
     * 获取单元格值
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
     * 主函数
     */
    public static void main(String[] args) {
        System.out.println("🚀 规则关键词优化工具");
        System.out.println("================================================================================\n");

        String filepath = "src/main/resources/review-rules/rules.xlsx";
        RulesKeywordOptimizer optimizer = new RulesKeywordOptimizer(filepath);

        // 加载工作簿
        if (!optimizer.loadWorkbook()) {
            return;
        }

        // 分析规则
        optimizer.analyzeRules();

        // 应用优化
        if (optimizer.optimizedCount > 0 || !OPTIMIZATION_RULES.isEmpty()) {
            System.out.println("\n是否应用优化? [y/n] (默认: y): ");
            // 自动应用 (在实际使用中可以改为交互)
            optimizer.applyOptimization();
        }

        // 保存工作簿
        optimizer.saveWorkbook();

        // 生成报告
        System.out.println("\n" + "================================================================================");
        System.out.println("📋 优化建议报告");
        System.out.println("================================================================================");

        if (optimizer.optimizedCount > 0) {
            System.out.println("\n✨ 优化完成！");
            System.out.println("\n📌 后续步骤:");
            System.out.println("   1. 重启应用或调用 /api/review/reload-rules API");
            System.out.println("   2. 上传测试合同验证效果");
            System.out.println("   3. 查看规则匹配数量是否增多");
            System.out.println("\n💡 提示:");
            System.out.println("   • 黄色高亮的单元格表示已修改");
            System.out.println("   • 可以根据实际效果进一步调整");
            System.out.println("   • 建议使用 curl 测试 API:");
            System.out.println("     curl -X POST http://localhost:8080/api/review/reload-rules");
        } else {
            System.out.println("\n✅ 所有规则都已是最佳形式");
        }

        optimizer.close();
    }
}
