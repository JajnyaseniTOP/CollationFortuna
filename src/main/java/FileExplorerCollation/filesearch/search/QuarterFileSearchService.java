package FileExplorerCollation.filesearch.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the activity statement inside an already located month or quarter folder.
 *
 * The expected shape is year_Month_AS_ClientName, but the server is not
 * consistent about any part of it:
 *
 * <pre>
 *   2026_April_AS_CarnarvonRegionalLabourHirePtyLt.pdf   underscores, name cut short
 *   2026 June AS - Carnarvon Regional Labour Hire Pty Ltd.pdf   spaces and a dash
 *   2026_March_AS_GlossopShane.pdf                        surname first
 *   2026 June AS - Shane Glossop.pdf                      given name first
 *   1. 2026_June_AS_GHCabinetsPtyLtd.pdf                  numbered for ordering
 * </pre>
 *
 * So punctuation is ignored, the client name is accepted in whichever order it
 * is written, and a name that stops short still counts. Where several files
 * could match, the closest one wins - which keeps an "- old" copy from being
 * picked over the current document.
 */
public class QuarterFileSearchService {

    /** "AS" on its own, so "June Bas 2026 - Signed" and names like "Glass" do not count. */
    private static final Pattern AS_MARKER = Pattern.compile("(^|[^A-Za-z])AS([^A-Za-z]|$)");

    /**
     * The processor files in a month or quarter folder: every file whose name
     * carries "AS". The folder already pins down the client and the period, so
     * the rest of the name does not matter.
     *
     * <p>Only the folder itself is searched - the Collation and Testing subfolders
     * hold copies.
     */
    public List<Path> findAsFiles(String periodFolderPath) {

        List<Path> asFiles = new ArrayList<>();

        try (var files = Files.list(Paths.get(periodFolderPath))) {

            for (Path file : (Iterable<Path>) files::iterator) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String fileName = file.getFileName().toString();
                int dotIndex = fileName.lastIndexOf('.');
                String withoutExtension = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

                if (AS_MARKER.matcher(withoutExtension).find()) {
                    System.out.println("YES - AS file: " + fileName);
                    asFiles.add(file);
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + periodFolderPath);
        }

        if (asFiles.isEmpty()) {
            System.out.println("NO - no AS file in " + periodFolderPath);
        }

        return asFiles;
    }

    /** How much of the client name has to line up before a loose match counts. */
    private static final int MIN_CLIENT_NAME_OVERLAP = 5;

    /**
     * @param clientNameForms the client name as written in the sheet, plus any
     *                        other spelling worth accepting (e.g. the reversed
     *                        "Surname, Given" form)
     */
    public String searchQuarterFile(String quarterFolderPath, String year, String monthFolder,
            String... clientNameForms) {

        String formattedClientName = clientNameForms.length == 0 || clientNameForms[0] == null
                ? ""
                : clientNameForms[0].replaceAll("\\s+", "");

        String expectedFileName = year + "_" + monthFolder + "_AS_" + formattedClientName;

        System.out.println("Searching for file: " + expectedFileName);
        System.out.println("Inside path: " + quarterFolderPath);

        Path quarterPath = Paths.get(quarterFolderPath);

        // Best loose match so far, and how much extra text it carried
        Path looseMatch = null;
        int looseMatchScore = Integer.MAX_VALUE;

        try {
            try (var files = Files.list(quarterPath)) {

                for (Path file : (Iterable<Path>) files::iterator) {

                    if (!Files.isRegularFile(file)) {
                        continue;
                    }

                    String fileName = file.getFileName().toString();

                    System.out.println("Checking file: " + fileName);

                    String fileNameWithoutExtension = fileName;

                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex > 0) {
                        fileNameWithoutExtension = fileName.substring(0, dotIndex);
                    }

                    // Staff number the files they put in order: "1. 2026_June_AS_GHCabinetsPtyLtd".
                    // One or two digits and a dot, so the year at the front is never taken off.
                    fileNameWithoutExtension = fileNameWithoutExtension.replaceFirst("^\\d{1,2}\\.\\s*", "");

                    if (fileNameWithoutExtension.equalsIgnoreCase(expectedFileName)) {

                        System.out.println("YES - Quarter file found");
                        System.out.println("File Name: " + fileName);
                        System.out.println("File Path: " + file);

                        return file.toString();
                    }

                    int score = scoreLooseMatch(fileNameWithoutExtension, year, monthFolder, clientNameForms);

                    if (score >= 0 && score < looseMatchScore) {
                        looseMatch = file;
                        looseMatchScore = score;
                    }
                }
            }

            if (looseMatch != null) {

                System.out.println("YES - Quarter file found (named differently on the server)");
                System.out.println("Expected  : " + expectedFileName);
                System.out.println("File Name : " + looseMatch.getFileName());
                System.out.println("File Path : " + looseMatch);

                return looseMatch.toString();
            }

            System.out.println("NO - Quarter file not found");
            return null;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to search quarter file");
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Scores how well a file name matches, ignoring punctuation.
     *
     * @return how many characters of slack the match needed - lower is better -
     *         or -1 when the file is not a match at all
     */
    private int scoreLooseMatch(String fileNameWithoutExtension, String year, String monthFolder,
            String... clientNameForms) {

        String normalizedFileName = normalize(fileNameWithoutExtension);
        String normalizedPrefix = normalize(year + monthFolder + "AS");

        // Must carry the year, the month and the AS marker
        if (!normalizedFileName.startsWith(normalizedPrefix)) {
            return -1;
        }

        String actualClientName = normalizedFileName.substring(normalizedPrefix.length());

        int bestScore = -1;

        for (String clientNameForm : clientNameForms) {

            if (clientNameForm == null || clientNameForm.trim().isEmpty()) {
                continue;
            }

            String expectedClientName = normalize(clientNameForm);

            int overlap = Math.min(actualClientName.length(), expectedClientName.length());

            if (overlap < MIN_CLIENT_NAME_OVERLAP) {
                continue;
            }

            if (!actualClientName.startsWith(expectedClientName)
                    && !expectedClientName.startsWith(actualClientName)) {
                continue;
            }

            int score = Math.abs(actualClientName.length() - expectedClientName.length());

            if (bestScore < 0 || score < bestScore) {
                bestScore = score;
            }
        }

        return bestScore;
    }

    /** Lower-cases and drops anything that is not a letter or digit. */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
