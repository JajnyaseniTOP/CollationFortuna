package FileExplorerCollation.filesearch.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the client folder tree: client code folder -> year folder -> BAS & IAS folder -> quarter folder.
 */
public class FolderSearchService {

    /**
     * Spellings the BAS folder goes by on the server, most specific first.
     * Plain "BAS" is last so a year holding both keeps the combined folder.
     */
    private static final String[] BAS_FOLDER_NAMES = {
            "BAS & IAS", "BAS-IAS", "BAS and IAS", "BAS"
    };

    /** Year folder to look for, e.g. "2026". */
    private final String year;

    /** Month name derived from the quarter; set by {@link #searchQuarterFolder}. */
    private String monthFolder;

    public FolderSearchService(String year) {
        this.year = year;
    }

    /** Month name resolved by the last {@link #searchQuarterFolder} call. */
    public String getMonthFolder() {
        return monthFolder;
    }

    /**
     * Finds the group folder ("Group+TeamName", column J) directly under the Clients root.
     *
     * Folder names on the server are inconsistent about the team suffix
     * ("ADDISCOTT GROUP - A" vs "ACTEON GROUP -B") and about case, so the
     * comparison ignores case and spacing.
     */
    public Path findGroupFolder(String clientsRoot, String groupFolderName) {

        if (groupFolderName == null || groupFolderName.trim().isEmpty()) {
            return null;
        }

        System.out.println("Looking for group folder: " + groupFolderName);

        Path match = findChildByName(Paths.get(clientsRoot), groupFolderName);

        if (match == null) {
            match = findGroupFolderRelaxed(Paths.get(clientsRoot), groupFolderName);
        }

        if (match != null) {
            System.out.println("YES - Group folder found: " + match);
        } else {
            System.out.println("NO - Group folder not found under " + clientsRoot);
        }

        return match;
    }

