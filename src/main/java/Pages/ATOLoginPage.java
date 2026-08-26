package Pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class ATOLoginPage extends MainClass {

	private static final String DEFAULT_EMAIL = "shravika.arora@fortunaadvisors.com.au";
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
		Thread.sleep(3000);
	}

	private boolean isAlreadyAuthenticated() {
		String url = DriverManager.getDriver().getCurrentUrl();
		boolean onAtoHome = url.contains("/OnlineServices/Home");
		boolean loginControlsAbsent = !hasElement(CONTINUE_WITH_DIGITAL_ID)
				&& !hasElement(SELECT_MY_ID) && !hasElement(EMAIL_INPUT);
		return onAtoHome && loginControlsAbsent;
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

    String searchClientName = clientName.trim();

    WebDriver driver = DriverManager.getDriver();

    WebDriverWait atoWait = new WebDriverWait(driver, Duration.ofSeconds(60));

    WebElement search = atoWait.until(
            ExpectedConditions.elementToBeClickable(CLIENT_SEARCH)
    );

    System.out.println("ATO client name: " + searchClientName);

    search.click();
    search.clear();
    search.sendKeys(searchClientName);

    System.out.println("Client name entered: " + searchClientName);

    Thread.sleep(2000);

    clickClientSuggestion(atoWait, searchClientName);

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

	private String findWithholdingValueInShadowTableExact(WebDriverWait atoWait) {
		return atoWait.until(browser -> {
			String script = "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
					+ "let answer=null;"
					+ "function walk(root){"
					+ "if(answer)return;"
					+ "for(const table of root.querySelectorAll(\"[role='table']\")){"
					+ "const headers=[...table.querySelectorAll(\"[role='columnheader']\")];"
					+ "const column=headers.findIndex(h=>{const x=clean(h.textContent).toLowerCase();"
					+ "return x.includes('lodgement type')||x.includes('lodgment cycle')||x.includes('lodgement cycle');});"
					+ "if(column<0)continue;"
					+ "const hosts=[...table.querySelectorAll('ng-com-table-row')];"
					+ "for(const host of hosts){"
					+ "const roots=host.shadowRoot?[host.shadowRoot]:[host];"
					+ "for(const rowRoot of roots){"
					+ "let cells=[...rowRoot.querySelectorAll(\"[role='cell'],[role='gridcell'],td\")];"
					+ "if(!cells.length)cells=[...rowRoot.querySelectorAll(\"[class*='cell'],[class*='column']\")];"
					+ "if(cells.length>column&&clean(cells[0].textContent).toLowerCase().includes('withholding')){"
					+ "const value=clean(cells[column].textContent);if(value)return answer=value;}}}"
					+ "for(const row of table.querySelectorAll(\"[role='row']\")){"
					+ "const cells=[...row.querySelectorAll(\"[role='cell'],[role='gridcell'],td\")];"
					+ "if(cells.length>column&&clean(cells[0].textContent).toLowerCase().includes('withholding')){"
					+ "const value=clean(cells[column].textContent);if(value)return answer=value;}}}"
					+ "for(const element of root.querySelectorAll('*'))if(element.shadowRoot)walk(element.shadowRoot);"
					+ "}walk(document);return answer;";
			String value = (String) ((JavascriptExecutor) browser).executeScript(script);
			if (value != null && !value.isEmpty()) {
				System.out.println("Lodgement Type for withholding: " + value);
				return value;
			}
			return null;
		});
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

 