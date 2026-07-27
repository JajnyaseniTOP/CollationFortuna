
package Pages;

import java.time.Duration;
import java.util.ArrayList;

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
	@FindBy(xpath="//a[contains(text(),'Practice Manager')]")
	WebElement practiseManager;
	@FindBy(xpath="//label[contains(text(),'Email address')]/following::input[@id='Code']")
	WebElement emailAdd;
	@FindBy(xpath="//button[contains(text(),'Continue')]")
	WebElement continues;
	
	@FindBy(xpath="//span[contains(text(),'Fortuna Unit Trust t/as Keypoi…')]")
	WebElement switchPortal_keypoint;
	
	@FindBy(xpath="//span[contains(text(),'Fortuna Accountants & Business…')]")
	WebElement switchPortal_business;
	
	@FindBy(xpath = "//input[@value='Connect']")
	WebElement clickConnect;
	
	@FindBy(xpath="//a[normalize-space()='Portal']")
	WebElement clickPortal;
	
	@FindBy(xpath="(//div[contains(@class,'x-nav-xui-button--caret')])[1]")
	WebElement dropDown ;
	
	@FindBy(xpath="//span[normalize-space(.)='Practice tools']")
	WebElement practiceTools;
	
	
	public XeroPractiseManagerPage() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
	public void clickClient() {
		wait.until(ExpectedConditions.elementToBeClickable(client));
		client.click();
	}
	
	public void clickPracticeManager() {
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
	    JavascriptExecutor js = (JavascriptExecutor) driver;

	    try {
	        // Open dropdown
	        WebElement dropdown = wait.until(
	            ExpectedConditions.elementToBeClickable(
	                By.xpath("(//div[contains(@class,'x-nav-xui-button--caret')])[1]")
	            )
	        );
	        dropdown.click();

	        // Wait for menu container
	        WebElement menuContainer = wait.until(
	            ExpectedConditions.visibilityOfElementLocated(
	                By.xpath("//div[@aria-label='Organization menu']")
	            )
	        );

	        // Scroll main menu
	        js.executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", menuContainer);

	        // Click Practice Tools
	        WebElement practiceToolsBtn = wait.until(
	            ExpectedConditions.elementToBeClickable(
	                By.xpath("//button[@aria-controls='practice-tools-sub-menu']")
	            )
	        );
	        practiceToolsBtn.click();

	        // Wait for Practice Manager
	        WebElement practiceManager = wait.until(
	            ExpectedConditions.presenceOfElementLocated(
	                By.xpath("//span[normalize-space()='Practice Manager']")
	            )
	        );

	        // 🔑 Scroll Practice Manager into view
	        js.executeScript("arguments[0].scrollIntoView({block:'center'});", practiceManager);

	        // 🔑 Click using JS (more reliable for hidden menu items)
	        js.executeScript("arguments[0].click();", practiceManager);

	    } catch (Exception e) {
	        throw new RuntimeException("Failed to click Practice Manager", e);
	    }
	}


	
	public void switchingTabs() {
		ArrayList<String> tabs = new ArrayList<>(DriverManager.getDriver().getWindowHandles());
		DriverManager.getDriver().switchTo().window(tabs.get(1));
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
	
	public void switchportal() throws InterruptedException {
		Thread.sleep(3000);
		try {
			switchPortal_business.click();
			clickPortal.click();
			clickConnect.click();
		}catch(Exception e) {
			//System.out.println("catch bloclk");
		}
	}
	
	public void clickCrossButton() {
		try {
			
		}catch(Exception e) {
			
		}
	}
}