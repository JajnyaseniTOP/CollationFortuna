package com.jj.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

public class ClientExcel extends MainClass {

	LocalDate currentDate = LocalDate.now();

	private static Workbook workbook;
	private static Sheet sheet;
	private static int currentRowNum = 1;
	private static int currentRowNum3 = 1;

	/**
	 * Each run writes its own workbook, stamped with the time it started.
	 *
	 * <p>Appending to one shared file meant every abandoned or half-finished run left
	 * its rows behind, so the sheet accumulated partial jobs that look exactly like
	 * good ones. A file per run is self-evidently the output of that run.
	 *
	 * <p>Static, so every step of the run writes to the same workbook.
	 */
	private static final String FILE_NAME = "ClientData_"
			+ java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
			+ ".xls";
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy");

	/** Column S, straight after Financial Quarter. */
	private static final int BUSINESS_STRUCTURE_COLUMN = 18;
	private static final String BUSINESS_STRUCTURE_HEADER = "Business Structure";

	/** Column T: which collation the job got, so the sheet alone shows BAS jobs from ITR jobs. */
	private static final int COLLATION_TYPE_COLUMN = 19;
	private static final String COLLATION_TYPE_HEADER = "Collation Type";
	private static final String COLLATION_TYPE_BAS = "BAS";
	private static final String COLLATION_TYPE_ITR = "ITR";

	/** Columns U to W: filled for ITR jobs, blank on BAS rows. */
	private static final int PREFERRED_NAME_COLUMN = 20;
	private static final int RECIPIENT_NAME_COLUMN = 21;
	private static final int DUE_DATE_COLUMN = 22;
	private static final String PREFERRED_NAME_HEADER = "Preferred Name";
	private static final String RECIPIENT_NAME_HEADER = "Cover Letter Recipient";
	private static final String DUE_DATE_HEADER = "ATO Due Date";

	/** Columns X and Y: the final invoice from the job's Financial tab (ITR jobs). */
	private static final int FINAL_INVOICE_NO_COLUMN = 23;
	private static final int FINAL_INVOICE_AMOUNT_COLUMN = 24;
	private static final String FINAL_INVOICE_NO_HEADER = "Final Invoice No";
	private static final String FINAL_INVOICE_AMOUNT_HEADER = "Final Invoice Amount";

	/** Columns Z and AA: whether a Fee From Refund was prepared, and its expected balance (ITR jobs). */
	private static final int FFR_COLUMN = 25;
	private static final int EXPECTED_BALANCE_COLUMN = 26;
	private static final String FFR_HEADER = "FFR Prepared";
	private static final String EXPECTED_BALANCE_HEADER = "FFR Expected Balance";
	private static final Pattern QUARTER_RANGE_PATTERN = Pattern.compile(
			"(?i)Q([1-4])\\s*(\\d{4})\\s*-\\s*Q([1-4])\\s*(\\d{4})");

	/*====================Get the Downloads folder path (cross-platform)===================================*/
	private static File getTargetFile() {
		String userHome = System.getProperty("user.home");
		File downloadsDir = new File(userHome, "Downloads");

		if (!downloadsDir.exists()) {
			downloadsDir.mkdirs(); // create Downloads folder if it somehow doesn't exist
		}

		return new File(downloadsDir, FILE_NAME);
	}

	/*====================Creation of Excel Sheet (only if it doesn't already exist)===================================*/
	public void createEmptyExcelSheet() {
		File file = getTargetFile();

		// Within a run this is called more than once, so reuse the workbook already in
		// hand rather than starting the sheet over and losing the rows written so far.
		if (workbook != null && sheet != null) {
			System.out.println("Using this run's workbook: " + file.getAbsolutePath());
			return;
		}

		if (file.exists()) {
			System.out.println("Reopening this run's workbook: " + file.getAbsolutePath());
			loadExistingWorkbook(file);
			return;
		}

		System.out.println("Creating this run's workbook: " + file.getAbsolutePath());
		workbook = new HSSFWorkbook();
		sheet = workbook.createSheet("Client Data");
		String[] headers = {"Job No", "ATO quarter", "Cover Letter Notes", "Date","Client Name","Client Name_xpm","Client code","Client Group Name","Internal team Name","Group+TeamName","Client+code+TeamName","ClientName+TeamName","ReversedNameforCol_10","Quarters", "Status", "Payble/Refund", "Amount", "Financial Quarter", BUSINESS_STRUCTURE_HEADER, COLLATION_TYPE_HEADER,
				PREFERRED_NAME_HEADER, RECIPIENT_NAME_HEADER, DUE_DATE_HEADER,
				FINAL_INVOICE_NO_HEADER, FINAL_INVOICE_AMOUNT_HEADER, FFR_HEADER, EXPECTED_BALANCE_HEADER};
		Row headerRow = sheet.createRow(0);
		for (int i = 0; i < headers.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(headers[i]);
		}
		saveWorkbook(file);
	}

