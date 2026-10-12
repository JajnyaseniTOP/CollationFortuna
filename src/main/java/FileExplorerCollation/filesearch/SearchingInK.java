package FileExplorerCollation.filesearch;

import static FileExplorerCollation.filesearch.util.ExcelUtils.getCellValue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import FileExplorerCollation.filesearch.config.ClientDataColumns;
import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.copy.CollationCopyService;
import FileExplorerCollation.filesearch.document.CoverLetterService;
import FileExplorerCollation.filesearch.excel.ClientDataStatusWriter;
import FileExplorerCollation.filesearch.search.FolderSearchService;
import FileExplorerCollation.filesearch.search.QuarterFileSearchService;
import FileExplorerCollation.filesearch.util.BasPeriod;

/**
 * Drives the collation run.
 *
 * ClientData.xls holds one block of rows per job: the first row carries the
 * client details, and every row of the block - that one included - carries one
 * month of the BAS period in column N. So the client folder is resolved once
 * per job, then every month of the period is collated in turn.
 *
 * The searching and copying itself lives in the service classes; this class
 * only decides the order things happen in and holds the run settings.
 */
public class SearchingInK {

	// ----- run settings ---------------------------------------------------
	String recipient ="Ramandeep Kaur";

	// ----- state discovered during the run --------------------------------
	String clientCodeFolderPath;
	String yearFolderPath;
	String basIasFolderPath;
	String monthFolderPath;
	String copiedFilePath;
	String asFilePath;
	String coverLetterPath;

	/** The date the job is run - goes in the cover letter's Due Date column. */
	private final java.time.LocalDate runDate = java.time.LocalDate.now();

	// ----- collaborators --------------------------------------------------
	private final FolderSearchService folderSearch = new FolderSearchService(null);
	private final QuarterFileSearchService quarterFileSearch = new QuarterFileSearchService();
	private final CollationCopyService collationCopy = new CollationCopyService();
	private final CoverLetterService coverLetter = new CoverLetterService();
	private final ClientDataStatusWriter statusWriter = new ClientDataStatusWriter();

