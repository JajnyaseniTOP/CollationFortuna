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
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Prepares an AGM minute (scope 4.7) from "AGM Minute -  Trust.docx" in the ITR Letters
 * folder. The template is a filled-in example (Jobelmac Pty Ltd), so every field is
 * replaced:
 *
 * <pre>
 *   JOBELMAC PTY LTD ACN 600 247 747          -> the company and its ACN
 *   REGISTERED OFFICE ADDRESS: ...            -> the principal place of business for a trust's
 *                                                trustee company; kept as it is for a company
 *   PRINCIPAL PLACE OF BUSINESS: ...          -> the company's address in XPM
 *   CHAIRMAN: ... was elected to chair        -> the first director in the financial statement
 *   PRESENT:                                  -> every director, one per line
 *   RE-APPOINTMENT OF DIRECTORS: ...          -> worded for one director or several
 *   ACCOUNTS: ... financial accounts of X     -> the trust (or company) and the year, with
 *                                                "director's" or "directors'" declaration
 *   CHAIRMAN / Date:                          -> the chairman's name and today's date
 * </pre>
 */
public class AgmMinuteService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param company            the company the meeting is of, e.g. "Grass Tree Landscaping Pty Ltd"
     * @param acn                its ACN, e.g. "143 646 339"
     * @param principalAddress   its address in XPM
     * @param registeredAddress  the registered office address, or null to keep the template's
     * @param directors          from the financial statement, chairman first
     * @param accountsOf         whose financial accounts are adopted: the trust, or the company itself
     * @return the minute, or null if it could not be written
     */
    public Path createMinute(Path collationFolder, String fileName, String company, String acn,
            String principalAddress, String registeredAddress, List<String> directors, String accountsOf,
            LocalDate date) {

        Path template = Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.TRUST_AGM_TEMPLATE);

        if (!Files.isRegularFile(template)) {
            System.out.println("!! AGM template not found: " + template);
            return null;
        }
        if (directors.isEmpty()) {
            System.out.println("!! No directors in the financial statement for " + company
                    + " — AGM minute written without chairman or attendees. Check it.");
        }

        Path minute = collationFolder.resolve(fileName);

        try {
            Files.copy(template, minute, StandardCopyOption.REPLACE_EXISTING);

            XWPFDocument document;
            try (FileInputStream fis = new FileInputStream(minute.toFile())) {
                document = new XWPFDocument(fis);
            }

            try {
                fill(document, company, acn, principalAddress, registeredAddress, directors, accountsOf, date);
                try (FileOutputStream fos = new FileOutputStream(minute.toFile())) {
                    document.write(fos);
                }
            } finally {
                document.close();
            }

            System.out.println("YES - AGM minute written: " + minute);
            System.out.println("  " + company + " ACN " + acn + " | chairman "
                    + (directors.isEmpty() ? "-" : directors.get(0)) + " | present " + directors
                    + " | accounts of " + accountsOf);
            return minute;

        } catch (IOException e) {
            System.out.println("ERROR - Unable to write the AGM minute for " + company + ": " + e.getMessage());
            return null;
        }
    }

    private void fill(XWPFDocument document, String company, String acn, String principalAddress,
            String registeredAddress, List<String> directors, String accountsOf, LocalDate date) {

        String year = ItrPaths.ITR_YEAR;
        String nextYear = String.valueOf(Integer.parseInt(year) + 1);
        String chairman = directors.isEmpty() ? "" : directors.get(0);
        boolean several = directors.size() > 1;

        for (XWPFParagraph paragraph : document.getParagraphs()) {

            String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
            String upper = text.toUpperCase(Locale.ENGLISH);

            if (upper.contains(" ACN ") || upper.matches(".*\\bACN\\b.*")) {
                setParagraphText(paragraph, company.toUpperCase(Locale.ENGLISH) + " ACN " + acn);
            } else if (upper.startsWith("REGISTERED OFFICE ADDRESS") && registeredAddress != null) {
                setParagraphText(paragraph, "REGISTERED OFFICE ADDRESS: " + registeredAddress.toUpperCase(Locale.ENGLISH));
            } else if (upper.startsWith("PRINCIPAL PLACE OF BUSINESS")) {
                setParagraphText(paragraph, "PRINCIPAL PLACE OF BUSINESS: " + principalAddress.toUpperCase(Locale.ENGLISH));
            } else if (upper.equals("CHAIRMAN")) {
                setParagraphText(paragraph, chairman.isEmpty() ? "CHAIRMAN" : chairman + " - CHAIRMAN");
            } else if (upper.startsWith("DATE:")) {
                setParagraphText(paragraph, "Date: " + date.format(DATE_FORMAT));
            }
        }

        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                if (row.getTableCells().size() < 2) {
                    continue;
                }
                String label = row.getCell(0).getText().trim().toUpperCase(Locale.ENGLISH);
                XWPFTableCell value = row.getCell(1);

                if (label.startsWith("CHAIRMAN")) {
                    setCellLines(value, chairman + " was elected to chair the meeting.");
                } else if (label.startsWith("PRESENT")) {
                    setCellLines(value, directors.toArray(new String[0]));
                } else if (label.startsWith("RE-APPOINTMENT")) {
                    setCellLines(value, reappointment(directors, several));
                } else if (label.startsWith("ACCOUNTS")) {
                    setCellLines(value, "It was resolved that the financial accounts of " + accountsOf
                            + " for the year ended 30 June " + year + " and the "
                            + (several ? "directors'" : "director's")
                            + " declaration tabled at the meeting, be received and adopted");
                } else if (label.contains("AUDITOR")) {
                    setCellLines(value, "It was resolved that no auditor be appointed for the year ended 30 June "
                            + nextYear + ".");
                }
            }
        }
    }

    /** The re-appointment resolution, for one director or several. */
    private String reappointment(List<String> directors, boolean several) {
        String names = TrustCoverLetterService.joinNames(directors);
        if (!several) {
            return "The chairman advised that " + names + " retired by rotation as director in accordance with the"
                    + " Constitution of the Company and being eligible for re-election offered himself for"
                    + " re-election as director of the Company. It was resolved that the director who retired in"
                    + " accordance with the Constitution of the Company, having offered himself for re-election"
                    + " and being eligible for re-election be re-elected director of the Company.";
        }
        return "The chairman advised that " + names + " retired by rotation as directors in accordance with the"
                + " Constitution of the Company and being eligible for re-election offered themselves for"
                + " re-election as directors of the Company. It was resolved that the directors who retired in"
                + " accordance with the Constitution of the Company, having offered themselves for re-election"
                + " and being eligible for re-election be re-elected directors of the Company.";
    }

    /** Replaces a cell's text with one line per value, keeping the first run's formatting. */
    private void setCellLines(XWPFTableCell cell, String... lines) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        for (int i = paragraph.getRuns().size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
        run.setText(lines.length == 0 ? "" : lines[0], 0);
        for (int i = 1; i < lines.length; i++) {
            run.addBreak();
            run.setText(lines[i]);
        }
        // A list of names in a justified cell gets stretched across the line ("John    Brunton")
        if (lines.length > 1) {
            paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
        }
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
