package FileExplorerCollation.filesearch.document;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.util.BasPeriod;

/**
 * Builds the BAS cover letter for one period.
 *
 * The Fortuna template is copied into the Collation Testing folder as
 * "Cover Letter.docx" and then filled in. The template looks like this:
 *
 * <pre>
 *   [00] Dear,
 *   [04] Your BUSINESS ACTIVITY STATEMENT for the quarter ended 31 December 2024
 *   [10] YOUR ESTIMATED TAX POSITION:
 *   TABLE 0 row 0 : Entity | Estimated Position | Due Date
 *           row 1 : (blank)| $1,676.00 Payable  | (blank)
 * </pre>
 */
public class CoverLetterService {

    /** What the finished letter is called, per the scope of work. */
    public static final String COVER_LETTER_FILE_NAME = "Cover Letter.docx";

    private static final DateTimeFormatter DUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * Copies the template into the collation folder and fills it in.
     *
     * @param collationFolderPath the Collation Testing folder for this period
     * @param clientName          goes in the greeting and the Entity cell
     * @param payableOrRefund     column P, e.g. "Amount payable"
     * @param amount              column Q, e.g. "21,889.00"
     * @param period              the month or quarter being collated
     * @param dueDate             the date the job is run
     * @return the finished letter's path, or null if it could not be written
     */
    public String createCoverLetter(String collationFolderPath, String clientName,
            String payableOrRefund, String amount, BasPeriod period, LocalDate dueDate) {

        Path templateFile = Paths.get(CollationPaths.BAS_TEMPLATE_FOLDER,
                CollationPaths.BAS_COVER_LETTER_FILE);

        if (!Files.exists(templateFile) || !Files.isRegularFile(templateFile)) {
            System.out.println("Cover letter template not found: " + templateFile);
            return null;
        }

        Path coverLetterFile = Paths.get(collationFolderPath, COVER_LETTER_FILE_NAME);

        try {

            Files.createDirectories(Paths.get(collationFolderPath));
            Files.copy(templateFile, coverLetterFile, StandardCopyOption.REPLACE_EXISTING);

            String estimatedPosition = buildEstimatedPosition(payableOrRefund, amount);

            fillInTheLetter(coverLetterFile, clientName, estimatedPosition, period, dueDate);

            System.out.println("YES - Cover Letter written");
            System.out.println("  Client   : " + clientName);
            System.out.println("  Position : " + estimatedPosition);
            System.out.println("  Due Date : " + dueDate.format(DUE_DATE_FORMAT));
            System.out.println("  Path     : " + coverLetterFile);

            return coverLetterFile.toString();

        } catch (IOException e) {

            System.out.println("ERROR - Unable to write the Cover Letter");
            e.printStackTrace();

            return null;
        }
    }

    /** Rewrites the greeting, the period line and the estimated position table. */
    private void fillInTheLetter(Path coverLetterFile, String clientName, String estimatedPosition,
            BasPeriod period, LocalDate dueDate) throws IOException {

        XWPFDocument document;

        try (FileInputStream fis = new FileInputStream(coverLetterFile.toFile())) {
            document = new XWPFDocument(fis);
        }

        try {

            for (XWPFParagraph paragraph : document.getParagraphs()) {

                String text = paragraph.getText();

                if (text == null) {
                    continue;
                }

                if (text.trim().equalsIgnoreCase("Dear,")) {
                    setParagraphText(paragraph, "Dear " + clientName + ",");
                }

                // The template carries a stale period ("quarter ended 31 December 2024")
                else if (text.contains("BUSINESS ACTIVITY STATEMENT for the")) {
                    setParagraphText(paragraph,
                            "Your BUSINESS ACTIVITY STATEMENT for the " + period.getPeriodDescription());
                }
            }

            if (!document.getTables().isEmpty()) {

                XWPFTable table = document.getTables().get(0);

                if (table.getNumberOfRows() > 1) {

                    XWPFTableRow row = table.getRow(1);

                    if (row.getTableCells().size() > 2) {
                        setCellText(row.getCell(0), clientName);
                        setCellText(row.getCell(1), estimatedPosition);
                        setCellText(row.getCell(2), dueDate.format(DUE_DATE_FORMAT));
                    } else {
                        System.out.println("WARNING - the estimated position table has fewer than 3 cells");
                    }
                }

            } else {
                System.out.println("WARNING - no estimated position table in the template");
            }

            try (FileOutputStream fos = new FileOutputStream(coverLetterFile.toFile())) {
                document.write(fos);
            }

        } finally {
            document.close();
        }
    }

    /**
     * Turns column P and Q into the wording the table expects,
     * e.g. "Amount payable" + "21,889.00" -> "$21,889.00 Payable".
     */
    String buildEstimatedPosition(String payableOrRefund, String amount) {

        String wording = payableOrRefund != null && payableOrRefund.toLowerCase().contains("refund")
                ? "Refundable"
                : "Payable";

        String cleanedAmount = amount == null ? "" : amount.replace(",", "").replace("$", "").trim();

        if (!cleanedAmount.matches("-?\\d+(\\.\\d+)?")) {
            // Nothing numeric to show - keep whatever the sheet said
            System.out.println("WARNING - no usable amount for the cover letter: '" + amount + "'");
            return amount == null ? "" : amount.trim();
        }

        return String.format("$%,.2f %s", Double.parseDouble(cleanedAmount), wording);
    }

    /**
     * Replaces a table cell's text.
     *
     * <p>{@code XWPFTableCell.setText} appends rather than replaces, which left the
     * template's sample "$1,676.00 Payable" in front of every real figure.
     */
    private void setCellText(XWPFTableCell cell, String text) {

        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }

        setParagraphText(cell.getParagraphs().get(0), text);

        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
    }

    /** Replaces a paragraph's text, keeping the first run so its formatting survives. */
    private void setParagraphText(XWPFParagraph paragraph, String text) {

        if (paragraph.getRuns().isEmpty()) {
            paragraph.createRun().setText(text, 0);
            return;
        }

        paragraph.getRuns().get(0).setText(text, 0);

        // Blank the rest, otherwise the old wording stays behind it
        for (int i = 1; i < paragraph.getRuns().size(); i++) {
            XWPFRun run = paragraph.getRuns().get(i);
            run.setText("", 0);
        }
    }
}
