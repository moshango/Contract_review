import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class InspectRules {
    public static void main(String[] args) throws IOException {
        String filePath = "src/main/resources/review-rules/rules.xlsx";
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            System.out.println("=== Rules.xlsx Inspection ===\n");
            System.out.println("Total rows: " + sheet.getLastRowNum());
            
            // Print header
            Row headerRow = sheet.getRow(0);
            System.out.println("\nHeaders:");
            for (int col = 0; col < 8; col++) {
                Cell cell = headerRow.getCell(col);
                System.out.println("  Col " + col + ": " + (cell != null ? cell.getStringCellValue() : ""));
            }
            
            System.out.println("\n=== All Rules and Keywords ===\n");
            
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                Row r = sheet.getRow(row);
                if (r == null) continue;
                
                String contractTypes = getCellValue(r, 0);
                String partyScope = getCellValue(r, 1);
                String risk = getCellValue(r, 2);
                String keywords = getCellValue(r, 3);
                String regex = getCellValue(r, 4);
                
                if (contractTypes.isEmpty() && partyScope.isEmpty() && risk.isEmpty()) {
                    continue;
                }
                
                System.out.println("Rule " + row + ":");
                System.out.println("  Contract Types: " + contractTypes);
                System.out.println("  Party Scope: " + partyScope);
                System.out.println("  Risk: " + risk);
                System.out.println("  Keywords: " + keywords);
                System.out.println("  Regex: " + regex);
                System.out.println();
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
