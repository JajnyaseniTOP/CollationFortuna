
package Pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class XeroSecurityQuesPage extends MainClass{

	@FindBy(xpath = "//label[contains(@class,\"auth-firstquestion\")]")
	WebElement firstquestion;
	@FindBy(xpath = "//div[contains(@data-automationid,\"auth-firstanswer\")]/div/input")
	WebElement firstanswer;
	@FindBy(xpath = "//label[contains(@class,\"auth-secondquestion\")]")
	WebElement secondquestion;
	@FindBy(xpath = "//div[contains(@data-automationid,\"auth-secondanswer\")]/div/input")
	WebElement secondanswer;
	@FindBy(xpath = "//button[@type='submit']")
	WebElement submitAns;

	// Constructor
	public XeroSecurityQuesPage() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
	public void getPageTitle() {
	}
	public void answerSecurityQuestions() {
		if(firstquestion.getText().equals("What is your dream job?")) {
			wait.until(ExpectedConditions.elementToBeClickable(firstanswer));
			firstanswer.sendKeys("Fortuna");
		}
		else if(firstquestion.getText().equals("What is your dream car?")) {
			wait.until(ExpectedConditions.elementToBeClickable(firstanswer));
			firstanswer.sendKeys("Fortuna1");
		}
		else {
			wait.until(ExpectedConditions.elementToBeClickable(firstanswer));
			firstanswer.sendKeys("Fortuna2");
		}

		if(secondquestion.getText().equals("What is your dream job?")) {
			wait.until(ExpectedConditions.elementToBeClickable(secondanswer));
			secondanswer.sendKeys("Fortuna");
		}
		else if(secondquestion.getText().equals("What is your dream car?")) {
			wait.until(ExpectedConditions.elementToBeClickable(secondanswer));
			secondanswer.sendKeys("Fortuna1");
		}
		else {
			wait.until(ExpectedConditions.elementToBeClickable(secondanswer));
			secondanswer.sendKeys("Fortuna2");
		}
	}
	public void clickSubmitButton() {
		wait.until(ExpectedConditions.elementToBeClickable(submitAns));
		submitAns.click();
	}
}
