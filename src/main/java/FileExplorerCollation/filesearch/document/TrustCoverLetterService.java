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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Builds the cover letter for a Trust ITR job from "Cover Letter 2026 for Trust.docx":
 *
 * <pre>
 *   Dear XXX,                                    -> Dear John and Jessica,
 *   1. FINANCIALS AND TAX RETURNS for the ...    -> kept with a financial statement, else "1. TAX RETURNS for the ..."
 *   Entity | Estimated Position | Due Date       -> one row per entity, the trust first with NIL / NIL
 *   Invoice will be issued separately ...        -> removed (the yellow paragraph)
 *   2. FEE FROM REFUND (FFR) AUTHORISATION FORM  -> kept only when an FFR was prepared
 *   SPECIFIC INFORMATION FOR CLIENT: XXXXXX      -> the job's Cover Letter Notes; the section goes if there are none
 * </pre>
 */
public class TrustCoverLetterService {

    private static final String INVOICE_PARAGRAPH = "Invoice will be issued separately";
    private static final String FFR_LINE = "FEE FROM REFUND (FFR) AUTHORISATION FORM";
    private static final String SPECIFIC_INFORMATION = "SPECIFIC INFORMATION FOR CLIENT";

    /** One row of the estimated tax position table. */
    public static final class EntityLine {
        public final String entity;
        public final String position;
        public final String dueDate;

        public EntityLine(String entity, String position, String dueDate) {
            this.entity = entity;
            this.position = position == null ? "" : position;
            this.dueDate = dueDate == null ? "" : dueDate;
        }
    }

    /**
     * @param recipients        first names the letter is addressed to, e.g. John, Jessica
     * @param lines             the table rows, trust first
     * @param financialStatement true when a financial statement is in the pack
     * @param ffrEnclosed       true when at least one FFR was prepared
     * @param coverLetterNotes  the job's Cover Letter Notes; blank when none
     * @return the finished letter, or null if it could not be written
     */
    public Path createCoverLetter(Path collationFolder, List<String> recipients, List<EntityLine> lines,
            boolean financialStatement, boolean ffrEnclosed, String coverLetterNotes) {

        Path template = Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.TRUST_COVER_LETTER_TEMPLATE);

        if (!Files.isRegularFile(template)) {
            System.out.println("!! Trust cover letter template not found: " + template);
            return null;
        }

        Path letter = collationFolder.resolve(ItrPaths.COVER_LETTER_FILE_NAME);

