package FileExplorerCollation.filesearch.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import FileExplorerCollation.filesearch.config.ItrPaths;

/**
 * Walks the client folder tree for an income tax return:
 * client folder -> year folder -> Year End Workpapers -> V1 Deliv -> Reports -> 2026_ITR_ClientName.
 *
 * <pre>
 *   FUT\Clients\STEVENS, KEVIN - C\Stevens, Kevin\2026\Year End Workpapers\V1 Deliv\Reports\2026_ITR_KevinStevens.pdf
 * </pre>
 *
 * Nothing here walks the tree recursively. A group folder holds several entities,
 * each with its own year folders, so a deep search for "2026" can land in the wrong
 * entity's folder; every step looks at direct children only and stops if one is missing.
 */
public class ItrFolderSearch {

    private final FolderSearchService folderSearch = new FolderSearchService(null);

    /**
     * Finds the client's own folder - the one that holds its year folders.
     *
     * A client with a group is looked for inside the group folder. A client without
     * one sits directly under the Clients root, usually as a wrapper named surname
     * first with the team ("STEVENS, KEVIN - C") around the folder that is really
     * wanted ("Stevens, Kevin"), so a match at the root is opened and searched again.
     *
     * @param groupFolderName "Group - Team", or null when the client has no group
     * @return the folder, or null when neither the name nor the code matched
     */
    public Path findClientFolder(String clientsRoot, String groupFolderName, String clientName, String clientCode) {

        if (groupFolderName != null && !groupFolderName.trim().isEmpty()) {

            Path groupFolder = folderSearch.findGroupFolder(clientsRoot, groupFolderName);

            if (groupFolder != null) {

                Path inGroup = findClientIn(groupFolder, clientName, clientCode);

                if (inGroup != null) {
                    return inGroup;
                }

                // A group of one: the group folder is the client folder
                if (findYearFolder(groupFolder, ItrPaths.ITR_YEAR) != null) {
                    System.out.println("Group folder holds the year folders itself: " + groupFolder);
                    return groupFolder;
                }
            }

            System.out.println("Client not found by group - searching the Clients root by name and code...");
        }

        Path atRoot = findClientIn(Paths.get(clientsRoot), clientName, clientCode);

        if (atRoot == null) {
            System.out.println("NO - no folder for '" + clientName + "' (" + clientCode + ") under " + clientsRoot);
            return null;
        }

        Path inside = findClientIn(atRoot, clientName, clientCode);

        return inside != null ? inside : atRoot;
    }

    /** The child of a folder that belongs to the client: matched by name first, then by code. */
    private Path findClientIn(Path directory, String clientName, String clientCode) {

        String wanted = nameKey(clientName);

        List<Path> children = listDirectories(directory);

        if (!wanted.isEmpty()) {
            for (Path child : children) {
                if (nameKey(child.getFileName().toString()).equals(wanted)) {
                    System.out.println("YES - Client folder found by name: " + child);
                    return child;
                }
            }
        }

        if (clientCode != null && !clientCode.trim().isEmpty() && !clientCode.trim().toLowerCase().startsWith("no ")) {

            String code = clientCode.trim().toLowerCase();

            for (Path child : children) {
                if (child.getFileName().toString().toLowerCase().contains(code)) {
                    System.out.println("YES - Client folder found by code: " + child);
                    return child;
                }
            }
        }

        return null;
    }

    /** True when two names are the same person or entity, whatever the word order or punctuation. */
    public boolean sameName(String first, String second) {
        String a = nameKey(first);
        return !a.isEmpty() && a.equals(nameKey(second));
    }

