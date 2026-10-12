package FileExplorerCollation.filesearch.excel;

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Writes the run's results back into ClientData.xls as extra columns on the right.
 *
 * Nothing is saved to disk until {@link #save} is called, so a run that blows up
 * part way through leaves the original file untouched.
 */
public class ClientDataStatusWriter {

    /** Adds a header in the given column if it is not already there. */
    public void ensureHeader(Sheet sheet, int column, String header) {

        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            headerRow = sheet.createRow(0);
        }

        cellAt(headerRow, column).setCellValue(header);
    }

    /** Writes a status without logging it - used to seed a row's default. */
    public void setStatus(Sheet sheet, int rowIndex, int column, String status) {

        Row row = sheet.getRow(rowIndex);

        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        cellAt(row, column).setCellValue(status);
    }

    /** Writes a status and logs it. */
    public void writeStatus(Sheet sheet, int rowIndex, int column, String status, String label) {

        setStatus(sheet, rowIndex, column, status);

        System.out.println("Excel row " + rowIndex + " -> " + label + " = " + status);
    }

    /** Saves the workbook back over the original file. */
    public void save(Workbook workbook, String filePath) {

        try (FileOutputStream fos = new FileOutputStream(filePath)) {

            workbook.write(fos);

            System.out.println("YES - ClientData.xls updated: " + filePath);

        } catch (IOException e) {

            System.out.println("ERROR - Unable to save ClientData.xls");
            System.out.println("Close the file in Excel and re-run.");
            e.printStackTrace();
        }
    }

    private Cell cellAt(Row row, int column) {

        Cell cell = row.getCell(column);

        if (cell == null) {
            cell = row.createCell(column);
        }

        return cell;
    }
}
