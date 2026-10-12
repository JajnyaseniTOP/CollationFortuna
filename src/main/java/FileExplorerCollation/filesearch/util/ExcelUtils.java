package FileExplorerCollation.filesearch.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;

/**
 * Small helpers for reading values out of a POI workbook.
 */
public final class ExcelUtils {

    private ExcelUtils() {
        // static helpers only
    }

    public static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