    /**
     * Reduces a client or folder name to its words in a fixed order, so the same
     * name compares equal however it is written:
     *
     * <pre>
     *   "Kevin Stevens", "STEVENS, KEVIN - C", "Stevens, Kevin (STEVKEVI)"  ->  "kevin stevens"
     * </pre>
     *
     * The bracketed client code and the trailing team marker are dropped first.
     */
    String nameKey(String name) {

        if (name == null) {
            return "";
        }

        String cleaned = name.replaceAll("\\([^()]*\\)", " ")
                .trim()
                .replaceAll("(?i)\\s*-\\s*[A-Z]\\d?$", "");

        String[] words = cleaned.toLowerCase().split("[^a-z0-9]+");

        List<String> kept = new ArrayList<>();

        for (String word : words) {
            if (!word.isEmpty()) {
                kept.add(word);
            }
        }

        String[] sorted = kept.toArray(new String[0]);
        Arrays.sort(sorted);

        return String.join(" ", sorted);
    }

    /**
     * The year folder inside a client folder: "2026" itself, "Year End\2026", or a
     * single folder carrying both, such as "Year End (2026)".
     */
    public Path findYearFolder(Path clientFolder, String year) {

        Path direct = childNamed(clientFolder, year);

        if (direct != null) {
            return direct;
        }

        Path yearEnd = childNamed(clientFolder, "Year End");

        if (yearEnd != null) {

            Path underYearEnd = childNamed(yearEnd, year);

            if (underYearEnd != null) {
                return underYearEnd;
            }
        }

        for (Path child : listDirectories(clientFolder)) {

            String name = compact(child.getFileName().toString());

            if (name.contains("yearend") && name.contains(year)) {
                return child;
            }
        }

        return null;
    }

    /**
     * The workpapers folder inside a year folder. It is spelled "Year End Workpapers",
     * "Year End WorkPapers" and "Year End Working Papers" on the server.
     */
    public Path findWorkpapersFolder(Path yearFolder) {
        return childStartingWith(yearFolder, "yearendwork");
    }

    /** "V1 Deliv", "V2 Deliv", ... - the number is the version of the deliverables. */
    private static final java.util.regex.Pattern DELIV_FOLDER =
            java.util.regex.Pattern.compile("(?i)^\\s*V\\s*(\\d+)\\s*Deliv");

    /**
     * "Reports" inside the latest deliverables folder - V2 Deliv over V1 Deliv - or
     * null if there is no deliverables folder or it has no Reports.
     */
    public Path findReportsFolder(Path workpapersFolder) {

        Path deliverables = null;
        int latestVersion = -1;

        for (Path child : listDirectories(workpapersFolder)) {

            java.util.regex.Matcher version = DELIV_FOLDER.matcher(child.getFileName().toString());

            if (version.find() && Integer.parseInt(version.group(1)) > latestVersion) {
                latestVersion = Integer.parseInt(version.group(1));
                deliverables = child;
            }
        }

        if (deliverables == null) {
            System.out.println("NO - no 'V1 Deliv' (or later) folder in " + workpapersFolder);
            return null;
        }

        System.out.println("Latest deliverables folder: " + deliverables.getFileName());

        Path reports = childStartingWith(deliverables, "report");

        if (reports == null) {
            System.out.println("NO - no 'Reports' folder in " + deliverables);
        }

        return reports;
    }

    /**
     * Finds "2026_ITR_ClientName" in the Reports folder.
     *
     * Punctuation is ignored and so is a leading "2. " put there for ordering. A lone
     * ITR file is taken as it is - the folder already pins down the client - and when
     * there are several the one carrying the client's name wins. If the name does not
     * settle it, nothing is returned rather than a guess.
     */
    public Path findItrFile(Path reportsFolder, String year, String clientName) {
        return findReturnFile(reportsFolder, year, clientName, ItrPaths.ITR_FILE_MARKER);
    }

