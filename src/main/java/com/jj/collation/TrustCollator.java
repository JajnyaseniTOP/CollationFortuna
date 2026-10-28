package com.jj.collation;

import FileExplorerCollation.filesearch.config.CollationPaths;
import FileExplorerCollation.filesearch.config.ItrPaths;
import FileExplorerCollation.filesearch.document.EngagementLetterService;
import FileExplorerCollation.filesearch.document.FfrService;
import FileExplorerCollation.filesearch.document.FinancialStatementReader;
import FileExplorerCollation.filesearch.document.ItrFinalPdfService;
import FileExplorerCollation.filesearch.document.ItrPositionReader;
import FileExplorerCollation.filesearch.document.ItrPositionReader.ItrPosition;
import FileExplorerCollation.filesearch.document.TrustCoverLetterService;
import FileExplorerCollation.filesearch.document.TrustCoverLetterService.EntityLine;
import FileExplorerCollation.filesearch.search.ItrFolderSearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Collates a Trust ITR job (scope 4): the family trust and every entity listed in the
 * job's "Entities Included", each with its own folder in the client group.
 *
 * <ol>
 *   <li>{@link #locate} finds each entity's folder and its 2026 return in the latest
 *       V Deliv\Reports - "2026 TRT - Brunton Family Trust", "2026_ITR_BruntonJohn" -
 *       plus the trust's Financial Statements and the Group Tax Summary, and reads the
 *       directors from the financial statements.</li>
 *   <li>The ATO due date for every non-trust entity is filled in by the step that
 *       follows ({@link TrustEntity#dueDate}).</li>
 *   <li>{@link #collate} fills the trust's Collation Testing folder and builds the
 *       single final PDF.</li>
 * </ol>
 *
 * <pre>
 *   FUT\Clients\Brunton Group (John) - A\Brunton Family Trust (BRUNFTRU)\2026\Year End Workpapers\Collation Testing
 * </pre>
 */
public class TrustCollator {

	private final ItrFolderSearch folderSearch = new ItrFolderSearch();
	private final ItrPositionReader positionReader = new ItrPositionReader();
	private final FinancialStatementReader statementReader = new FinancialStatementReader();
	private final EngagementLetterService engagementLetters = new EngagementLetterService();
	private final TrustCoverLetterService coverLetter = new TrustCoverLetterService();
	private final FfrService ffrService = new FfrService();
	private final ItrFinalPdfService finalPdf = new ItrFinalPdfService();

	/** What kind of taxpayer an entity is, from its name. */
	public enum Kind { TRUST, COMPANY, INDIVIDUAL }

	public static Kind kindOf(String name) {
		String lower = name == null ? "" : name.toLowerCase();
		if (lower.matches(".*\\btrust\\b.*")) {
			return Kind.TRUST;
		}
		if (lower.matches(".*\\b(pty|ltd|limited|company|incorporated|inc)\\b.*")) {
			return Kind.COMPANY;
		}
		return Kind.INDIVIDUAL;
	}

	/**
	 * Splits XPM's "Entities Included" into names. Entries are taken one per line (or
	 * separated by ";"); a single line is split at commas only when every part is a
	 * full name, so "Brunton, John" stays one person while "Brunton Family Trust, John
	 * Brunton, Jessica Brunton" becomes three.
	 */
	public static List<String> parseEntities(String raw) {
		List<String> entities = new ArrayList<>();
		if (raw == null) {
			return entities;
		}
		for (String part : raw.split("\\r?\\n|;|\\|")) {
			String entry = part.replaceAll("^[\\s•\\-*\\d.)]+", "").trim();
			if (entry.isEmpty() || entry.equals("-")) {
				continue;
			}
			String[] byComma = entry.split("\\s*,\\s*");
			boolean allFullNames = byComma.length > 1;
			for (String piece : byComma) {
				if (piece.trim().split("\\s+").length < 2) {
					allFullNames = false;
				}
			}
			if (allFullNames) {
				for (String piece : byComma) {
					entities.add(piece.trim());
				}
			} else {
				entities.add(entry);
			}
		}
		return entities;
	}

	/**
	 * @param entitiesIncluded the job's Entities Included, as read from XPM
	 * @return the job, or null when the trust's own folder cannot be found
	 */
	public TrustPack locate(String jobNumber, String clientName, String clientCode, String groupName,
			String internalTeam, String entitiesIncluded) {

		String clientsRoot = jobNumber != null && jobNumber.toUpperCase().startsWith(CollationPaths.JOB_PREFIX_FUT)
				? CollationPaths.FUT_CLIENTS
				: CollationPaths.FABA_CLIENTS;

		return locateUnder(clientsRoot, clientName, clientCode, groupName, internalTeam, entitiesIncluded);
	}

	public TrustPack locateUnder(String clientsRoot, String clientName, String clientCode, String groupName,
			String internalTeam, String entitiesIncluded) {

		String year = ItrPaths.ITR_YEAR;
		TrustPack pack = new TrustPack(clientName, internalTeam);

		List<String> names = parseEntities(entitiesIncluded);
		if (names.isEmpty()) {
			System.out.println("!! No Entities Included on the job — collating the trust '" + clientName + "' alone.");
			names.add(clientName);
		}

		// The family trust first, then the others in the order XPM lists them
		List<String> ordered = new ArrayList<>();
		for (String name : names) {
			if (kindOf(name) == Kind.TRUST) {
				ordered.add(name);
			}
		}
		for (String name : names) {
			if (kindOf(name) != Kind.TRUST) {
				ordered.add(name);
			}
		}

		String groupFolderName = isBlank(groupName) ? null
				: isBlank(internalTeam) ? groupName.trim() : groupName.trim() + " - " + internalTeam.trim();

		System.out.println("Locating the " + year + " Trust pack for " + clientName + " under " + clientsRoot
				+ " — entities " + ordered);

		for (String name : ordered) {
			TrustEntity entity = new TrustEntity(name, kindOf(name));
			pack.entities.add(entity);

			entity.folder = folderSearch.findClientFolder(clientsRoot, groupFolderName, name, null);
			if (entity.folder == null) {
				System.out.println("!! No folder for entity '" + name + "'.");
				continue;
			}
			Path yearFolder = folderSearch.findYearFolder(entity.folder, year);
			Path workpapers = yearFolder == null ? null : folderSearch.findWorkpapersFolder(yearFolder);
			entity.workpapers = workpapers;
			entity.reports = workpapers == null ? null : folderSearch.findReportsFolder(workpapers);
			if (entity.reports == null) {
				System.out.println("!! No " + year + " Year End Workpapers \\ V Deliv \\ Reports for '" + name + "'.");
				continue;
			}

			String[] markers = entity.kind == Kind.TRUST ? new String[] {"TRT", "ITR"}
					: entity.kind == Kind.COMPANY ? new String[] {"CTR", "ITR"}
					: new String[] {"ITR"};
			entity.returnFile = folderSearch.findReturnFile(entity.reports, year, name, markers);

			if (entity.returnFile != null && entity.kind != Kind.TRUST) {
				entity.position = positionReader.read(entity.returnFile);
				entity.bankAccounts.addAll(positionReader.readBankAccounts(entity.returnFile));
				String fy = positionReader.readFinancialYear(entity.returnFile);
				entity.financialYear = fy != null ? fy : year;
			}
		}

		// The trust's own folder holds the financial statements and gets the Collation Testing folder
		TrustEntity trust = pack.entities.stream().filter(e -> e.kind == Kind.TRUST && e.workpapers != null)
				.findFirst().orElse(null);
		if (trust == null) {
			System.out.println("!! The trust's own Year End Workpapers folder was not found — nothing to collate.");
			return null;
		}
		pack.mainEntity = trust;

		pack.financialStatement = folderSearch.findFileContaining(trust.reports, "financial statement");
		System.out.println(pack.financialStatement == null ? "No financial statement in " + trust.reports
				: "YES - Financial statement: " + pack.financialStatement.getFileName());

		pack.groupTaxSummary = folderSearch.findFileContaining(trust.reports, "group tax summary");
		for (TrustEntity entity : pack.entities) {
			if (pack.groupTaxSummary == null && entity.reports != null) {
				pack.groupTaxSummary = folderSearch.findFileContaining(entity.reports, "group tax summary");
			}
		}
		System.out.println(pack.groupTaxSummary == null ? "!! No Group Tax Summary found."
				: "YES - Group Tax Summary: " + pack.groupTaxSummary.getFileName());

		if (pack.financialStatement != null) {
			pack.directors.addAll(statementReader.readDirectors(pack.financialStatement));
		}

		// Addressed to the individuals only, plus any director who is not one of them
		List<String> people = new ArrayList<>();
		for (TrustEntity entity : pack.entities) {
			if (entity.kind == Kind.INDIVIDUAL) {
				people.add(entity.name);
			}
		}
		for (String director : pack.directors) {
			boolean known = people.stream().anyMatch(p -> folderSearch.sameName(p, director));
			if (!known) {
				people.add(director);
			}
		}
		for (String person : people) {
			pack.recipients.add(engagementLetters.firstName(person));
		}
		System.out.println("Cover letter addressed to: " + pack.recipients);

		return pack;
	}

	/**
	 * Builds the pack in the trust's Collation Testing folder.
	 *
	 * @param coverLetterNotes  the job's Cover Letter Notes
	 * @param ignitionJob       true when the job came through Ignition (Client order no. PROP-...),
	 *                          so no engagement letter is needed
	 */
	public TrustOutcome collate(TrustPack pack, String coverLetterNotes, String finalInvoiceNumber,
			String finalInvoiceAmount, boolean ignitionJob) {

		TrustOutcome outcome = new TrustOutcome();

		Path collationFolder = pack.mainEntity.workpapers.resolve(CollationPaths.COLLATION_FOLDER_NAME);
		try {
			Files.createDirectories(collationFolder);
		} catch (IOException e) {
			System.out.println("  could not create " + collationFolder + ": " + e.getMessage());
			return outcome;
		}
		System.out.println("Collation Testing folder: " + collationFolder);

		Path groupTaxSummary = pack.groupTaxSummary == null ? null : copyInto(pack.groupTaxSummary, collationFolder);
		Path financialStatement = pack.financialStatement == null ? null
				: copyInto(pack.financialStatement, collationFolder);

		for (TrustEntity entity : pack.entities) {
			if (entity.returnFile != null) {
				entity.returnCopy = copyInto(entity.returnFile, collationFolder);
			}
		}

		// FFR for an individual or company refund paid into Fortuna's account - never the trust's
		// return. First table only (financial year, trust account transaction, expected refund):
		// the invoice table and the expected balance (scope 4.5.2) are on hold.
		for (TrustEntity entity : pack.entities) {
			if (entity.isFfr()) {
				Path ffr = ffrService.createFfr(collationFolder,
						"Fee From Refund " + ItrPaths.ITR_YEAR + " - " + safe(entity.name) + ".docx",
						entity.financialYear, entity.name, entity.position.getAmount(),
						"", "", entity.clientAccount(), false);
				entity.ffrWritten = ffr != null;
				outcome.ffrCount += ffr != null ? 1 : 0;
			}
		}

		// One Notes for Persons Signing Tax Returns per individual
		List<Path> notes = new ArrayList<>();
		Path notesSource = Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.NOTES_FOR_SIGNING_FILE);
		if (!Files.isRegularFile(notesSource)) {
			notesSource = Paths.get(ItrPaths.ITR_TEMPLATE_FOLDER, ItrPaths.NOTES_FOR_SIGNING_FILE);
		}
		for (TrustEntity entity : pack.entities) {
			if (entity.kind == Kind.INDIVIDUAL) {
				Path copy = copyAs(notesSource, collationFolder,
						"Notes for Persons Signing Tax Returns - " + safe(entity.name) + ".pdf");
				if (copy != null) {
					notes.add(copy);
				}
			}
		}
		outcome.notesCopies = notes.size();

		// AGM minute: the template as it is, for now (scope 4.7)
		outcome.agmCopied = copyAs(Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.TRUST_AGM_TEMPLATE),
				collationFolder, ItrPaths.TRUST_AGM_TEMPLATE) != null;

		// Engagement letter unless the job came through Ignition
		if (!ignitionJob) {
			Path engagement = copyAs(Paths.get(ItrPaths.ITR_LETTERS_FOLDER, ItrPaths.ENGAGEMENT_LETTER_FILE),
					collationFolder, ItrPaths.ENGAGEMENT_LETTER_FILE);
			List<String> entityNames = new ArrayList<>();
			List<String> individuals = new ArrayList<>();
			for (TrustEntity entity : pack.entities) {
				entityNames.add(entity.name);
				if (entity.kind == Kind.INDIVIDUAL) {
					individuals.add(entity.name);
				}
			}

			// Signature columns: the individuals, then the directors. A director who is
			// already one of the individuals is not repeated; when that leaves the director
			// column empty, the individuals are shared across both columns instead, so each
			// person has a place to sign (John Brunton | Jessica Brunton).
			List<String> otherDirectors = new ArrayList<>();
			for (String director : pack.directors) {
				if (individuals.stream().noneMatch(i -> folderSearch.sameName(i, director))) {
					otherDirectors.add(director);
				}
			}
			List<String> firstColumn = individuals;
			List<String> secondColumn = otherDirectors;
			if (otherDirectors.isEmpty() && individuals.size() > 1) {
				firstColumn = individuals.subList(0, 1);
				secondColumn = individuals.subList(1, individuals.size());
			}

			outcome.engagementLetterWritten = engagement != null && engagementLetters.fillForTrust(engagement,
					pack.recipients, entityNames, pack.clientName, firstColumn, secondColumn, LocalDate.now());
		} else {
			System.out.println("Job came through Ignition — no engagement letter.");
		}

		// Cover letter: the trust first with NIL, then each entity's position and ATO due date
		List<EntityLine> lines = new ArrayList<>();
		for (TrustEntity entity : pack.entities) {
			if (entity.kind == Kind.TRUST) {
				lines.add(new EntityLine(entity.name, "NIL", "NIL"));
			} else {
				lines.add(new EntityLine(entity.name, entity.position == null ? "" : entity.position.describe(),
						entity.dueDate));
			}
		}
		Path letter = coverLetter.createCoverLetter(collationFolder, pack.recipients, lines,
				financialStatement != null, outcome.ffrCount > 0, coverLetterNotes);
		outcome.coverLetterWritten = letter != null;

		// The single final PDF: letter, Group Tax Summary, financials then the trust's return,
		// the other entities' returns, and the Notes.
		if (letter == null) {
			System.out.println("!! No cover letter — no final PDF was built.");
			return outcome;
		}
		Path letterPdf = finalPdf.convertToPdf(letter);
		if (letterPdf == null) {
			return outcome;
		}

		List<Path> parts = new ArrayList<>();
		parts.add(letterPdf);
		addIfPresent(parts, groupTaxSummary);
		addIfPresent(parts, financialStatement);
		for (TrustEntity entity : pack.entities) {
			if (entity.kind == Kind.TRUST) {
				addIfPresent(parts, entity.returnCopy);
			}
		}
		for (TrustEntity entity : pack.entities) {
			if (entity.kind != Kind.TRUST) {
				addIfPresent(parts, entity.returnCopy);
			}
		}
		parts.addAll(notes);

		String team = isBlank(pack.internalTeam) ? "" : " (" + pack.internalTeam.trim() + ")";
		String baseName = safe(pack.clientName) + " - " + ItrPaths.ITR_YEAR
				+ (financialStatement != null ? " Financials and Tax Return" : " Tax Return") + team;

		Path merged = finalPdf.merge(parts, collationFolder.resolve(baseName + ".pdf"));
		outcome.finalPdfBuilt = merged != null;

		// The final folder: the same documents as separate files, numbered in sending order.
		// The combined PDF stays in Collation Testing itself.
		Path finalFolder = collationFolder.resolve(baseName);
		try {
			Files.createDirectories(finalFolder);
			int n = 1;
			for (Path part : parts) {
				Path source = part;
				copyAs(source, finalFolder, (n++) + ". " + source.getFileName());
			}
			outcome.finalFolderBuilt = true;
			System.out.println("Final folder: " + finalFolder);
		} catch (IOException e) {
			System.out.println("  could not create " + finalFolder + ": " + e.getMessage());
		}

		return outcome;
	}

	private void addIfPresent(List<Path> parts, Path file) {
		if (file != null) {
			parts.add(file);
		}
	}

	private Path copyInto(Path source, Path folder) {
		return copyAs(source, folder, source.getFileName().toString());
	}

	private Path copyAs(Path source, Path folder, String fileName) {
		if (source == null || !Files.isRegularFile(source)) {
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

	private static String safe(String name) {
		return name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty() || value.trim().toLowerCase().startsWith("no ");
	}

	/** One entity of the job: the trust, an individual or a company. */
	public static class TrustEntity {
		public final String name;
		public final Kind kind;
		public Path folder;
		public Path workpapers;
		public Path reports;

		/** "2026 TRT - ..." for the trust, "2026_ITR_..." for an individual. */
		public Path returnFile;
		Path returnCopy;

		/** Null for the trust, whose position is NIL. */
		public ItrPosition position;
		public final List<ItrPositionReader.BankAccount> bankAccounts = new ArrayList<>();
		public String financialYear = ItrPaths.ITR_YEAR;

		/** From the ATO; "NIL" for the trust. */
		public String dueDate = "";

		public boolean ffrWritten;
		public String expectedBalance = "";

		TrustEntity(String name, Kind kind) {
			this.name = name;
			this.kind = kind;
		}

		/** Refund paid into Fortuna's account - never checked on the trust's own return. */
		public boolean isFfr() {
			return kind != Kind.TRUST && position != null && position.isRefund()
					&& bankAccounts.stream().anyMatch(ItrPositionReader.BankAccount::isFortuna);
		}

		public ItrPositionReader.BankAccount clientAccount() {
			return bankAccounts.stream().filter(a -> !a.isFortuna()).findFirst().orElse(null);
		}
	}

	/** What {@link #locate} found for one Trust job. */
	public static class TrustPack {
		public final String clientName;
		public final String internalTeam;
		public final List<TrustEntity> entities = new ArrayList<>();
		public TrustEntity mainEntity;
		public Path financialStatement;
		public Path groupTaxSummary;
		public final List<String> directors = new ArrayList<>();

		/** First names the cover letter and engagement letter are addressed to. */
		public final List<String> recipients = new ArrayList<>();

		TrustPack(String clientName, String internalTeam) {
			this.clientName = clientName;
			this.internalTeam = internalTeam;
		}
	}

	/** What a Trust job's collation managed. */
	public static class TrustOutcome {
		public boolean coverLetterWritten;
		public boolean engagementLetterWritten;
		public boolean agmCopied;
		public int notesCopies;
		public int ffrCount;
		public boolean finalPdfBuilt;
		public boolean finalFolderBuilt;

		@Override
		public String toString() {
			return "cover letter " + (coverLetterWritten ? "written" : "NOT written")
					+ ", engagement letter " + (engagementLetterWritten ? "written" : "not included")
					+ ", AGM " + (agmCopied ? "copied" : "NOT copied")
					+ ", signing notes x" + notesCopies
					+ ", FFR x" + ffrCount
					+ ", final PDF " + (finalPdfBuilt ? "built" : "NOT built")
					+ ", final folder " + (finalFolderBuilt ? "built" : "NOT built");
		}
	}
}
