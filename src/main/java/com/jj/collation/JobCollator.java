package com.jj.collation;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.copy.CollationCopyService;
import FileExplorerCollation.filesearch.document.CoverLetterService;
import FileExplorerCollation.filesearch.search.FolderSearchService;
import FileExplorerCollation.filesearch.search.QuarterFileSearchService;
import FileExplorerCollation.filesearch.util.BasPeriod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Collates one job into its client's folders on the practice share.
 *
 * <p>For every period the job covers it finds the client's folder, then the year,
 * then the BAS/IAS folder, then the month or quarter folder, and fills a
 * "Collation Testing" folder inside it with:
 *
 * <ul>
 *   <li>the processor file already in that folder - any file with "AS" in its name,</li>
 *   <li>the cover letter, carrying that period's figures,</li>
 *   <li>and, when the estimated position is <b>payable</b>, the activity statement
 *       downloaded from the ATO.</li>
 * </ul>
 *
 * <p>A refundable position gets the first two only — there is no ATO statement to
 * add, because the download is skipped for refunds.
 *
 * <p>One ATO statement covers a whole quarter, so for a monthly client the same file
 * is copied into each of that quarter's three months.
 */
public class JobCollator {

	private final FolderSearchService folderSearch = new FolderSearchService(null);
	private final QuarterFileSearchService quarterFileSearch = new QuarterFileSearchService();
	private final CollationCopyService collationCopy = new CollationCopyService();
	private final CoverLetterService coverLetter = new CoverLetterService();

	private final LocalDate runDate = LocalDate.now();

	/**
	 * @param jobNumber      e.g. "J010726" — decides which practice's Clients root to search
	 * @param periods        the months or quarters this job covers, in sheet order
	 * @param atoStatement   the ATO download, or null when the position is refundable
	 */
	public CollationOutcome collate(String jobNumber, String clientName, String clientCode,
			String groupName, String internalTeam, List<CollationTarget> periods, Path atoStatement) {

		CollationOutcome outcome = new CollationOutcome();

		String clientsRoot = jobNumber != null && jobNumber.toUpperCase().startsWith(CollationPaths.JOB_PREFIX_FUT)
				? CollationPaths.FUT_CLIENTS
				: CollationPaths.FABA_CLIENTS;

		String groupTeam = join(groupName, internalTeam);
		String nameWithCode = clientName + " (" + clientCode + ") - " + internalTeam;
		String nameWithTeam = join(clientName, internalTeam);
		String reversedName = reverseName(clientName, internalTeam);

		System.out.println("Collating " + jobNumber + " under " + clientsRoot);
		System.out.println("  group folder: " + groupTeam);

		Path clientFolder = folderSearch.searchClientFolderByNames(
				clientsRoot, groupTeam, internalTeam, nameWithCode, nameWithTeam, reversedName);

		String clientFolderPath = clientFolder == null ? null : clientFolder.toString();

		if (clientFolderPath == null) {
			Path groupFolder = folderSearch.findGroupFolder(clientsRoot, groupTeam);
			clientFolderPath = groupFolder == null ? null
					: folderSearch.searchClientCodeFile(groupFolder, clientCode);
		}

		if (clientFolderPath == null) {
			System.out.println("!! No client folder for " + clientName + " — nothing collated for " + jobNumber);
			outcome.clientFolderFound = false;
			return outcome;
		}

		outcome.clientFolderFound = true;
		System.out.println("  client folder: " + clientFolderPath);

		for (CollationTarget target : periods) {
			collateOne(target, clientFolderPath, clientName, reversedName, atoStatement, outcome);
		}

		return outcome;
	}

