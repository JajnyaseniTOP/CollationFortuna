package FileExplorerCollation.filesearch.document;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Builds the cover letter for an income tax return.
 *
 * It is the BAS letter - the same Fortuna template {@link CoverLetterService} uses -
 * reworded for a tax return:
 *
 * <pre>
 *   Dear,                                              ->  Dear Kevin Stevens,
 *   Your BUSINESS ACTIVITY STATEMENT for the quarter   ->  TAX RETURN for the year ended 30 June 2026
 *   ATO PAYMENT SLIP                                   ->  kept when payable, removed for a refund
 *   Entity | Estimated Position | Due Date             ->  Kevin Stevens | $1,165.00 Refund | 17/05/2026
 * </pre>
 *
 * The template is only ever read; the letter is written to the Collation Testing folder.
 */
public class ItrCoverLetterService {

    /**
     * Copies the template into the collation folder and fills it in.
     *
     * @param estimatedPosition e.g. "$1,165.00 Refund"; blank when it could not be read
     * @param paymentSlipEnclosed false for a refund, which takes the payment slip line out
     * @return the finished letter's path, or null if it could not be written
     */
    public Path createCoverLetter(Path collationFolder, String clientName, String estimatedPosition,
            boolean paymentSlipEnclosed) {

        Path templateFile = Paths.get(CollationPaths.BAS_TEMPLATE_FOLDER,
                CollationPaths.BAS_COVER_LETTER_FILE);

        if (!Files.isRegularFile(templateFile)) {
            System.out.println("Cover letter template not found: " + templateFile);
            return null;
        }

        Path coverLetterFile = collationFolder.resolve(ItrPaths.COVER_LETTER_FILE_NAME);

        try {

            Files.createDirectories(collationFolder);
            Files.copy(templateFile, coverLetterFile, StandardCopyOption.REPLACE_EXISTING);

            fillInTheLetter(coverLetterFile, clientName, estimatedPosition, paymentSlipEnclosed);

            System.out.println("YES - Cover Letter written");
            System.out.println("  Client   : " + clientName);
            System.out.println("  Position : " + estimatedPosition);
            System.out.println("  Due Date : " + ItrPaths.ITR_DUE_DATE);
            System.out.println("  Path     : " + coverLetterFile);

            return coverLetterFile;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to write the Cover Letter");
            e.printStackTrace();

            return null;
        }
    }

    /** Rewrites the greeting, the enclosure lines and the estimated position table. */
    private void fillInTheLetter(Path coverLetterFile, String clientName, String estimatedPosition,
            boolean paymentSlipEnclosed) throws IOException {

        XWPFDocument document;

        try (FileInputStream fis = new FileInputStream(coverLetterFile.toFile())) {
            document = new XWPFDocument(fis);
        }

        try {

            // Collected first: removing from the document while walking its paragraphs fails
            List<XWPFParagraph> toRemove = new ArrayList<>();

            for (XWPFParagraph paragraph : document.getParagraphs()) {

                String text = paragraph.getText();

                if (text == null) {
                    continue;
                }

                if (text.trim().equalsIgnoreCase("Dear,")) {
                    setParagraphText(paragraph, "Dear " + clientName + ",");
                }

                else if (text.contains("BUSINESS ACTIVITY STATEMENT for the")) {
                    setParagraphText(paragraph, "TAX RETURN for the year ended 30 June " + ItrPaths.ITR_YEAR);
                }

                else if (!paymentSlipEnclosed && text.trim().equalsIgnoreCase("ATO PAYMENT SLIP")) {
                    toRemove.add(paragraph);
                }
            }

            for (XWPFParagraph paragraph : toRemove) {
                document.removeBodyElement(document.getPosOfParagraph(paragraph));
            }

            if (!document.getTables().isEmpty()) {

                XWPFTable table = document.getTables().get(0);

                if (table.getNumberOfRows() > 1) {

                    XWPFTableRow row = table.getRow(1);

                    if (row.getTableCells().size() > 2) {
                        setCellText(row.getCell(0), clientName);
                        setCellText(row.getCell(1), estimatedPosition);
                        setCellText(row.getCell(2), ItrPaths.ITR_DUE_DATE);
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

    /** Replaces a table cell's text; {@code XWPFTableCell.setText} appends rather than replaces. */
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
