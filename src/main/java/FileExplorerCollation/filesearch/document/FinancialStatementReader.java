package FileExplorerCollation.filesearch.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads a trust's financial statements for the people who sign them.
 *
 * The Trustee Declaration page ends with one block per director:
 *
 * <pre>
 *   Director:   John Brunton
 *   Date:
 *   Director:   Jessica Brunton
 *   Date:
 * </pre>
 */
public class FinancialStatementReader {

    private static final Pattern DIRECTOR = Pattern.compile("(?i)^\\s*(?:Director|Trustee)\\s*:\\s*(.+?)\\s*$");

    /** "The director(s) of Grass Tree Landscaping and Reticulation Pty Ltd declare that ..." */
    private static final Pattern TRUSTEE_COMPANY = Pattern.compile(
            "(?i)directors?(?:\\(s\\))?\\s+of\\s+(.+?\\b(?:Pty\\.?\\s*Ltd\\.?|Limited|Ltd\\.?))(?=\\s|$)");

    /**
     * The trust's corporate trustee, from the Trustee Declaration: the company whose
     * directors make the declaration (scope 4.7, condition 1).
     *
     * @return the company name, or null when the trustee is not a company
     */
    public String readTrusteeCompany(Path financialStatement) {

        try (PDDocument document = PDDocument.load(financialStatement.toFile())) {

            String text = new PDFTextStripper().getText(document).replace(' ', ' ').replaceAll("\\s+", " ");
            int declaration = text.toLowerCase().indexOf("trustee declaration");
            Matcher company = TRUSTEE_COMPANY.matcher(declaration >= 0 ? text.substring(declaration) : text);

            if (company.find()) {
                String name = company.group(1).trim();
                System.out.println("Corporate trustee in " + financialStatement.getFileName() + ": " + name);
                return name;
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to read " + financialStatement + ": " + e.getMessage());
        }

        System.out.println("No corporate trustee in " + financialStatement.getFileName());
        return null;
    }

    /**
     * @return the directors' names in the order printed, each once; empty when the
     *         statements carry no Director lines
     */
    public List<String> readDirectors(Path financialStatement) {

        List<String> directors = new ArrayList<>();

        try (PDDocument document = PDDocument.load(financialStatement.toFile())) {

            // The declaration uses non-breaking spaces around the names
            String text = new PDFTextStripper().getText(document).replace(' ', ' ');

            for (String line : text.split("\\r?\\n")) {

                Matcher director = DIRECTOR.matcher(line);

                if (director.find()) {
                    String name = director.group(1).replaceAll("\\s+", " ").trim();
                    if (!name.isEmpty() && !name.toLowerCase().startsWith("date")
                            && directors.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                        directors.add(name);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to read " + financialStatement + ": " + e.getMessage());
        }

        System.out.println("Directors in the financial statements: " + (directors.isEmpty() ? "none" : directors));
        return directors;
    }
}
