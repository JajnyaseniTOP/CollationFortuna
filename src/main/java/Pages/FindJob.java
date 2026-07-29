package Pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jj.util.ClientExcel;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class FindJob extends MainClass {
	static ClientExcel creator = new ClientExcel();
	public static String extractedJobNo = "";
	public static String coverLetterNotes = "";
	public static String clientName = "";
	public static String internalteam = "";
	public static String clientnamecode = "";
	public static String groupName = "";

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
	@FindBy(xpath="//div[text()='Internal Team']/following-sibling::div[1]")
	private static WebElement internal_team;
	@FindBy(xpath="//h1[@class='xui-pageheading--title']")
	private static WebElement client_name_code;
	@FindBy(xpath="//span[normalize-space()='Groups']/following::span[contains(@class,'xui-pill--text')][1]")
	private static WebElement client_GroupName;

	private static final By CLIENT_NAME_CELL_LOCATOR =	By.xpath("//td[normalize-space()='Client:']/following-sibling::td[1]");

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

		extractedJobNo   = extractText(jobNo, "Job No.");
		coverLetterNotes = extractText(Cover_Letter_Notes, "Cover Letter Notes");
		clientName       = extractText(Client_Name, "Client Name");

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

		clientnamecode = extractText(client_name_code, "Client Name Code");
		groupName      = extractText(client_GroupName, "Client Group Name");
		internalteam   = extractText(internal_team, "Internal Team");

		creator.insertData(extractedJobNo, coverLetterNotes, currentDate, clientName, clientnamecode, groupName, internalteam);
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

		creator.insertData(extractedJobNo, coverLetterNotes, currentDate, clientName, clientnamecode, groupName, internalteam);
	}

	/*====================Helper: extract text with fallback to "No Value"===================================*/
	private static String extractText(WebElement element, String fieldLabelForLog) {
		try {
			String value = element.getText().trim();
			if (value.isEmpty()) {
				System.out.println(fieldLabelForLog + " was empty — defaulting to 'No Value'");
				return "No Value";
			}
			return value;
		} catch (Exception e) {
			System.out.println("Unable to extract " + fieldLabelForLog + ": " + e.getMessage());
			return "No Value";
		}
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