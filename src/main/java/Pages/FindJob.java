package Pages;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.google.common.collect.Table.Cell;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class FindJob extends MainClass {
	static ClientExcel creator = new ClientExcel();
	public static String quarter;
	public static String extractedJobNo = "";
	public static String coverLetterNotes = "";
	public static String clientName = "";
	public static String internalteam = "";
	public static String clientnamecode = "";
	public static String groupName = "";
	public static String cientCode = "";
	public static String startQuarter = "Q4";
	public static String endQuarter = "Q3";
	public static String startingYear = "2026";
	public static String endingYear = "2027";
	public static String atoQuarter = "Not Found";
	public static String quarters = startQuarter + " " + startingYear + "-" + endQuarter + " " + endingYear;
//	public static String taxReturnpayble = "";
//	public static String taxamount = "";
	

	@FindBy(xpath="//span[@class='xnav-appbutton--text' and text()='Fortuna Unit Trust']")
	private static WebElement switchPortal_keypoint;
	@FindBy(xpath="//a[normalize-space()='Portal']")
	private static WebElement clickPortal;
	@FindBy(xpath = "//input[@value='Connect']")
	private static WebElement clickConnect;
	@FindBy(xpath="//span[contains(text(),'Fortuna Accountants & Business…')]")
	private static WebElement switchPortal_business;
	@FindBy(xpath="//button[normalize-space()='Jobs']")
	private static WebElement job;
	@FindBy(xpath="//a[normalize-space()='Jobs']")
	private static WebElement jobs;
	@FindBy(xpath="//input[@type='search' and @placeholder='Search']")
	private static WebElement searchbox;
	@FindBy(xpath="//a[contains(@class,'practicemanager-jobs-fe-AllJobsTable--job-link')]")
	private static WebElement jobNumber;
	@FindBy(xpath="//td[normalize-space()='Job No.:']/following::div[1]")
	private static WebElement jobNo;
	@FindBy(xpath="//td[normalize-space()='Cover Letter Notes:']/following::div[1]")
	private static WebElement Cover_Letter_Notes;
	@FindBy(xpath="//td[normalize-space()='Client:']/following-sibling::td[1]")
	private static WebElement Client_Name;
	@FindBy(xpath="//h1[@class='xui-pageheading--title']")
	private static WebElement client_name_code;
	
	
	private static final By TAX_RETURNS_LINK = By.xpath("//a[normalize-space()='Tax Returns']");
	private static final By TAX_RETURN_ROWS = By.xpath("//tr[.//a[contains(normalize-space(.), 'AS')]]");
	private static final By NEXT_PAGE = By.xpath("//a[@role='button' and .//span[normalize-space()='Next']]");
	private static final By PAYABLE_REFUND = By.cssSelector(
			"div.xui-u-flex.YYW5D.WtDG6.xui-padding-top-large > div.xui-u-flex-col.xui-u-flex-grow.xui-padding-right-xlarge");
	private static final By AMOUNT_PAYABLE = By.cssSelector(
			"div.xui-u-flex.YYW5D.WtDG6.xui-padding-top-large > div.xui-u-flex-col:nth-child(2)");
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
	
	
	@FindBy(xpath="//div[@data-automationid='information-item-businessName--value']//span")
	private static WebElement client_name;	
	@FindBy(xpath="//div[@data-automationid='information-item-clientReferenceCode--value']//span")
	private static WebElement client_code;
	
	@FindBy(xpath="//span[normalize-space()='Groups']/following::span[contains(@class,'xui-pill--text')][1]")
	private static WebElement client_GroupName;

	private static final By CLIENT_NAME_CELL_LOCATOR =	By.xpath("//td[normalize-space()='Client:']/following-sibling::td[1]");
	private static final By[] INTERNAL_TEAM_LOCATORS = {
		By.xpath("//div[normalize-space()='Internal Team']/following-sibling::*[1]"),
		By.xpath("//*[normalize-space()='Internal Team']/following::*[normalize-space() and normalize-space()!='-'][1]")
	};

	public static void switchportal() throws InterruptedException {
		Thread.sleep(3000);
		try {
			switchPortal_keypoint.click();
			clickPortal.click();
			clickConnect.click();
		} catch (Exception e) {
		}
	}

	public static void switchportal2() throws InterruptedException {
		Thread.sleep(3000);
		try {
			switchPortal_business.click();
			clickPortal.click();
			clickConnect.click();
		} catch (Exception e) {
		}
	}

	public FindJob() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}

	public static void clickjoblinks(String jobnumber) throws InterruptedException {
		job.click();
		jobs.click();
		searchbox.click();
		searchbox.sendKeys(jobnumber);
		Thread.sleep(2000);
		jobNumber.click();
	}

	private static void extractClientDetailsInJobPage() throws InterruptedException {
		System.out.println(">>> ENTERED extractClientDetailsInJobPage()");
		Thread.sleep(1000);
		try {
			JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
			js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
			Thread.sleep(1000);
			js.executeScript("window.scrollTo(0, 0)");
			Thread.sleep(800);
		} catch (Exception e) {
			System.out.println("Scroll failed, continuing anyway: " + e.getMessage());
		}

		extractedJobNo   = extractText(jobNo, "Job No.","No Job");
		coverLetterNotes = extractText(Cover_Letter_Notes, "Cover Letter Notes","No cover letter");
		clientName       = extractText(Client_Name, "Client Name","No cleintName");

		System.out.println(">>> EXITED extractClientDetailsInJobPage()");
	}

	/*====================Modern SPA client page (xui-* layout)===================================*/
	private static void extractClientDetail() throws InterruptedException {
		System.out.println(">>> ENTERED extractClientDetail() [modern layout]");
		Thread.sleep(1000);
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
			Thread.sleep(1000);
			js.executeScript("window.scrollTo(0, 0)");
			Thread.sleep(800);
		} catch (Exception e) {
			System.out.println("Scroll failed, continuing anyway: " + e.getMessage());
		}

		clientnamecode = extractText(client_name, "Client Name", "No Value");
		groupName      = extractText(client_GroupName, "Group Name", "No GroupName");
		internalteam   = extractInternalTeam();
		cientCode   = extractText(client_code, "Internal Team", "No Value");
		System.out.println(">>> Extracted Internal Team: '" + internalteam + "'");
