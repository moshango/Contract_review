import java.io.FileInputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class inspect_rules {
    public static void main(String[] args) throws IOException {
        String filePath = "src/main/resources/review-rules/rules.xlsx";
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            System.out.println("=== Rules.xlsx Inspection ===\n");
            System.out.println("Total rows: " + sheet.getLastRowNum());
            
            // Print header
            Row headerRow = sheet.getRow(0);
            System.out.println("Headers:");
            for (int col = 0; col < 8; col++) {
                Cell cell = headerRow.getCell(col);
                System.out.println("  Col " + col + ": " + (cell != null ? cell.getStringCellValue() : ""));
            }
            
            System.out.println("\nSearching for '违约' keyword:\n");
            boolean found = false;
            
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                Row r = sheet.getRow(row);
                if (r == null) continue;
                
                Cell keywordCell = r.getCell(3);  // Column 3 = keywords
                if (keywordCell != null && keywordCell.getStringCellValue().contains("违约")) {
                    found = true;
                    System.out.println("✓ Found in row " + (row + 1));
                    System.out.println("  Rule ID (col 0): " + getCellValue(r, 0));
                    System.out.println("  Contract Types (col 0): " + getCellValue(r, 0));
                    System.out.println("  Party Scope (col 1): " + getCellValue(r, 1));
                    System.out.println("  Risk (col 2): " + getCellValue(r, 2));
                    System.out.println("  Keywords (col 3): " + getCellValue(r, 3));
                    System.out.println();
                }
            }
            
            if (!found) {
                System.out.println("✗ '违约' NOT FOUND in any rule\n");
                System.out.println("First 10 rules' keywords:\n");
                for (int row = 1; row <= Math.min(10, sheet.getLastRowNum()); row++) {
                    Row r = sheet.getRow(row);
                    if (r == null) continue;
                    String keywords = getCellValue(r, 3);
                    System.out.println("Row " + (row + 1) + ": " + keywords);
                }
            }
        }
    }
    
    static String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long)cell.getNumericCellValue());
        return "";
    }
}
