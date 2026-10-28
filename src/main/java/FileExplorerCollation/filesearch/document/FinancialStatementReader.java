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