//		taxReturn.click();
//		itr.click();
//		
//		taxReturnpayble   = extractText(paymentpayble, "Tax Return", "No Value");
//		taxamount   = extractText(amount, "value", "No Value");
		
		
		

		String quarterRange = startQuarter + " " + startingYear + "-" + endQuarter + " " + endingYear;
		TaxReturnValues taxReturnValues = findTaxReturns(quarterRange);
		creator.insertData(extractedJobNo, atoQuarter, coverLetterNotes, currentDate, clientName, clientnamecode,
				cientCode, groupName, internalteam, quarterRange, taxReturnValues.payableByMonth,
				taxReturnValues.amountPayableByMonth);
	}

	/*====================Legacy ASP.NET client page (clientview.aspx layout)===================================*/
	private static void extractClientDetailLegacy() throws InterruptedException {
		System.out.println(">>> ENTERED extractClientDetailLegacy() [legacy layout]");
		Thread.sleep(1000);

		// PLACEHOLDER — locators below are guesses based on your jobNo/Cover_Letter_Notes pattern.
		// Once you share the actual HTML from clientview.aspx, I'll replace these with confirmed locators.
		clientnamecode = "No Value";
		groupName      = "No Value";
		internalteam   = "No Value";
		cientCode      =  "No Value";
//		taxReturnpayble   = "No Value";
//		taxamount   = "No Value";

		creator.insertData(extractedJobNo, atoQuarter, coverLetterNotes, currentDate, clientName, clientnamecode,cientCode, groupName, internalteam, startQuarter + " " + startingYear + "-" + endQuarter + " " + endingYear);
		
	}
	



	/*====================Helper: extract text with fallback to "No Value"===================================*/
	private static String extractText(WebElement element,String fieldLabelForLog,String defaultValue) {
		try {
			String value = element.getText();
			if (value.isEmpty()) {
				System.out.println(fieldLabelForLog + " was empty — defaulting to '" + defaultValue + "'");
				return defaultValue;
			}
			return value;
		} catch (Exception e) {
			System.out.println("Unable to extract " + fieldLabelForLog + ": " + e.getMessage());
			return defaultValue;
		}
	}

	private static String extractInternalTeam() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		for (By locator : INTERNAL_TEAM_LOCATORS) {
			try {
				String value = wait.until(d -> {
					for (WebElement element : d.findElements(locator)) {
						if (element.isDisplayed()) {
							String text = element.getText().trim();
							if (!text.isEmpty() && !"-".equals(text)) {
								return text;
							}
						}
					}
					return null;
				});
				return value;
			} catch (Exception e) {
			}
		}
		return "No Value";
	}

	private static TaxReturnValues findTaxReturns(String quarterRange) throws InterruptedException {
		Map<String, String> payableByMonth = new HashMap<>();
		Map<String, String> amountPayableByMonth = new HashMap<>();
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
			WebElement taxReturns = wait.until(d -> d.findElement(TAX_RETURNS_LINK));
			taxReturns.click();
			wait.until(d -> !d.findElements(TAX_RETURN_ROWS).isEmpty());
			Map<String, String> taxReturnLinks = collectTaxReturnLinks(quarterRange, wait);

			for (YearMonth month : ClientExcel.getMonthsForQuarterRange(quarterRange)) {
				String monthText = month.format(MONTH_FORMATTER);
				try {
					String taxReturnUrl = taxReturnLinks.get(monthText);
					if (taxReturnUrl == null) {
						continue;
					}

					driver.get(taxReturnUrl);
					String payableRefund = extractText(wait.until(d -> d.findElement(PAYABLE_REFUND)), "Payable/refund", "No Value");
					String amount = extractText(wait.until(d -> d.findElement(AMOUNT_PAYABLE)), "Amount", "No Value");
					payableByMonth.put(monthText, payableRefund);
					amountPayableByMonth.put(monthText, amount);
					driver.navigate().back();
					wait.until(d -> !d.findElements(TAX_RETURN_ROWS).isEmpty());
				} catch (Exception e) {
					System.out.println("Unable to process Period End " + monthText + ": " + e.getMessage());
					try {
						if (driver.getCurrentUrl().contains("Tax/Return")) {
							driver.navigate().back();
						}
					} catch (Exception recoveryException) {
						System.out.println("Unable to return to Tax Returns: " + recoveryException.getMessage());
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Unable to read Tax Returns: " + e.getMessage());
		}
		return new TaxReturnValues(payableByMonth, amountPayableByMonth);
	}

	private static Map<String, String> collectTaxReturnLinks(String quarterRange, WebDriverWait wait) {
		Map<String, String> linksByMonth = new HashMap<>();
		List<YearMonth> quarterMonths = ClientExcel.getMonthsForQuarterRange(quarterRange);
		boolean morePages = true;
		while (morePages) {
			for (WebElement row : driver.findElements(TAX_RETURN_ROWS)) {
				for (YearMonth month : quarterMonths) {
					if (findPeriodEndCell(row, month) != null) {
						List<WebElement> links = row.findElements(By.xpath(".//a[normalize-space()='AS']"));
						if (!links.isEmpty()) {
							String href = links.get(0).getAttribute("href");
							linksByMonth.putIfAbsent(month.format(MONTH_FORMATTER), href);
						}
					}
				}
			}

			List<WebElement> nextButtons = driver.findElements(NEXT_PAGE);
			if (nextButtons.isEmpty() || !nextButtons.get(0).isDisplayed()
					|| nextButtons.get(0).getAttribute("class").contains("x-item-disabled")) {
				morePages = false;
			} else {
				WebElement firstRow = driver.findElements(TAX_RETURN_ROWS).get(0);
				nextButtons.get(0).click();
				wait.until(ExpectedConditions.stalenessOf(firstRow));
			}
		}
		return linksByMonth;
	}

	private static WebElement findPeriodEndCell(WebElement row, YearMonth targetMonth) {
		List<WebElement> cells = row.findElements(By.xpath("./td"));
		return cells.size() > 1 && isPeriodEndMonth(cells.get(1).getText(), targetMonth) ? cells.get(1) : null;
	}

	private static class TaxReturnValues {
		private final Map<String, String> payableByMonth;
		private final Map<String, String> amountPayableByMonth;

		private TaxReturnValues(Map<String, String> payableByMonth, Map<String, String> amountPayableByMonth) {
			this.payableByMonth = payableByMonth;
			this.amountPayableByMonth = amountPayableByMonth;
		}
	}

	private static WebElement findTaxReturnRow(YearMonth targetMonth) {
		for (WebElement row : driver.findElements(TAX_RETURN_ROWS)) {
			if (!row.isDisplayed()) {
				continue;
			}

			WebElement table = row.findElement(By.xpath("./ancestor::table[1]"));
			List<WebElement> headers = table.findElements(By.xpath(".//th | .//*[@role='columnheader']"));
			int periodEndIndex = 1;
			for (int index = 0; index < headers.size(); index++) {
				if ("Period End".equalsIgnoreCase(headers.get(index).getText().trim())) {
					periodEndIndex = index;
					break;
				}
			}

			List<WebElement> cells = row.findElements(By.xpath("./td"));
			if (periodEndIndex < cells.size() && isPeriodEndMonth(cells.get(periodEndIndex).getText(), targetMonth)) {
				System.out.println("Matched Period End '" + targetMonth.format(MONTH_FORMATTER)
						+ "' in cell: " + cells.get(periodEndIndex).getText());
				return row;
			}
		}
		System.out.println("No Period End matched: " + targetMonth.format(MONTH_FORMATTER));
		return null;
	}

	private static boolean isPeriodEndMonth(String periodEndText, YearMonth targetMonth) {
		String normalizedText = periodEndText.replaceAll("\\s+", " ").trim();
		String targetMonthText = targetMonth.format(MONTH_FORMATTER);
		String fullMonthText = targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
		String lowerText = normalizedText.toLowerCase(Locale.ENGLISH);
		return lowerText.contains(targetMonthText.toLowerCase(Locale.ENGLISH))
				|| lowerText.contains(fullMonthText.toLowerCase(Locale.ENGLISH))
				|| Pattern.compile("\\b\\d{1,2}[/-]" + String.format("%02d", targetMonth.getMonthValue())
						+ "[/-]" + targetMonth.getYear() + "\\b").matcher(normalizedText).find()
				|| normalizedText.contains(targetMonth.getYear() + "-"
						+ String.format("%02d", targetMonth.getMonthValue()));
	}
	
	
	private static WebElement resolveClientNameElement() {
	    WebElement cell = driver.findElement(CLIENT_NAME_CELL_LOCATOR);
	    List<WebElement> links = cell.findElements(By.tagName("a"));
	    if (!links.isEmpty()) {
	        System.out.println("Found anchor inside cell — targeting the <a> tag.");
	        return links.get(0);
	    }
	    System.out.println("No anchor found inside cell — falling back to the <td> itself.");
	    return cell;
	}

	private static void clickOnClientName() {
	    int maxAttempts = 5;
	    System.out.println("Inside clickOnClientName method");

	    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
	        try {
	            System.out.println("Click attempt: " + attempt);

	            WebElement clientElement = resolveClientNameElement();
	            System.out.println("Element tag: " + clientElement.getTagName());

	            JavascriptExecutor js = (JavascriptExecutor) driver;
	            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", clientElement);
	            Thread.sleep(500);

	            try {
	                clientElement.click();
	            } catch (Exception e) {
	                System.out.println("Normal click failed. Trying JavaScript click.");
	                js.executeScript("arguments[0].click();", clientElement);
	            }

	            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
	            wait.until(d ->
	                d.getCurrentUrl().contains("app/clients")
	                || d.getCurrentUrl().contains("clientview.aspx")
	            );

	            System.out.println("Successfully opened Client page.");
	            System.out.println("Landed on URL: " + driver.getCurrentUrl());
	            return;

	        } catch (Exception e) {
	            System.out.println("Attempt " + attempt + " failed.");
	            System.out.println("Current URL: " + driver.getCurrentUrl());
	            if (attempt < maxAttempts) {
	                try {
	                    Thread.sleep(1000);
	                } catch (InterruptedException ie) {
	                    Thread.currentThread().interrupt();
	                    return;
	                }
	            }
	        }
	    }
	    System.out.println("❌ Could not open Client page after " + maxAttempts + " attempts.");
	}

	public static void switchPortalByPrefix(String input) throws InterruptedException {
		String xeroUrl = driver.getCurrentUrl();
		driver.get(xeroUrl);
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> d.getCurrentUrl().equals(xeroUrl));

		if (input != null && input.startsWith("KPF")) {
			switchportal2();
		} else {
			switchportal();
		}

		clickjoblinks(input);
		extractClientDetailsInJobPage();
		clickOnClientName();

		if (driver.getCurrentUrl().contains("clientview.aspx")) {
			extractClientDetailLegacy();
		} else {
			extractClientDetail();
			
		}
		
	}
	

}