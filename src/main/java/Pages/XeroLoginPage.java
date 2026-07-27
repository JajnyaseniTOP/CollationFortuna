package Pages;
 
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.jj.util.MainClass;

import Driver_manager.DriverManager;
 
public class XeroLoginPage extends MainClass {
 
    // ── Locators ──────────────────────────────────────────────────────────────
 
    @FindBy(id = "xl-form-email")
    WebElement emailAddress;
 
    @FindBy(id = "xl-form-password")
    WebElement password;
 
    @FindBy(id = "xl-form-submit")
    WebElement loginButton;
 
    @FindBy(xpath = "//button[contains(text(),'Use another authentication method')]")
    WebElement anotherAuthMethod;
 
    @FindBy(xpath = "//h2[contains(text(),'Security questions')]")
    WebElement securityQsn;
 
 
    // ── Constructor ───────────────────────────────────────────────────────────
 
    public XeroLoginPage() {
        PageFactory.initElements(DriverManager.getDriver(), this);
    }
 
 
    // ── Page warm-up (call this once after navigating to the login URL) ───────
 
    /**
     * Runs the full human warm-up routine:
     *   - Random pause as if reading the page
     *   - Gentle scroll up/down
     *   - Random mouse wiggles
     * Must be called BEFORE any form interaction to satisfy Akamai's
     * behavioural entropy checks.
     */
    public void warmUpLoginPage() {
        System.out.println("[XeroLoginPage] Warming up login page...");
        DriverManager.warmUpPage(DriverManager.getDriver());
    }
 
 
    // ── Page title (placeholder, extend as needed) ────────────────────────────
 
    public void getPageTitle() {
        // Extend if you need to assert/return the page title
    }
 
 
    // ── Form interactions (each wrapped with realistic delays) ────────────────
 
    /**
     * Clicks into the email field, pauses briefly as a human would,
     * then types the email address character-by-character with small
     * inter-key delays to mimic natural typing rhythm.
     */
    public void enterUserId() {
        wait.until(ExpectedConditions.elementToBeClickable(emailAddress));
 
        // Click the field first — real users click before they type
        emailAddress.click();
        DriverManager.humanDelay(400, 900);   // short pause after clicking in
 
        // Type with human-like inter-key cadence
        humanType(emailAddress, "accountant2@fortunaadvisors.com.au");
 
        // Pause after finishing the field, as if proofreading
        DriverManager.humanDelay(600, 1400);
    }
 
    /**
     * Moves to the password field with a natural pause, then types
     * the password with randomised key delays.
     */
    public void enterPassword() {
        wait.until(ExpectedConditions.elementToBeClickable(password));
 
        DriverManager.humanDelay(500, 1100);  // transition delay between fields
        password.click();
        DriverManager.humanDelay(300, 700);
 
        humanType(password, "User123456@");
 
        // Pause before hitting submit — people often hesitate here
        DriverManager.humanDelay(800, 1800);
    }
 
    /**
     * Clicks the login button after a final human-like pause.
     */
    public void clickLoginButton() {
        wait.until(ExpectedConditions.elementToBeClickable(loginButton));
 
        DriverManager.humanDelay(500, 1200);  // brief hesitation before clicking
        loginButton.click();
 
        // Post-click pause — gives the page time to react naturally
        DriverManager.humanDelay(1000, 2500);
    }
 
    /**
     * Clicks "Use another authentication method" with a realistic delay.
     */
    public void clickAnotherAuthMethod() {
        wait.until(ExpectedConditions.elementToBeClickable(anotherAuthMethod));
 
        DriverManager.humanDelay(700, 1500);  // read the options before clicking
        anotherAuthMethod.click();
 
        DriverManager.humanDelay(800, 1600);
    }
 
    /**
     * Clicks the "Security questions" heading with a realistic delay.
     */
    public void clickSecurityQsn() {
        wait.until(ExpectedConditions.elementToBeClickable(securityQsn));
 
        DriverManager.humanDelay(600, 1400);
        securityQsn.click();
 
        DriverManager.humanDelay(800, 1800);
    }
 
 
    // ── Private helper: human-like typing ────────────────────────────────────
 
    /**
     * Types each character of the given text into the target element with
     * a small random delay between keystrokes (40–120 ms).
     * This replicates natural typing speed variance and is far less
     * detectable than a single bulk sendKeys() call.
     *
     * @param element  the WebElement to type into
     * @param text     the string to type
     */
    private void humanType(WebElement element, String text) {
        for (char ch : text.toCharArray()) {
            element.sendKeys(String.valueOf(ch));
            DriverManager.humanDelay(40, 120);  // inter-key delay
        }
    }
}