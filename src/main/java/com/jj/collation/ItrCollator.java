package com.jj.collation;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.config.ItrPaths;
import FileExplorerCollation.filesearch.document.EngagementLetterService;
import FileExplorerCollation.filesearch.document.FfrService;
import FileExplorerCollation.filesearch.document.ItrCoverLetterService;
import FileExplorerCollation.filesearch.document.ItrFinalPdfService;
import FileExplorerCollation.filesearch.document.ItrPositionReader;
import FileExplorerCollation.filesearch.document.ItrPositionReader.ItrPosition;
import FileExplorerCollation.filesearch.search.ItrFolderSearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Collates one income tax return job into its client's folder on the practice share.
 *
 * <p>It works in two halves, because the ATO leg sits between them: the estimated
 * position is read off the ITR document, and only a payable position sends the run
 * to the ATO for a payment slip.
 *
 * <ol>
 *   <li>{@link #locate} finds the client folder and "2026_ITR_ClientName" under
 *       year -> Year End Workpapers -> latest V Deliv -> Reports, and reads the
 *       position and the name the return is declared in.</li>
 *   <li>{@link #collate} fills a "Collation Testing" folder inside Year End
 *       Workpapers with the ITR, the cover letter, the Notes for Persons Signing Tax
 *       Returns and - when payable - the ATO payment slip, then joins the letter,
 *       the return and the notes into the one PDF that is sent, e.g.
 *       "Kevin Stevens - 2026 Tax return (C).pdf".</li>
 * </ol>
 *
 * <p>The BAS collation is {@link JobCollator}; nothing here is shared with it beyond
 * the Clients roots.
 */
public class ItrCollator {

	private final ItrFolderSearch folderSearch = new ItrFolderSearch();
	private final ItrPositionReader positionReader = new ItrPositionReader();
	private final ItrCoverLetterService coverLetter = new ItrCoverLetterService();
	private final ItrFinalPdfService finalPdf = new ItrFinalPdfService();
	private final EngagementLetterService engagementLetterService = new EngagementLetterService();
	private final FfrService ffrService = new FfrService();

	/**
	 * @param jobNumber e.g. "KPF019886" — decides which practice's Clients root to search
	 * @param groupName the client's group in XPM, or blank / "No GroupName" when it has none
	 * @return what was found, or null when there is no ITR document to collate
	 */
	public ItrPack locate(String jobNumber, String clientName, String clientCode,
			String groupName, String internalTeam) {

		String clientsRoot = jobNumber != null && jobNumber.toUpperCase().startsWith(CollationPaths.JOB_PREFIX_FUT)
				? CollationPaths.FUT_CLIENTS
				: CollationPaths.FABA_CLIENTS;

		return locateUnder(clientsRoot, clientName, clientCode, groupName, internalTeam);
	}

	/** {@link #locate} against a given Clients root. */
	public ItrPack locateUnder(String clientsRoot, String clientName, String clientCode,
			String groupName, String internalTeam) {

		String year = ItrPaths.ITR_YEAR;

		System.out.println("Locating the " + year + " ITR for " + clientName + " under " + clientsRoot);

		// Per the scope: a client with a group is searched by group, one without by name or code.
		String groupFolderName = isBlank(groupName) ? null
				: isBlank(internalTeam) ? groupName.trim() : groupName.trim() + " - " + internalTeam.trim();

		Path clientFolder = folderSearch.findClientFolder(clientsRoot, groupFolderName, clientName, clientCode);
		if (clientFolder == null) {
			System.out.println("!! No client folder for " + clientName + " — nothing to collate.");
			return null;
		}
		System.out.println("  client folder: " + clientFolder);

		Path yearFolder = folderSearch.findYearFolder(clientFolder, year);
		if (yearFolder == null) {
			System.out.println("!! No " + year + " folder in " + clientFolder);
			return null;
		}

		Path workpapers = folderSearch.findWorkpapersFolder(yearFolder);
		if (workpapers == null) {
			System.out.println("!! No Year End Workpapers folder in " + yearFolder);
			return null;
		}

		Path reports = folderSearch.findReportsFolder(workpapers);
		if (reports == null) {
			return null;
		}

		Path itrFile = folderSearch.findItrFile(reports, year, clientName);
		if (itrFile == null) {
			return null;
		}

		ItrPosition position = positionReader.read(itrFile);
		if (position == null) {
			System.out.println("!! The estimated position could not be read from " + itrFile.getFileName()
					+ " — the cover letter will be written without it. Check this job.");
		}

		// The letter is addressed to the name the return is declared in, by first name:
		// "I, Kevin Stevens, confirm that:" -> "Kevin". The XPM name is the fallback.
		String declarant = positionReader.readDeclarantName(itrFile);
		String recipient = engagementLetterService.firstName(declarant != null ? declarant : clientName);
		if (declarant == null) {
			System.out.println("!! No declaration name in " + itrFile.getFileName()
					+ " — addressing the letter to '" + recipient + "' from the XPM name. Check this job.");
		}

		ItrPack pack = new ItrPack(clientName, internalTeam, workpapers, itrFile, position, recipient);
		pack.bankAccounts.addAll(positionReader.readBankAccounts(itrFile));
		String financialYear = positionReader.readFinancialYear(itrFile);
		pack.financialYear = financialYear != null ? financialYear : year;
		System.out.println("  bank accounts on the return: " + pack.bankAccounts.size()
				+ (pack.isFfr() ? "  (one is Fortuna's, refund paid via the trust account: FFR)" : ""));
		return pack;
	}

	/**
	 * @param paymentSlip the ATO payment slip, or null when the position is a refund
	 *                    (or the slip could not be downloaded)
	 */
	public ItrOutcome collate(ItrPack pack, Path paymentSlip, String dueDate) {
		return collate(pack, paymentSlip, dueDate, "", "", null);
	}

	/**
	 * @param dueDate               the tax return due date read from the ATO, e.g.
	 *                              "02/11/2026"; blank when it could not be read
	 * @param finalInvoiceNumber    the job's final invoice, from its Financial tab, for the FFR
	 * @param finalInvoiceAmount    its amount
	 * @param engagementLetterNames the people or entities to fill the engagement
	 *                              letter in for; null when no letter is needed
	 */
	public ItrOutcome collate(ItrPack pack, Path paymentSlip, String dueDate, String finalInvoiceNumber,
			String finalInvoiceAmount, List<String> engagementLetterNames) {

		ItrOutcome outcome = new ItrOutcome();

		Path collationFolder = pack.workpapersFolder.resolve(CollationPaths.COLLATION_FOLDER_NAME);
		try {
			Files.createDirectories(collationFolder);
		} catch (IOException e) {
			System.out.println("  could not create " + collationFolder + ": " + e.getMessage());
			return outcome;
		}
		System.out.println("Collation Testing folder: " + collationFolder);

		Path itrCopy = copyInto(pack.itrFile, collationFolder, pack.itrFile.getFileName().toString());
		outcome.itrCopied = itrCopy != null;

		boolean payable = pack.isPayable();

		if (payable && paymentSlip != null) {
			outcome.paymentSlipCopied =
					copyInto(paymentSlip, collationFolder, paymentSlip.getFileName().toString()) != null;
		} else if (payable) {
			System.out.println("!! Position is payable but no ATO payment slip was saved — collating without it.");
		}

		if (engagementLetterNames != null) {
			Path engagementLetter = copyInto(
					Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.ENGAGEMENT_LETTER_FILE),
					collationFolder, ItrPaths.ENGAGEMENT_LETTER_FILE);
			outcome.engagementLetterCopied = engagementLetter != null
					&& engagementLetterService.fill(engagementLetter, engagementLetterNames, LocalDate.now());
		}

		// Scope 3.6: a refund paid into Fortuna's trust account gets the Fee From Refund authority.
		// Made before the letter, which lists it as an enclosure only once it exists.
		if (pack.isFfr()) {
			Path ffr = ffrService.createFfr(collationFolder, pack.financialYear, pack.recipientName,
					pack.position.getAmount(), finalInvoiceNumber, finalInvoiceAmount, pack.clientAccount());
			outcome.ffrWritten = ffr != null;
			outcome.expectedBalance = ffrService.expectedBalance(pack.position.getAmount(), finalInvoiceAmount);
		}

		Path letter = coverLetter.createCoverLetter(collationFolder, pack.recipientName, pack.clientName,
				pack.position == null ? "" : pack.position.describe(), dueDate == null ? "" : dueDate,
				outcome.ffrWritten);
		outcome.coverLetterWritten = letter != null;

		Path notes = copyInto(Paths.get(ItrPaths.ITR_TEMPLATE_FOLDER, ItrPaths.NOTES_FOR_SIGNING_FILE),
				collationFolder, ItrPaths.NOTES_FOR_SIGNING_FILE);
		outcome.notesCopied = notes != null;

		// The one file that goes to the client: letter, return, notes. The payment slip
		// stays beside it in Collation Testing and is not part of it.
		if (letter != null && itrCopy != null && notes != null) {
			Path letterPdf = finalPdf.convertToPdf(letter);
			if (letterPdf != null) {
				Path merged = collationFolder.resolve(
						finalPdf.finalFileName(pack.clientName, ItrPaths.ITR_YEAR, pack.internalTeam));
				outcome.finalPdfBuilt = finalPdf.merge(List.of(letterPdf, itrCopy, notes), merged) != null;
			}
		} else {
			System.out.println("!! The pack is incomplete — no final PDF was built.");
		}

		return outcome;
	}

	private Path copyInto(Path source, Path folder, String fileName) {
		if (!Files.isRegularFile(source)) {
			System.out.println("  not found: " + source);
			return null;
		}
		try {
			Path destination = folder.resolve(fileName);
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("  copied -> " + destination);
			return destination;
		} catch (IOException e) {
			System.out.println("  could not copy " + source.getFileName() + ": " + e.getMessage());
			return null;
		}
	}

	/** XPM fields that could not be read come through as "No Value", "No GroupName" and the like. */
	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty() || value.trim().toLowerCase().startsWith("no ");
	}

	/** What {@link #locate} found for one job. */
	public static class ItrPack {
		public final String clientName;
		public final String internalTeam;
		public final Path workpapersFolder;
		public final Path itrFile;

		/** Null when the ITR document did not give up its estimated position. */
		public final ItrPosition position;

		/** First name the cover letter is addressed to, e.g. "Kevin". */
		public final String recipientName;

		/** Every bank account printed in the return, in order. */
		public final List<ItrPositionReader.BankAccount> bankAccounts = new java.util.ArrayList<>();

		/** The return's financial year, e.g. "2026". */
		public String financialYear;

		/**
		 * Scope 3.6: an FFR is prepared only for a refund paid into Fortuna's account,
		 * i.e. a Fortuna account under Account Name in the return.
		 */
		public boolean isFfr() {
			return position != null && position.isRefund()
					&& bankAccounts.stream().anyMatch(ItrPositionReader.BankAccount::isFortuna);
		}

		/** The client's own account: the first one on the return that is not Fortuna's. */
		public ItrPositionReader.BankAccount clientAccount() {
			return bankAccounts.stream().filter(a -> !a.isFortuna()).findFirst().orElse(null);
		}

		ItrPack(String clientName, String internalTeam, Path workpapersFolder, Path itrFile, ItrPosition position,
				String recipientName) {
			this.recipientName = recipientName;
			this.clientName = clientName;
			this.internalTeam = internalTeam;
			this.workpapersFolder = workpapersFolder;
			this.itrFile = itrFile;
			this.position = position;
		}

		/** True when the ATO payment slip is needed. */
		public boolean isPayable() {
			return position != null && position.isPayable();
		}
	}

	/** What a job's collation actually managed, for reporting at the end of a run. */
	public static class ItrOutcome {
		public boolean itrCopied;
		public boolean coverLetterWritten;
		public boolean notesCopied;
		public boolean paymentSlipCopied;
		public boolean finalPdfBuilt;
		public boolean engagementLetterCopied;
		public boolean ffrWritten;

		/** Refund minus final invoice, as on the FFR; blank when there is no FFR or no invoice. */
		public String expectedBalance = "";

		@Override
		public String toString() {
			return "ITR " + (itrCopied ? "copied" : "NOT copied")
					+ ", cover letter " + (coverLetterWritten ? "written" : "NOT written")
					+ ", signing notes " + (notesCopied ? "copied" : "NOT copied")
					+ ", ATO payment slip " + (paymentSlipCopied ? "copied" : "not included")
					+ ", engagement letter " + (engagementLetterCopied ? "copied" : "not included")
					+ ", FFR " + (ffrWritten ? "written" : "not needed")
					+ ", final PDF " + (finalPdfBuilt ? "built" : "NOT built");
		}
	}
}
