package FileExplorerCollation.filesearch.document;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Fills in the Fee From Refund (FFR) authority for a refund that is paid into the
 * practice's trust account.
 *
 * The template ("Fee From Refund (new authority).docx" in the ITR Letters folder):
 *
 * <pre>
 *   F/Y 202#                                              -> F/Y 2026, from the ITR
 *   TRUST ACCOUNT TRANSACTION | EXPECTED REFUND           -> recipient | refund
 *   TOTAL:                    |                           -> TOTAL:    | refund
 *   Client Name | Invoice Number | Amount                 -> recipient | final invoice no | its amount
 *   ACCT NAME / BSB / A/C: ______                         -> the client's own account, from the ITR
 *   EXPECTED BALANCE AMOUNT:                              -> refund minus the final invoice amount
 * </pre>
 *
 * The Date, Name and Signature lines and the "I, ____" authority are left for the
 * client to complete.
 */
public class FfrService {

    /** What the filled-in form is called in Collation Testing. */
    public static final String FFR_FILE_NAME = "Fee From Refund " + ItrPaths.ITR_YEAR + ".docx";

    /**
     * @param recipientName      from the cover letter, e.g. "Kevin"
     * @param refundAmount       as on the return, e.g. "1,165.00"
     * @param finalInvoiceNumber from the job's Financial tab; blank when none
     * @param finalInvoiceAmount its amount; blank when none
     * @param clientAccount      the client's own bank account from the ITR, or null
     * @return the filled-in form, or null if it could not be written
     */
    public Path createFfr(Path collationFolder, String financialYear, String recipientName, String refundAmount,
            String finalInvoiceNumber, String finalInvoiceAmount, ItrPositionReader.BankAccount clientAccount) {

        Path template = Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.FFR_TEMPLATE_FILE);

        if (!Files.isRegularFile(template)) {
            System.out.println("!! FFR template not found: " + template);
            return null;
        }

        Path ffr = collationFolder.resolve(FFR_FILE_NAME);

        Double refund = money(refundAmount);
        Double invoice = money(finalInvoiceAmount);
        String balance = refund != null && invoice != null ? format(refund - invoice) : "";
        String refundText = refund == null ? nullToEmpty(refundAmount) : format(refund);

        if (balance.isEmpty()) {
            System.out.println("!! FFR: no final invoice amount — Expected Balance Amount left blank. Check this job.");
        }
        if (clientAccount == null) {
            System.out.println("!! FFR: no client bank account (other than Fortuna's) in the ITR — account lines left blank.");
        }

        try {

            Files.copy(template, ffr, StandardCopyOption.REPLACE_EXISTING);

            XWPFDocument document;
            try (FileInputStream fis = new FileInputStream(ffr.toFile())) {
                document = new XWPFDocument(fis);
            }

            try {

                for (XWPFParagraph paragraph : document.getParagraphs()) {

                    String text = paragraph.getText().trim();

                    if (text.startsWith("F/Y")) {
                        setParagraphText(paragraph, "F/Y " + financialYear);
                    } else if (text.startsWith("ACCT NAME")) {
                        setValue(paragraph, clientAccount == null ? "" : clientAccount.name);
                    } else if (text.startsWith("BSB")) {
                        setValue(paragraph, clientAccount == null ? "" : clientAccount.bsb);
                    } else if (text.startsWith("A/C")) {
                        setValue(paragraph, clientAccount == null ? "" : clientAccount.number);
                    } else if (text.startsWith("EXPECTED BALANCE AMOUNT")) {
                        setValue(paragraph, balance);
                    }
                }

                for (XWPFTable table : document.getTables()) {

                    String headings = table.getRow(0).getTableCells().stream()
                            .map(XWPFTableCell::getText).reduce("", (a, b) -> a + "|" + b).toUpperCase();

                    if (headings.contains("TRUST ACCOUNT TRANSACTION")) {
                        setCellText(table.getRow(1).getCell(0), recipientName);
                        setCellText(table.getRow(1).getCell(1), refundText);
                        for (int r = 1; r < table.getNumberOfRows(); r++) {
                            if (table.getRow(r).getCell(0).getText().trim().toUpperCase().startsWith("TOTAL")) {
                                setCellText(table.getRow(r).getCell(1), refundText);
                            }
                        }
                    } else if (headings.contains("INVOICE NUMBER")) {
                        setCellText(table.getRow(1).getCell(0), recipientName);
                        setCellText(table.getRow(1).getCell(1), nullToEmpty(finalInvoiceNumber));
                        setCellText(table.getRow(1).getCell(2),
                                invoice == null ? nullToEmpty(finalInvoiceAmount) : format(invoice));
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(ffr.toFile())) {
                    document.write(fos);
                }

            } finally {
                document.close();
            }

            System.out.println("YES - FFR written: " + ffr);
            System.out.println("  F/Y " + financialYear + " | refund " + refundText + " | invoice "
                    + nullToEmpty(finalInvoiceNumber) + " " + nullToEmpty(finalInvoiceAmount)
                    + " | balance " + balance);

            return ffr;

        } catch (IOException e) {
            System.out.println("ERROR - Unable to write the FFR: " + e.getMessage());
            return null;
        }
    }

    /** The expected balance as written on the form, for the run report and the sheet. */
    public String expectedBalance(String refundAmount, String finalInvoiceAmount) {
        Double refund = money(refundAmount);
        Double invoice = money(finalInvoiceAmount);
        return refund != null && invoice != null ? format(refund - invoice) : "";
    }

    /** "$1,165.00" or "1,165.00" -> 1165.0; null when there is no single amount to read. */
    private Double money(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("$", "").replace(",", "").trim();
        return cleaned.matches("-?\\d+(\\.\\d+)?") ? Double.valueOf(cleaned) : null;
    }

    private String format(double amount) {
        return String.format("$%,.2f", amount);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * "ACCT NAME:  ______" -> "ACCT NAME:  Kevin Walter Stevens". The underline sits in
     * the same run as the label, followed by a run holding only a line break, so the
     * underline is replaced in place and the break left alone.
     */
    private void setValue(XWPFParagraph paragraph, String value) {

        if (paragraph.getRuns().isEmpty()) {
            paragraph.createRun().setText(value, 0);
            return;
        }

        XWPFRun labelRun = paragraph.getRuns().get(0);
        String text = labelRun.getText(0) == null ? "" : labelRun.getText(0);
        String label = text.replaceAll("_+.*$", "").replaceAll("\\s+$", "");

        labelRun.setText(label + "  " + value, 0);

        // Any underline left in later runs goes too
        for (int i = 1; i < paragraph.getRuns().size(); i++) {
            XWPFRun run = paragraph.getRuns().get(i);
            String rest = run.getText(0);
            if (rest != null && rest.contains("_")) {
                run.setText(rest.replace("_", "").trim(), 0);
            }
        }
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
