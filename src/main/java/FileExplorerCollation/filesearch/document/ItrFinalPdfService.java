package FileExplorerCollation.filesearch.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

/**
 * Builds the one PDF that is sent to the client: the cover letter, the tax return
 * and the Notes for Persons Signing Tax Returns, in that order, e.g.
 * "Kevin Stevens - 2026 Tax return (C).pdf".
 *
 * The cover letter is a Word document, so it is first saved as a PDF by Word itself -
 * the letterhead and footer only come out right that way. Word has to be installed
 * on the machine running the collation.
 */
public class ItrFinalPdfService {

    /** Word can be slow to start; anything past this means it is stuck on a dialog. */
    private static final int WORD_TIMEOUT_SECONDS = 120;

    /** Opens the letter read-only and saves it as PDF (format 17). Paths come in by environment. */
    private static final String WORD_TO_PDF_SCRIPT =
            "$ErrorActionPreference='Stop';"
            + "$word=New-Object -ComObject Word.Application;"
            + "$word.Visible=$false;$word.DisplayAlerts=0;"
            + "try{$doc=$word.Documents.Open($env:ITR_DOCX,$false,$true);"
            + "$doc.SaveAs2($env:ITR_PDF,17);$doc.Close($false)}"
            + "finally{$word.Quit()}";

    /**
     * Saves a Word document as a PDF of the same name beside it.
     *
     * The conversion runs on a copy in the temp folder: Word refuses paths past
     * about 255 characters, and client folders on the share get close to that.
     *
     * @return the PDF, or null if Word could not produce it
     */
    public Path convertToPdf(Path wordDocument) {

        String fileName = wordDocument.getFileName().toString();
        String pdfName = fileName.replaceFirst("(?i)\\.docx?$", "") + ".pdf";

        Path pdfFile = wordDocument.resolveSibling(pdfName);
        Path workFolder = null;

        try {

            workFolder = Files.createTempDirectory("itr-letter-");

            Path workDocument = workFolder.resolve("letter.docx");
            Path workPdf = workFolder.resolve("letter.pdf");

            Files.copy(wordDocument, workDocument);

            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", WORD_TO_PDF_SCRIPT);
            builder.environment().put("ITR_DOCX", workDocument.toString());
            builder.environment().put("ITR_PDF", workPdf.toString());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes()).trim();

            if (!process.waitFor(WORD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                System.out.println("ERROR - Word did not finish saving the cover letter as PDF");
                return null;
            }

            if (process.exitValue() != 0 || !Files.isRegularFile(workPdf)) {
                System.out.println("ERROR - Word could not save the cover letter as PDF: " + output);
                return null;
            }

            Files.copy(workPdf, pdfFile, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("YES - Cover Letter saved as PDF: " + pdfFile);

            return pdfFile;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to save the cover letter as PDF: " + e.getMessage());
            return null;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return null;

        } finally {
            deleteQuietly(workFolder);
        }
    }

    /**
     * Joins the PDFs, in the order given, into one file.
     *
     * @return the merged file, or null if it could not be written
     */
    public Path merge(List<Path> pdfFiles, Path mergedFile) {

        try {

            PDFMergerUtility merger = new PDFMergerUtility();

            for (Path pdfFile : pdfFiles) {
                merger.addSource(pdfFile.toFile());
            }

            merger.setDestinationFileName(mergedFile.toString());
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

            System.out.println("YES - Final PDF written: " + mergedFile);

            return mergedFile;

        } catch (IOException e) {

            System.out.println("ERROR - Unable to write the final PDF: " + e.getMessage());
            return null;
        }
    }

    /**
     * "Kevin Stevens - 2026 Tax return (C).pdf". The team is left off when it is not known.
     */
    public String finalFileName(String clientName, String year, String internalTeam) {

        String safeClientName = clientName.trim().replaceAll("[\\\\/:*?\"<>|]", "-");

        String team = internalTeam == null || internalTeam.trim().isEmpty()
                || internalTeam.trim().toLowerCase().startsWith("no ")
                ? ""
                : " (" + internalTeam.trim() + ")";

        return safeClientName + " - " + year + " Tax return" + team + ".pdf";
    }

    private void deleteQuietly(Path folder) {

        if (folder == null) {
            return;
        }

        try (var files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            // Left for the temp folder's own clean-up
        }

        try {
            Files.deleteIfExists(folder);
        } catch (IOException e) {
            // Left for the temp folder's own clean-up
        }
    }
}