	private void collateOne(CollationTarget target, String clientFolderPath, String clientName,
			String reversedName, Path atoStatement, CollationOutcome outcome) {

		BasPeriod period = BasPeriod.parse(target.label, target.financialQuarter);
		if (period == null) {
			System.out.println("  cannot read the period '" + target.label + "'");
			return;
		}

		String yearFolder = folderSearch.searchYearFolder(clientFolderPath, period.getFinancialYear());
		if (yearFolder == null) {
			return;
		}

		String basIasFolder = folderSearch.searchBasIasFolder(yearFolder);
		if (basIasFolder == null) {
			System.out.println("  no BAS folder under " + yearFolder);
			return;
		}

		String periodFolder = period.isQuarterly()
				? folderSearch.searchQuarterFolderByName(basIasFolder, period.getQuarterName())
				: folderSearch.searchMonthFolder(basIasFolder, period.getMonthName(), period.getMonthAbbreviation());

		// Some clients name a quarter after its last month instead: GH Cabinets keeps
		// "June 2026 BAS" where others keep "Q4".
		if (periodFolder == null && period.isQuarterly()) {
			periodFolder = folderSearch.searchMonthFolder(basIasFolder, period.getMonthName(), period.getMonthAbbreviation());
		}

		if (periodFolder == null) {
			return;
		}

		String collationFolder = Paths.get(periodFolder, CollationPaths.COLLATION_FOLDER_NAME).toString();
		try {
			Files.createDirectories(Paths.get(collationFolder));
		} catch (IOException e) {
			System.out.println("  could not create " + collationFolder + ": " + e.getMessage());
			return;
		}

		// The processor file: any file in the folder with "AS" in its name.
		for (Path asFile : quarterFileSearch.findAsFiles(periodFolder)) {
			if (collationCopy.copyQuarterFileToCollation(asFile.toString(), periodFolder) != null) {
				outcome.processorFilesCopied++;
			}
		}

		// The ATO statement, only when the position is payable.
		if (atoStatement != null) {
			copyAtoStatement(atoStatement, collationFolder, outcome);
		}

		String letter = coverLetter.createCoverLetter(collationFolder, clientName,
				target.payableOrRefund, target.amount, period, runDate);
		if (letter != null) {
			outcome.coverLettersWritten++;
		}
	}

	/** The same ATO statement goes into every period of the quarter it covers. */
	private void copyAtoStatement(Path atoStatement, String collationFolder, CollationOutcome outcome) {
		try {
			Path destination = Paths.get(collationFolder).resolve(atoStatement.getFileName());
			Files.copy(atoStatement, destination, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("  ATO statement -> " + destination);
			outcome.atoStatementsCopied++;
		} catch (IOException e) {
			System.out.println("  could not copy the ATO statement: " + e.getMessage());
		}
	}

	private static String join(String value, String internalTeam) {
		return value + " - " + internalTeam;
	}

	/**
	 * "Bucktin Reginald Eric - A" becomes "Eric, Reginald, Bucktin - A": the filed
	 * statement is sometimes named surname first, so both forms are offered.
	 *
	 * <p>Company names are left alone.
	 */
	private static String reverseName(String clientName, String internalTeam) {
		String lower = clientName.toLowerCase();
		if (lower.contains("pty") || lower.contains("ltd") || lower.contains("superannuation")
				|| lower.contains("family trust")) {
			return join(clientName, internalTeam);
		}

		String[] words = clientName.trim().split("\\s+");
		if (words.length < 2) {
			return join(clientName, internalTeam);
		}

		StringBuilder reversed = new StringBuilder(words[words.length - 1]);
		for (int i = words.length - 2; i >= 0; i--) {
			reversed.append(", ").append(words[i]);
		}
		return join(reversed.toString(), internalTeam);
	}

	/** One month or quarter to collate, with the figures for its cover letter. */
	public static class CollationTarget {
		public final String label;
		public final String financialQuarter;
		public final String payableOrRefund;
		public final String amount;

		public CollationTarget(String label, String financialQuarter, String payableOrRefund, String amount) {
			this.label = label;
			this.financialQuarter = financialQuarter;
			this.payableOrRefund = payableOrRefund;
			this.amount = amount;
		}
	}

	/** What a job's collation actually managed, for reporting at the end of a run. */
	public static class CollationOutcome {
		public boolean clientFolderFound;
		public int processorFilesCopied;
		public int atoStatementsCopied;
		public int coverLettersWritten;

		@Override
		public String toString() {
			return "client folder " + (clientFolderFound ? "found" : "NOT FOUND")
					+ ", processor files copied " + processorFilesCopied
					+ ", ATO activity statements copied " + atoStatementsCopied
					+ ", cover letters " + coverLettersWritten;
		}
	}
}
