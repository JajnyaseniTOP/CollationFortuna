package com.jj.collation;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.config.ItrPaths;
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
 *       year -> Year End Workpapers -> V1 Deliv -> Reports, and reads the position.</li>
 *   <li>{@link #collate} fills a "Collation Testing" folder inside Year End
 *       Workpapers with the ITR, the cover letter, the Notes for Persons Signing Tax
 *       Returns and - when payable - the ATO payment slip, then joins the letter,
 *       the return and the notes into the one PDF that is sent, e.g.
 *       "Kevin Stevens - 2026 Tax return (C).pdf".</li>
 * </ol>
 *
 * <p>The BAS collation is {@link JobCollator}; nothing here is shared with it beyond
 * the Clients roots and the cover letter template.
 */
public class ItrCollator {

	private final ItrFolderSearch folderSearch = new ItrFolderSearch();
	private final ItrPositionReader positionReader = new ItrPositionReader();
	private final ItrCoverLetterService coverLetter = new ItrCoverLetterService();
	private final ItrFinalPdfService finalPdf = new ItrFinalPdfService();

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

		return new ItrPack(clientName, internalTeam, workpapers, itrFile, position);
	}

	/**
	 * @param paymentSlip the ATO payment slip, or null when the position is a refund
	 *                    (or the slip could not be downloaded)
	 */
	public ItrOutcome collate(ItrPack pack, Path paymentSlip) {

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

		Path letter = coverLetter.createCoverLetter(collationFolder, pack.clientName,
				pack.position == null ? "" : pack.position.describe(), payable);
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

		ItrPack(String clientName, String internalTeam, Path workpapersFolder, Path itrFile, ItrPosition position) {
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

		@Override
		public String toString() {
			return "ITR " + (itrCopied ? "copied" : "NOT copied")
					+ ", cover letter " + (coverLetterWritten ? "written" : "NOT written")
					+ ", signing notes " + (notesCopied ? "copied" : "NOT copied")
					+ ", ATO payment slip " + (paymentSlipCopied ? "copied" : "not included")
					+ ", final PDF " + (finalPdfBuilt ? "built" : "NOT built");
		}
	}
}
