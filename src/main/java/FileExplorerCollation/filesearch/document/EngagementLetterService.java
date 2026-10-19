package FileExplorerCollation.filesearch.document;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;

/**
 * Fills in the engagement letter that goes to an ITR client with no Ignition link.
 *
 * The template ("New Engagement Letter 2026-27.docx") has four things to complete:
 *
 * <pre>
 *   Dear &lt;&lt;  &gt;&gt;,                          ->  Dear Kevin,
 *   OBJECTIVES, SCOPE ... : Entity 1..4      ->  one bullet per person or entity
 *   Signature on behalf of the "Client"      ->  name and date, one column per person
 *   Signature of the "Client" as Guarantor   ->  name and date, one column per person
 * </pre>
 *
 * The signature tables carry two columns: the client, and an additional person or
 * director. A single client gets the second column taken out; a partnership of two
 * gets both filled.
 */
public class EngagementLetterService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Marks the client columns of both signature tables. */
    private static final String CLIENT_NAME_PLACEHOLDER = "<<Insert Client Name";

    /** Words that make a name an entity rather than a person, so it has no first name. */
    private static final String ENTITY_WORDS =
            "(?i).*\\b(pty|ltd|limited|trust|superannuation|super fund|smsf|partnership|group|company|co)\\b.*";

    /**
     * The people (or entities) a client name stands for.
     *
     * A partnership lists its partners in the one name, joined by "&amp;" or "and" -
     * "Kevin Stevens &amp; Jane Stevens" is two. Anything else is one name, however it
     * is written: "Stevens, Kevin" is one person, and a company called "Smith &amp; Co
     * Pty Ltd" is one entity, which is why only a partnership is split.
     */
    public List<String> namesIn(String clientName, String businessStructure) {

        List<String> names = new ArrayList<>();
        String name = clientName == null ? "" : clientName.trim();

        if (name.isEmpty()) {
            return names;
        }

        boolean partnership = businessStructure != null
                && businessStructure.toLowerCase().contains("partnership");

        if (partnership) {
            for (String part : name.split("\\s*&\\s*|\\s+(?i:and)\\s+")) {
                if (!part.trim().isEmpty()) {
                    names.add(part.trim());
                }
            }
        }

        if (names.isEmpty()) {
            names.add(name);
        }

        return names;
    }

    /**
     * "Stevens, Kevin" -> "Kevin", "Kevin Stevens" -> "Kevin". An entity such as a
     * company or trust has no first name, so it keeps its full name.
     */
    public String firstName(String name) {

        String trimmed = name == null ? "" : name.trim();

        if (trimmed.isEmpty() || trimmed.matches(ENTITY_WORDS)) {
            return trimmed;
        }

        int comma = trimmed.indexOf(',');

        if (comma >= 0) {
            String afterComma = trimmed.substring(comma + 1).trim();
            return afterComma.isEmpty() ? trimmed : afterComma.split("\\s+")[0];
        }

        return trimmed.split("\\s+")[0];
    }

    /**
     * Fills in a copy of the template in place.
     *
     * @param names the people or entities, from {@link #namesIn}
     * @return false if the letter could not be read or written
     */
    public boolean fill(Path letter, List<String> names, LocalDate date) {

        if (names.isEmpty()) {
            System.out.println("WARNING - no client name for the engagement letter; left as the template");
            return false;
        }

        try {

            XWPFDocument document;

            try (FileInputStream fis = new FileInputStream(letter.toFile())) {
                document = new XWPFDocument(fis);
            }

            try {

                fillGreeting(document, names);
                fillEntities(document, names);

                for (XWPFTable table : document.getTables()) {
                    if (table.getText().contains(CLIENT_NAME_PLACEHOLDER)) {
                        fillSignatureTable(table, names, date.format(DATE_FORMAT));
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(letter.toFile())) {
                    document.write(fos);
                }

            } finally {
                document.close();
            }

            System.out.println("YES - Engagement letter filled in for " + String.join(" and ", names));
            return true;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to fill in the engagement letter: " + e.getMessage());
            return false;
        }
    }

    /** "Dear <<  >>," -> "Dear Kevin," or, for two partners, "Dear Kevin and Jane,". */
    private void fillGreeting(XWPFDocument document, List<String> names) {

        List<String> firstNames = new ArrayList<>();

        for (String name : names) {
            firstNames.add(firstName(name));
        }

        for (XWPFParagraph paragraph : document.getParagraphs()) {

            String text = paragraph.getText();

            if (text != null && text.trim().startsWith("Dear") && text.contains("<<")) {
                setParagraphText(paragraph, "Dear " + joinNames(firstNames) + ",");
                return;
            }
        }

        System.out.println("WARNING - no 'Dear << >>,' line in the engagement letter");
    }

    /**
     * The "Entity 1" to "Entity 4" bullets: one per name, and the spare bullets are
     * removed. Names beyond the template's four are added to the last bullet so none
     * is lost.
     */
    private void fillEntities(XWPFDocument document, List<String> names) {

        List<XWPFParagraph> bullets = new ArrayList<>();

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && text.trim().matches("Entity \\d+")) {
                bullets.add(paragraph);
            }
        }

        if (bullets.isEmpty()) {
            System.out.println("WARNING - no 'Entity' bullets in the engagement letter");
            return;
        }

        for (int i = 0; i < bullets.size(); i++) {

            if (i < names.size()) {

                boolean lastBullet = i == bullets.size() - 1;
                String text = lastBullet && names.size() > bullets.size()
                        ? joinNames(names.subList(i, names.size()))
                        : names.get(i);

                setParagraphText(bullets.get(i), text);

            } else {
                document.removeBodyElement(document.getPosOfParagraph(bullets.get(i)));
            }
        }
    }

    /**
     * Writes a name and the date into each person's column of a signature table, and
     * removes the second column when there is only one person. With more people than
     * columns the last column takes the rest of the names.
     */
    private void fillSignatureTable(XWPFTable table, List<String> names, String date) {

        int columns = 0;

        for (XWPFTableRow row : table.getRows()) {
            columns = Math.max(columns, row.getTableCells().size());
        }

        int used = Math.min(names.size(), columns);

        for (XWPFTableRow row : table.getRows()) {

            List<XWPFTableCell> cells = row.getTableCells();

            for (int c = 0; c < cells.size() && c < used; c++) {

                XWPFTableCell cell = cells.get(c);
                String text = cell.getText();

                if (text.contains(CLIENT_NAME_PLACEHOLDER)) {
                    boolean lastColumn = c == used - 1;
                    setCellText(cell, lastColumn && names.size() > used
                            ? joinNames(names.subList(c, names.size()))
                            : names.get(c));
                } else if (text.trim().startsWith("Date")) {
                    setCellText(cell, "Date: " + date);
                }
            }

            // One person: take the "additional Person/ Director" column out
            for (int c = cells.size() - 1; c >= used; c--) {
                row.removeCell(c);
            }
        }

        // Keep the column definitions in step with the cells, or Word shows a ragged table
        CTTblGrid grid = table.getCTTbl().getTblGrid();

        if (grid != null) {
            for (int g = grid.sizeOfGridColArray() - 1; g >= used; g--) {
                grid.removeGridCol(g);
            }
        }
    }

    /** "Kevin", "Kevin and Jane", "Kevin, Jane and Sam". */
    private String joinNames(List<String> names) {

        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }

        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
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

        for (int i = 1; i < paragraph.getRuns().size(); i++) {
            XWPFRun run = paragraph.getRuns().get(i);
            run.setText("", 0);
        }
    }
}
