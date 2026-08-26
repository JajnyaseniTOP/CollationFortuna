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

	private static final String FILE_NAME = "ClientData.xls";
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy");
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

		if (file.exists()) {
			System.out.println("Excel file already exists at: " + file.getAbsolutePath() + " — skipping creation.");
			loadExistingWorkbook(file);
			return;
		}

		workbook = new HSSFWorkbook();
		sheet = workbook.createSheet("Client Data");
		String[] headers = {"Job No", "ATO quarter", "Cover Letter Notes", "Date","Client Name","Client Name_xpm","Client code","Client Group Name","Internal team Name","Group+TeamName","Client+code+TeamName","ClientName+TeamName","ReversedNameforCol_10","Quarters", "Status", "Payble/Refund", "Amount", "Financial Quarter"};
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
		if (workbook == null || sheet == null) {
			createEmptyExcelSheet();
		}
		boolean firstMonth = true;
		for (YearMonth month : getMonthsForQuarterRange(quarters)) {
			Row row = sheet.getRow(currentRowNum);
			if (row == null) {
				row = sheet.createRow(currentRowNum);
			}
			currentRowNum++;
			if (firstMonth) {
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
				firstMonth = false;
			}
			String monthValue = month.format(MONTH_FORMATTER);
			row.createCell(13).setCellValue(monthValue);
			String payable = payableByMonth.get(monthValue);
			row.createCell(14).setCellValue(payable == null ? "Not Found" : "Found");
			row.createCell(15).setCellValue(payable == null ? "Not Found" : payable);
			String amountPayable = amountPayableByMonth.get(monthValue);
			row.createCell(16).setCellValue(amountPayable == null ? "Not Found" : amountPayable);
			row.createCell(17).setCellValue(financialQuarterFor(month));
		}

		saveWorkbook(getTargetFile());
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