	public void checkClientFolders() {

	    String excelFilePath = CollationPaths.CLIENT_DATA_FILE;

	    Workbook workbook = null;

	    try {

	        // Read the whole workbook into memory, then close the stream so the
	        // same file can be written back at the end of the run.
	        try (FileInputStream fis = new FileInputStream(excelFilePath)) {
	            workbook = WorkbookFactory.create(fis);
	        }

	        Sheet sheet = workbook.getSheetAt(0);

	        statusWriter.ensureHeader(sheet, ClientDataColumns.CLIENT_FOLDER_STATUS,
	                ClientDataColumns.CLIENT_FOLDER_STATUS_HEADER);
	        statusWriter.ensureHeader(sheet, ClientDataColumns.AS_FILE_STATUS,
	                ClientDataColumns.AS_FILE_STATUS_HEADER);

	        // Carried down from the first row of a job block to its month rows
	        String jobName = "";
	        String clientName = "";
	        String reversedClientName = "";
	        String clientFolder = null;

	        for (int i = 1; i <= sheet.getLastRowNum(); i++) {

	            Row row = sheet.getRow(i);

	            if (row == null) {
	                continue;
	            }

	            String rowJobName = getCellValue(row.getCell(ClientDataColumns.JOB_NO));

	            // A job number means a new block: resolve that client's folder once
	            if (!rowJobName.isEmpty()) {

	                jobName = rowJobName;
	                clientName = getCellValue(row.getCell(ClientDataColumns.CLIENT_NAME));

	                // "Glossop, Shane - A" -> "Glossop, Shane": the AS file is
	                // sometimes named surname first, sometimes given name first
	                reversedClientName = stripTeamSuffix(
	                        getCellValue(row.getCell(ClientDataColumns.CLIENT_FOLDER_NAME_M)),
	                        getCellValue(row.getCell(ClientDataColumns.INTERNAL_TEAM)));

	                clientFolder = resolveClientFolder(sheet, i, row, jobName, clientName);
	            }

	            if (jobName.isEmpty()) {
	                // A month row before any job number - nothing to attach it to
	                continue;
	            }

	            collateMonth(sheet, i, row, jobName, clientName, reversedClientName, clientFolder);
	        }

	        statusWriter.save(workbook, excelFilePath);

	    } catch (IOException e) {

	        System.out.println("Error while reading ClientData.xls");
	        e.printStackTrace();

	    } finally {

	        if (workbook != null) {
	            try {
	                workbook.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	    }
	}

	/** Finds the client folder for a job block and records the result on its first row. */
	private String resolveClientFolder(Sheet sheet, int rowIndex, Row row, String jobName, String clientName) {

	    statusWriter.setStatus(sheet, rowIndex, ClientDataColumns.CLIENT_FOLDER_STATUS,
	            ClientDataColumns.STATUS_NOT_FOUND);

	    String clientCode = getCellValue(row.getCell(ClientDataColumns.CLIENT_CODE));
	    String internalTeam = getCellValue(row.getCell(ClientDataColumns.INTERNAL_TEAM));
	    String groupTeamName = getCellValue(row.getCell(ClientDataColumns.GROUP_TEAM_NAME));
	    String lodgementCycle = getCellValue(row.getCell(ClientDataColumns.LODGEMENT_CYCLE));

	    // The three candidate folder names, tried in this order: K, then L, then M
	    String folderNameK = getCellValue(row.getCell(ClientDataColumns.CLIENT_FOLDER_NAME_K));
	    String folderNameL = getCellValue(row.getCell(ClientDataColumns.CLIENT_FOLDER_NAME_L));
	    String folderNameM = getCellValue(row.getCell(ClientDataColumns.CLIENT_FOLDER_NAME_M));

	    // KPF jobs live under FUT, J jobs under FABA
	    String primaryPath;

	    if (jobName.toUpperCase().startsWith(CollationPaths.JOB_PREFIX_FUT)) {
	        primaryPath = CollationPaths.FUT_CLIENTS;
	    } else if (jobName.toUpperCase().startsWith(CollationPaths.JOB_PREFIX_FABA)) {
	        primaryPath = CollationPaths.FABA_CLIENTS;
	    } else {
	        System.out.println("Unknown job prefix '" + jobName + "' - defaulting to FABA");
	        primaryPath = CollationPaths.FABA_CLIENTS;
	    }

	    System.out.println("============================================");
	    System.out.println("Row       : " + rowIndex);
	    System.out.println("Job Name  : " + jobName);
	    System.out.println("Client    : " + clientName);
	    System.out.println("Lodgement : " + lodgementCycle);
	    System.out.println("Searching : " + primaryPath);

	    if (folderNameK.isEmpty() && folderNameL.isEmpty() && folderNameM.isEmpty()) {
	        System.out.println("No client folder name in columns K, L or M. Skipping this job...");
	        return null;
	    }

	    Path clientFolderPath = folderSearch.searchClientFolderByNames(
	            primaryPath, groupTeamName, internalTeam, folderNameK, folderNameL, folderNameM);

	    if (clientFolderPath != null) {

	        clientCodeFolderPath = clientFolderPath.toString();

	    } else {

	        // None of the K/L/M spellings matched, so fall back to hunting
	        // for the client code inside the group folder.
	        System.out.println("Falling back to a client code search...");

	        Path groupFolder = folderSearch.findGroupFolder(primaryPath, groupTeamName);

	        clientCodeFolderPath = groupFolder == null
	                ? null
	                : folderSearch.searchClientCodeFile(groupFolder, clientCode);
	    }

	    if (clientCodeFolderPath == null) {
	        System.out.println("NO - Client folder is NOT present. Skipping this job...");
	        return null;
	    }

	    System.out.println("YES - Client folder is present");
	    System.out.println("Client Folder Path: " + clientCodeFolderPath);

	    statusWriter.writeStatus(sheet, rowIndex, ClientDataColumns.CLIENT_FOLDER_STATUS,
	            ClientDataColumns.STATUS_FOUND, ClientDataColumns.CLIENT_FOLDER_STATUS_HEADER);

	    return clientCodeFolderPath;
	}

	/** Removes a trailing " - A" team suffix from a client name. */
	private String stripTeamSuffix(String name, String internalTeam) {

	    if (name == null || internalTeam == null || internalTeam.trim().isEmpty()) {
	        return name == null ? "" : name.trim();
	    }

	    String trimmedName = name.trim();
	    String suffix = "-" + internalTeam.trim();

	    if (trimmedName.replace(" ", "").toLowerCase().endsWith(suffix.toLowerCase())) {

	        int dashIndex = trimmedName.lastIndexOf('-');

	        if (dashIndex > 0) {
	            return trimmedName.substring(0, dashIndex).trim();
	        }
	    }

	    return trimmedName;
	}

	/** Collates the one month or quarter this row covers. */
	private void collateMonth(Sheet sheet, int rowIndex, Row row, String jobName, String clientName,
	        String reversedClientName, String clientFolder) {

	    String monthValue = getCellValue(row.getCell(ClientDataColumns.MONTH));

	    if (monthValue.isEmpty()) {
	        return;
	    }

	    statusWriter.setStatus(sheet, rowIndex, ClientDataColumns.AS_FILE_STATUS,
	            ClientDataColumns.STATUS_NOT_FOUND);

	    if (clientFolder == null) {
	        return;
	    }

	    String financialQuarter = getCellValue(row.getCell(ClientDataColumns.FINANCIAL_QUARTER));

	    BasPeriod period = BasPeriod.parse(monthValue, financialQuarter);

	    if (period == null) {
	        System.out.println("Row " + rowIndex + " : cannot read the month '" + monthValue + "'");
	        return;
	    }

	    System.out.println("--------------------------------------------");
	    System.out.println("Row " + rowIndex + " : " + jobName + " - " + period);

	    yearFolderPath = folderSearch.searchYearFolder(clientFolder, period.getFinancialYear());

	    if (yearFolderPath == null) {
	        return;
	    }

	    basIasFolderPath = folderSearch.searchBasIasFolder(yearFolderPath);

	    if (basIasFolderPath == null) {
	        System.out.println("BAS folder not found");
	        return;
	    }

	    // Monthly clients have month folders; quarterly clients have Q1..Q4
	    monthFolderPath = period.isQuarterly()
	            ? folderSearch.searchQuarterFolderByName(basIasFolderPath, period.getQuarterName())
	            : folderSearch.searchMonthFolder(
	                    basIasFolderPath, period.getMonthName(), period.getMonthAbbreviation());

	    if (monthFolderPath == null) {
	        return;
	    }

	    // The name is written both ways round on the server, so offer both
	    asFilePath = quarterFileSearch.searchQuarterFile(
	            monthFolderPath, period.getCalendarYear(), period.getMonthName(),
	            clientName, reversedClientName);

	    if (asFilePath == null) {
	        System.out.println("AS file not found for " + period);
	        return;
	    }

	    statusWriter.writeStatus(sheet, rowIndex, ClientDataColumns.AS_FILE_STATUS,
	            ClientDataColumns.STATUS_FOUND, ClientDataColumns.AS_FILE_STATUS_HEADER);

	    copiedFilePath = collationCopy.copyQuarterFileToCollation(asFilePath, monthFolderPath);

	    if (copiedFilePath == null) {
	        return;
	    }

	    String collationFolderPath = Paths.get(copiedFilePath).getParent().toString();

	    // Each period gets its own letter, carrying that period's amount
	    String payableOrRefund = getCellValue(row.getCell(ClientDataColumns.PAYABLE_OR_REFUND));
	    String amount = getCellValue(row.getCell(ClientDataColumns.AMOUNT));

	    coverLetterPath = coverLetter.createCoverLetter(
	            collationFolderPath, clientName, payableOrRefund, amount, period, runDate);
	}
}
