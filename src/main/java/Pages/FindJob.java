package Pages;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.jj.util.ClientExcel;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;

public class FindJob extends MainClass{
	ClientExcel creator = new ClientExcel();	
	public String extractedJobNo = "";
	public String coverLetterNotes = "";
	
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
	
	public static  void switchportal() throws InterruptedException {
		Thread.sleep(3000);
		try {
			switchPortal_keypoint.click();
			clickPortal.click();
			clickConnect.click();
		}catch(Exception e) {
		}
	}
	public static  void switchportal2() throws InterruptedException {
		Thread.sleep(3000);
		try {
			switchPortal_business.click();
			clickPortal.click();
			clickConnect.click();
		}catch(Exception e) {
			//System.out.println("It is already in keypoint portal");
		}
	}
	public FindJob() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
	public static  void clickjoblinks(String jobnumber) throws InterruptedException {
		    job.click();
	        jobs.click();
	        searchbox.click();
	        searchbox.sendKeys(jobnumber);
	        jobNumber.click();
	}
	
	private String getTextSafely(By locator) {
	    try {
	        List<WebElement> elements = driver.findElements(locator);
	        if (!elements.isEmpty()) {
	            String text = elements.get(0).getText().trim();	        
	        }
	    } catch (Exception e) {
	        // ignore
	    }
	    return "";
	}
	
	private void extractClientDetails() throws InterruptedException {
	    System.out.println(">>> ENTERED extractClientDetails()");
	    Thread.sleep(1000);	   
	    JavascriptExecutor js = (JavascriptExecutor) driver;
	    js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
	    Thread.sleep(1000);	    
	    js.executeScript("window.scrollTo(0, 0)");
	    Thread.sleep(800);
	    
	    try {
	        extractedJobNo = jobNo.getText().trim();   

	    } catch (Exception e) {
	        System.out.println("Unable to extract Job No.: " + e.getMessage());
	    }	
	    
	    try {
	        coverLetterNotes = Cover_Letter_Notes.getText().trim();	        
	    } catch (Exception e) {
	        System.out.println("Unable to extract Cover Letter Notes: " + e.getMessage());
	    }  
	    
	    creator.insertData(extractedJobNo, coverLetterNotes,currentDate);
	    
      
	    System.out.println("Extracted Client Details");
	    System.out.println("Job Number          : " + extractedJobNo);
	    System.out.println("Cover Letter Notes  : " + coverLetterNotes);
	    System.out.println("Current Date        : " + currentDate);
	    System.out.println("-----------------------------------");
	}
	
	
	
	
	public static void switchPortalByPrefix(String input) throws InterruptedException {
		
	    if (input != null && input.startsWith("KPF")) {
	        switchportal2();
	        clickjoblinks(input);	       
	    
	    } else {
	        switchportal();
	        clickjoblinks(input);
	    }
	}
	
	

}