    /**
     * Finds a tax return in the Reports folder by year and return type: "2026_ITR_Name",
     * "2026 ITR - Name", or for a trust "2026 TRT - Brunton Family Trust".
     *
     * @param markers the return types accepted, e.g. "ITR", "TRT"
     */
    public Path findReturnFile(Path reportsFolder, String year, String clientName, String... markers) {

        List<Path> candidates = new ArrayList<>();
        java.util.Map<Path, Integer> prefixLength = new java.util.HashMap<>();

        try (var files = Files.list(reportsFolder)) {

            for (Path file : (Iterable<Path>) files::iterator) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                for (String marker : markers) {
                    String prefix = compact(year + marker);
                    if (compact(baseName(file)).startsWith(prefix)) {
                        candidates.add(file);
                        prefixLength.put(file, prefix.length());
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + reportsFolder);
            return null;
        }

        String wanted = year + " " + String.join("/", markers);

        if (candidates.isEmpty()) {
            System.out.println("NO - no " + wanted + " file in " + reportsFolder);
            return null;
        }

        if (candidates.size() == 1) {
            System.out.println("YES - " + wanted + " file found: " + candidates.get(0).getFileName());
            return candidates.get(0);
        }

        // "Kevin Stevens" is filed as KevinStevens or StevensKevin, and long names are cut short
        String forwards = compact(clientName);
        String backwards = compact(clientName == null ? "" : clientName.trim().replaceAll("^(.*)\\s+(\\S+)$", "$2 $1"));

        List<Path> named = new ArrayList<>();

        for (Path candidate : candidates) {

            String filedName = compact(baseName(candidate)).substring(prefixLength.get(candidate));

            if (filedName.isEmpty()) {
                continue;
            }

            if (forwards.startsWith(filedName) || filedName.startsWith(forwards)
                    || backwards.startsWith(filedName) || filedName.startsWith(backwards)) {
                named.add(candidate);
            }
        }

        if (named.size() == 1) {
            System.out.println("YES - " + wanted + " file found: " + named.get(0).getFileName());
            return named.get(0);
        }

        System.out.println("Ambiguous " + wanted + " file for '" + clientName
                + "' - leaving it alone rather than guessing between " + candidates);
        return null;
    }

    /**
     * The first file in a folder whose name carries the given words, ignoring case and
     * punctuation - e.g. "financial statement" matches "2026 Financial Statements -
     * Brunton Family Trust.pdf", and "group tax summary" matches "Group tax Summary  -
     * Brunton Group.pdf".
     */
    public Path findFileContaining(Path folder, String words) {

        if (folder == null) {
            return null;
        }

        String wanted = compact(words);

        try (var files = Files.list(folder)) {

            for (Path file : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(file) && compact(file.getFileName().toString()).contains(wanted)) {
                    return file;
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + folder);
        }

        return null;
    }

    /** File name without its extension or a leading "2. " ordering number. */
    private String baseName(Path file) {

        String fileName = file.getFileName().toString();

        int dotIndex = fileName.lastIndexOf('.');

        String withoutExtension = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        return withoutExtension.replaceFirst("^\\d{1,2}\\.\\s*", "");
    }

    /** Direct child directory with this name, ignoring case and punctuation. */
    private Path childNamed(Path directory, String name) {

        String wanted = compact(name);

        for (Path child : listDirectories(directory)) {
            if (compact(child.getFileName().toString()).equals(wanted)) {
                return child;
            }
        }

        return null;
    }

    /** First direct child directory whose name starts this way, ignoring case and punctuation. */
    private Path childStartingWith(Path directory, String compactPrefix) {

        for (Path child : listDirectories(directory)) {
            if (compact(child.getFileName().toString()).startsWith(compactPrefix)) {
                return child;
            }
        }

        return null;
    }

    private List<Path> listDirectories(Path directory) {

        List<Path> directories = new ArrayList<>();

        try (var children = Files.list(directory)) {

            for (Path child : (Iterable<Path>) children::iterator) {
                if (Files.isDirectory(child)) {
                    directories.add(child);
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR - Unable to list " + directory);
        }

        return directories;
    }

    /** Lower-cases and drops anything that is not a letter or digit. */
    private String compact(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
