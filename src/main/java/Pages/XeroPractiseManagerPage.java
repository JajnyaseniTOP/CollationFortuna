
package Pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class XeroPractiseManagerPage extends MainClass{

	@FindBy(xpath="//button[@data-automationid='xnav-appbutton']")
	WebElement client;
	@FindBy(xpath="//label[contains(text(),'Email address')]/following::input[@id='Code']")
	WebElement emailAdd;
	@FindBy(xpath="//button[contains(text(),'Continue')]")
	WebElement continues;

	// The portal switchers, Portal/Connect buttons and nav caret that used to live
	// here went with the Xero redesign. Practice switching is now per job, handled by
	// FindJob.switchToPractice, and the menu locators sit further down this class.
	
	
	public XeroPractiseManagerPage() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
	public void clickClient() {
		wait.until(ExpectedConditions.elementToBeClickable(client));
		client.click();
	}
	
	/** Practice Manager's own address, used since it left the Xero navigation. */
	private static final String PRACTICE_MANAGER_URL = "https://practicemanager.xero.com/app/homepage";

	private static final By TENANT_MENU_BUTTON = By.cssSelector("button.x-nav--tenant-menu-button");

	/** "Practice tools" is an accordion in the menu footer; Practice Manager sits inside it. */
	private static final By PRACTICE_TOOLS = By.xpath(
			"//li[@aria-label='Practice tools']//button[normalize-space()='Practice tools']");
	private static final By PRACTICE_TOOLS_LIST = By.xpath("//ul[@aria-label='Practice tools list']");
	private static final By PRACTICE_MANAGER_LINK = By.xpath(
			"//ul[@aria-label='Practice tools list']//a[contains(normalize-space(), 'Practice Manager')]");

	/**
	 * Opens Practice Manager.
	 *
	 * <p>Xero's redesigned navigation no longer carries Practice Manager: the
	 * "Practice tools" menu now lists only Xero Partner Hub, Classic Workpapers and
	 * My Xero, and the words "Practice Manager" appear nowhere on the home page. The
	 * app is therefore opened by its own address, which the login session already
	 * covers.
	 *
	 * <p>The menu is still tried first, so a tenant that keeps the old entry carries
	 * on working — and because that entry opened a new tab, the caller still follows
	 * this with {@link #switchingTabs()}.
	 */
	public void clickPracticeManager() {

	    if (clickPracticeManagerInMenu()) {
	        return;
	    }

	    System.out.println("Practice Manager is not in the menu — opening " + PRACTICE_MANAGER_URL);
	    driver.get(PRACTICE_MANAGER_URL);

	    new WebDriverWait(driver, Duration.ofSeconds(30))
	            .until(ExpectedConditions.urlContains("practicemanager.xero.com"));

	    System.out.println("Practice Manager ready at: " + driver.getCurrentUrl());
	}

	/** @return true when the old menu entry was there and was clicked. */
	private boolean clickPracticeManagerInMenu() {

	    JavascriptExecutor js = (JavascriptExecutor) driver;

	    try {
	        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
	        shortWait.until(ExpectedConditions.elementToBeClickable(TENANT_MENU_BUTTON)).click();
	        Thread.sleep(1500);

	        // The accordion starts collapsed, so open it unless the list is already out.
	        if (driver.findElements(PRACTICE_TOOLS_LIST).isEmpty()) {
	            WebElement practiceTools = shortWait.until(
	                ExpectedConditions.presenceOfElementLocated(PRACTICE_TOOLS)
	            );
	            js.executeScript("arguments[0].scrollIntoView({block:'center'});", practiceTools);
	            js.executeScript("arguments[0].click();", practiceTools);
	            Thread.sleep(1000);
	        }

	        List<WebElement> links = driver.findElements(PRACTICE_MANAGER_LINK);
	        if (links.isEmpty()) {
	            return false;
	        }

	        js.executeScript("arguments[0].scrollIntoView({block:'center'});", links.get(0));
	        js.executeScript("arguments[0].click();", links.get(0));
	        return true;

	    } catch (Exception e) {
	        // The menu has changed shape; the address below is the way in.
	        return false;
	    }
	}


	
	/**
	 * Moves to the tab Practice Manager opened.
	 *
	 * <p>The old menu entry opened a second tab. Opening the address directly does
	 * not, so when there is only one tab this stays where it is instead of failing.
	 */
	public void switchingTabs() {
		ArrayList<String> tabs = new ArrayList<>(DriverManager.getDriver().getWindowHandles());

		if (tabs.size() < 2) {
			return;
		}

		DriverManager.getDriver().switchTo().window(tabs.get(tabs.size() - 1));
	}
	public void enterEmailAddress() {
		wait.until(ExpectedConditions.visibilityOf(emailAdd));
		emailAdd.sendKeys("accountant2@fortunaadvisors.com.au");
	}
	public void enterContinue() {
		try {
		wait.until(ExpectedConditions.elementToBeClickable(continues));
		continues.click();
		}
		catch(Exception e) {
			
		}
	}	
	
	
	public void clickCrossButton() {
		try {
			
		}catch(Exception e) {
			
		}
	}
}