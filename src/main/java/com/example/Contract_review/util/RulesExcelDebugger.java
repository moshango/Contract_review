package com.example.Contract_review.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;

/**
 * 检查 rules.xlsx 的列结构和 partyScope 配置
 */
public class RulesExcelDebugger {
    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream("src/main/resources/review-rules/rules.xlsx");
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);

            System.out.println("=".repeat(80));
            System.out.println("📋 Excel 列结构分析");
            System.out.println("=".repeat(80));

            // 读取表头
            Row headerRow = sheet.getRow(0);
            System.out.println("\n【表头行】");
            for (int i = 0; i < 15; i++) {
                Cell cell = headerRow.getCell(i);
                String header = (cell != null) ? cell.getStringCellValue() : "---";
                System.out.printf("  列 %2d (%s): %s\n", i, columnIndexToLetter(i), header);
            }

            // 读取 Rule 2 (第3行，索引为2)
            System.out.println("\n【Rule 2 (违约条款) 数据】");
            Row rule2Row = sheet.getRow(2);
            for (int i = 0; i < 15; i++) {
                Cell cell = rule2Row.getCell(i);
                String value = (cell != null) ? getCellValue(cell) : "---";
                System.out.printf("  列 %2d (%s): %s\n", i, columnIndexToLetter(i), value);
            }

            // 特别关注 partyScope
            System.out.println("\n【partyScope 检查】");
            System.out.println("假设列索引：");
            System.out.println("  0-A: ID");
            System.out.println("  1-B: Name");
            System.out.println("  2-C: ContractTypes");
            System.out.println("  3-D: PartyScope (或其他列)");
            System.out.println("  4-E: Risk");
            System.out.println("  5-F: Keywords");
            System.out.println("  6-G: Regex");

            Cell partyScopeCell = rule2Row.getCell(3);
            String partyScope = (partyScopeCell != null) ? getCellValue(partyScopeCell) : null;
            System.out.printf("\nRule 2 的 partyScope (列D, 索引3): %s\n", partyScope);
            System.out.println("⚠️  如果为 null 或空，则默认值为 'Neutral'");

            workbook.close();
            fis.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String columnIndexToLetter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return null;
        }
    }
}
