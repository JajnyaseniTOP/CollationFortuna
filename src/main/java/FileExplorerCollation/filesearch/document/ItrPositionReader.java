package FileExplorerCollation.filesearch.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the estimated tax position off the ITR document.
 *
 * The estimate page of "2026_ITR_ClientName.pdf" ends with one line that settles it:
 *
 * <pre>
 *   Assessed tax payable ($1,165.00)
 *   ...
 *   $1,165.00Amount refundable        or        $2,340.00Amount payable
 * </pre>
 *
 * Only "Amount refundable" / "Amount payable" is read. "Assessed tax payable" sits a
 * few lines above it on every return, refunds included, so "payable" on its own
 * proves nothing.
 */
public class ItrPositionReader {

    private static final Pattern POSITION_LINE = Pattern.compile("(?i)amount\\s+(refundable|payable)");

    private static final Pattern DOLLAR_AMOUNT = Pattern.compile("\\$\\s?(\\d[\\d,]*(?:\\.\\d{1,2})?)");

    /**
     * @return the position, or null when the document has no "Amount refundable" or
     *         "Amount payable" line to read it from
     */
    public ItrPosition read(Path itrFile) {

        try (PDDocument document = PDDocument.load(itrFile.toFile())) {

            String[] lines = new PDFTextStripper().getText(document).split("\\r?\\n");

            for (int i = 0; i < lines.length; i++) {

                Matcher position = POSITION_LINE.matcher(lines[i]);

                if (!position.find()) {
                    continue;
                }

                boolean refund = position.group(1).toLowerCase().startsWith("refund");

                // The figure is normally on the same line; the lines either side are a
                // fallback for a layout that wraps it.
                String amount = amountIn(lines[i]);

                if (amount == null && i + 1 < lines.length) {
                    amount = amountIn(lines[i + 1]);
                }

                if (amount == null && i > 0) {
                    amount = amountIn(lines[i - 1]);
                }

                ItrPosition result = new ItrPosition(refund, amount);

                System.out.println("YES - Estimated position read from the ITR: " + result.describe());

                return result;
            }

            System.out.println("NO - no 'Amount refundable' or 'Amount payable' line in " + itrFile.getFileName());
            return null;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to read " + itrFile + ": " + e.getMessage());
            return null;
        }
    }

    /** "$1,165.00Amount refundable" -> "1,165.00"; null when the line has no dollar figure. */
    private String amountIn(String line) {

        Matcher amount = DOLLAR_AMOUNT.matcher(line);

        return amount.find() ? amount.group(1) : null;
    }

    /** What the ITR says the client will pay or get back. */
    public static final class ItrPosition {

        private final boolean refund;

        /** As printed, e.g. "1,165.00". Null when the figure could not be read. */
        private final String amount;

        ItrPosition(boolean refund, String amount) {
            this.refund = refund;
            this.amount = amount;
        }

        public boolean isRefund() {
            return refund;
        }

        /** As printed on the return, e.g. "1,165.00"; null when it could not be read. */
        public String getAmount() {
            return amount;
        }

        /**
         * True when there is something for the client to pay. A return that comes out
         * at exactly nil is worded "Amount payable" but needs no payment slip.
         */
        public boolean isPayable() {

            if (refund) {
                return false;
            }

            if (amount == null) {
                return true;
            }

            try {
                return Double.parseDouble(amount.replace(",", "")) > 0;
            } catch (NumberFormatException e) {
                return true;
            }
        }

        /** The wording for the cover letter table, e.g. "$1,165.00 Refund". */
        public String describe() {

            String wording = refund ? "Refund" : "Payable";

            if (amount == null) {
                return wording;
            }

            try {
                return String.format("$%,.2f %s", Double.parseDouble(amount.replace(",", "")), wording);
            } catch (NumberFormatException e) {
                return "$" + amount + " " + wording;
            }
        }
    }
}
