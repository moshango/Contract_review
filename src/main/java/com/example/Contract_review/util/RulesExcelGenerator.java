package com.example.Contract_review.util;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

import java.io.FileOutputStream;
import java.nio.file.Paths;

/**
 * 规则Excel生成器
 * 用于初始化 rules.xlsx，包含审查规则配置
 */
public class RulesExcelGenerator {

    public static void generateRulesExcel(String filePath) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("rules");

        // 设置列宽
        sheet.setColumnWidth(0, 3000);   // contract_types
        sheet.setColumnWidth(1, 2000);   // party_scope
        sheet.setColumnWidth(2, 1500);   // risk
        sheet.setColumnWidth(3, 3000);   // keywords
        sheet.setColumnWidth(4, 3000);   // regex
        sheet.setColumnWidth(5, 4000);   // checklist
        sheet.setColumnWidth(6, 4000);   // suggest_A
        sheet.setColumnWidth(7, 4000);   // suggest_B

        // 创建表头样式
        XSSFCellStyle headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 创建表头
        String[] headers = {
            "contract_types", "party_scope", "risk",
            "keywords", "regex", "checklist", "suggest_A", "suggest_B"
        };

        XSSFRow headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 创建数据样式
        XSSFCellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setWrapText(true);
        dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);

        // 规则数据：15条常见规则
        String[][] rules = {
            // 付款条款
            {"采购;外包;通用合同", "Neutral", "high",
             "付款方式;支付周期;付款条件", "支付.*\\d+天",
             "1. 确认付款方式（现金/票据）\n2. 明确付款周期\n3. 检查付款条件是否完整",
             "建议甲方明确付款方式和周期，避免纠纷",
             "乙方应确认付款条件，避免逾期支付风险"},

            // 违约条款
            {"采购;外包;通用合同", "Neutral", "high",
             "违约责任;逾期;赔偿", "违约.*赔偿|赔偿.*\\d+%",
             "1. 检查违约金计算方式\n2. 确认违约金上限\n3. 明确是否为累计计算",
             "建议甲方明确违约金额度，保护自身权益",
             "乙方应争取合理的违约金限额"},

            // 保密期限
            {"NDA;采购;外包", "Neutral", "high",
             "保密;机密;保密期", "保密.*\\d+年|\\d+年.*保密",
             "1. 确认保密信息范围\n2. 检查保密期限是否合理\n3. 明确例外情况",
             "建议甲方明确保密期限（通常3-5年）",
             "乙方应争取合理的保密期限"},

            // 管辖/仲裁地
            {"采购;外包;通用合同", "Neutral", "high",
             "管辖;仲裁;司法管辖", "(?:管辖|仲裁).*(?:法院|仲裁委)",
             "1. 确认纠纷解决方式（诉讼/仲裁）\n2. 明确管辖法院或仲裁地\n3. 检查是否有仲裁条款",
             "建议甲方选择有利的管辖地",
             "乙方应争取中立的仲裁地"},

            // 不可抗力
            {"采购;外包;通用合同", "Neutral", "medium",
             "不可抗力;力量;天灾", "不可抗力|自然灾害|战争",
             "1. 检查不可抗力条款是否存在\n2. 确认免责范围\n3. 明确通知责任",
             "建议甲方明确不可抗力的定义和免责范围",
             "乙方应避免过宽的不可抗力条款"},

            // 价格调整
            {"采购;外包", "Neutral", "medium",
             "价格调整;价格变动;成本", "价格.*调整|调价|价格变动",
             "1. 确认是否存在价格调整条款\n2. 明确调整条件和幅度\n3. 检查触发机制",
             "建议甲方限制价格调整幅度",
             "乙方应争取合理的调价机制"},

            // 发票要求
            {"采购;外包", "Neutral", "medium",
             "发票;税票;增值税", "发票|增值税|税务",
             "1. 明确发票开具时间\n2. 确认发票内容准确\n3. 检查是否影响付款",
             "建议甲方要求乙方及时开具发票",
             "乙方应明确发票开具流程和费用"},

            // 验收条件
            {"采购;外包", "Neutral", "high",
             "验收;交付;交收", "验收|交付标准|交收条件",
             "1. 明确验收标准\n2. 确认验收期限\n3. 检查验收流程",
             "建议甲方明确详细的验收标准和流程",
             "乙方应争取合理的验收期限"},

            // 知识产权
            {"采购;外包;NDA", "Neutral", "high",
             "知识产权;著作权;专利", "知识产权|著作权|专利|IP",
             "1. 确认作品所有权归属\n2. 检查第三方知识产权风险\n3. 明确授权范围",
             "建议甲方明确作品和知识产权归甲方所有",
             "乙方应明确保留不涉及项目的知识产权"},

            // 保险要求
            {"采购;外包", "Neutral", "medium",
             "保险;责任险", "保险|责任保险|投保",
             "1. 确认保险类型\n2. 明确保险金额\n3. 检查保险费用责任",
             "建议甲方要求乙方投保相关责任保险",
             "乙方应明确保险费用由谁承担"},

            // 合同变更
            {"采购;外包;通用合同", "Neutral", "medium",
             "变更;修改;补充", "(?:合同)?变更|(?:合同)?修改|补充协议",
             "1. 明确变更的审批流程\n2. 确认变更的生效条件\n3. 检查是否需要书面确认",
             "建议甲方严格控制合同变更流程",
             "乙方应争取灵活的变更机制"},

            // 终止条款
            {"采购;外包;通用合同", "Neutral", "high",
             "终止;解除;提前终止", "终止|解除合同|提前.*终止",
             "1. 明确终止的触发条件\n2. 确认终止的通知期限\n3. 检查终止后的责任",
             "建议甲方保留单方终止权",
             "乙方应争取合理的终止通知期"},

            // 保证责任
            {"采购;外包", "Neutral", "high",
             "保证;质量保证;性能", "质量保证|保证期|保修",
             "1. 确认保证内容和范围\n2. 明确保证期限\n3. 检查保证责任",
             "建议甲方要求足够的保证期（通常12个月）",
             "乙方应明确保证责任的边界"},

            // 费用及成本
            {"采购;外包", "Neutral", "medium",
             "费用;成本;报价;价格", "(?:项目)?预算|总价|单价|费用",
             "1. 确认费用构成\n2. 明确费用支付方式\n3. 检查是否有隐性费用",
             "建议甲方要求详细的费用清单和支付进度",
             "乙方应明确报价范围和调整条件"},

            // 协议生效
            {"采购;外包;通用合同", "Neutral", "low",
             "生效;签署;有效期", "生效|签署|有效期|自.*起",
             "1. 确认协议生效条件\n2. 明确有效期\n3. 检查续期条款",
             "建议甲方明确协议的生效和失效时间",
             "乙方应确认清楚协议的有效期和续期条款"},
        };

        // 填充数据
        for (int i = 0; i < rules.length; i++) {
            XSSFRow row = sheet.createRow(i + 1);
            row.setHeightInPoints(60);

            for (int j = 0; j < rules[i].length; j++) {
                XSSFCell cell = row.createCell(j);
                cell.setCellValue(rules[i][j]);
                cell.setCellStyle(dataStyle);
            }
        }

        // 冻结首行
        sheet.createFreezePane(0, 1);

        // 保存文件
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
            System.out.println("✅ rules.xlsx generated successfully: " + filePath);
            System.out.println("   Contains " + rules.length + " review rules");
        }

        workbook.close();
    }

    public static void main(String[] args) {
        try {
            String projectRoot = "D:\\工作\\合同审查系统开发\\spring boot\\Contract_review";
            String filePath = projectRoot + "\\src\\main\\resources\\review-rules\\rules.xlsx";
            generateRulesExcel(filePath);
        } catch (Exception e) {
            System.err.println("Error generating rules.xlsx: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