    /**
     * Second pass for group folders whose name on the server does not match XPM
     * exactly.
     *
     * <p>Two differences are common. The team suffix drifts, and XPM adds a
     * parenthetical to tell two groups of the same name apart where the server
     * only carries one of them ("Davidson Group (Thomas)" against the folder
     * "Davidson Group - A").
     *
     * <p>The parenthetical is dropped only as a last resort, and only when exactly
     * one folder is left: three separate Becker groups live under the one root, so
     * an ambiguous match has to stay unresolved rather than pick a client's folder
     * at random.
     */
    private Path findGroupFolderRelaxed(Path clientsRoot, String groupFolderName) {

        String wantedBase = normalize(stripTrailingTeam(groupFolderName));
        String wantedWithoutQualifier = normalize(stripParenthetical(stripTrailingTeam(groupFolderName)));

        List<Path> sameBase = new ArrayList<>();
        List<Path> sameBaseIgnoringQualifier = new ArrayList<>();

        try (var children = Files.list(clientsRoot)) {

            for (Path child : (Iterable<Path>) children::iterator) {

                if (!Files.isDirectory(child)) {
                    continue;
                }

                String childName = stripTrailingTeam(child.getFileName().toString());

                if (normalize(childName).equals(wantedBase)) {
                    sameBase.add(child);
                }

                if (!wantedWithoutQualifier.isEmpty()
                        && normalize(stripParenthetical(childName)).equals(wantedWithoutQualifier)) {
                    sameBaseIgnoringQualifier.add(child);
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + clientsRoot);
            return null;
        }

        if (sameBase.size() == 1) {
            System.out.println("Matched the group folder on a different team suffix: " + sameBase.get(0));
            return sameBase.get(0);
        }

        if (sameBaseIgnoringQualifier.size() == 1) {
            System.out.println("Matched the group folder without XPM's qualifier: "
                    + sameBaseIgnoringQualifier.get(0));
            return sameBaseIgnoringQualifier.get(0);
        }

        if (sameBaseIgnoringQualifier.size() > 1) {
            System.out.println("Ambiguous group folder for '" + groupFolderName
                    + "' - leaving it alone rather than guessing between " + sameBaseIgnoringQualifier);
        }

        return null;
    }

    /** Removes a trailing team marker such as " - A", " -B1" or " - C". */
    private String stripTrailingTeam(String name) {
        return name == null ? "" : name.trim().replaceAll("(?i)\\s*-\\s*[A-Z]\\d?$", "").trim();
    }

    /** Removes a qualifier in brackets: "Davidson Group (Thomas)" -> "Davidson Group". */
    private String stripParenthetical(String name) {
        return name == null ? "" : name.replaceAll("\\([^()]*\\)", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Looks for the client folder by name, trying each candidate in turn
     * (column K, then L, then M) and stopping at the first one that exists.
     *
     * Each candidate is tried as-is and again with its trailing " - team"
     * suffix removed, because the spreadsheet appends the team to the client
     * name but the folders on the server only carry it at group level.
     *
     * The group folder is searched first; if the client is not in there, the
     * Clients root is tried as well.
     *
     * @return the folder that was found, or null if none of the names matched
     */
    public Path searchClientFolderByNames(String clientsRoot, String groupFolderName,
            String internalTeam, String... candidateNames) {

        Path groupFolder = findGroupFolder(clientsRoot, groupFolderName);

        if (groupFolder != null) {

            Path match = searchNamesIn(groupFolder, internalTeam, candidateNames);

            if (match != null) {
                return match;
            }
        }

        System.out.println("Falling back to the Clients root...");

        return searchNamesIn(Paths.get(clientsRoot), internalTeam, candidateNames);
    }

    /** Tries every candidate name, and its suffix-stripped form, inside one directory. */
    private Path searchNamesIn(Path directory, String internalTeam, String... candidateNames) {

        for (String candidateName : candidateNames) {

            if (candidateName == null || candidateName.trim().isEmpty()) {
                continue;
            }

            System.out.println("Trying client name: " + candidateName + "  (in " + directory + ")");

            Path match = findChildByName(directory, candidateName);

            if (match != null) {
                System.out.println("YES - Client folder found");
                System.out.println("Folder Path: " + match);
                return match;
            }

            String withoutTeam = stripTeamSuffix(candidateName, internalTeam);

            if (!withoutTeam.equalsIgnoreCase(candidateName)) {

                System.out.println("Trying without team suffix: " + withoutTeam);

                match = findChildByName(directory, withoutTeam);

                if (match != null) {
                    System.out.println("YES - Client folder found");
                    System.out.println("Folder Path: " + match);
                    return match;
                }
            }

            System.out.println("NO - not found under this name");
        }

        return null;
    }

    /** Removes a trailing " - A" / " -A" team suffix from a client name. */
    private String stripTeamSuffix(String name, String internalTeam) {

        if (internalTeam == null || internalTeam.trim().isEmpty()) {
            return name;
        }

        String trimmedName = name.trim();
        String suffix = "-" + internalTeam.trim();

        String compact = trimmedName.replace(" ", "");

        if (compact.toLowerCase().endsWith(suffix.toLowerCase())) {

            int dashIndex = trimmedName.lastIndexOf('-');

            if (dashIndex > 0) {
                return trimmedName.substring(0, dashIndex).trim();
            }
        }

        return trimmedName;
    }

    /** Finds a direct child directory whose name matches, ignoring case and spacing. */
    private Path findChildByName(Path directory, String name) {

        String wanted = normalize(name);

        try (var children = Files.list(directory)) {

            for (Path child : (Iterable<Path>) children::iterator) {

                if (!Files.isDirectory(child)) {
                    continue;
                }

                if (normalize(child.getFileName().toString()).equals(wanted)) {
                    return child;
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + directory);
            e.printStackTrace();
        }

        return null;
    }

    /** Lower-cases and removes spaces so " - A" and " -A" compare equal. */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replace(" ", "").trim();
    }

    /** True if the folder name is one of the known BAS spellings. */
    private boolean isBasFolderName(String folderName) {

        String normalized = normalize(folderName);

        for (String basFolderName : BAS_FOLDER_NAMES) {
            if (normalize(basFolderName).equals(normalized)) {
                return true;
            }
        }

        return false;
    }

    public String searchClientCodeFile(Path clientFolderPath, String clientCode) {
        System.out.println("Searching for Client Code Folder: " + clientCode);
        System.out.println("Inside Client Folder: " + clientFolderPath);

        // Individuals are filed as "Surname, Given (CODE)" - the reversed name
        // with the code appended - which no single spreadsheet column spells
        // out, so the code is the reliable way in. Check the direct children
        // before falling back to walking the whole tree.
        if (clientCode != null && !clientCode.trim().isEmpty()) {

            Path directChild = findChildContaining(clientFolderPath, clientCode.trim());

            if (directChild != null) {
                System.out.println("YES - Client Code folder exists");
                System.out.println("Found Folder: " + directChild.getFileName());
                System.out.println("Folder Path : " + directChild);
                return directChild.toString();
            }
        }

        try {
            try (var files = Files.walk(clientFolderPath)) {
                for (Path path : (Iterable<Path>) files::iterator) {
                    if (!Files.isDirectory(path)) {
                        continue;
                    }
                    // Skip the original client folder
                    if (path.equals(clientFolderPath)) {
                        continue;
                    }
                    String folderName = path.getFileName().toString();
                    System.out.println("Checking folder: " + folderName);
                    if (folderName.toLowerCase().contains(clientCode.toLowerCase())) {
                        System.out.println("YES - Client Code folder exists");
                        System.out.println("Client Code : " + clientCode);
                        System.out.println("Found Folder: " + folderName);
                        System.out.println("Folder Path : " + path);
                        return path.toString();
                    }
                }
            }

            System.out.println("NO - Client Code folder does not exist");
            return null;
        } catch (IOException e) {
            System.out.println("ERROR - Unable to search Client Folder");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Goes inside the client folder and picks out the year folder.
     *
     * The year folder normally sits directly inside the client folder
     * (alongside "Permanent", "Xero reports" and the other years), so that is
     * checked first; the recursive walk is only a fallback for odd layouts.
     */
    public String searchYearFolder(String clientCodeFolderPath) {
        return searchYearFolder(clientCodeFolderPath, year);
    }

    /**
     * Goes inside the client folder and picks out the given year folder.
     *
     * FABA clients keep the years directly inside the client folder, while FUT
     * clients nest them under "Year End", so both are checked before falling
     * back to a recursive walk.
     */
    public String searchYearFolder(String clientCodeFolderPath, String wantedYear) {

        System.out.println("Going inside the client folder: " + clientCodeFolderPath);
        System.out.println("Searching for " + wantedYear + " folder");

        Path clientCodePath = Paths.get(clientCodeFolderPath);

        Path directChild = findChildByName(clientCodePath, wantedYear);

        if (directChild != null) {
            System.out.println("YES - " + wantedYear + " folder exists");
            System.out.println(wantedYear + " Folder Path: " + directChild);
            return directChild.toString();
        }

        Path yearEndFolder = findChildByName(clientCodePath, "Year End");

        if (yearEndFolder != null) {

            Path underYearEnd = findChildByName(yearEndFolder, wantedYear);

            if (underYearEnd != null) {
                System.out.println("YES - " + wantedYear + " folder exists under 'Year End'");
                System.out.println(wantedYear + " Folder Path: " + underYearEnd);
                return underYearEnd.toString();
            }
        }

        System.out.println(wantedYear + " folder is not a direct child - searching deeper...");

        try {
            try (var files = Files.walk(clientCodePath)) {

                for (Path path : (Iterable<Path>) files::iterator) {

                    if (!Files.isDirectory(path)) {
                        continue;
                    }

                    String folderName = path.getFileName().toString();
                    if (folderName.equals(wantedYear)) {
                        System.out.println("YES - " + wantedYear + " folder exists");
                        System.out.println(wantedYear + " Folder Path: " + path);
                        return path.toString();
                    }
                }
            }

            System.out.println("NO - " + wantedYear + " folder does not exist");
            return null;

        } catch (IOException e) {
            System.out.println("ERROR - Unable to search " + wantedYear + " folder");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Goes inside the year folder and picks out the BAS folder.
     *
     * The name is not consistent across years on the server - 2026 uses
     * "BAS & IAS" while 2027 uses "BAS-IAS" - so every known spelling is tried
     * as a direct child, most specific first, before falling back to a walk.
     */
    public String searchBasIasFolder(String yearFolderPath) {

        System.out.println("Going inside the year folder: " + yearFolderPath);
        System.out.println("Searching for the BAS folder");

        Path yearPath = Paths.get(yearFolderPath);

        for (String basFolderName : BAS_FOLDER_NAMES) {

            Path directChild = findChildByName(yearPath, basFolderName);

            if (directChild != null) {
                System.out.println("YES - '" + directChild.getFileName() + "' folder exists");
                System.out.println("BAS Folder Path: " + directChild);
                return directChild.toString();
            }

            System.out.println("NO - no '" + basFolderName + "' folder here");
        }

        System.out.println("BAS folder is not a direct child - searching deeper...");

        try {
            try (var files = Files.walk(yearPath)) {

                for (Path path : (Iterable<Path>) files::iterator) {

                    if (!Files.isDirectory(path)) {
                        continue;
                    }

                    String folderName = path.getFileName().toString();
                    if (isBasFolderName(folderName)) {

                        System.out.println("YES - '" + folderName + "' folder exists");
                        System.out.println("BAS Folder Path: " + path);

                        return path.toString();
                    }
                }
            }

            System.out.println("NO - BAS folder does not exist");
            return null;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to search BAS folder");
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Goes inside the BAS folder and picks out one month.
     *
     * Month folders are numbered and not in calendar order ("1. July",
     * "10. April", "7. January 2026"), so the match is on the folder
     * containing the month name. The three letter form is tried second
     * because at least one folder on the server is misspelled ("8. Febrary").
     */
    public String searchMonthFolder(String basIasFolderPath, String monthName, String monthAbbreviation) {

        System.out.println("Going inside the BAS folder: " + basIasFolderPath);
        System.out.println("Searching for month: " + monthName);

        Path basIasPath = Paths.get(basIasFolderPath);

        Path match = findChildContaining(basIasPath, monthName);

        if (match == null) {

            match = findChildContaining(basIasPath, monthAbbreviation);

            if (match != null) {
                System.out.println("Matched on '" + monthAbbreviation + "' - folder name is not spelled out in full");
            }
        }

        if (match != null) {
            System.out.println("YES - Month folder found: " + match.getFileName());
            System.out.println("Month Folder Path: " + match);
            return match.toString();
        }

        System.out.println("NO - No folder for " + monthName + " in " + basIasFolderPath);
        return null;
    }

    /**
     * Goes inside the BAS folder and picks out one quarter.
     *
     * Quarterly clients keep plain "Q1".."Q4" folders, e.g.
     * "Glossop, Shane (GLOSSHAN)\2026\BAS\Q4".
     */
    public String searchQuarterFolderByName(String basIasFolderPath, String quarterName) {

        System.out.println("Going inside the BAS folder: " + basIasFolderPath);
        System.out.println("Searching for quarter: " + quarterName);

        Path match = findChildByName(Paths.get(basIasFolderPath), quarterName);

        if (match != null) {
            System.out.println("YES - Quarter folder found: " + match.getFileName());
            System.out.println("Quarter Folder Path: " + match);
            return match.toString();
        }

        System.out.println("NO - No " + quarterName + " folder in " + basIasFolderPath);
        return null;
    }

    /** First direct child directory whose name contains the given text. */
    private Path findChildContaining(Path directory, String text) {

        String wanted = text.toLowerCase();

        try (var children = Files.list(directory)) {

            for (Path child : (Iterable<Path>) children::iterator) {

                if (!Files.isDirectory(child)) {
                    continue;
                }

                if (child.getFileName().toString().toLowerCase().contains(wanted)) {
                    return child;
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + directory);
            e.printStackTrace();
        }

        return null;
    }

    public String searchQuarterFolder(String basIasFolderPath, String quarter) {

        monthFolder = getMonthFolderFromQuarter(quarter);

        if (monthFolder == null) {
            System.out.println("Invalid Quarter: " + quarter);
            return null;
        }

        System.out.println("Searching for month containing: " + monthFolder);
        System.out.println("Inside path: " + basIasFolderPath);

        Path basIasPath = Paths.get(basIasFolderPath);

        try {
            try (var files = Files.walk(basIasPath)) {

                for (Path path : (Iterable<Path>) files::iterator) {

                    if (!Files.isDirectory(path)) {
                        continue;
                    }

                    String folderName = path.getFileName().toString();

                    //System.out.println("Checking folder: " + folderName);

                    // Contains month name instead of exact match
                    if (folderName.toLowerCase().contains(monthFolder.toLowerCase())) {

                        System.out.println("YES - Quarter folder exists");
                        System.out.println("Quarter : " + quarter);
                        System.out.println("Month   : " + monthFolder);
                        System.out.println("Found Folder: " + folderName);
                        System.out.println("Folder Path: " + path);

                        return path.toString();
                    }
                }
            }

            System.out.println("NO - Quarter folder does not exist");
            return null;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to search quarter folder");
            e.printStackTrace();

            return null;
        }
    }

    private String getMonthFolderFromQuarter(String quarter) {

        switch (quarter.toUpperCase()) {

            case "Q1":
                return "January";

            case "Q2":
                return "April";

            case "Q3":
                return "August";

            case "Q4":
                return "December";

            default:
                return null;
        }
    }
}