        try {

            Files.copy(template, letter, StandardCopyOption.REPLACE_EXISTING);

            XWPFDocument document;
            try (FileInputStream fis = new FileInputStream(letter.toFile())) {
                document = new XWPFDocument(fis);
            }

            try {
                fill(document, recipients, lines, financialStatement, ffrEnclosed, coverLetterNotes);

                try (FileOutputStream fos = new FileOutputStream(letter.toFile())) {
                    document.write(fos);
                }
            } finally {
                document.close();
            }

            System.out.println("YES - Trust cover letter written: " + letter);
            System.out.println("  Dear " + joinNames(recipients) + " | financials " + (financialStatement ? "yes" : "no")
                    + " | FFR " + (ffrEnclosed ? "yes" : "no"));
            for (EntityLine line : lines) {
                System.out.println("  " + line.entity + " | " + line.position + " | " + line.dueDate);
            }
            return letter;

        } catch (IOException e) {
            System.out.println("ERROR - Unable to write the Trust cover letter: " + e.getMessage());
            return null;
        }
    }

    private void fill(XWPFDocument document, List<String> recipients, List<EntityLine> lines,
            boolean financialStatement, boolean ffrEnclosed, String coverLetterNotes) {

        List<XWPFParagraph> toRemove = new ArrayList<>();
        List<XWPFParagraph> placeholders = new ArrayList<>();
        XWPFParagraph specificHeading = null;

        for (XWPFParagraph paragraph : document.getParagraphs()) {

            String text = paragraph.getText() == null ? "" : paragraph.getText().trim();

            if (text.contains(INVOICE_PARAGRAPH) || isYellow(paragraph)) {
                toRemove.add(paragraph);
            } else if (text.startsWith("Dear")) {
                setParagraphText(paragraph, "Dear " + joinNames(recipients) + ",");
            } else if (text.toLowerCase().contains("for the year ended")) {
                setParagraphText(paragraph, (financialStatement ? "FINANCIALS AND TAX RETURNS" : "TAX RETURNS")
                        + " for the year ended 30 June " + ItrPaths.ITR_YEAR);
            } else if (text.equalsIgnoreCase(FFR_LINE) && !ffrEnclosed) {
                toRemove.add(paragraph);
            } else if (text.toUpperCase().startsWith(SPECIFIC_INFORMATION)) {
                specificHeading = paragraph;
            } else if (text.startsWith("XXXXXX")) {
                placeholders.add(paragraph);
            }
        }

        // SPECIFIC INFORMATION FOR CLIENT: the notes, one line per placeholder, or the whole section goes
        List<String> notes = new ArrayList<>();
        for (String note : (coverLetterNotes == null ? "" : coverLetterNotes).split("\\r?\\n")) {
            if (!note.trim().isEmpty() && !note.trim().toLowerCase().startsWith("no cover letter")) {
                notes.add(note.trim());
            }
        }

        if (notes.isEmpty()) {
            if (specificHeading != null) {
                toRemove.add(specificHeading);
            }
            toRemove.addAll(placeholders);
        } else {
            for (int i = 0; i < placeholders.size(); i++) {
                if (i >= notes.size()) {
                    toRemove.add(placeholders.get(i));
                } else if (i == placeholders.size() - 1) {
                    setParagraphText(placeholders.get(i), String.join(" ", notes.subList(i, notes.size())));
                } else {
                    setParagraphText(placeholders.get(i), notes.get(i));
                }
            }
        }

        for (XWPFParagraph paragraph : toRemove) {
            document.removeBodyElement(document.getPosOfParagraph(paragraph));
        }

        fillTable(document, lines);
    }

    /** One row per entity: rows are copied when there are more entities than rows, and spare rows go. */
    private void fillTable(XWPFDocument document, List<EntityLine> lines) {

        for (XWPFTable table : document.getTables()) {

            if (table.getNumberOfRows() < 2
                    || !table.getRow(0).getCell(0).getText().trim().toLowerCase().startsWith("entity")) {
                continue;
            }

            while (table.getNumberOfRows() - 1 < lines.size()) {
                XWPFTableRow template = table.getRow(1);
                CTRow copy = (CTRow) template.getCtRow().copy();
                table.addRow(new XWPFTableRow(copy, table));
            }
            while (table.getNumberOfRows() - 1 > lines.size()) {
                table.removeRow(table.getNumberOfRows() - 1);
            }

            for (int i = 0; i < lines.size(); i++) {
                XWPFTableRow row = table.getRow(i + 1);
                setCellText(row.getCell(0), lines.get(i).entity);
                setCellText(row.getCell(1), lines.get(i).position);
                if (row.getTableCells().size() > 2) {
                    setCellText(row.getCell(2), lines.get(i).dueDate);
                }
            }
            return;
        }

        System.out.println("WARNING - no Entity table in the Trust cover letter template");
    }

    /** "John", "John and Jessica", "John, Jessica and Sam". */
    static String joinNames(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private boolean isYellow(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            CTRPr properties = run.getCTR().getRPr();
            if (properties != null && properties.sizeOfHighlightArray() > 0
                    && String.valueOf(properties.getHighlightArray(0).getVal()).toLowerCase().contains("yellow")) {
                return true;
            }
        }
        return false;
    }

    private void setCellText(XWPFTableCell cell, String text) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        setParagraphText(cell.getParagraphs().get(0), text == null ? "" : text);
        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        if (paragraph.getRuns().isEmpty()) {
            paragraph.createRun().setText(text, 0);
            return;
        }
        paragraph.getRuns().get(0).setText(text, 0);
        for (int i = 1; i < paragraph.getRuns().size(); i++) {
            paragraph.getRuns().get(i).setText("", 0);
        }
    }
}
