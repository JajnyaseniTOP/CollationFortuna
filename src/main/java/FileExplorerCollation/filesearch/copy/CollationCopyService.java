package FileExplorerCollation.filesearch.copy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import FileExplorerCollation.filesearch.config.CollationPaths;

/**
 * Copies the files that make up a collation pack into the "Collation Testing" folder.
 */
public class CollationCopyService {

    public String copyBasCoverLetterToCollation(String collationFolderPath) {

        String sourceFolder = CollationPaths.BAS_TEMPLATE_FOLDER;
        String fileName = CollationPaths.BAS_COVER_LETTER_FILE;

        Path sourceFile = Paths.get(sourceFolder, fileName);
        Path destinationFolder = Paths.get(collationFolderPath);

        try {

            if (!Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
                System.out.println("BAS Cover Letter file not found: " + sourceFile);
                return null;
            }

            Files.createDirectories(destinationFolder);

            Path destinationFile = destinationFolder.resolve(fileName);

            Files.copy(
                    sourceFile,
                    destinationFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("YES - BAS Cover Letter copied successfully");
            System.out.println("Copied File Path: " + destinationFile);

            return destinationFile.toString();

        } catch (IOException e) {

            System.out.println("ERROR - Unable to copy BAS Cover Letter");
            e.printStackTrace();

            return null;
        }
    }

    public String copyQuarterFileToCollation(String quarterFilePath, String quarterFolderPath) {

        try {
            Path sourceFile = Paths.get(quarterFilePath);
            Path quarterPath = Paths.get(quarterFolderPath);

            // Create "Collation Testing" folder
            Path collationFolderPath = quarterPath.resolve(CollationPaths.COLLATION_FOLDER_NAME);

            Files.createDirectories(collationFolderPath);

            System.out.println("Collation Testing folder: " + collationFolderPath);

            // Keep the original file name
            String fileName = sourceFile.getFileName().toString();

            // Destination path
            Path destinationFile = collationFolderPath.resolve(fileName);

            // Copy and replace if already exists
            Files.copy(sourceFile,destinationFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("YES - File copied successfully");
            System.out.println("Copied File: " + destinationFile);

            return destinationFile.toString();

        } catch (IOException e) {

            System.out.println("ERROR - Unable to copy quarter file");
            e.printStackTrace();

            return null;
        }
    }
}