	public void addAtoValue(String atoValue) {
		if (workbook == null || sheet == null) {
			createEmptyExcelSheet();
		}

		int atoRowNum = findPendingAtoRow();
		if (atoRowNum >= 0) {
			currentRowNum = atoRowNum;
		}
		Row row = sheet.getRow(currentRowNum);
		if (row == null) {
			row = sheet.createRow(currentRowNum);
		}
		row.createCell(1).setCellValue(atoValue == null ? "" : atoValue.trim());
		saveWorkbook(getTargetFile());
	}

	private int findPendingAtoRow() {
		for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (row != null && getCellValue(row.getCell(0)).isEmpty()
					&& !getCellValue(row.getCell(1)).isEmpty()
					&& getCellValue(row.getCell(13)).isEmpty()) {
				return rowIndex;
			}
		}
		return -1;
	}

	/*====================Load Existing Workbook (so data appends instead of overwriting)===================================*/
	private void loadExistingWorkbook(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			workbook = new HSSFWorkbook(fis);
			sheet = workbook.getSheet("Client Data");
			migrateMonthColumn();
			migrateAtoQuarterColumn();
			Row header = sheet.getRow(0);
			if (header != null) {
				header.createCell(14).setCellValue("Status");
				header.createCell(15).setCellValue("Payble/Refund");
				header.createCell(16).setCellValue("Amount");
				header.createCell(17).setCellValue("Financial Quarter");
				header.createCell(BUSINESS_STRUCTURE_COLUMN).setCellValue(BUSINESS_STRUCTURE_HEADER);
				header.createCell(COLLATION_TYPE_COLUMN).setCellValue(COLLATION_TYPE_HEADER);
				header.createCell(PREFERRED_NAME_COLUMN).setCellValue(PREFERRED_NAME_HEADER);
				header.createCell(RECIPIENT_NAME_COLUMN).setCellValue(RECIPIENT_NAME_HEADER);
				header.createCell(DUE_DATE_COLUMN).setCellValue(DUE_DATE_HEADER);
				header.createCell(FINAL_INVOICE_NO_COLUMN).setCellValue(FINAL_INVOICE_NO_HEADER);
				header.createCell(FINAL_INVOICE_AMOUNT_COLUMN).setCellValue(FINAL_INVOICE_AMOUNT_HEADER);
				header.createCell(FFR_COLUMN).setCellValue(FFR_HEADER);
				header.createCell(EXPECTED_BALANCE_COLUMN).setCellValue(EXPECTED_BALANCE_HEADER);
			}
			migrateFinancialQuarterColumn();
			int pendingAtoRow = findPendingAtoRow();
			currentRowNum = pendingAtoRow >= 0 ? pendingAtoRow : sheet.getLastRowNum() + 1;
		} catch (IOException e) {
			System.out.println("Failed to load existing Excel file: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			// File exists but isn't a valid BIFF8 .xls — likely corrupted or wrong format
			System.out.println("Existing file is not a valid .xls file: " + e.getMessage());
			System.out.println("Delete or rename the file at " + file.getAbsolutePath() + " and re-run.");
		}
	}

	/*====================Insert a row of data into the existing sheet===================================*/
	public void insertData(String jobNo, String atoQuarter, String coverLetterNotes, String currentDate,String clientname, String clientNamexpm,String clientName,String GroupName,String internalTeam,String quarters) {
		insertData(jobNo, atoQuarter, coverLetterNotes, currentDate, clientname, clientNamexpm, clientName,
				GroupName, internalTeam, quarters, Collections.emptyMap(), Collections.emptyMap());
	}

	public void insertData(String jobNo, String atoQuarter, String coverLetterNotes, String currentDate,String clientname, String clientNamexpm,String clientName,String GroupName,String internalTeam,String quarters, Map<String, String> payableByMonth) {
		insertData(jobNo, atoQuarter, coverLetterNotes, currentDate, clientname, clientNamexpm, clientName,
				GroupName, internalTeam, quarters, payableByMonth, payableByMonth);
	}

	public void insertData(String jobNo, String atoQuarter, String coverLetterNotes, String currentDate,String clientname, String clientNamexpm,String clientName,String GroupName,String internalTeam,String quarters, Map<String, String> payableByMonth, Map<String, String> amountPayableByMonth) {
		insertData(jobNo, atoQuarter, coverLetterNotes, currentDate, clientname, clientNamexpm, clientName,
				GroupName, internalTeam, quarters, payableByMonth, amountPayableByMonth, "");
	}

	/** As above, also writing the client's XPM Business structure (e.g. "Individual") on the job's first row. */
	public void insertData(String jobNo, String atoQuarter, String coverLetterNotes, String currentDate,String clientname, String clientNamexpm,String clientName,String GroupName,String internalTeam,String quarters, Map<String, String> payableByMonth, Map<String, String> amountPayableByMonth, String businessStructure) {
		if (workbook == null || sheet == null) {
			createEmptyExcelSheet();
		}
		List<CollationPeriod> periods = collationPeriods(quarters, atoQuarter);
		System.out.println("Lodgement cycle '" + atoQuarter + "' -> writing " + periods.size()
				+ (isQuarterlyCycle(atoQuarter) ? " quarterly" : " monthly") + " row(s) for " + quarters);

		boolean firstPeriod = true;
		for (CollationPeriod period : periods) {
			Row row = sheet.getRow(currentRowNum);
			if (row == null) {
				row = sheet.createRow(currentRowNum);
			}
			currentRowNum++;
			if (firstPeriod) {
				row.createCell(0).setCellValue(jobNo);
				if (getCellValue(row.getCell(1)).isEmpty()) {
					row.createCell(1).setCellValue(atoQuarter);
				}
				row.createCell(2).setCellValue(coverLetterNotes);
				row.createCell(3).setCellValue(currentDate);
				row.createCell(4).setCellValue(clientname);
				row.createCell(5).setCellValue(clientNamexpm);
				row.createCell(6).setCellValue(clientName);
				row.createCell(7).setCellValue(GroupName);
				row.createCell(8).setCellValue(internalTeam);
				createCombinedColumns(row);
				row.createCell(BUSINESS_STRUCTURE_COLUMN).setCellValue(businessStructure == null ? "" : businessStructure);
				firstPeriod = false;
			}
			row.createCell(13).setCellValue(period.label);
			String figureMonth = period.figureMonth.format(MONTH_FORMATTER);
			String payable = payableByMonth.get(figureMonth);
			row.createCell(14).setCellValue(payable == null ? "Not Found" : "Found");
			row.createCell(15).setCellValue(payable == null ? "Not Found" : payable);
			String amountPayable = amountPayableByMonth.get(figureMonth);
			row.createCell(16).setCellValue(amountPayable == null ? "Not Found" : amountPayable);
			row.createCell(17).setCellValue(period.financialQuarter);
			row.createCell(COLLATION_TYPE_COLUMN).setCellValue(COLLATION_TYPE_BAS);

			// Saved per row rather than once at the end: a job that fails part way
			// through then still leaves everything it did manage on disk.
			saveWorkbook(getTargetFile());
		}
	}

	/**
	 * Writes the one row an ITR job gets, marked "ITR" in the Collation Type column.
	 *
	 * <p>The columns line up with the BAS rows so the sheet reads the same way for
	 * both: Status says whether the 2026_ITR file was found, and Payable/Refund and
	 * Amount come off that file. Quarters, ATO quarter and Financial Quarter stay
	 * blank — an income tax return has none of them.
	 */
	public void insertItrRow(String jobNo, String currentDate, String clientname, String clientNamexpm,
			String clientCode, String groupName, String internalTeam, String businessStructure,
			String preferredName, String recipientName, String dueDate,
			String finalInvoiceNumber, String finalInvoiceAmount, boolean ffrPrepared, String expectedBalance,
			boolean itrFileFound, String payableOrRefund, String amount) {
		if (workbook == null || sheet == null) {
			createEmptyExcelSheet();
		}

		Row row = sheet.getRow(currentRowNum);
		if (row == null) {
			row = sheet.createRow(currentRowNum);
		}
		currentRowNum++;

		row.createCell(0).setCellValue(jobNo);
		row.createCell(1).setCellValue("");
		row.createCell(2).setCellValue("");
		row.createCell(3).setCellValue(currentDate);
		row.createCell(4).setCellValue(clientname);
		row.createCell(5).setCellValue(clientNamexpm);
		row.createCell(6).setCellValue(clientCode);
		row.createCell(7).setCellValue(groupName);
		row.createCell(8).setCellValue(internalTeam);
		createCombinedColumns(row);
		row.createCell(13).setCellValue("");
		row.createCell(14).setCellValue(itrFileFound ? "Found" : "Not Found");
		row.createCell(15).setCellValue(payableOrRefund == null || payableOrRefund.isEmpty() ? "Not Found" : payableOrRefund);
		row.createCell(16).setCellValue(amount == null || amount.isEmpty() ? "Not Found" : amount);
		row.createCell(17).setCellValue("");
		row.createCell(BUSINESS_STRUCTURE_COLUMN).setCellValue(businessStructure == null ? "" : businessStructure);
		row.createCell(COLLATION_TYPE_COLUMN).setCellValue(COLLATION_TYPE_ITR);
		row.createCell(PREFERRED_NAME_COLUMN).setCellValue(preferredName == null ? "" : preferredName);
		row.createCell(RECIPIENT_NAME_COLUMN).setCellValue(recipientName == null ? "" : recipientName);
		row.createCell(DUE_DATE_COLUMN).setCellValue(dueDate == null ? "" : dueDate);
		row.createCell(FINAL_INVOICE_NO_COLUMN).setCellValue(finalInvoiceNumber == null ? "" : finalInvoiceNumber);
		row.createCell(FINAL_INVOICE_AMOUNT_COLUMN).setCellValue(finalInvoiceAmount == null ? "" : finalInvoiceAmount);
		row.createCell(FFR_COLUMN).setCellValue(ffrPrepared ? "Yes" : "No");
		row.createCell(EXPECTED_BALANCE_COLUMN).setCellValue(expectedBalance == null ? "" : expectedBalance);

		saveWorkbook(getTargetFile());
	}

	/*====================How many rows the BAS period needs===================================*/

	/**
	 * One row of the sheet: the label written to column N, the financial quarter for
	 * column R, and the month whose Payable/Amount figures belong on the row.
	 *
	 * <p>The filesearch project decides monthly vs quarterly purely from column N —
	 * "Apr 2026" is a month, "Q4 2026" is a quarter — so this is where the ATO
	 * lodgement cycle turns into the number of documents to collate.
	 */
	private static final class CollationPeriod {
		private final String label;
		private final String financialQuarter;
		private final YearMonth figureMonth;

		private CollationPeriod(String label, String financialQuarter, YearMonth figureMonth) {
			this.label = label;
			this.financialQuarter = financialQuarter;
			this.figureMonth = figureMonth;
		}
	}

	/** True when the ATO lodgement cycle reads as quarterly rather than monthly. */
	static boolean isQuarterlyCycle(String lodgementCycle) {
		return lodgementCycle != null && lodgementCycle.toLowerCase(Locale.ENGLISH).contains("quarter");
	}

	/**
	 * A monthly client gets one row per month of the BAS period — twelve for
	 * Q4 2026-Q3 2027. A quarterly client gets one row per financial quarter — four
	 * for the same range — and each quarter's figures are taken from its last month,
	 * because that is the month the activity statement is filed under.
	 *
	 * <p>An unreadable cycle ("NA") falls back to monthly, which is the behaviour
	 * this sheet has always had.
	 */
	static List<CollationPeriod> collationPeriods(String quarterRange, String lodgementCycle) {
		return collationPeriods(BasPeriodParser.quartersFrom(quarterRange), lodgementCycle);
	}

	/**
	 * The periods a job covers, paired with the figures written against each, ready
	 * for the collation to copy documents and write cover letters.
	 *
	 * <p>Derived the same way as the rows, so the sheet and the client folders can
	 * never disagree about which periods a job has.
	 */
	public static List<com.jj.collation.JobCollator.CollationTarget> collationTargets(
			String jobName, String lodgementCycle,
			Map<String, String> payableByMonth, Map<String, String> amountPayableByMonth) {

		List<com.jj.collation.JobCollator.CollationTarget> targets = new ArrayList<>();

		for (CollationPeriod period : collationPeriods(jobName, lodgementCycle)) {
			String figureMonth = period.figureMonth.format(MONTH_FORMATTER);
			String payable = payableByMonth.get(figureMonth);
			String amount = amountPayableByMonth.get(figureMonth);

			// A job's name can cover a whole year, so most of its periods are still in
			// the future: the ATO has no statement for them and the client folder has
			// no folder for them either. They belong in the sheet as "Not Found", but
			// there is nothing to collate, so they are left out here.
			if (payable == null) {
				continue;
			}

			targets.add(new com.jj.collation.JobCollator.CollationTarget(
					period.label,
					period.financialQuarter,
					payable,
					amount == null ? "Not Found" : amount));
		}
		return targets;
	}

	/**
	 * The rows one job needs, from the quarters its name covers.
	 *
	 * <p>A quarterly client gets a row per quarter; a monthly client gets a row for
	 * each of the three months inside every quarter — so "Q3 27" alone becomes three
	 * rows for a monthly client and one for a quarterly one.
	 */
	static List<CollationPeriod> collationPeriods(List<String> quarters, String lodgementCycle) {
		List<CollationPeriod> periods = new ArrayList<>();

		for (String quarter : quarters) {
			if (isQuarterlyCycle(lodgementCycle)) {
				List<YearMonth> months = BasPeriodParser.monthsIn(quarter);
				if (!months.isEmpty()) {
					// Figures come from the quarter's last month: that is the period the
					// activity statement is filed under.
					periods.add(new CollationPeriod(quarter, quarter, months.get(months.size() - 1)));
				}
			} else {
				for (YearMonth month : BasPeriodParser.monthsIn(quarter)) {
					periods.add(new CollationPeriod(month.format(MONTH_FORMATTER), quarter, month));
				}
			}
		}
		return periods;
	}

	public static List<YearMonth> getMonthsForQuarterRange(String quarterRange) {
		Matcher matcher = QUARTER_RANGE_PATTERN.matcher(quarterRange == null ? "" : quarterRange.trim());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Quarter range must look like Q4 2026-Q3 2027: " + quarterRange);
		}

		int startQuarter = Integer.parseInt(matcher.group(1));
		int startYear = Integer.parseInt(matcher.group(2));
		int endQuarter = Integer.parseInt(matcher.group(3));
		int endYear = Integer.parseInt(matcher.group(4));

		YearMonth start = quarterStart(startQuarter, startYear);
		YearMonth endQuarterStart = quarterStart(endQuarter, endYear);
		if (endQuarterStart.isBefore(start)) {
			endQuarterStart = endQuarterStart.plusYears(1);
		}
		YearMonth end = endQuarterStart.plusMonths(2);
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("Quarter range ends before it starts: " + quarterRange);
		}

		List<YearMonth> months = new ArrayList<>();
		for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
			months.add(month);
		}
		return months;
	}

	private static YearMonth quarterStart(int quarter, int year) {
		int month = quarter == 1 ? 7 : quarter == 2 ? 10 : quarter == 3 ? 1 : 4;
		return YearMonth.of(year, month);
	}

	private void migrateMonthColumn() {
		Row header = sheet.getRow(0);
		if (header == null || !"Month".equalsIgnoreCase(getCellValue(header.getCell(13)))) {
			return;
		}

		header.getCell(12).setCellValue("Quarters");
		header.removeCell(header.getCell(13));
		for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (row != null && row.getCell(13) != null) {
				row.getCell(12).setCellValue(getCellValue(row.getCell(13)));
				row.removeCell(row.getCell(13));
			}
		}
	}

	private void migrateAtoQuarterColumn() {
		Row header = sheet.getRow(0);
		if (header == null || "ATO quarter".equalsIgnoreCase(getCellValue(header.getCell(1)))) {
			return;
		}

		int lastColumn = Math.max(1, header.getLastCellNum() - 1);
		sheet.shiftColumns(1, lastColumn, 1);
		header.createCell(1).setCellValue("ATO quarter");
	}

	private void migrateFinancialQuarterColumn() {
		for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (row == null) {
				continue;
			}
			String monthValue = getCellValue(row.getCell(13));
			if (monthValue.isEmpty() || !monthValue.matches("[A-Za-z]{3} \\d{4}")) {
				continue;
			}
			try {
				YearMonth month = YearMonth.parse(monthValue, MONTH_FORMATTER);
				row.createCell(17).setCellValue(financialQuarterFor(month));
			} catch (RuntimeException ignored) {
				// Leave malformed legacy month values unchanged.
			}
		}
	}

	private static String financialQuarterFor(YearMonth month) {
		int monthNumber = month.getMonthValue();
		if (monthNumber >= 4 && monthNumber <= 6) {
			return "Q4 " + month.getYear();
		}
		if (monthNumber >= 7 && monthNumber <= 9) {
			return "Q1 " + (month.getYear() + 1);
		}
		if (monthNumber >= 10) {
			return "Q2 " + (month.getYear() + 1);
		}
		return "Q3 " + month.getYear();
	}
	
	
	/*====================Create Combined Columns for Current Row===================================*/
	private void createCombinedColumns(Row row) {
	    String clientName   = getCellValue(row.getCell(5));
	    String clientCode   = getCellValue(row.getCell(6));
	    String groupName    = getCellValue(row.getCell(7));
	    String internalTeam = getCellValue(row.getCell(8));

	    String clientNameWithTeam = clientName + " - " + internalTeam;	
	    row.createCell(9).setCellValue(groupName + " - " + internalTeam);
	    row.createCell(10).setCellValue(clientName + " (" + clientCode + ") - " + internalTeam);
	    row.createCell(11).setCellValue(clientNameWithTeam);
	    String formattedClientName = formatClientName(clientNameWithTeam);
	    row.createCell(12).setCellValue(formattedClientName);
	}


	private String formatClientName(String value) {
	    String[] parts = value.split("\\s+-\\s+", 2);
	    if (parts.length != 2) {
	        return value;
	    }
	    String clientName = parts[0];
	    String internalTeam = parts[1];	    
	    String lowerClientName = clientName.toLowerCase();	   
	    if (lowerClientName.contains("pty")
	            || lowerClientName.contains("ltd")
	            || lowerClientName.contains("superannuation")
	            || lowerClientName.contains("family trust")) {

	        return value;
	    }

	  
	    String[] words = clientName.trim().split("\\s+");	   
	    if (words.length >= 2) {
	        StringBuilder reversedName = new StringBuilder();	       
	        reversedName.append(words[words.length - 1]);
	        for (int i = words.length - 2; i >= 0; i--) {
	            reversedName.append(", ").append(words[i]);
	        }
	        return reversedName + " - " + internalTeam;
	    }
	    return value;
	}
	private String getCellValue(Cell cell) {
	    return (cell == null) ? "" : cell.toString().trim();
	}
	
	

	/*====================Save Workbook to disk===================================*/
	private void saveWorkbook(File file) {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			workbook.write(fos);
			System.out.println("Excel file saved at: " + file.getAbsolutePath());
		} catch (IOException e) {
			System.out.println("Failed to write Excel file: " + e.getMessage());
		}
	}
}