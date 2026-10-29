package Pages;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.google.common.collect.Table.Cell;
import com.jj.collation.JobCollator;
import com.jj.util.BasPeriodParser;
import com.jj.util.ClientExcel;
import com.jj.util.Dom;
import com.jj.util.DomCapture;
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
	/** The client's Business structure on XPM, e.g. "Individual", "Company", "Trust". */
	public static String businessStructure = "";
	public static String startQuarter = "Q4";
	public static String endQuarter = "Q3";
	public static String startingYear = "2026";
	public static String endingYear = "2027";
	public static String atoQuarter = "Not Found";
	public static boolean estimatedPositionPayable = false;

	/** The job's own name, which carries the BAS period it covers. */
	public static String jobName = "";

	/** The financial quarters this job collates, read from its name. */
	public static List<String> jobQuarters = new ArrayList<>();

	/** The ATO statement saved for this job, or null when the position is refundable. */
	public static java.nio.file.Path lastAtoStatement = null;
	public static String quarters = startQuarter + " " + startingYear + "-" + endQuarter + " " + endingYear;
//	public static String taxReturnpayble = "";
//	public static String taxamount = "";
	

	// The portal switchers, the Portal/Connect buttons and the Jobs nav buttons that
	// used to live here were all removed with the Xero redesign. Practice switching
	// now goes through TENANT_MENU_BUTTON and the job manager through JOBS_NAV.
	@FindBy(xpath="//input[@type='search' and @placeholder='Search']")
	private static WebElement searchbox;
	/*
	 * Two job page layouts are in circulation, so every field accepts both.
	 *
	 * The current one labels each field with a span and puts the value in the element
	 * straight after it — which is a readonly span for Job no., a bare span for the
	 * notes and an anchor for the Client, hence following-sibling::*[1] rather than
	 * anything more specific.
	 *
	 * The older one is a table of label cells followed by value cells.
	 */
	@FindBy(xpath="//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Job no.']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Job No.:']/following-sibling::td[1]")
	private static WebElement jobNo;
	@FindBy(xpath="//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Cover Letter Notes']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Cover Letter Notes:']/following-sibling::td[1]")
	private static WebElement Cover_Letter_Notes;
	/** Carries the BAS period, e.g. "BAS Q4 26 to Q3 27" or "Client Name Q3 27 BAS". */
	@FindBy(xpath="//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Job name']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Job Name:']/following-sibling::td[1]")
	private static WebElement Job_Name;
	@FindBy(xpath="//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Client']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Client:']/following-sibling::td[1]")
	private static WebElement Client_Name;
	
	
	// The tab now carries a count, e.g. "Tax Returns (3)".
	private static final By TAX_RETURNS_LINK = By.xpath("//a[starts-with(normalize-space(), 'Tax Returns')]");

	// Exact 'AS' only: 'contains' also matches BAS and IAS rows.
	private static final By TAX_RETURN_ROWS = By.xpath("//tr[.//a[normalize-space()='AS']]");

	// The paging control puts "Next" straight on the anchor now, not in a child span.
	private static final By NEXT_PAGE = By.xpath("//a[@role='button' and normalize-space()='Next']");

	// Box 9 of the activity statement — "Payment or Refund?" and "Amount payable".
	// These replace CSS selectors built on hashed class names, which changed with the
	// redesign and would change again on any Xero build.
	private static final By PAYABLE_REFUND = By.cssSelector("[data-automationid='box9_IsRefund']");
	private static final By AMOUNT_PAYABLE = By.cssSelector("[data-automationid='box9']");

	/*==================== Practice (org) switching ====================*/

	/**
	 * The practice switcher sits in whichever header rendered: the new SPA uses
	 * x-nav--tenant-menu-button, the legacy overview uses the xnav-appbutton.
	 */
	private static final By TENANT_MENU_BUTTON = By.xpath(
			"//button[contains(@class,'x-nav--tenant-menu-button')]"
			+ " | //button[@data-automationid='xnav-appbutton']");

	// The button inside the accordion is what toggles it; the li is the fallback.
	private static final By OTHER_PRACTICES = By.xpath(
			"//li[@aria-label='Other Practices']//button[normalize-space()='Other Practices']"
			+ " | //li[@aria-label='Other Practices']");

	private static final String FUT_PRACTICE = "Fortuna Unit Trust";
	private static final String FABA_PRACTICE = "Fortuna Accountants & Business Advisors";

	/*==================== Job manager ====================*/

	/**
	 * Two navigations reach the job list and which one you get depends on where the
	 * practice switch drops you: the new SPA (practicemanager.xero.com) uses an
	 * aria-labelled Jobs item leading to "Job manager", while the legacy overview
	 * (app.practicemanager.xero.com) uses a Jobs tab opening a dropdown whose first
	 * entry is also "Jobs". Both are accepted here.
	 */
	private static final By JOBS_NAV = By.xpath(
			"//li[@aria-label='Jobs']//button[normalize-space()='Jobs']"
			+ " | //button[contains(@class,'xnav-tab--body')][normalize-space()='Jobs']"
			+ " | //button[normalize-space()='Jobs']");
	private static final By JOB_MANAGER = By.xpath(
			"//*[self::a or self::button][normalize-space()='Job manager']"
			+ " | //a[contains(@class,'xnav-verticalmenuitem--body')][normalize-space()='Jobs']");
	/** Present once jobview.aspx has painted its fields, in either layout. */
	private static final By JOB_PAGE_READY = By.xpath(
			"//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Job no.']"
			+ " | //td[normalize-space()='Job No.:']");

	/** Every job starts from here, so the switcher always meets the same navigation. */
	private static final String PRACTICE_MANAGER_HOME = "https://practicemanager.xero.com/app/homepage";

	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
	
	
	// The value sits directly on the div — the redesign dropped the inner span.
	@FindBy(xpath="//div[@data-automationid='information-item-businessName--value']")
	private static WebElement client_name;
	@FindBy(xpath="//div[@data-automationid='information-item-clientReferenceCode--value']")
	private static WebElement client_code;

	@FindBy(xpath="//div[@data-automationid='information-item-business-structure--value']")
	private static WebElement client_BusinessStructure;

	@FindBy(xpath="//div[@data-automationid='group-item--group-container']//a[contains(@class,'xui-pill--content')]")
	private static WebElement client_GroupName;

	private static final By CLIENT_NAME_CELL_LOCATOR = By.xpath(
			"//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Client']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Client:']/following-sibling::td[1]");
	/**
	 * Internal Team is a custom field, so its data-automationid carries a GUID that
	 * differs between FABA and FUT. Find it by its label instead and take the value
	 * div beside it, which works in either practice.
	 */
	private static final By[] INTERNAL_TEAM_LOCATORS = {
		By.xpath("//div[contains(@data-automationid,'--label') and normalize-space()='Internal Team']"
				+ "/following-sibling::div[contains(@data-automationid,'--value')]"),
		By.xpath("//*[normalize-space()='Internal Team']/following::*[normalize-space() and normalize-space()!='-'][1]")
	};

	/** Fortuna Unit Trust t/as Keypoint — the FUT practice, used for KPF jobs. */
	public static void switchportal() throws InterruptedException {
		switchToPractice(FUT_PRACTICE);
	}

	/** Fortuna Accountants &amp; Business Advisors — the FABA practice, used for J jobs. */
	public static void switchportal2() throws InterruptedException {
		switchToPractice(FABA_PRACTICE);
	}

	/**
	 * Switches practice through the redesigned top navigation: open the practice
	 * menu, expand "Other Practices", pick the one wanted.
	 *
	 * <p>The old Portal/Connect buttons are gone from this nav entirely.
	 */
	public static void switchToPractice(String practiceName) throws InterruptedException {
		Thread.sleep(2000);

		WebDriverWait navWait = new WebDriverWait(driver, Duration.ofSeconds(20));

		try {
			WebElement menuButton = navWait.until(ExpectedConditions.elementToBeClickable(TENANT_MENU_BUTTON));

			// Read the header through shadow roots. getText() stops at the boundary, so
			// on the SPA it returns nothing, the check never matches, and the run tries
			// to switch to the practice it is already in — which is how J010726 broke.
			String currentPractice = deepTextOf(menuButton);
			System.out.println("Practice header reads: '" + currentPractice + "'");

			if (alreadyInPractice(currentPractice, practiceName)) {
				System.out.println("Already in " + practiceName + " — no switch needed.");
				return;
			}

			System.out.println("Switching practice to: " + practiceName);
			menuButton.click();
			Thread.sleep(1500);

			// "Other Practices" is usually open already, showing the practice beneath it.
			// Try to click it first and only touch the toggle if it genuinely is not on
			// screen — toggling an open section closes it and hides the very entry we
			// came for, which is what made this fail for FUT.
			if (clickPracticeInMenu(practiceName, true)) {
				Thread.sleep(3000);
				System.out.println("Practice after switch: " + driver.getCurrentUrl());
				return;
			}

			System.out.println(practiceName + " not on screen — expanding Other Practices.");
			List<WebElement> accordion = driver.findElements(OTHER_PRACTICES);
			if (!accordion.isEmpty()) {
				clickWithJs(accordion.get(0));
				Thread.sleep(1500);
			}

			if (clickPracticeInMenu(practiceName, true) || clickPracticeInMenu(practiceName, false)) {
				Thread.sleep(3000);
				System.out.println("Practice after switch: " + driver.getCurrentUrl());
				return;
			}

			System.out.println("'" + practiceName + "' was not in the practice menu. It offered:");
			System.out.println(listPracticeMenu());

		} catch (Exception e) {
			System.out.println("Unable to switch practice to " + practiceName + ": " + e.getMessage());
		}
	}

	/**
	 * Clicks the entry for a practice anywhere inside the open practice menu.
	 *
	 * <p>Menu entries live in the DOM before the menu animates open, so Selenium never
	 * sees them become clickable — hence the JavaScript click. Their text is read
	 * through shadow roots, and the button that opened the menu is excluded so the
	 * menu is not simply closed again.
	 */
	private static boolean clickPracticeInMenu(String practiceName, boolean visibleOnly) {
		String script = Dom.DEEP_TEXT
				+ "const wanted=arguments[0].toLowerCase();"
				+ "const visibleOnly=arguments[1];"
				+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim().toLowerCase();"
				+ "let found=null;"
				+ "function walk(root){"
				+ "if(found)return;"
				+ "for(const el of root.querySelectorAll(\"a,button,[role='link'],[role='menuitem']\")){"
				+ "if(visibleOnly && el.getClientRects().length===0)continue;"
				+ "if(el.classList && el.className && String(el.className).includes('tenant-menu-button'))continue;"
				+ "if(el.getAttribute('data-automationid')==='xnav-appbutton')continue;"
				// The menu repeats the practice you are already in, as a heading that
				// goes nowhere. Clicking it looks like a successful switch and is not.
				+ "if(el.closest && el.closest(\"[class*='tenant-menu-primary-items'],[class*='nav-item-header']\"))continue;"
				+ "if(clean(deepText(el)).includes(wanted)){found=el;return;}}"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot){walk(el.shadowRoot);if(found)return;}"
				+ "}walk(document);"
				+ "if(!found)return false;"
				+ "found.scrollIntoView({block:'center'});"
				+ "found.click();"
				+ "return true;";

		Object clicked = ((JavascriptExecutor) driver).executeScript(script, practiceName, visibleOnly);
		return Boolean.TRUE.equals(clicked);
	}

	/** What the practice menu is offering, for when the wanted practice is not in it. */
	private static String listPracticeMenu() {
		try {
			String script = Dom.DEEP_TEXT
					+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
					+ "const out=[];"
					+ "function walk(root){"
					+ "for(const el of root.querySelectorAll(\"a,button,[role='link'],[role='menuitem']\")){"
					+ "const t=clean(deepText(el));"
					+ "if(t && t.toLowerCase().includes('fortuna'))out.push('  - '+t.slice(0,90));}"
					+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)walk(el.shadowRoot);}"
					+ "walk(document);"
					+ "return out.length ? out.join('\\n') : '  (nothing mentioning Fortuna in the menu)';";

			return String.valueOf(((JavascriptExecutor) driver).executeScript(script));
		} catch (Exception e) {
			return "  (could not read the menu: " + e.getMessage() + ")";
		}
	}

	/**
	 * Whether the header already shows the practice we want.
	 *
	 * <p>Not a plain contains: the legacy header truncates long names to
	 * "Fortuna Accountants &amp; Business…", while the new one prefixes the avatar
	 * initials, as in "FABFortuna Accountants &amp; Business Advisors". Comparing both
	 * ways round covers the pair.
	 */
	private static boolean alreadyInPractice(String buttonText, String practiceName) {
		if (buttonText == null) {
			return false;
		}

		String shown = buttonText.replace("…", "").replace("...", "").trim().toLowerCase(Locale.ENGLISH);
		String wanted = practiceName.trim().toLowerCase(Locale.ENGLISH);

		if (shown.isEmpty()) {
			return false;
		}
		return shown.contains(wanted) || wanted.startsWith(shown);
	}

	/**
	 * Waits for the job detail table, reloading the job page between attempts.
	 *
	 * <p>jobview.aspx can settle showing only the Xero navigation shell: the correct
	 * job, the correct URL and title, but no detail table. More waiting never brings
	 * it; a reload does.
	 */
	private static void awaitJobDetail() throws InterruptedException {
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(15))
						.until(ExpectedConditions.presenceOfElementLocated(JOB_PAGE_READY));
				return;
			} catch (TimeoutException e) {
				System.out.println("Job detail not painted (attempt " + attempt + " at "
						+ driver.getCurrentUrl() + ") — reloading.");
				driver.navigate().refresh();
				Thread.sleep(2000);
			}
		}

		// Let the caller's own wait raise the failure, with the page captured for it.
		DomCapture capture = new DomCapture();
		capture.capture(driver, "job-detail-never-painted");
		throw new IllegalStateException("The job page never showed its detail table at "
				+ driver.getCurrentUrl() + ". Captured to: " + capture.getFolder());
	}

	/** Switches to a Xero tab, leaving the ATO tab and its approved session alone. */
	private static void focusXeroWindow() {
		try {
			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				String url = driver.getCurrentUrl();
				if (url.contains("xero.com") && !url.contains("ato.gov.au")) {
					return;
				}
			}
		} catch (Exception e) {
			System.out.println("Could not pick a Xero tab: " + e.getMessage());
		}
	}

	/** An element's text including anything inside its shadow roots. */
	private static String deepTextOf(WebElement element) {
		try {
			Object text = ((JavascriptExecutor) driver).executeScript(
					Dom.DEEP_TEXT + "return deepText(arguments[0]).replace(/\\s+/g,' ').trim();", element);
			return text == null ? "" : String.valueOf(text);
		} catch (Exception e) {
			return element.getText() == null ? "" : element.getText();
		}
	}

	private static boolean isDisplayed(By locator) {
		for (WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private static void clickWithJs(WebElement element) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (Exception e) {
			js.executeScript("arguments[0].click();", element);
		}
	}

	/** Quotes a value for use inside an XPath expression, apostrophes included. */
	private static String xpathLiteral(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		String[] parts = value.split("'", -1);
		StringBuilder literal = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[index]).append("'");
		}
		return literal.append(")").toString();
	}

	public FindJob() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}

	/**
	 * Opens the job manager, searches for the job and opens it.
	 *
	 * <p>The job is opened by matching the job number itself rather than the table's
	 * link class, so a build-generated class name cannot break it.
	 */
	public static void clickjoblinks(String jobnumber) throws InterruptedException {
		try {
			clickWithJs(searchJobList(jobnumber));
		} catch (TimeoutException notInJobList) {
			// Closed jobs are not in Job manager's list; the search at the top right finds them.
			openJobFromGlobalSearch(jobnumber);
		}

		// Wait for the job page to paint. Reading the fields on a half-loaded page is
		// what made Job No., Cover Letter Notes and Client all come back empty.
		//
		// The page sometimes stops at the Xero navigation shell with the job detail
		// never arriving — the right job, the right URL, no table. Waiting longer does
		// not help; reloading does, the same as the ATO agent home.
		awaitJobDetail();
		System.out.println("Job open at: " + driver.getCurrentUrl());
	}

	/**
	 * Opens the job manager and searches for the job.
	 *
	 * @return the job number's link in the results, not yet clicked
	 */
	private static WebElement searchJobList(String jobnumber) throws InterruptedException {
		WebDriverWait navWait = new WebDriverWait(driver, Duration.ofSeconds(20));

		// Clicked through JavaScript: the dropdown entries sit in the DOM before the
		// menu opens, so Selenium never sees them become clickable.
		clickWithJs(navWait.until(ExpectedConditions.presenceOfElementLocated(JOBS_NAV)));
		Thread.sleep(1000);
		clickWithJs(navWait.until(ExpectedConditions.presenceOfElementLocated(JOB_MANAGER)));
		Thread.sleep(2000);
		System.out.println("Job list open at: " + driver.getCurrentUrl());

		WebElement search = navWait.until(ExpectedConditions.elementToBeClickable(searchbox));
		search.click();
		search.clear();
		search.sendKeys(jobnumber);
		Thread.sleep(2000);

		By jobLink = By.xpath("//a[normalize-space()=" + xpathLiteral(jobnumber) + "]");
		return navWait.until(ExpectedConditions.presenceOfElementLocated(jobLink));
	}

	/**
	 * Reads the job's Name from the job manager's search results — "2026 Income Tax
	 * Return", "Client Name Q3 27 BAS" — which is what decides BAS or ITR.
	 *
	 * <p>The Name cell is found by its column header rather than by position, so a
	 * column added or moved in the list cannot shift it. If the header cannot be
	 * matched, the whole row's text is returned instead and the log says so.
	 */
	public static String readJobNameFromJobList(String input) throws InterruptedException {
		focusXeroWindow();

		driver.get(PRACTICE_MANAGER_HOME);
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(
				d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		if (input != null && input.toUpperCase(Locale.ENGLISH).startsWith("KPF")) {
			switchportal();
		} else {
			switchportal2();
		}

		WebElement jobLink;
		try {
			jobLink = searchJobList(input);
		} catch (TimeoutException notInJobList) {
			// A closed job is not in Job manager's list: open it from the top-right search
			// and read the name off the job page instead - the same text as the Name column.
			openJobFromGlobalSearch(input);
			String name = extractText(Job_Name, "Job Name", "");
			System.out.println(">>> " + input + " job name (from the job page): " + name);
			return name;
		}

		String script =
				"const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
				+ "const row=arguments[0].closest(\"tr,[role='row']\");"
				+ "if(!row)return null;"
				+ "const cells=[...row.querySelectorAll(\"td,[role='cell'],[role='gridcell']\")]"
				+ ".filter(c=>c.closest(\"tr,[role='row']\")===row);"
				+ "const grid=row.closest(\"table,[role='grid'],[role='table']\");"
				+ "const headers=grid?[...grid.querySelectorAll(\"th,[role='columnheader']\")]:[];"
				+ "const index=headers.findIndex(h=>/^name\\b/i.test(clean(h.textContent)));"
				+ "if(index>=0&&index<cells.length)return clean(cells[index].textContent);"
				+ "return '\\u0000'+clean(row.textContent);";

		Object result = ((JavascriptExecutor) driver).executeScript(script, jobLink);
		String name = result == null ? "" : String.valueOf(result);

		if (name.startsWith("\u0000")) {
			name = name.substring(1);
			System.out.println("!! No Name column header found — deciding from the whole row: " + name);
		}
		System.out.println(">>> " + input + " job name: " + name);
		return name;
	}

	/** True when the page in front is this job's own page, its Job no. showing the job number. */
	private static boolean isJobPageOpen(String jobnumber) {
		try {
			if (!driver.getCurrentUrl().contains("jobview.aspx") || driver.findElements(JOB_PAGE_READY).isEmpty()) {
				return false;
			}
			return jobnumber != null && jobnumber.trim().equalsIgnoreCase(jobNo.getText().trim());
		} catch (Exception e) {
			return false;
		}
	}

	/*====================Closed jobs: the search at the top right===================================*/

	/** The magnifier in the top navigation's tools. */
	private static final By GLOBAL_SEARCH_BUTTON = By.id("x-nav--global-tool-button-global-tools-search");


	/**
	 * Opens a job through Xero's own search - the magnifier at the right of the top
	 * navigation - for jobs Job manager's list does not show, such as closed jobs.
	 *
	 * <p>Used only after the Job manager search has come up empty; that search is
	 * unchanged. The navigation is read through shadow roots, so the magnifier is found
	 * by its label ("Search") and its place on the right of the header rather than by a
	 * class name, and the result is the first visible item carrying the job number.
	 */
	private static void openJobFromGlobalSearch(String jobnumber) throws InterruptedException {
		System.out.println(jobnumber + " is not in Job manager's list (closed job?) — trying the search at the top right.");
		clickGlobalSearchResult(jobnumber);
		awaitJobDetail();
		System.out.println("Job open (from the top-right search) at: " + driver.getCurrentUrl());
	}

	/**
	 * The magnifier search itself: opens the Search panel, types the text once, presses
	 * Enter and clicks the first result carrying it (case ignored). Leaves the browser on
	 * whatever page the result opens - a job or a client.
	 */
	private static void clickGlobalSearchResult(String jobnumber) throws InterruptedException {
		String findSearchButton = Dom.DEEP_TEXT
				+ "const clean=v=>(v||'').toLowerCase();"
				+ "let found=null;"
				+ "function walk(root){"
				+ "if(found)return;"
				+ "for(const el of root.querySelectorAll(\"button,a,[role='button']\")){"
				+ "const r=el.getBoundingClientRect();"
				+ "if(r.width===0||r.top>140||r.left<window.innerWidth/2)continue;"
				+ "const label=clean(el.getAttribute('aria-label'))+' '+clean(el.getAttribute('title'))+' '+clean(el.getAttribute('data-automationid'))+' '+clean(deepText(el));"
				+ "if(label.includes('search')){found=el;return;}}"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot){walk(el.shadowRoot);if(found)return;}}"
				+ "walk(document);return found;";

		// The magnifier has a fixed id; the label search is the fallback
		WebElement searchButton;
		try {
			searchButton = new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
				List<WebElement> byId = d.findElements(GLOBAL_SEARCH_BUTTON);
				return !byId.isEmpty() ? byId.get(0)
						: (WebElement) ((JavascriptExecutor) d).executeScript(findSearchButton);
			});
		} catch (TimeoutException e) {
			throw globalSearchFailure("no search button at the top right", jobnumber);
		}
		clickWithJs(searchButton);
		Thread.sleep(1500);

		// The search box in the Search panel on the right. It is drawn inside a shadow root, and
		// Job manager's own search box sits at the same height on the left, so only an input in
		// the right-hand part of the screen counts.
		String findInput =
				"let found=null;"
				+ "function walk(root){"
				+ "if(found)return;"
				+ "for(const i of root.querySelectorAll(\"input[type='search'],input[type='text'],input:not([type])\")){"
				+ "const r=i.getBoundingClientRect();"
				+ "if(r.width>0&&r.height>0&&r.left>window.innerWidth*0.6){found=i;return;}}"
				+ "for(const e of root.querySelectorAll('*'))if(e.shadowRoot){walk(e.shadowRoot);if(found)return;}}"
				+ "walk(document);return found;";

		// The search box in the Search panel that opens on the right
		WebElement input = null;
		try {
			input = new WebDriverWait(driver, Duration.ofSeconds(5))
					.until(d -> (WebElement) ((JavascriptExecutor) d).executeScript(findInput));
		} catch (TimeoutException e) {
			// The panel is drawn in its own embedded frame, out of reach of the page's
			// lookups - but it opens with the cursor already in its search box.
		}

		// Clear the box first - a box that keeps its last value turned the number into
		// "KPF014528KPF014528" - then type the job number once and press Enter.
		org.openqa.selenium.Keys selectAll = org.openqa.selenium.Keys.CONTROL;
		if (input != null) {
			input.click();
			input.sendKeys(org.openqa.selenium.Keys.chord(selectAll, "a"), org.openqa.selenium.Keys.DELETE);
			input.sendKeys(jobnumber, org.openqa.selenium.Keys.ENTER);
			System.out.println("Top-right search for: " + input.getAttribute("value"));
		} else {
			new org.openqa.selenium.interactions.Actions(driver)
					.keyDown(selectAll).sendKeys("a").keyUp(selectAll).sendKeys(org.openqa.selenium.Keys.DELETE)
					.sendKeys(jobnumber).sendKeys(org.openqa.selenium.Keys.ENTER)
					.perform();
			System.out.println("Top-right search for: " + jobnumber + " (typed into the panel's focused search box)");
		}
		Thread.sleep(3000);

		// The first visible result carrying the job number
		String findResult = Dom.DEEP_TEXT
				+ "const want=arguments[0].toLowerCase();"
				+ "let found=null;"
				+ "function walk(root){"
				+ "if(found)return;"
				+ "for(const el of root.querySelectorAll(\"a,button,[role='option'],[role='link'],[role='menuitem'],li\")){"
				+ "if(el.tagName==='INPUT')continue;"
				+ "const r=el.getBoundingClientRect();"
				+ "if(r.width===0||r.height===0||r.left<window.innerWidth*0.6)continue;"
				+ "if((deepText(el)||'').toLowerCase().includes(want)){found=el;return;}}"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot){walk(el.shadowRoot);if(found)return;}}"
				+ "walk(document);return found;";

		// The job link in the results: a link in the Search panel carrying the job number first
		By panelLink = By.xpath("//aside[@id='wac-right-panel']//a[contains(normalize-space(), "
				+ xpathLiteral(jobnumber) + ")]");
		Set<String> windowsBefore = new HashSet<>(driver.getWindowHandles());

		WebElement result = null;
		try {
			result = new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
				for (WebElement link : d.findElements(panelLink)) {
					if (link.isDisplayed()) {
						return link;
					}
				}
				return (WebElement) ((JavascriptExecutor) d).executeScript(findResult, jobnumber);
			});
		} catch (TimeoutException e) {
			// Not on the page itself: look in the panel's embedded frame
		}

		if (result != null) {
			System.out.println("Clicking search result: " + deepTextOf(result));
			clickWithJs(result);
		} else if (!clickJobLinkInFrames(jobnumber)) {
			throw globalSearchFailure("no search result for the job number", jobnumber);
		}
		Thread.sleep(2000);

		Set<String> opened = new HashSet<>(driver.getWindowHandles());
		opened.removeAll(windowsBefore);
		if (!opened.isEmpty()) {
			driver.switchTo().window(opened.iterator().next());
		}
	}

	/**
	 * Clicks the job link inside the Search panel's embedded frame: the blue job number
	 * at the top of the results ("KPF014528 / 2025 Accounts - Brunton Group").
	 *
	 * <p>Every frame on the page is tried, those inside shadow roots included, for up to
	 * 20 seconds while the results load. The driver is always returned to the page.
	 *
	 * @return true once the link has been clicked
	 */
	private static boolean clickJobLinkInFrames(String jobnumber) throws InterruptedException {
		String collectFrames =
				"const out=[];"
				+ "function walk(root){"
				+ "for(const f of root.querySelectorAll('iframe,frame'))out.push(f);"
				+ "for(const e of root.querySelectorAll('*'))if(e.shadowRoot)walk(e.shadowRoot);}"
				+ "walk(document);return out;";
		// Case ignored: a company is written "Grass Tree Landscaping Pty Ltd" in one place and
		// "GRASS TREE LANDSCAPING PTY LTD" in another
		String lower = "translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')";
		String wanted = xpathLiteral(jobnumber.toLowerCase(Locale.ENGLISH));
		By link = By.xpath("//a[contains(" + lower + ", " + wanted + ")]"
				+ " | //*[@role='link' or @role='option' or self::button][contains(" + lower + ", " + wanted + ")]");

		long deadline = System.currentTimeMillis() + 20000;
		while (System.currentTimeMillis() < deadline) {
			@SuppressWarnings("unchecked")
			List<WebElement> frames = (List<WebElement>) ((JavascriptExecutor) driver).executeScript(collectFrames);
			for (WebElement frame : frames) {
				try {
					driver.switchTo().frame(frame);
					for (WebElement candidate : driver.findElements(link)) {
						if (candidate.isDisplayed()) {
							System.out.println("Clicking search result (in the panel's frame): " + candidate.getText());
							candidate.click();
							driver.switchTo().defaultContent();
							return true;
						}
					}
				} catch (Exception e) {
					// A frame that went away or cannot be entered: try the next one
				} finally {
					driver.switchTo().defaultContent();
				}
			}
			Thread.sleep(1000);
		}
		return false;
	}

	/*====================Trust AGM minutes: a company's ACN and address===================================*/

	/** A company's details from its XPM client page, for an AGM minute. */
	public static final class CompanyDetails {
		public final String name;
		/** "143 646 339"; from the ABN without its first two digits when XPM has no ACN. */
		public final String acn;
		/** The street address under Contact details, e.g. "14 Contour Rd, ROLEYSTONE, WA, 6111, Australia". */
		public final String address;

		CompanyDetails(String name, String acn, String address) {
			this.name = name;
			this.acn = acn;
			this.address = address;
		}
	}

	/**
	 * Finds a company in Xero with the search at the top right and reads its ACN and
	 * address off its client page (scope 4.7). If the search opens one of the company's
	 * jobs instead, the client is opened from the job.
	 *
	 * <p>The ACN is the client page's ACN field; when XPM shows none, the ABN without its
	 * first two digits, which is the same number. The address is the client's street (or
	 * else first) address under Contact details on the right.
	 *
	 * @return the details, or null when the company could not be opened in Xero
	 */
	public static CompanyDetails readCompanyFromXero(String companyName) {
		try {
			focusXeroWindow();
			driver.get(PRACTICE_MANAGER_HOME);
			new WebDriverWait(driver, Duration.ofSeconds(30)).until(
					d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

			System.out.println("Looking up " + companyName + " in Xero for its AGM minute.");
			clickGlobalSearchResult(companyName);
			Thread.sleep(3000);

			if (driver.getCurrentUrl().contains("jobview.aspx")) {
				clickOnClientName();
			}

			WebDriverWait pageWait = new WebDriverWait(driver, Duration.ofSeconds(30));
			pageWait.until(d -> !d.findElements(By.xpath(
					"//div[@data-automationid='information-item-abn--value' or @data-automationid='information-item-acn--value']")).isEmpty());
			Thread.sleep(1000);

			String acn = digitsOf(textOf(By.xpath("//div[@data-automationid='information-item-acn--value']")));
			if (acn.length() != 9) {
				String abn = digitsOf(textOf(By.xpath("//div[@data-automationid='information-item-abn--value']")));
				acn = abn.length() == 11 ? abn.substring(2) : "";
				System.out.println("  no ACN in XPM — taken from the ABN: " + acn);
			}
			if (acn.length() == 9) {
				acn = acn.substring(0, 3) + " " + acn.substring(3, 6) + " " + acn.substring(6);
			}

			String address = textOf(By.xpath(
					"//div[@data-automationid='client-details--client-sidebar-address--Street address']"));
			if (address.isEmpty()) {
				address = textOf(By.xpath("//div[starts-with(@data-automationid,'client-details--client-sidebar-address')]"));
			}

			System.out.println("  " + companyName + ": ACN '" + acn + "', address '" + address + "'");
			return new CompanyDetails(companyName, acn, address);

		} catch (Exception e) {
			System.out.println("!! Could not read " + companyName + " from Xero: " + e.getMessage());
			return null;
		}
	}

	private static String textOf(By locator) {
		List<WebElement> found = driver.findElements(locator);
		return found.isEmpty() ? "" : found.get(0).getText().replace("Copy to clipboard", "").trim();
	}

	private static String digitsOf(String text) {
		return text == null ? "" : text.replaceAll("\\D", "");
	}

	/** Saves the page and builds the error, naming the step of the top-right search that failed. */
	private static IllegalStateException globalSearchFailure(String what, String jobnumber) {
		DomCapture capture = new DomCapture();
		capture.capture(driver, "global-search-" + jobnumber);
		// The panel's results do not all show in the saved page, so keep a picture of the screen too
		try {
			byte[] png = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
			java.nio.file.Files.write(capture.getFolder().resolve("global-search-" + jobnumber + ".png"), png);
		} catch (Exception e) {
			System.out.println("  could not save a screenshot: " + e.getMessage());
		}
		return new IllegalStateException("Could not open " + jobnumber + " from the top-right search: " + what
				+ ". Page captured to: " + capture.getFolder());
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
		jobName          = extractText(Job_Name, "Job Name", "");

		// The BAS period comes from the job's own name — one quarter, a range of them,
		// or a whole financial year — rather than a period fixed in the code.
		jobQuarters = BasPeriodParser.quartersFrom(jobName);
		System.out.println(">>> Job name: " + jobName);
		System.out.println(">>> BAS period: " + BasPeriodParser.describe(jobQuarters));

		if (jobQuarters.isEmpty()) {
			jobQuarters = BasPeriodParser.quartersFrom(basPeriod());
			System.out.println("!! No period in the job name — falling back to the configured "
					+ basPeriod() + ". Check this job.");
		}

		// Provisional only — the tax returns overwrite this in extractClientDetail().
		estimatedPositionPayable = isEstimatedPositionPayable(coverLetterNotes);
		System.out.println(">>> Estimated position payable (from Cover Letter Notes): " + estimatedPositionPayable);

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
		businessStructure = extractText(client_BusinessStructure, "Business Structure", "No Value");
		System.out.println(">>> Extracted Internal Team: '" + internalteam + "'");
		System.out.println(">>> Extracted Business Structure: '" + businessStructure + "'");
//		taxReturn.click();
//		itr.click();
//		
//		taxReturnpayble   = extractText(paymentpayble, "Tax Return", "No Value");
//		taxamount   = extractText(amount, "value", "No Value");
		
		
		

		lastTaxReturnValues = findTaxReturns(jobName);

		estimatedPositionPayable = isTaxPositionPayable(lastTaxReturnValues.payableByMonth, estimatedPositionPayable);
		System.out.println(">>> Estimated position payable (from tax returns): " + estimatedPositionPayable);
	}

	/*====================Writing the ClientData rows===================================*/

	/** The BAS period this run collates, e.g. "Q4 2026-Q3 2027". */
	public static String basPeriod() {
		return startQuarter + " " + startingYear + "-" + endQuarter + " " + endingYear;
	}

	/** Tax return figures for the current job, held until the ATO leg has run. */
	private static TaxReturnValues lastTaxReturnValues =
			new TaxReturnValues(Collections.emptyMap(), Collections.emptyMap());

	/**
	 * Writes this job's rows into ClientData.xls.
	 *
	 * <p>Deliberately not called from the extraction methods: the number of rows
	 * depends on the ATO lodgement cycle (twelve monthly rows or four quarterly
	 * ones), so the row write has to wait until the ATO leg has read that cycle.
	 */
	public static void writeClientDataRows() {
		if (extractedJobNo == null || extractedJobNo.trim().isEmpty() || "No Job".equals(extractedJobNo)) {
			System.out.println("No job number was extracted — not writing any ClientData rows.");
			return;
		}

		// Rows with no figures at all mean the tax returns were never read — a silent
		// gap in the sheet that the run would otherwise report as a success.
		boolean anyFigures = false;
		for (String value : lastTaxReturnValues.payableByMonth.values()) {
			if (value != null && !value.trim().isEmpty() && !"No Value".equalsIgnoreCase(value.trim())) {
				anyFigures = true;
				break;
			}
		}
		if (!anyFigures) {
			System.out.println("!! " + extractedJobNo + " (" + clientnamecode + ") is being written with no"
					+ " Payable or Amount figures — its tax returns could not be read. Check this job.");
		}

		// The job's own name carries its period, so pass that rather than a fixed range.
		creator.insertData(extractedJobNo, atoQuarter, coverLetterNotes, currentDate, clientName, clientnamecode,
				cientCode, groupName, internalteam, jobName, lastTaxReturnValues.payableByMonth,
				lastTaxReturnValues.amountPayableByMonth, businessStructure);

		collateIntoClientFolders();
	}

	/**
	 * Copies this job's documents into the client's folders on the practice share.
	 *
	 * <p>Runs straight after the rows so the sheet and the folders are built from the
	 * same periods. The ATO statement is only included when the estimated position is
	 * payable; a refundable job still gets its filed statement and cover letter.
	 */
	private static void collateIntoClientFolders() {
		try {
			java.nio.file.Path atoStatement = estimatedPositionPayable ? lastAtoStatement : null;

			if (estimatedPositionPayable && atoStatement == null) {
				System.out.println("!! Position is payable but no ATO statement was saved — collating without it.");
			}

			JobCollator.CollationOutcome outcome = new JobCollator().collate(
					extractedJobNo, clientnamecode, cientCode, groupName, internalteam,
					ClientExcel.collationTargets(jobName, atoQuarter,
							lastTaxReturnValues.payableByMonth, lastTaxReturnValues.amountPayableByMonth),
					atoStatement);

			System.out.println("Collation for " + extractedJobNo + ": " + outcome);

		} catch (Exception e) {
			// The rows are already saved; a collation problem should not lose them.
			System.out.println("!! Collation failed for " + extractedJobNo + ": " + e.getMessage());
		}
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
		businessStructure = "No Value";
//		taxReturnpayble   = "No Value";
//		taxamount   = "No Value";

		// No tax returns on the legacy page, so the Cover Letter Notes stay the only
		// read on the estimated position. Rows are written after the ATO leg.
		lastTaxReturnValues = new TaxReturnValues(Collections.emptyMap(), Collections.emptyMap());
		
	}
	



	/*====================Is the estimated tax position payable?===================================*/

	/**
	 * The authoritative estimated tax position comes off the XPM tax returns — these
	 * are the values written to columns P/Q of ClientData.xls, and the filesearch
	 * project builds the cover letter's "$1,676.00 Payable" wording from exactly
	 * those two columns. The Cover Letter Notes are only a fallback, used when no
	 * tax return could be read at all.
	 *
	 * @param fallback what the Cover Letter Notes suggested
	 */
	private static boolean isTaxPositionPayable(Map<String, String> payableByMonth, boolean fallback) {
		boolean sawValue = false;
		for (String value : payableByMonth.values()) {
			if (value == null || value.trim().isEmpty()
					|| "No Value".equalsIgnoreCase(value.trim())
					|| "Not Found".equalsIgnoreCase(value.trim())) {
				continue;
			}
			sawValue = true;
			// Box 9 words this as "Payment" or "Refund"; older wording said "Amount payable".
			String lower = value.toLowerCase(Locale.ENGLISH);
			if ((lower.contains("payable") || lower.contains("payment")) && !lower.contains("refund")) {
				return true;
			}
		}
		return sawValue ? false : fallback;
	}

	/*====================Cover Letter Notes fallback===================================*/

	private static final Pattern ESTIMATED_POSITION = Pattern.compile(
			"(?is)estimated\\s+(?:tax\\s+)?position\\b(.{0,160})");

	/**
	 * True when the Cover Letter Notes quote an estimated position that is payable
	 * (as opposed to a refund). The ATO statement download only runs in that case.
	 */
	public static boolean isEstimatedPositionPayable(String notes) {
		if (notes == null || notes.trim().isEmpty()) {
			return false;
		}

		String scope;
		Matcher matcher = ESTIMATED_POSITION.matcher(notes);
		if (matcher.find()) {
			scope = matcher.group(1);
		} else if (notes.toLowerCase(Locale.ENGLISH).contains("estimat")) {
			scope = notes;
		} else {
			return false;
		}

		String lower = scope.toLowerCase(Locale.ENGLISH);
		int payableAt = lower.indexOf("payable");
		if (payableAt < 0) {
			return false;
		}
		int refundAt = lower.indexOf("refund");
		return refundAt < 0 || payableAt < refundAt;
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

	/**
	 * Reads an element's text even when it is not rendered.
	 *
	 * <p>The activity statement keeps its box 9 values in a summary panel that stays
	 * in the DOM but out of view, and getText() returns nothing for anything hidden.
	 */
	/** Captured once per run, so a twelve-month loop cannot fill the disk. */
	private static boolean taxReturnPageCaptured = false;

	private static String extractTextContent(By locator, String fieldLabelForLog, String defaultValue) {
		try {
			WebElement element = new WebDriverWait(driver, Duration.ofSeconds(40)).until(d -> {
				List<WebElement> found = d.findElements(locator);
				return found.isEmpty() ? null : found.get(0);
			});

			String value = String.valueOf(((JavascriptExecutor) driver).executeScript(
					"return (arguments[0].textContent||'').replace(/\\s+/g,' ').trim();", element));

			if (value.isEmpty()) {
				System.out.println(fieldLabelForLog + " was empty — defaulting to '" + defaultValue + "'");
				return defaultValue;
			}
			System.out.println(fieldLabelForLog + ": " + value);
			return value;
		} catch (Exception e) {
			System.out.println("Unable to extract " + fieldLabelForLog + " at " + driver.getCurrentUrl());
			if (!taxReturnPageCaptured) {
				taxReturnPageCaptured = true;
				com.jj.util.DomCapture capture = new com.jj.util.DomCapture();
				capture.capture(driver, "tax-return-" + fieldLabelForLog.replace(' ', '-'));
				System.out.println("Tax return page captured to: " + capture.getFolder());
			}
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

	/**
	 * Reads Payment/Refund and Amount off each activity statement in the BAS period.
	 *
	 * <p>Returns are opened by <em>clicking</em> the AS link in the grid rather than
	 * by navigating to its href. Navigating directly returned 403.aspx on a run where
	 * the practice had not been actively switched, because the href alone does not
	 * carry the practice context the grid link establishes. Clicking is also simply
	 * what a person does.
	 */
	private static TaxReturnValues findTaxReturns(String jobNameOrPeriod) throws InterruptedException {
		String quarterRange = jobNameOrPeriod;
		Map<String, String> payableByMonth = new HashMap<>();
		Map<String, String> amountPayableByMonth = new HashMap<>();
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

			// The Tax Returns grid is sometimes slow. Giving up on the first timeout
			// costs the client every figure it has, so try again rather than write a
			// set of blank rows.
			if (!openTaxReturnsList(wait)) {
				System.out.println("!! Tax Returns never loaded for this client — its rows will "
						+ "have no Payable or Amount figures.");
				return new TaxReturnValues(payableByMonth, amountPayableByMonth);
			}

			String returnsUrl = driver.getCurrentUrl();
			System.out.println("Tax returns list at: " + returnsUrl);

			// Every month of every quarter the job covers. A quarterly client only has
			// returns at the quarter-end months, and those are the ones that match.
			for (YearMonth month : BasPeriodParser.monthsFrom(quarterRange)) {
				String monthText = month.format(MONTH_FORMATTER);
				try {
					if (!openTaxReturnForMonth(month, wait, returnsUrl)) {
						continue;
					}

					System.out.println("Tax return " + monthText + " opened at: " + driver.getCurrentUrl());

					String payableRefund = extractTextContent(PAYABLE_REFUND, "Payment or refund", "No Value");
					if ("No Value".equals(payableRefund)) {
						// Box 9 sometimes takes longer to paint than the wait allows. Reopen
						// the return from the grid rather than reloading it: refreshing an
						// autax.xero.com URL directly loses the practice context and lands
						// on error/e403, turning a slow page into a permanently lost value.
						System.out.println("Box 9 missing — reopening " + monthText + " from the grid.");
						if (backToTaxReturns(returnsUrl, wait) && openTaxReturnForMonth(month, wait, returnsUrl)) {
							payableRefund = extractTextContent(PAYABLE_REFUND, "Payment or refund", "No Value");
						}
					}

					String amount = extractTextContent(AMOUNT_PAYABLE, "Amount payable", "No Value");

					// A return that was found but could not be read is not the same as a
					// period with no return, and the run reports success either way. Say so
					// loudly, or the sheet quietly carries a gap where a figure belongs.
					if ("No Value".equals(payableRefund) || "No Value".equals(amount)) {
						System.out.println("!! " + monthText + " has a return but its figures could not be read"
								+ " (" + driver.getCurrentUrl() + "). The sheet will show No Value.");
					}

					payableByMonth.put(monthText, payableRefund);
					amountPayableByMonth.put(monthText, amount);
				} catch (Exception e) {
					System.out.println("Unable to process Period End " + monthText + ": " + e.getMessage());
				} finally {
					if (!backToTaxReturns(returnsUrl, wait)) {
						System.out.println("Cannot get back to the Tax Returns list — stopping here rather than "
								+ "retrying every remaining month against a broken page.");
						break;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Unable to read Tax Returns: " + e.getMessage());
		}
		return new TaxReturnValues(payableByMonth, amountPayableByMonth);
	}

	/**
	 * Opens the client's Tax Returns tab and waits for its rows.
	 *
	 * <p>Retries because the grid loads slowly often enough to matter: one timeout
	 * used to abandon the whole client, writing rows with no figures while the run
	 * still reported success.
	 *
	 * @return false when the grid could not be loaded at all
	 */
	private static boolean openTaxReturnsList(WebDriverWait wait) throws InterruptedException {
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				wait.until(d -> d.findElement(TAX_RETURNS_LINK)).click();
				wait.until(d -> !d.findElements(TAX_RETURN_ROWS).isEmpty());
				return true;
			} catch (Exception e) {
				System.out.println("Tax Returns grid not ready (attempt " + attempt + " at "
						+ driver.getCurrentUrl() + ")");
				if (attempt < 3) {
					driver.navigate().refresh();
					Thread.sleep(3000);
				}
			}
		}
		return false;
	}

	/**
	 * Clicks the AS link whose Period End falls in the month, paging through the grid
	 * if it is not on the first page.
	 *
	 * @return false when the client filed no activity statement for that month
	 */
	private static boolean openTaxReturnForMonth(YearMonth month, WebDriverWait wait, String returnsUrl) {
		boolean morePages = true;
		while (morePages) {
			for (WebElement row : driver.findElements(TAX_RETURN_ROWS)) {
				if (findPeriodEndCell(row, month) == null) {
					continue;
				}
				List<WebElement> links = row.findElements(By.xpath(".//a[normalize-space()='AS']"));
				if (links.isEmpty()) {
					continue;
				}

				clickWithJs(links.get(0));
				wait.until(d -> !d.getCurrentUrl().equals(returnsUrl));
				return true;
			}

			List<WebElement> nextButtons = driver.findElements(NEXT_PAGE);
			if (nextButtons.isEmpty() || !nextButtons.get(0).isDisplayed()
					|| nextButtons.get(0).getAttribute("class").contains("x-item-disabled")) {
				morePages = false;
			} else {
				WebElement firstRow = driver.findElements(TAX_RETURN_ROWS).get(0);
				clickWithJs(nextButtons.get(0));
				wait.until(ExpectedConditions.stalenessOf(firstRow));
			}
		}
		return false;
	}

	/**
	 * Back to the grid, ready for the next month.
	 *
	 * @return false when the grid could not be reached, so the caller can stop rather
	 *         than run every remaining month against a page that will never load
	 */
	private static boolean backToTaxReturns(String returnsUrl, WebDriverWait wait) {
		for (int attempt = 1; attempt <= 2; attempt++) {
			try {
				driver.get(returnsUrl);
				wait.until(d -> !d.findElements(TAX_RETURN_ROWS).isEmpty());
				return true;
			} catch (Exception e) {
				System.out.println("Attempt " + attempt + " to reopen the Tax Returns list failed.");
			}
		}
		return false;
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
	
	
	/**
	 * The client link on jobview.aspx: the current layout gives it a flex-layout link
	 * class, the older one an ASP.NET control id.
	 */
	private static final By CLIENT_NAME_LINK = By.xpath(
			"//a[contains(@class,'sdl-flex-layout__control__link')][contains(@href,'clientview.aspx')]"
			+ " | //a[@id='ctl00_PageContent_ctlxlayoutClient']");

	/**
	 * Opens the client behind the job.
	 *
	 * <p>Waits for the link rather than assuming the job page has painted, clicks
	 * through JavaScript, and follows the client if it opens in a new tab — the
	 * previous version gave the page five seconds and no tab handling, and spent all
	 * five attempts on a job page that had not finished loading.
	 */
	private static void clickOnClientName() {
	    System.out.println("Inside clickOnClientName method");

	    WebDriverWait pageWait = new WebDriverWait(driver, Duration.ofSeconds(20));

	    for (int attempt = 1; attempt <= 3; attempt++) {
	        try {
	            System.out.println("Click attempt: " + attempt);

	            WebElement clientElement = pageWait.until(d -> {
	                List<WebElement> byControlId = d.findElements(CLIENT_NAME_LINK);
	                if (!byControlId.isEmpty()) {
	                    return byControlId.get(0);
	                }
	                List<WebElement> cells = d.findElements(CLIENT_NAME_CELL_LOCATOR);
	                if (cells.isEmpty()) {
	                    return null;
	                }
	                List<WebElement> links = cells.get(0).findElements(By.tagName("a"));
	                return links.isEmpty() ? cells.get(0) : links.get(0);
	            });

	            Set<String> windowsBefore = new HashSet<>(driver.getWindowHandles());
	            String urlBefore = driver.getCurrentUrl();

	            clickWithJs(clientElement);

	            pageWait.until(d -> d.getWindowHandles().size() > windowsBefore.size()
	                    || !d.getCurrentUrl().equals(urlBefore));

	            Set<String> opened = new HashSet<>(driver.getWindowHandles());
	            opened.removeAll(windowsBefore);
	            if (!opened.isEmpty()) {
	                driver.switchTo().window(opened.iterator().next());
	                System.out.println("Client opened in a new tab.");
	            }

	            pageWait.until(d -> d.getCurrentUrl().contains("app/clients")
	                    || d.getCurrentUrl().contains("clientview.aspx"));

	            System.out.println("Successfully opened Client page.");
	            System.out.println("Landed on URL: " + driver.getCurrentUrl());
	            return;

	        } catch (Exception e) {
	            System.out.println("Attempt " + attempt + " failed at " + driver.getCurrentUrl());
	        }
	    }
	    System.out.println("Could not open the Client page.");
	}

	public static void switchPortalByPrefix(String input) throws InterruptedException {
		// The previous job ends on the ATO tab, so come back to Xero before navigating
		// — otherwise this would steer the ATO tab to Practice Manager and throw away
		// the approved session.
		focusXeroWindow();

		// Start every job from the Practice Manager home page. Reloading whatever page
		// the previous job finished on left the second job on a client's Tax Returns
		// screen, whose legacy header has no "Other Practices" list for the switcher.
		//
		// The wait is on readyState, not on the URL: Practice Manager rewrites the
		// address as it loads, so waiting for an exact match never comes true.
		driver.get(PRACTICE_MANAGER_HOME);
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(
				d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		System.out.println("Practice Manager ready at: " + driver.getCurrentUrl());

		// Routing per the scope of work: KPF jobs belong to FUT, everything else (JO) to FABA.
		if (input != null && input.toUpperCase(Locale.ENGLISH).startsWith("KPF")) {
			switchportal();
		} else {
			switchportal2();
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

	/*====================ITR jobs===================================*/

	/**
	 * The job's Description, in either job page layout. It is where the Ignition
	 * proposal link sits, when the job has one.
	 */
	private static final By JOB_DESCRIPTION = By.xpath(
			"//span[contains(@class,'sdl-flex-layout__control__label') and normalize-space()='Description']/following-sibling::*[1]"
			+ " | //td[normalize-space()='Description:']/following-sibling::td[1]");

	/*====================ITR: final invoice from the job's Financial tab===================================*/

	/** The job's Financial tab, in either layout: a tab cell on jobview.aspx, or a tab/link on the SPA. */
	private static final By FINANCIAL_TAB = By.xpath(
			"//td[contains(@class,'Tab')][normalize-space()='Financial']"
			+ " | //*[self::a or self::button or @role='tab'][normalize-space()='Financial']");

	/** Final invoice numbers found on the job's Financial tab, e.g. "INV-19498"; joined with "; " if several. */
	public static String finalInvoiceNumbers = "";

	/** Their amounts, in the same order, e.g. "$330.00". */
	public static String finalInvoiceAmounts = "";

	/** The Financial page is saved for diagnosis once per run, not for every job. */
	private static boolean financialPageCaptured = false;

	/**
	 * Opens the job's Financial tab and reads the final invoices from the last
	 * Invoices table: every row whose Type (the 4th column) says "Final Invoice" gives
	 * its invoice number and amount. Then goes back to the job's Information tab,
	 * where the client link is.
	 *
	 * <p>The table is found by its headings — the last table with a Type column — and
	 * its columns by their header text, falling back to the 4th column for Type. If no
	 * such table is found, the page is captured so the layout can be checked.
	 */
	private static void readFinalInvoices() throws InterruptedException {
		finalInvoiceNumbers = "";
		finalInvoiceAmounts = "";

		String jobUrl = driver.getCurrentUrl();
		List<WebElement> tabs = driver.findElements(FINANCIAL_TAB);
		if (tabs.isEmpty()) {
			System.out.println("!! No Financial tab on the job page — no final invoice read.");
			return;
		}

		clickWithJs(tabs.get(0));
		Thread.sleep(3000);

		String script =
				"const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
				+ "const tables=[...document.querySelectorAll(\"table,[role='grid'],[role='table']\")];"
				+ "let chosen=null;"
				+ "for(const t of tables){"
				+ "const firstRow=t.querySelector(\"thead tr,tr,[role='row']\");"
				+ "if(!firstRow)continue;"
				+ "const heads=[...firstRow.querySelectorAll(\"th,td,[role='columnheader']\")].map(c=>clean(c.textContent).toLowerCase());"
				+ "if(heads.some(h=>h==='type'||h.startsWith('type ')))chosen={t,heads};}"
				+ "if(!chosen)return null;"
				+ "const heads=chosen.heads;"
				+ "let typeIdx=heads.findIndex(h=>h==='type'||h.startsWith('type '));if(typeIdx<0)typeIdx=3;"
				+ "let numIdx=heads.findIndex(h=>/invoice|number|^no\\.?$|^#/.test(h));if(numIdx<0)numIdx=0;"
				+ "let amtIdx=heads.findIndex(h=>/amount|total/.test(h));"
				+ "const out=['HEADERS '+heads.join(' | ')];"
				+ "const rows=[...chosen.t.querySelectorAll(\"tr,[role='row']\")].slice(1);"
				+ "for(const r of rows){"
				+ "const cells=[...r.querySelectorAll(\"td,[role='cell'],[role='gridcell']\")].map(c=>clean(c.textContent));"
				+ "if(cells.length<=typeIdx)continue;"
				+ "if(!/final invoice/i.test(cells[typeIdx]))continue;"
				+ "const amount=amtIdx>=0&&amtIdx<cells.length?cells[amtIdx]:(cells.filter(c=>/\\$?\\d[\\d,]*\\.\\d{2}/.test(c)).pop()||'');"
				+ "out.push(cells[numIdx]+'\\t'+amount);}"
				+ "return out.join('\\n');";

		Object result = null;
		try {
			result = new WebDriverWait(driver, Duration.ofSeconds(20))
					.until(d -> ((JavascriptExecutor) d).executeScript(script));
		} catch (TimeoutException e) {
			// handled below
		}

		if (result == null) {
			// Most jobs have no invoices yet, so the page is saved once per run, not per job.
			if (!financialPageCaptured) {
				financialPageCaptured = true;
				DomCapture capture = new DomCapture();
				capture.capture(driver, "job-financial-no-invoices-table");
				System.out.println("No Invoices table with a Type column on the Financial tab. Page captured to: "
						+ capture.getFolder());
			} else {
				System.out.println("No Invoices table with a Type column on the Financial tab.");
			}
		} else {
			List<String> numbers = new ArrayList<>();
			List<String> amounts = new ArrayList<>();
			for (String line : String.valueOf(result).split("\n")) {
				if (line.startsWith("HEADERS ")) {
					System.out.println("Invoices table columns: " + line.substring(8));
					continue;
				}
				String[] parts = line.split("\t", -1);
				numbers.add(parts[0].trim());
				amounts.add(parts.length > 1 ? parts[1].trim() : "");
			}
			finalInvoiceNumbers = String.join("; ", numbers);
			finalInvoiceAmounts = String.join("; ", amounts);
			System.out.println(">>> Final invoice(s): " + (numbers.isEmpty() ? "none on this job"
					: finalInvoiceNumbers + "  amount(s): " + finalInvoiceAmounts));
		}

		// Back to the Information tab, where the client link is
		driver.get(jobUrl);
		awaitJobDetail();
	}

	/** The job's "Entities Included" (Admin section), one entity per line as XPM shows it. */
	public static String entitiesIncluded = "";

	/** The job's "Client order no.", e.g. "PROP-21652" for a job that came through Ignition. */
	public static String clientOrderNo = "";

	/** A job came through Ignition when its Client order no. is a proposal number: PROP-something. */
	public static boolean isIgnitionJob() {
		return clientOrderNo != null && clientOrderNo.trim().toUpperCase().matches("PROP-\\S+");
	}

	/**
	 * A labelled field on the job page, in either layout, keeping its line breaks.
	 * The label is matched ignoring case and a trailing colon, so "Client order no."
	 * also finds the legacy "Client Order No.:".
	 *
	 * @return the value, or blank when the field is missing or shows "-"
	 */
	private static String readJobField(String label) {
		String script =
				"const want=arguments[0].toLowerCase().replace(/:$/,'');"
				+ "const clean=v=>(v||'').replace(/\\u00a0/g,' ').trim();"
				+ "for(const el of document.querySelectorAll('span,td,div,label,dt,th')){"
				+ "if(el.children.length>1)continue;"
				+ "const t=clean(el.textContent).toLowerCase().replace(/:$/,'');"
				+ "if(t!==want)continue;"
				+ "const value=el.nextElementSibling||(el.parentElement&&el.parentElement.nextElementSibling);"
				+ "if(!value)continue;"
				+ "const v=clean(value.innerText);"
				+ "return v==='-'?'':v;}"
				+ "return '';";
		try {
			Object value = ((JavascriptExecutor) driver).executeScript(script, label);
			return value == null ? "" : String.valueOf(value).trim();
		} catch (Exception e) {
			System.out.println("Unable to read " + label + ": " + e.getMessage());
			return "";
		}
	}

	/** The client's Preferred Name on XPM; blank when the field is empty ("-") or missing. */
	public static String preferredName = "";

	/**
	 * A custom field on the XPM client page, found by its label. Custom fields carry a
	 * GUID in their automation id that differs between practices, so the label is the
	 * only stable way in — the same approach as Internal Team.
	 *
	 * @return the value, or blank when the field is missing or shows "-"
	 */
	private static String extractLabelledValue(String label) {
		By locator = By.xpath("//div[contains(@data-automationid,'--label') and normalize-space()="
				+ xpathLiteral(label) + "]/following-sibling::div[contains(@data-automationid,'--value')]");
		List<WebElement> found = driver.findElements(locator);
		if (found.isEmpty()) {
			System.out.println(label + " not on the client page.");
			return "";
		}
		String value = found.get(0).getText().trim();
		return "-".equals(value) ? "" : value;
	}

	/** The Ignition proposal link in the ITR job's Description, or null when there is none. */
	public static String ignitionLink = null;

	/** False when the Description could not be read at all, so "no link" is not certain. */
	public static boolean descriptionRead = false;

	/**
	 * Looks for the Ignition proposal link under the job's Description, e.g.
	 * https://go.ignitionapp.com/clients/cli_…/proposals/prop_…
	 *
	 * <p>The link differs for every client, so it is recognised by where it points —
	 * ignitionapp.com — not by its exact address. Both the anchors' targets and the
	 * Description's text are checked, in case the link is written out without being
	 * made clickable.
	 */
	private static void readIgnitionLink() {
		ignitionLink = null;
		descriptionRead = false;

		List<WebElement> found = driver.findElements(JOB_DESCRIPTION);
		if (found.isEmpty()) {
			System.out.println("!! No Description on the job page — cannot tell whether it has an Ignition link.");
			return;
		}
		descriptionRead = true;

		Object link = ((JavascriptExecutor) driver).executeScript(
				"const el=arguments[0];"
				+ "for(const a of el.querySelectorAll('a[href]'))"
				+ "if(/ignitionapp\\.com/i.test(a.href))return a.href;"
				+ "const m=(el.textContent||'').match(/https?:\\/\\/\\S*ignitionapp\\.com\\S*/i);"
				+ "return m?m[0]:null;", found.get(0));

		ignitionLink = link == null ? null : String.valueOf(link);
		System.out.println(">>> Ignition link in the Description: " + (ignitionLink == null ? "none" : ignitionLink));
	}

	/**
	 * Opens an income tax return job and reads who its client is.
	 *
	 * <p>The same route to the job and its client as {@link #switchPortalByPrefix},
	 * without anything that belongs to a BAS: no period is read out of the job name and
	 * the activity statements under Tax Returns are not opened. An ITR's estimated
	 * position comes from the ITR document in the client folder instead.
	 *
	 * <p>Separate from the BAS method on purpose, so neither can change the other.
	 */
	public static void openItrJob(String input) throws InterruptedException {
		focusXeroWindow();

		// The job is often already open: a closed job is opened from the top-right search
		// to read its name, and searching for it a second time would only repeat that.
		if (isJobPageOpen(input)) {
			System.out.println(input + " is already open — carrying on from its job page.");
		} else {
			driver.get(PRACTICE_MANAGER_HOME);
			new WebDriverWait(driver, Duration.ofSeconds(30)).until(
					d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
			System.out.println("Practice Manager ready at: " + driver.getCurrentUrl());

			if (input != null && input.toUpperCase(Locale.ENGLISH).startsWith("KPF")) {
				switchportal();
			} else {
				switchportal2();
			}

			clickjoblinks(input);
		}

		readIgnitionLink();

		extractedJobNo = extractText(jobNo, "Job No.", "No Job");
		clientName     = extractText(Client_Name, "Client Name", "No cleintName");
		jobName        = extractText(Job_Name, "Job Name", "");
		System.out.println(">>> ITR job: " + extractedJobNo + " - " + jobName);

		// Trust jobs (scope 4.1, 4.4): the notes for the cover letter, the entities the job
		// covers, and the Client order no. that shows whether it came through Ignition.
		coverLetterNotes = readJobField("Cover Letter Notes");
		entitiesIncluded = readJobField("Entities Included");
		clientOrderNo    = readJobField("Client order no.");
		System.out.println(">>> Entities Included: '" + entitiesIncluded.replace("\n", " | ") + "'");
		System.out.println(">>> Client order no.: '" + clientOrderNo + "'" + (isIgnitionJob() ? "  (Ignition job)" : ""));

		// Cleared so a field the client page does not show cannot carry over from the
		// job before this one.
		clientnamecode = "No Value";
		groupName      = "No GroupName";
		internalteam   = "No Value";
		cientCode      = "No Value";
		businessStructure = "No Value";
		preferredName  = "";

		// Before the client page: the job's own Financial tab, for its final invoice.
		readFinalInvoices();

		clickOnClientName();

		if (driver.getCurrentUrl().contains("clientview.aspx")) {
			System.out.println("Legacy client page — only the client name from the job is available.");
			return;
		}

		// Wait for the details panel rather than a fixed pause. Once the ATO login has
		// run, the implicit wait is zero, so a field read a moment too early comes back
		// missing — which is how a run lost the client name and group.
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(ExpectedConditions.presenceOfElementLocated(
					By.xpath("//div[@data-automationid='information-item-businessName--value']")));
			Thread.sleep(1000);
		} catch (TimeoutException e) {
			System.out.println("Client details did not appear within 20s at " + driver.getCurrentUrl());
		}
		clientnamecode = extractText(client_name, "Client Name", "No Value");
		groupName      = extractText(client_GroupName, "Group Name", "No GroupName");
		internalteam   = extractInternalTeam();
		cientCode      = extractText(client_code, "Client Code", "No Value");
		businessStructure = extractText(client_BusinessStructure, "Business Structure", "No Value");
		preferredName  = extractLabelledValue("Preferred Name");
		System.out.println(">>> Preferred Name: '" + preferredName + "'");
		System.out.println(">>> ITR client: " + clientnamecode + " (" + cientCode + "), group '" + groupName
				+ "', team '" + internalteam + "', business structure '" + businessStructure + "'");
	}


}