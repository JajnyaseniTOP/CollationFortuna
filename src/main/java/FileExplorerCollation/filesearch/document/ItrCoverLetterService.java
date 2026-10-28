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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;

import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Builds the cover letter for an individual's income tax return from the Word
 * template in the ITR Letters folder:
 *
 * <pre>
 *   Dear Kevin,                                       recipient, from the ITR declaration
 *   1. TAX RETURN for the year ended 30 June 2026     year
 *   Entity        | Estimated Position | Due Date     client name | from the ITR | from the ATO
 *   Invoice will be issued separately ...             removed - the yellow paragraph
 * </pre>
 *
 * The table is filled by its column headings rather than by position, and any
 * paragraph highlighted or shaded yellow in the template is taken out, as well as the
 * "Invoice will be issued separately" paragraph, which is the one marked yellow on the
 * practice's sample.
 *
 * The template is only ever read; the letter is written to the Collation Testing folder.
 */
public class ItrCoverLetterService {

    /** The paragraph marked yellow on the sample letter - it must not appear in any letter. */
    private static final String INVOICE_PARAGRAPH = "Invoice will be issued separately";

    /**
     * The Word template: the first .docx in the ITR Letters folder with "Cover Letter"
     * in its name, so the file can be renamed for a new year without a code change.
     *
     * @return the template, or null when there is none
     */
    public Path findTemplate() {

        Path folder = Paths.get(ItrPaths.ITR_LETTERS_FOLDER);

        try (var files = Files.list(folder)) {

            for (Path file : (Iterable<Path>) files::iterator) {

                String name = file.getFileName().toString().toLowerCase();

                // The Trust letter ("Cover Letter 2026 for Trust.docx") sits in the same folder
                if (Files.isRegularFile(file) && name.endsWith(".docx") && name.contains("cover letter")
                        && !name.contains("trust") && !name.startsWith("~$")) {
                    return file;
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + folder);
        }

        return null;
    }

    /**
     * Copies the template into the collation folder and fills it in.
     *
     * @param recipientName     the first name the letter is addressed to, e.g. "Kevin"
     * @param entityName        the client, for the Entity column, e.g. "Kevin Stevens"
     * @param estimatedPosition e.g. "$1,165.00 Refund"; blank when it could not be read
     * @param dueDate           the tax return due date from the ATO, e.g. "02/11/2026"; blank if unknown
     * @param ffrEnclosed       true when a Fee From Refund authority goes with the letter
     * @return the finished letter's path, or null if it could not be written
     */
    public Path createCoverLetter(Path collationFolder, String recipientName, String entityName,
            String estimatedPosition, String dueDate, boolean ffrEnclosed) {

        Path templateFile = findTemplate();

        if (templateFile == null) {
            System.out.println("!! No Word cover letter template (a .docx with 'Cover Letter' in its name) in "
                    + ItrPaths.ITR_LETTERS_FOLDER + " — no cover letter written.");
            return null;
        }

        Path coverLetterFile = collationFolder.resolve(ItrPaths.COVER_LETTER_FILE_NAME);

        try {

            Files.createDirectories(collationFolder);
            Files.copy(templateFile, coverLetterFile, StandardCopyOption.REPLACE_EXISTING);

            fillInTheLetter(coverLetterFile, recipientName, entityName, estimatedPosition, dueDate, ffrEnclosed);

            System.out.println("YES - Cover Letter written from " + templateFile.getFileName());
            System.out.println("  Dear     : " + recipientName);
            System.out.println("  Entity   : " + entityName);
            System.out.println("  Position : " + estimatedPosition);
            System.out.println("  Due Date : " + dueDate);
            System.out.println("  Path     : " + coverLetterFile);

            return coverLetterFile;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to write the Cover Letter");
            e.printStackTrace();

            return null;
        }
    }

    private void fillInTheLetter(Path coverLetterFile, String recipientName, String entityName,
            String estimatedPosition, String dueDate, boolean ffrEnclosed) throws IOException {

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

                if (text.contains(INVOICE_PARAGRAPH) || isYellow(paragraph)) {
                    toRemove.add(paragraph);
                }

                else if (text.trim().startsWith("Dear")) {
                    setParagraphText(paragraph, "Dear " + recipientName + ",");
                }

                // "TAX RETURN for the year ended 30 June 2026": only the year changes
                else if (text.toLowerCase().contains("for the year ended")) {
                    String updated = text.replaceAll("(?i)(year ended\\s+30\\s+June\\s+)\\d{4}", "$1" + ItrPaths.ITR_YEAR);
                    // Rewritten only when the year differs, so the line keeps its formatting otherwise
                    if (!updated.equals(text)) {
                        setParagraphText(paragraph, updated);
                    }
                }
            }

            for (XWPFParagraph paragraph : toRemove) {
                System.out.println("  removed from the letter: " + abbreviate(paragraph.getText()));
                document.removeBodyElement(document.getPosOfParagraph(paragraph));
            }

            XWPFTable positionTable = fillPositionTable(document, entityName, estimatedPosition, dueDate);

            if (ffrEnclosed) {
                addFfrEnclosure(document, positionTable);
            }

            try (FileOutputStream fos = new FileOutputStream(coverLetterFile.toFile())) {
                document.write(fos);
            }

        } finally {
            document.close();
        }
    }

    /** The enclosure line added when a Fee From Refund authority goes with the letter. */
    private static final String FFR_ENCLOSURE = "FEE FROM REFUND (FFR) AUTHORISATION FORM";

    /**
     * Adds "2. FEE FROM REFUND (FFR) AUTHORISATION FORM" straight after the estimated
     * tax position table.
     *
     * <p>The line copies the paragraph and run formatting of the "1. TAX RETURN" line,
     * numbering included, so Word numbers it as the next enclosure and it looks the same.
     */
    private void addFfrEnclosure(XWPFDocument document, XWPFTable positionTable) {

        XWPFParagraph numberedItem = null;

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText() != null && paragraph.getText().toLowerCase().contains("for the year ended")) {
                numberedItem = paragraph;
                break;
            }
        }

        if (positionTable == null || numberedItem == null) {
            System.out.println("WARNING - could not place the FFR line in the cover letter");
            return;
        }

        // A cursor on the first element after the table: the new paragraph goes in front of it.
        org.apache.xmlbeans.XmlCursor cursor = positionTable.getCTTbl().newCursor();
        cursor.toEndToken();
        while (cursor.toNextToken() != org.apache.xmlbeans.XmlCursor.TokenType.START) {
            if (!cursor.hasNextToken()) {
                System.out.println("WARNING - nothing after the estimated position table to place the FFR line before");
                return;
            }
        }

        XWPFParagraph line = document.insertNewParagraph(cursor);

        if (numberedItem.getCTP().getPPr() != null) {
            line.getCTP().setPPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr)
                    numberedItem.getCTP().getPPr().copy());
        }

        // A line's gap below the table, as item 1 has above it
        line.setSpacingBefore(240);

        XWPFRun run = line.createRun();
        if (!numberedItem.getRuns().isEmpty() && numberedItem.getRuns().get(0).getCTR().getRPr() != null) {
            run.getCTR().setRPr((CTRPr) numberedItem.getRuns().get(0).getCTR().getRPr().copy());
        }
        run.setText(FFR_ENCLOSURE, 0);

        System.out.println("  added to the letter: " + FFR_ENCLOSURE);
    }

    /**
     * The Entity / Estimated Position / Due Date table: the row under the headings.
     *
     * @return the table, or null when the template has none
     */
    private XWPFTable fillPositionTable(XWPFDocument document, String entityName, String estimatedPosition,
            String dueDate) {

        for (XWPFTable table : document.getTables()) {

            if (table.getNumberOfRows() < 2) {
                continue;
            }

            List<XWPFTableCell> headings = table.getRow(0).getTableCells();
            XWPFTableRow row = table.getRow(1);

            boolean isPositionTable = false;

            for (int c = 0; c < headings.size() && c < row.getTableCells().size(); c++) {

                String heading = headings.get(c).getText().trim().toLowerCase();

                if (heading.startsWith("entity")) {
                    setCellText(row.getCell(c), entityName);
                    isPositionTable = true;
                } else if (heading.startsWith("estimated position")) {
                    setCellText(row.getCell(c), estimatedPosition);
                    isPositionTable = true;
                } else if (heading.startsWith("due date")) {
                    setCellText(row.getCell(c), dueDate);
                }
            }

            if (isPositionTable) {
                return table;
            }
        }

        System.out.println("WARNING - no Entity / Estimated Position table in the cover letter template");
        return null;
    }

    /** True when any run of the paragraph is highlighted or shaded yellow. */
    private boolean isYellow(XWPFParagraph paragraph) {

        for (XWPFRun run : paragraph.getRuns()) {

            CTRPr properties = run.getCTR().getRPr();

            if (properties == null) {
                continue;
            }

            if (properties.sizeOfHighlightArray() > 0
                    && String.valueOf(properties.getHighlightArray(0).getVal()).toLowerCase().contains("yellow")) {
                return true;
            }

            if (properties.sizeOfShdArray() > 0) {
                CTShd shading = properties.getShdArray(0);
                Object fill = shading.getFill();
                if (fill != null && String.valueOf(fill).equalsIgnoreCase("FFFF00")) {
                    return true;
                }
            }
        }

        return false;
    }

    private String abbreviate(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    /** Replaces a table cell's text; {@code XWPFTableCell.setText} appends rather than replaces. */
    private void setCellText(XWPFTableCell cell, String text) {

        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }

        setParagraphText(cell.getParagraphs().get(0), text == null ? "" : text);

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
            paragraph.getRuns().get(i).setText("", 0);
        }
    }
}
