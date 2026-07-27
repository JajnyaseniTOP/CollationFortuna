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

	/*====================Creation of Excel Sheet (only if it doesn't already exist)===================================*/

	public void createEmptyExcelSheet() {
		String projectDir = System.getProperty("user.dir");
		File file = new File(projectDir, FILE_NAME);

		if (file.exists()) {
			System.out.println("Excel file already exists at: " + file.getAbsolutePath() + " — skipping creation.");
			loadExistingWorkbook(file);
			return;
		}

		workbook = new HSSFWorkbook();
		sheet = workbook.createSheet("Client Data");

		String[] headers = {"Job No", "Cover Letter Notes","Date"};

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

			// Figure out where the next empty row is, so new data appends after existing rows
			currentRowNum = sheet.getLastRowNum() + 1;

		} catch (IOException e) {
			System.out.println("Failed to load existing Excel file: " + e.getMessage());
		}
	}

	/*====================Insert a row of data into the existing sheet===================================*/

	public void insertData(String jobNo, String coverLetterNotes,String currentDate) {
		if (workbook == null || sheet == null) {			
			createEmptyExcelSheet();
		}

		Row row = sheet.createRow(currentRowNum);
		row.createCell(0).setCellValue(jobNo);
		row.createCell(1).setCellValue(coverLetterNotes);
		row.createCell(2).setCellValue(currentDate);
		
		currentRowNum++;

		String projectDir = System.getProperty("user.dir");
		File file = new File(projectDir, FILE_NAME);
		saveWorkbook(file);
	}

	/*====================Save Workbook to disk===================================*/

	private void saveWorkbook(File file) {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			workbook.write(fos);
			System.out.println("Excel file saved at: " + file.getAbsolutePath());
		} catch (IOException e) {
			System.out.println("Failed to write Excel file: " + e.getMessage());
		} finally {
			try {
				workbook.close();
			} catch (IOException e) {
				System.out.println("Failed to close workbook: " + e.getMessage());
			}
		}
	}
}