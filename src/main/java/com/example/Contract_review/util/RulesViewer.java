package com.example.Contract_review.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;

/**
 * 规则查看工具
 * 用于查看 rules.xlsx 中的所有规则详情
 */
public class RulesViewer {

    public static void main(String[] args) {
        String filepath = "src/main/resources/review-rules/rules.xlsx";

        try {
            FileInputStream fis = new FileInputStream(filepath);
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);

            System.out.println("📋 规则详情查看");
            System.out.println("================================================================================");
            System.out.println();

            int ruleCount = 0;
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String contractTypes = getCellValue(row, 0);
                String partyScope = getCellValue(row, 1);
                String risk = getCellValue(row, 2);
                String keywords = getCellValue(row, 3);
                String regex = getCellValue(row, 4);

                if (keywords == null || keywords.trim().isEmpty()) {
                    continue;
                }

                ruleCount++;
                System.out.println("【规则 " + ruleCount + "】第 " + (rowIdx + 1) + " 行");
                System.out.println("  合同类型: " + (contractTypes != null ? contractTypes : "N/A"));
                System.out.println("  风险等级: " + (risk != null ? risk : "N/A"));
                System.out.println("  关键词数: " + countKeywords(keywords));
                System.out.println("  关键词: " + keywords);
                if (regex != null && !regex.isEmpty()) {
                    System.out.println("  正则表达式: " + regex);
                }
                System.out.println();
            }

            System.out.println("================================================================================");
            System.out.println("总规则数: " + ruleCount);

            workbook.close();
            fis.close();

        } catch (Exception e) {
            System.err.println("❌ 出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int countKeywords(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return 0;
        }
        return keywords.split(";").length;
    }

    private static String getCellValue(Row row, int columnIndex) {
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
}
