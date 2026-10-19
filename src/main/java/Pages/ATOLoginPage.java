package Pages;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jj.util.Dom;
import com.jj.util.DomCapture;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class ATOLoginPage extends MainClass {

	private static final String DEFAULT_EMAIL = "shravika.arora@fortunaadvisors.com.au";

	/** How long the run holds for the myID prompt to be approved on the device. */
	private static final Duration MYID_APPROVAL_WINDOW = Duration.ofSeconds(180);

	private static final By CONTINUE_WITH_DIGITAL_ID = By.xpath(
			"//*[self::button or self::a or self::span][contains(normalize-space(), 'Continue with Digital ID')]");
	private static final By SELECT_MY_ID = By.xpath(
			"//*[self::button or self::a][contains(normalize-space(), 'Select myID')]");
	private static final By EMAIL_INPUT = By.xpath("//input[@type='email']");
	private static final By CLIENT_SEARCH = By.xpath(
			"//input[@id='search-atoo-ahp-search-for-client-by-tfn-abn-wpn-ein-or-name-001']");
	private static final By PROFILE = By.xpath(
			"//*[self::span or self::a or self::button][normalize-space()='Profile']");
	private static final By TAX_REGISTRATIONS = By.xpath(
			"//*[self::span or self::a or self::button][normalize-space()='Tax registrations']");
	private static final By REGISTRATION_ROWS = By.xpath(
			"//*[@role='table']//*[@role='row'][not(contains(@class, 'column-headers'))]");
	private static final By ATO_TABLES = By.xpath("//*[@role='table']");
	private static final By ATO_TABLE_COMPONENT = By.cssSelector("ng-com-table");
	private static final By TABLE_HEADERS = By.xpath(".//*[@role='columnheader']");

	private static final String ATO_HOME_URL = "https://onlineservices.ato.gov.au/OnlineServices/Home#home";
	private static final By ACCOUNTS_AND_PAYMENTS = By.xpath(
			"//*[self::span or self::a or self::button]"
			+ "[contains(normalize-space(), 'Accounts and payments')"
			+ " or contains(normalize-space(), 'Accounts & payments')]");
	private static final By ACCOUNTS_SUMMARY = By.xpath(
			"//*[self::span or self::a or self::button]"
			+ "[contains(normalize-space(), 'Accounts summary')"
			+ " or contains(normalize-space(), 'Account summary')]");
	private static final String[] ACTIVITY_STATEMENT_KEYS = {"activity statement", "integrated client account"};
	private static final String[] DOWNLOAD_KEYS = {"download", "statement of account", "print", "export"};

	@FindBy(xpath = "//input[@type='email']")
	private WebElement emailAddress;

	@FindBy(xpath = "//button[@type='submit']")
	private WebElement loginButton;

	@FindBy(xpath = "//*[self::button or self::a][contains(normalize-space(), 'Select myID')]")
	private WebElement selectMyIdButton;

	public ATOLoginPage() {
		if (DriverManager.getDriver() != null) {
			PageFactory.initElements(DriverManager.getDriver(), this);
		}
	}

	public void clickOnMyGOVButton() {
		if (!DriverManager.getDriver().findElements(CONTINUE_WITH_DIGITAL_ID).isEmpty()) {
			wait.until(ExpectedConditions.elementToBeClickable(CONTINUE_WITH_DIGITAL_ID)).click();
		}
	}

	public void clickOnSelectmyIDButton() {
		wait.until(ExpectedConditions.elementToBeClickable(SELECT_MY_ID)).click();
	}

	public void sendingEmailAddress(String email) {
		String loginEmail = email == null || email.trim().isEmpty() ? DEFAULT_EMAIL : email.trim();
		wait.until(ExpectedConditions.elementToBeClickable(emailAddress)).sendKeys(loginEmail);
	}

	public void login(String email) throws InterruptedException {
		DriverManager.getDriver().manage().timeouts().implicitlyWait(Duration.ZERO);
		if (isAlreadyAuthenticated()) {
			return;
		}

		wait.until(d -> isAlreadyAuthenticated() || hasElement(CONTINUE_WITH_DIGITAL_ID)
				|| hasElement(SELECT_MY_ID) || hasElement(EMAIL_INPUT));
		if (!isAlreadyAuthenticated()) {
			clickOnMyGOVButton();
		}

		wait.until(d -> hasElement(SELECT_MY_ID) || hasElement(EMAIL_INPUT));
		if (hasElement(SELECT_MY_ID)) {
			clickOnSelectmyIDButton();
		}

		wait.until(d -> isAlreadyAuthenticated() || hasElement(EMAIL_INPUT));
		if (!isAlreadyAuthenticated()) {
			sendingEmailAddress(email);
			clickOnLoginButton();
		}
		waitForMyIdApproval(MYID_APPROVAL_WINDOW);
	}

	/**
	 * Holds while the myID approval is accepted on the operator's device.
	 *
	 * <p>This is the one step the bot cannot perform itself, so it polls rather than
	 * sleeping: the run carries on the moment the portal shows an authenticated page,
	 * and only uses the whole window if the approval never arrives.
	 */
	private void waitForMyIdApproval(Duration window) {
		WebDriver driver = DriverManager.getDriver();

		if (isAlreadyAuthenticated()) {
			return;
		}

		System.out.println("=========================================================");
		System.out.println(" Waiting up to " + window.getSeconds() + "s for the myID approval.");
		System.out.println(" Approve the prompt on your device when it arrives.");
		System.out.println("=========================================================");

		long deadline = System.currentTimeMillis() + window.toMillis();
		String lastReported = "";

		while (System.currentTimeMillis() < deadline) {
			if (isAlreadyAuthenticated()) {
				System.out.println("myID approved — ATO portal authenticated.");
				return;
			}

			String title = String.valueOf(driver.getTitle());
			if (!title.equals(lastReported)) {
				System.out.println("  login page: " + title);
				lastReported = title;
			}

			// Digital ID can hand back to the provider chooser or the email form part
			// way through, so keep nudging the flow rather than assuming one sequence
			// of clicks got us all the way to the prompt.
			try {
				if (hasElement(SELECT_MY_ID)) {
					clickOnSelectmyIDButton();
				} else if (hasElement(CONTINUE_WITH_DIGITAL_ID)) {
					clickOnMyGOVButton();
				} else if (hasElement(EMAIL_INPUT) && emailBoxIsEmpty()) {
					sendingEmailAddress(MainClass.ATO_USER_NAME);
					clickOnLoginButton();
				}
			} catch (Exception e) {
				// A page can change under us mid-click; the next pass picks it up.
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		System.out.println("No myID approval after " + window.getSeconds() + "s (last page: "
				+ driver.getTitle() + ").");
		DomCapture capture = new DomCapture();
		capture.capture(driver, "ato-login-not-approved");
		System.out.println("Login page captured to: " + capture.getFolder());
	}

	private boolean emailBoxIsEmpty() {
		for (WebElement input : DriverManager.getDriver().findElements(EMAIL_INPUT)) {
			String value = input.getAttribute("value");
			return value == null || value.trim().isEmpty();
		}
		return false;
	}

	/**
	 * Signed in if we are anywhere inside Online services with no login control in
	 * sight — not just on the home page. Each job leaves the tab on whatever ATO page
	 * it finished with, and all of those are equally signed in.
	 */
	private boolean isAlreadyAuthenticated() {
		String url = DriverManager.getDriver().getCurrentUrl();
		boolean insideOnlineServices = url.contains("onlineservices.ato.gov.au/OnlineServices");
		boolean loginControlsAbsent = !hasElement(CONTINUE_WITH_DIGITAL_ID)
				&& !hasElement(SELECT_MY_ID) && !hasElement(EMAIL_INPUT);
		return insideOnlineServices && loginControlsAbsent;
	}

	private boolean hasElement(By locator) {
		return !DriverManager.getDriver().findElements(locator).isEmpty();
	}

	public byte[] clickOnLoginButton() throws InterruptedException {
		wait.until(ExpectedConditions.elementToBeClickable(loginButton)).click();
		Thread.sleep(3000);
		return screenshot();
	}

	public byte[] screenshot() {
		return ((TakesScreenshot) DriverManager.getDriver()).getScreenshotAs(OutputType.BYTES);
	}
public String findWithholdingRegistration(String clientName) throws InterruptedException {

    if (clientName == null || clientName.trim().isEmpty()) {
        throw new IllegalArgumentException("Client name must not be empty");
    }

    WebDriverWait atoWait = searchAndOpenClient(clientName);

    atoWait.until(
            ExpectedConditions.elementToBeClickable(PROFILE)
    ).click();

    atoWait.until(
            ExpectedConditions.elementToBeClickable(TAX_REGISTRATIONS)
    ).click();

    atoWait.until(
            d -> d.getCurrentUrl().contains("/OnlineServices/TaxRoles")
    );

    atoWait.until(
			ExpectedConditions.presenceOfElementLocated(ATO_TABLE_COMPONENT)
    );

    System.out.println("Current registrations table found.");

    return findWithholdingValueInShadowTableExact(atoWait);
}

	/*==================== Client search ====================*/

	/**
	 * Types the client name into the ATO search box and opens the matching client.
	 *
	 * <p>Called twice per run — once for the lodgement cycle, once for the account
	 * statements — so it returns to the ATO home page whenever the current page has
	 * no search box, such as after drilling into Tax registrations.
	 */
	private WebDriverWait searchAndOpenClient(String clientName) throws InterruptedException {
		WebDriver driver = DriverManager.getDriver();
		if (driver.findElements(CLIENT_SEARCH).isEmpty()) {
			System.out.println("No client search box on the current page — returning to the ATO home page.");
			driver.get(ATO_HOME_URL);
		}

		String searchClientName = clientName.trim();
		WebDriverWait atoWait = new WebDriverWait(driver, Duration.ofSeconds(60));

		WebElement search = awaitClientSearch();
		System.out.println("ATO client name: " + searchClientName);

		search.click();
		search.clear();
		search.sendKeys(searchClientName);
		System.out.println("Client name entered: " + searchClientName);

		Thread.sleep(2000);
		clickClientSuggestion(atoWait, searchClientName);
		return atoWait;
	}

	/**
	 * Waits for the agent home's client search box, reloading between attempts.
	 *
	 * <p>After the myID approval the agent home regularly stops on its loading shell:
	 * the title stays "Loading", nothing renders into the page, and no amount of
	 * waiting fixes it. A reload populates it in about a second, so this reloads
	 * rather than waiting out a page that is never going to finish.
	 */
	/**
	 * Lists what the registrations tables actually contain.
	 *
	 * <p>Printed when no cycle is found, so an NA distinguishes "the client has no
	 * registrations" from "the rows are there and the match missed them".
	 */
	private String readRegistrationRows() {
		try {
			String script = Dom.DEEP_TEXT
					+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
					+ "const out=[];"
					+ "function walk(root){"
					+ "for(const row of root.querySelectorAll(\"[role='row'],tr,ng-com-table-row,com-table-tr\")){"
					+ "const t=clean(deepText(row));"
					+ "if(t)out.push('  | '+t.slice(0,140));}"
					+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)walk(el.shadowRoot);}"
					+ "walk(document);"
					+ "return out.length ? out.join('\\n') : '  (no rows found on the page at all)';";

			return String.valueOf(((JavascriptExecutor) DriverManager.getDriver()).executeScript(script));
		} catch (Exception e) {
			return "  (could not read the page: " + e.getMessage() + ")";
		}
	}

	private WebElement awaitClientSearch() {
		WebDriver driver = DriverManager.getDriver();

		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				return new WebDriverWait(driver, Duration.ofSeconds(20))
						.until(ExpectedConditions.elementToBeClickable(CLIENT_SEARCH));
			} catch (TimeoutException e) {
				System.out.println("No client search box yet (attempt " + attempt + ", title '"
						+ driver.getTitle() + "') — reloading the agent home.");
				driver.navigate().refresh();
			}
		}

		DomCapture capture = new DomCapture();
		capture.capture(driver, "ato-no-client-search");
		throw new IllegalStateException("No client search box at " + driver.getCurrentUrl()
				+ " after three attempts. The page has been captured to: " + capture.getFolder());
	}

	/*==================== Accounts and payments -> Accounts summary -> statements ====================*/

	/**
	 * Brings the ATO portal to the front, reusing its tab when one is already open.
	 *
	 * <p>Every job does its own ATO lookup, so opening a fresh tab each time would
	 * stack them up and, worse, throw away the session that was approved on the first
	 * job — costing an myID approval per job instead of one for the run.
	 */
	public void openAtoPortal() {
		WebDriver driver = DriverManager.getDriver();

		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (driver.getCurrentUrl().contains("ato.gov.au")) {
				System.out.println("Reusing the ATO tab.");
				// Back to the agent home: the previous job leaves this tab deep in an
				// account's transactions, which is neither a recognisable signed-in
				// page nor a place the next client search can start from.
				driver.get(ATO_HOME_URL);
				PageFactory.initElements(driver, this);
				return;
			}
		}

		driver.switchTo().newWindow(WindowType.TAB);
		driver.get(ATO_HOME_URL);
		driver.manage().window().maximize();
		if (wait == null) {
			wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		}
		PageFactory.initElements(driver, this);
	}

	/**
	 * Searches the client, opens Accounts and payments -> Accounts summary and
	 * downloads the activity statement and income tax account statements.
	 *
	 * @return the names of the files saved into the ATO statements folder
	 */
	public List<String> downloadAccountStatements(String clientName) throws InterruptedException {
		if (clientName == null || clientName.trim().isEmpty()) {
			throw new IllegalArgumentException("Client name must not be empty");
		}

		WebDriverWait atoWait = searchAndOpenClient(clientName);
		openAccountsSummary(atoWait);

		List<String> downloaded = new ArrayList<>();

		// Activity statement only. The income tax account is deliberately not collected.
		downloadAccountStatement(atoWait, "Activity statement", ACTIVITY_STATEMENT_KEYS, clientName, downloaded);

		return downloaded;
	}

	/*==================== ITR: client search by name words, tax return due date ====================*/

	private static final By CLIENT_SUGGESTIONS = By.xpath(
			"//*[@role='option' or @role='listitem' or @role='treeitem']");

	/**
	 * Opens the client in the ATO by the words of its name, in any order.
	 *
	 * <p>The ATO lists an individual surname first — "KIRK, JAMES" — where XPM has
	 * "James Kirk", so the BAS search, which wants the whole name as written, would not
	 * find them. A suggestion counts when it carries every word of the name. The name is
	 * typed as written, then surname first, then the surname alone, until one matches.
	 *
	 * <p>Used by the ITR steps only.
	 */
	private WebDriverWait openClientByNameWords(String clientName) throws InterruptedException {
		WebDriver driver = DriverManager.getDriver();

		List<String> words = new ArrayList<>();
		for (String word : clientName.toLowerCase().split("[^a-z0-9]+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}

		String[] parts = clientName.replace(",", " ").trim().split("\\s+");
		String surname = parts[parts.length - 1];
		Set<String> attempts = new java.util.LinkedHashSet<>();
		attempts.add(clientName.trim());
		if (parts.length > 1 && !clientName.contains(",")) {
			attempts.add(surname + " " + String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)));
			attempts.add(surname);
		}

		for (String attempt : attempts) {
			if (driver.findElements(CLIENT_SEARCH).isEmpty()) {
				driver.get(ATO_HOME_URL);
			}
			WebElement search = awaitClientSearch();
			search.click();
			search.clear();
			search.sendKeys(attempt);
			System.out.println("ATO client search: " + attempt);
			Thread.sleep(2000);

			try {
				WebElement match = new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
					for (WebElement suggestion : d.findElements(CLIENT_SUGGESTIONS)) {
						if (!suggestion.isDisplayed()) {
							continue;
						}
						String text = suggestion.getText().toLowerCase();
						if (words.stream().allMatch(text::contains)) {
							return suggestion;
						}
					}
					return null;
				});
				System.out.println("Clicking ATO client suggestion: " + match.getText());
				match.click();
				return new WebDriverWait(driver, Duration.ofSeconds(60));
			} catch (TimeoutException e) {
				System.out.println("  no suggestion carrying every word of '" + clientName + "'");
			}
		}

		DomCapture capture = new DomCapture();
		capture.capture(driver, "ato-itr-client-not-found");
		throw new IllegalStateException("No ATO client matching '" + clientName + "'. Page captured to: "
				+ capture.getFolder());
	}

	/**
	 * Reads the income tax return's due date off the client's Summary, from the
	 * "For action" lodgments: the row whose description is the tax return —
	 * "Jul 2025 - Jun 2026 Tax return" — and the date in its Due date column.
	 *
	 * <p>Where several tax returns are listed, the one ending June of the tax year wins.
	 *
	 * @param taxYear e.g. "2026"
	 * @return the due date as the ATO shows it, e.g. "02/11/2026", or null if no tax
	 *         return is listed — the page is then captured
	 */
	public String readIncomeTaxDueDate(String clientName, String taxYear) throws InterruptedException {
		if (clientName == null || clientName.trim().isEmpty()) {
			throw new IllegalArgumentException("Client name must not be empty");
		}

		WebDriver driver = DriverManager.getDriver();
		openClientByNameWords(clientName);

		String script = Dom.DEEP_TEXT
				+ "const year=arguments[0];"
				+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
				+ "const rows=[];"
				+ "function collect(root){"
				+ "for(const row of root.querySelectorAll(\"[role='row'],tr,ng-com-table-row,com-table-tr\"))rows.push(row);"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)collect(el.shadowRoot);}"
				+ "collect(document);"
				// The tightest row wins: an outer wrapper can carry the whole page's text,
				// header included, and its first date is not the tax return's.
				+ "let best=null,bestLen=1e9,bestYear=false;"
				+ "for(const row of rows){"
				+ "const text=clean(deepText(row));"
				+ "if(!/tax return/i.test(text)||text.length>400)continue;"
				+ "const date=(text.match(/\\b\\d{2}\\/\\d{2}\\/\\d{4}\\b/)||[null])[0];"
				+ "if(!date)continue;"
				+ "const forYear=new RegExp('jun\\\\w*\\\\s+'+year,'i').test(text);"
				+ "if((forYear&&!bestYear)||(forYear===bestYear&&text.length<bestLen)){"
				+ "best=date+'|'+text;bestLen=text.length;bestYear=forYear;}}"
				+ "return best;";

		try {
			String found = new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
				Object result = ((JavascriptExecutor) d).executeScript(script, taxYear);
				return result == null ? null : String.valueOf(result);
			});
			String dueDate = found.substring(0, found.indexOf('|'));
			System.out.println("Tax return due date from the ATO: " + dueDate + "  (" + found.substring(found.indexOf('|') + 1) + ")");
			return dueDate;
		} catch (TimeoutException e) {
			DomCapture capture = new DomCapture();
			capture.capture(driver, "ato-itr-no-due-date");
			System.out.println("!! No tax return in the client's For action list — no due date. Page captured to: "
					+ capture.getFolder());
			return null;
		}
	}

	/*==================== Accounts summary -> Income tax row -> payment slip (ITR) ====================*/

	private static final String[] PAYMENT_SLIP_KEYS = {"payment slip"};

	/**
	 * Saves the client's income tax payment slip, for an ITR whose estimated position
	 * is payable.
	 *
	 * <p>Same Accounts summary the BAS statement comes from, but the other end of the
	 * row: BAS opens the account by the name on the left, while the payment slip is
	 * behind the link on the right-hand side of the Income tax row.
	 *
	 * <p>Used by the ITR flow only. Nothing in the BAS statement download calls it.
	 *
	 * @return the name of the file saved into the ATO statements folder, or null when
	 *         no slip could be produced — the page is captured so the step that failed
	 *         can be seen
	 */
	public String downloadIncomeTaxPaymentSlip(String clientName) throws InterruptedException {
		if (clientName == null || clientName.trim().isEmpty()) {
			throw new IllegalArgumentException("Client name must not be empty");
		}

		WebDriver driver = DriverManager.getDriver();
		WebDriverWait atoWait = openClientByNameWords(clientName);

		try {
			openAccountsSummary(atoWait);

			Set<String> downloadsBefore = snapshotDownloads();
			Set<String> windowsBefore = new HashSet<>(driver.getWindowHandles());
			String summaryWindow = driver.getWindowHandle();

			WebElement rightHandLink = findRightHandLinkInIncomeTaxRow();
			System.out.println("Income tax row, right-hand link: '" + linkLabel(rightHandLink) + "'");
			clickWithJs(rightHandLink);
			Thread.sleep(4000);

			// The right-hand link may be the slip itself or lead to a page that offers
			// it; if a "payment slip" control shows up, that is the one to press.
			String newFile = waitForNewDownload(downloadsBefore, Duration.ofSeconds(8));
			if (newFile == null) {
				followNewWindow(windowsBefore);
				try {
					WebElement slipControl = findInShadowDom(new WebDriverWait(driver, Duration.ofSeconds(10)),
							PAYMENT_SLIP_KEYS, "a, button, [role='button'], [role='link']");
					System.out.println("  pressing '" + linkLabel(slipControl) + "'");
					clickWithJs(slipControl);
					Thread.sleep(4000);
					newFile = waitForNewDownload(downloadsBefore, Duration.ofSeconds(8));
				} catch (TimeoutException e) {
					System.out.println("  no separate 'payment slip' control — taking this page as the slip.");
				}
			}

			if (newFile != null) {
				closeExtraWindows(windowsBefore, summaryWindow);
				return renameDownload(newFile, clientName, "Payment slip");
			}

			// No file arrived, so the slip is a page: render it to PDF, the same way
			// the activity statement is saved.
			followNewWindow(windowsBefore);
			System.out.println("  payment slip view: " + driver.getCurrentUrl());

			String saved = savePageAsPdf(clientName, "Payment slip");
			closeExtraWindows(windowsBefore, summaryWindow);

			if (saved == null) {
				System.out.println("No payment slip produced for " + clientName);
			}
			return saved;

		} catch (Exception e) {
			DomCapture capture = new DomCapture();
			capture.capture(driver, "ato-payment-slip");
			System.out.println("Unable to download the payment slip: " + e.getMessage());
			System.out.println("  page captured to: " + capture.getFolder());
			return null;
		}
	}

	/**
	 * The link furthest to the right in the Accounts summary's Income tax row.
	 *
	 * <p>Picked by position rather than by wording: the account name on the left is
	 * also a link, and it is the one that must not be clicked here. A link that
	 * carries the account's own name is never taken.
	 */
	private WebElement findRightHandLinkInIncomeTaxRow() {
		String script = Dom.DEEP_TEXT
				+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim().toLowerCase();"
				+ "const visible=el=>el.getClientRects().length>0;"
				+ "function deepAll(root,sel,out){"
				+ "for(const el of root.querySelectorAll(sel))out.push(el);"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)deepAll(el.shadowRoot,sel,out);"
				+ "return out;}"
				// The tightest row that mentions income tax — an outer wrapper can carry
				// the whole table's text and would offer every account's links.
				+ "let row=null;"
				+ "for(const candidate of deepAll(document,\"[role='row'],tr,ng-com-table-row,com-table-tr\",[])){"
				+ "if(!visible(candidate))continue;"
				+ "const text=clean(deepText(candidate));"
				+ "if(!text.includes('income tax'))continue;"
				+ "if(!row||text.length<clean(deepText(row)).length)row=candidate;}"
				+ "if(!row)return null;"
				+ "let best=null;let bestLeft=-1;"
				+ "for(const link of deepAll(row,\"a,button,[role='link'],[role='button']\",[])){"
				+ "if(!visible(link))continue;"
				+ "if(clean(deepText(link)).includes('income tax'))continue;"
				+ "const left=link.getBoundingClientRect().left;"
				+ "if(left>bestLeft){best=link;bestLeft=left;}}"
				+ "return best;";

		return new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(30))
				.until(browser -> (WebElement) ((JavascriptExecutor) browser).executeScript(script));
	}

	/** What a link says, for the log: its text, or failing that its aria-label or title. */
	private String linkLabel(WebElement link) {
		try {
			Object label = ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
					Dom.DEEP_TEXT + "const el=arguments[0];"
					+ "return (deepText(el)||el.getAttribute('aria-label')||el.getAttribute('title')||'')"
					+ ".replace(/\\s+/g,' ').trim();", link);
			return String.valueOf(label);
		} catch (Exception e) {
			return "";
		}
	}

	/** Moves to a tab the last click opened, if it opened one. */
	private void followNewWindow(Set<String> windowsBefore) throws InterruptedException {
		WebDriver driver = DriverManager.getDriver();
		Set<String> opened = new HashSet<>(driver.getWindowHandles());
		opened.removeAll(windowsBefore);
		if (!opened.isEmpty() && !opened.contains(driver.getWindowHandle())) {
			driver.switchTo().window(opened.iterator().next());
			Thread.sleep(2000);
		}
	}

	/** Closes any tab the slip opened and returns to the ATO tab the run keeps signed in. */
	private void closeExtraWindows(Set<String> windowsBefore, String returnTo) {
		WebDriver driver = DriverManager.getDriver();
		for (String handle : new HashSet<>(driver.getWindowHandles())) {
			if (!windowsBefore.contains(handle)) {
				driver.switchTo().window(handle);
				driver.close();
			}
		}
		driver.switchTo().window(returnTo);
	}

	private void openAccountsSummary(WebDriverWait atoWait) throws InterruptedException {
		atoWait.until(ExpectedConditions.elementToBeClickable(ACCOUNTS_AND_PAYMENTS)).click();
		Thread.sleep(1000);
		atoWait.until(ExpectedConditions.elementToBeClickable(ACCOUNTS_SUMMARY)).click();
		atoWait.until(d -> !d.findElements(ATO_TABLE_COMPONENT).isEmpty() || !d.findElements(ATO_TABLES).isEmpty());
		System.out.println("Accounts summary opened.");
	}

	private void downloadAccountStatement(WebDriverWait atoWait, String label, String[] accountKeys,
			String clientName, List<String> downloaded) throws InterruptedException {
		try {
			WebElement accountLink = findInShadowDom(atoWait, accountKeys, "a, button, [role='link']");
			System.out.println("Opening ATO account: " + label);
			clickWithJs(accountLink);
			Thread.sleep(3000);

			System.out.println("  account page: " + DriverManager.getDriver().getCurrentUrl());

			Set<String> before = snapshotDownloads();
			WebElement downloadControl;
			try {
				downloadControl = findInShadowDom(atoWait, DOWNLOAD_KEYS,
						"a, button, [role='button'], [role='link']");
			} catch (Exception e) {
				DomCapture capture = new DomCapture();
				capture.capture(DriverManager.getDriver(), "ato-" + label.replace(' ', '-') + "-no-download");
				System.out.println("  no download control on the " + label + " page — captured to: "
						+ capture.getFolder());
				return;
			}
			clickWithJs(downloadControl);
			Thread.sleep(4000);
			System.out.println("  print view: " + DriverManager.getDriver().getCurrentUrl());

			// The ATO offers a print view rather than a file: its only control opens the
			// browser's print dialog, which yields nothing a script can collect. Render
			// the page to PDF through DevTools instead.
			String saved = savePageAsPdf(clientName, label);
			if (saved != null) {
				downloaded.add(saved);
				return;
			}

			// Some pages may still push a real file; honour that if it happens.
			String newFile = waitForNewDownload(before, Duration.ofSeconds(20));
			if (newFile == null) {
				System.out.println("No " + label + " file produced for " + clientName);
				return;
			}
			downloaded.add(renameDownload(newFile, clientName, label));
		} catch (Exception e) {
			System.out.println("Unable to download the " + label + " statement: " + e.getMessage());
		}
	}

	/** Finds the first element matching one of the keywords, descending into shadow roots. */
	private WebElement findInShadowDom(WebDriverWait atoWait, String[] keywords, String selector) {
		StringBuilder keys = new StringBuilder("[");
		for (int index = 0; index < keywords.length; index++) {
			if (index > 0) {
				keys.append(",");
			}
			keys.append("'").append(keywords[index]).append("'");
		}
		keys.append("]");

		String script = "const keys=" + keys + ";"
				+ "const sel=\"" + selector + "\";"
				+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim().toLowerCase();"
				// Read through shadow roots: an account name sits in a nested component,
				// so textContent alone comes back empty for the row that holds it.
				+ "function deepText(node){let out='';"
				+ "for(const c of node.childNodes){"
				+ "if(c.nodeType===3){out+=c.nodeValue+' ';}"
				+ "else if(c.nodeType===1){if(c.shadowRoot)out+=deepText(c.shadowRoot);out+=deepText(c);}}"
				+ "return out;}"
				+ "let found=null;"
				+ "function walk(root){"
				+ "if(found)return;"
				+ "for(const el of root.querySelectorAll(sel)){"
				// Visible only. The collapsed navigation menu also holds 'Income tax'
				// and 'Activity statements', and it comes first in document order, so
				// without this the search leaves the Accounts summary for Lodgments.
				+ "if(el.getClientRects().length===0)continue;"
				+ "const text=clean(deepText(el))+' '+clean(el.getAttribute('aria-label'))+' '+clean(el.getAttribute('title'));"
				+ "if(keys.some(k=>text.includes(k))){found=el;return;}}"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot){walk(el.shadowRoot);if(found)return;}"
				+ "}walk(document);return found;";

		return atoWait.until(browser -> (WebElement) ((JavascriptExecutor) browser).executeScript(script));
	}

	private void clickWithJs(WebElement element) {
		JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
		js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (Exception e) {
			js.executeScript("arguments[0].click();", element);
		}
	}

	/**
	 * Renders the current page to a PDF through Chrome DevTools.
	 *
	 * <p>The ATO's account screens offer "Print-friendly version", which leads to a
	 * print view whose only action opens the browser print dialog — nothing lands on
	 * disk and no script can dismiss it. Page.printToPDF gives the same document
	 * without a dialog.
	 *
	 * @return the file name written, or null if this browser cannot print to PDF
	 */
	private String savePageAsPdf(String clientName, String label) {
		WebDriver driver = DriverManager.getDriver();

		if (!(driver instanceof ChromeDriver)) {
			return null;
		}

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("printBackground", true);
			params.put("preferCSSPageSize", true);

			Map<String, Object> result =
					((ChromeDriver) driver).executeCdpCommand("Page.printToPDF", params);

			String data = String.valueOf(result.get("data"));
			if (data.isEmpty() || "null".equals(data)) {
				return null;
			}

			File directory = new File(DriverManager.getDownloadDirectory());
			if (!directory.exists()) {
				directory.mkdirs();
			}

			String safeClientName = clientName.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
			File target = new File(directory,
					safeClientName + " - " + label + " - " + LocalDate.now() + ".pdf");

			Files.write(target.toPath(), Base64.getDecoder().decode(data));

			System.out.println("  saved " + label + ": " + target.getAbsolutePath()
					+ " (" + target.length() / 1024 + " KB)");
			return target.getName();

		} catch (Exception e) {
			System.out.println("  could not print the " + label + " page to PDF: " + e.getMessage());
			return null;
		}
	}

	private Set<String> snapshotDownloads() {
		Set<String> names = new HashSet<>();
		File[] files = new File(DriverManager.getDownloadDirectory()).listFiles();
		if (files != null) {
			for (File file : files) {
				names.add(file.getName());
			}
		}
		return names;
	}

	private String waitForNewDownload(Set<String> before, Duration timeout) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			File[] files = new File(DriverManager.getDownloadDirectory()).listFiles();
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (!before.contains(name) && !name.endsWith(".crdownload") && !name.endsWith(".tmp")) {
						return name;
					}
				}
			}
			Thread.sleep(500);
		}
		return null;
	}

	private String renameDownload(String fileName, String clientName, String label) {
		File directory = new File(DriverManager.getDownloadDirectory());
		File source = new File(directory, fileName);
		int dot = fileName.lastIndexOf('.');
		String extension = dot < 0 ? "" : fileName.substring(dot);
		String safeClientName = clientName.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
		File target = new File(directory, safeClientName + " - " + label + " - " + LocalDate.now() + extension);

		if (source.renameTo(target)) {
			System.out.println("Saved " + label + ": " + target.getAbsolutePath());
			return target.getName();
		}
		System.out.println("Saved " + label + ": " + source.getAbsolutePath());
		return fileName;
	}

	/**
	 * Reads the BAS lodgement cycle off the Current registrations table.
	 *
	 * <p>Priority is set by the scope of work: the PAYG Withholding cycle decides the
	 * BAS cycle whenever the client is registered for it. The GST cycle is only used
	 * as a fallback when there is no withholding registration to read a cycle from.
	 * Both are searched across the whole table before a winner is picked, so row
	 * order in the ATO table cannot let GST override withholding.
	 */
	private String findWithholdingValueInShadowTableExact(WebDriverWait atoWait) {
		// Read each row as a whole line of text rather than picking cells by index.
		// The ATO builds rows from ng-com-table-row / com-table-td custom elements
		// whose values arrive through slots, so cell-by-cell indexing is fragile;
		// "Pay as you go withholding Quarterly 01 Jul 2019" as one string is not.
		String script = Dom.DEEP_TEXT
				+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
				+ "const preferences=["
				+ "['withholding','pay as you go - withholding','pay as you go - witholding','payg withholding'],"
				+ "['goods & services tax','goods and services tax','gst']"
				+ "];"
				+ "const found=new Array(preferences.length).fill(null);"
				+ "const rows=[];"
				+ "function collect(root){"
				+ "for(const row of root.querySelectorAll(\"[role='row'],tr,ng-com-table-row,com-table-tr\"))rows.push(row);"
				+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)collect(el.shadowRoot);"
				+ "}"
				+ "collect(document);"
				+ "for(const row of rows){"
				+ "const text=clean(deepText(row));"
				+ "if(!text)continue;"
				// Current registrations only. The historical table underneath repeats the
				// same registrations with their old cycles — Carnarvon's withholding
				// shows Monthly today and Quarterly before 30/06/2024 — and its rows
				// carry a Cancellation column the current table does not have.
				+ "if(/cancellation/i.test(text))break;"
				+ "const lower=text.toLowerCase();"
				+ "const cycle=(text.match(/quarterly|monthly/i)||[null])[0];"
				+ "if(!cycle)continue;"
				+ "for(let p=0;p<preferences.length;p++){"
				+ "if(found[p])continue;"
				+ "if(preferences[p].some(k=>lower.includes(k))){found[p]=cycle;break;}"
				+ "}"
				+ "}"
				+ "for(const value of found) if(value) return value;"
				+ "return null;";

		WebDriverWait cycleWait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(20));
		try {
			String value = cycleWait.until(browser -> {
				String result = (String) ((JavascriptExecutor) browser).executeScript(script);
				return result == null || result.isEmpty() ? null : result;
			});
			System.out.println("ATO registration cycle extracted: " + value);
			return value;
		} catch (TimeoutException e) {
			System.out.println("No PAYG withholding or GST lodgement cycle found — defaulting to NA");
			System.out.println("Registration rows actually on the page:");
			System.out.println(readRegistrationRows());
			return "NA";
		}
	}



	private String findWithholdingValueInShadowTable(WebDriverWait atoWait) {

    return atoWait.until(driver -> {

        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script =
                "const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();" +

                "function getAllElements(root) {" +
                "    let elements = [];" +
                "    elements.push(...root.querySelectorAll('*'));" +

                "    root.querySelectorAll('*').forEach(el => {" +
                "        if (el.shadowRoot) {" +
                "            elements.push(...getAllElements(el.shadowRoot));" +
                "        }" +
                "    });" +

                "    return elements;" +
                "}" +

                "const table = document.querySelector(\"ng-com-table[headertext='Current registrations']\");" +

                "if (!table || !table.shadowRoot) {" +
                "    return null;" +
                "}" +

                "const elements = getAllElements(table.shadowRoot);" +

                "let rows = elements.filter(el => {" +
                "    const text = normalize(el.textContent);" +
                "    return text.toLowerCase().includes('withholding');" +
                "});" +

                "for (const row of rows) {" +

                "    let cells = [...row.querySelectorAll(\"[role='cell'], [role='gridcell'], td\")];" +

                "    if (cells.length === 0 && row.shadowRoot) {" +
                "        cells = [...getAllElements(row.shadowRoot)].filter(el => {" +
                "            return el.matches(\"[role='cell'], [role='gridcell'], td\");" +
                "        });" +
                "    }" +

                "    if (cells.length > 0) {" +

                "        const texts = cells.map(cell => normalize(cell.textContent));" +

                "        const withholdingIndex = texts.findIndex(text => " +
                "            text.toLowerCase().includes('withholding')" +
                "        );" +

                "        if (withholdingIndex >= 0 && cells.length > withholdingIndex + 1) {" +
                "            return texts[withholdingIndex + 1];" +
                "        }" +

                "        if (cells.length >= 2) {" +
                "            return texts[1];" +
                "        }" +
                "    }" +

                "    const rowText = normalize(row.textContent);" +

                "    if (rowText.toLowerCase().includes('withholding')) {" +
                "        const parts = rowText.split(/\\s{2,}|\\n/).map(x => normalize(x)).filter(x => x);" +

                "        if (parts.length >= 2) {" +
                "            return parts[1];" +
                "        }" +
                "    }" +
                "}" +

                "return null;";

        String value = (String) js.executeScript(script);

        if (value != null && !value.trim().isEmpty()) {

            System.out.println("Lodgement Type for withholding: " + value.trim());

            return value.trim();
        }

        return null;
    });
}





	private void clickClientSuggestion(WebDriverWait atoWait, String clientName) {
		String clientNameLiteral = xpathLiteral(clientName);
		By suggestion = By.xpath(
				"//*[@role='option' or @role='listitem' or @role='treeitem']"
						+ "[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', "
						+ "'abcdefghijklmnopqrstuvwxyz'), translate(" + clientNameLiteral
						+ ", 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'))]");

		WebElement clientSuggestion = atoWait.until(driver -> {
			for (WebElement element : driver.findElements(suggestion)) {
				if (element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			}
			return null;
		});
		System.out.println("Clicking ATO client suggestion: " + clientSuggestion.getText());
		atoWait.until(ExpectedConditions.elementToBeClickable(clientSuggestion)).click();
	}

	private String xpathLiteral(String value) {
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


	public void user_launch_website() throws InterruptedException {
		setupDriver("Chrome");
		launchSite("https://onlineservices.ato.gov.au/OnlineServices/Home#home");
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
}

 
