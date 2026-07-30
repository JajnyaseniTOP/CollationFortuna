package com.jj.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

public class ClientExcel extends MainClass {

	LocalDate currentDate = LocalDate.now();

	private static Workbook workbook;
	private static Sheet sheet;
	private static int currentRowNum = 1;
	private static int currentRowNum3 = 1;

	private static final String FILE_NAME = "ClientData.xls";

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
		String[] headers = {"Job No", "Cover Letter Notes", "Date","Client Name","Client Name Code","Client Group Name","Internal team Name","Group+TeamName","Client+code+TeamName","ClientName+TeamName"};
		Row headerRow = sheet.createRow(0);
		for (int i = 0; i < headers.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(headers[i]);
		}
		saveWorkbook(file);
	}

	/*====================Load Existing Workbook (so data appends instead of overwriting)===================================*/
	private void loadExistingWorkbook(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			workbook = new HSSFWorkbook(fis);
			sheet = workbook.getSheet("Client Data");
			currentRowNum = sheet.getLastRowNum() + 1;
		} catch (IOException e) {
			System.out.println("Failed to load existing Excel file: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			// File exists but isn't a valid BIFF8 .xls — likely corrupted or wrong format
			System.out.println("Existing file is not a valid .xls file: " + e.getMessage());
			System.out.println("Delete or rename the file at " + file.getAbsolutePath() + " and re-run.");
		}
	}

	/*====================Insert a row of data into the existing sheet===================================*/
	public void insertData(String jobNo, String coverLetterNotes, String currentDate,String clientname, String clientNameCode,String GroupName,String internalTeam) {
		if (workbook == null || sheet == null) {
			createEmptyExcelSheet();
		}
		Row row = sheet.createRow(currentRowNum);
		row.createCell(0).setCellValue(jobNo);
		row.createCell(1).setCellValue(coverLetterNotes);
		row.createCell(2).setCellValue(currentDate);
		row.createCell(3).setCellValue(clientname);
		row.createCell(4).setCellValue(clientNameCode);
		row.createCell(5).setCellValue(GroupName);
		row.createCell(6).setCellValue(internalTeam);
		createCombinedColumns(row); 
		

		currentRowNum++;

		saveWorkbook(getTargetFile());
	}
	
	
	/*====================Create Combined Columns for Current Row===================================*/
	private void createCombinedColumns(Row row) {

	    String clientName   = getCellValue(row.getCell(3));
	    String clientCode   = getCellValue(row.getCell(4));
	    String groupName    = getCellValue(row.getCell(5));
	    String internalTeam = getCellValue(row.getCell(6));

	    row.createCell(7).setCellValue(groupName + " - " + internalTeam);
	    row.createCell(8).setCellValue(clientCode + " - " + internalTeam);
	    row.createCell(9).setCellValue(clientName + " - " + internalTeam);
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