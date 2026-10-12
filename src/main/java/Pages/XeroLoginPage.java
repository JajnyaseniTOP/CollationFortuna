package Pages;
 
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.jj.util.DomCapture;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;
 
public class XeroLoginPage extends MainClass {

    /** Used when XERO_Id is not configured. The password has no default on purpose. */
    private static final String DEFAULT_XERO_EMAIL = "accountant2@fortunaadvisors.com.au";
 
    // ── Locators ──────────────────────────────────────────────────────────────
 
    @FindBy(id = "xl-form-email")
    WebElement emailAddress;
 
    @FindBy(id = "xl-form-password")
    WebElement password;
 
    @FindBy(id = "xl-form-submit")
    WebElement loginButton;
 
    /**
     * The two-factor page now opens straight onto the security questions, so both
     * steps below are no-ops when the answer fields are already on screen. They stay
     * in place for accounts where Xero offers another method first.
     */
    private static final By SECURITY_ANSWER_INPUT =
            By.cssSelector("[data-automationid='auth-firstanswer--input']");

    /**
     * Xero gives this button a different automation id depending on which challenge
     * is showing — auth-othermethodbutton on the security-question page,
     * auth-continuebutton on the authenticator-code page — so match the text first
     * and treat the ids as extras.
     */
    private static final By ANOTHER_AUTH_METHOD = By.xpath(
            "//*[self::button or self::a][contains(normalize-space(), 'Use another authentication method')]"
            + " | //button[@data-automationid='auth-othermethodbutton']"
            + " | //button[@data-automationid='auth-continuebutton']");

    /**
     * On the "How would you like to authenticate?" chooser the options are divs
     * carrying role="button", not real buttons or links, so the tag cannot be relied
     * on here.
     */
    private static final By SECURITY_QUESTIONS_OPTION = By.xpath(
            "//*[@data-automationid='auth-authwithsecurityquestionsbutton']"
            + " | //*[self::button or self::a or @role='button']"
            + "[contains(normalize-space(), 'Security question')]");
 
 
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
        humanType(emailAddress, DEFAULT_XERO_EMAIL);
 
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
     * Clicks "Use another authentication method", unless the security questions are
     * already showing — which is how the redesigned page arrives.
     */
    public void clickAnotherAuthMethod() {
        if (securityQuestionsShown()) {
            System.out.println("Security questions already showing — no need to change method.");
            return;
        }

        if (!hasElement(ANOTHER_AUTH_METHOD)) {
            System.out.println("No 'Use another authentication method' button on this page.");
            return;
        }

        DriverManager.humanDelay(700, 1500);  // read the options before clicking
        wait.until(ExpectedConditions.elementToBeClickable(ANOTHER_AUTH_METHOD)).click();

        DriverManager.humanDelay(800, 1600);
    }

    /** Writes the current page to disk and fails with the folder named in the message. */
    private IllegalStateException captureAndFail(String what) {
        DomCapture capture = new DomCapture();
        capture.capture(DriverManager.getDriver(), what);
        return new IllegalStateException("Stuck on the two-factor page: " + what
                + ". The page as it looked has been captured to: " + capture.getFolder());
    }

    /**
     * Picks "Security questions" from the list of methods, when that list is shown.
     */
    public void clickSecurityQsn() {
        if (securityQuestionsShown()) {
            return;
        }

        if (hasElement(SECURITY_QUESTIONS_OPTION)) {
            DriverManager.humanDelay(600, 1400);
            wait.until(ExpectedConditions.elementToBeClickable(SECURITY_QUESTIONS_OPTION)).click();
        } else {
            System.out.println("No 'Security questions' option offered — the page may already be past it.");
        }

        try {
            wait.until(d -> !d.findElements(SECURITY_ANSWER_INPUT).isEmpty());
        } catch (TimeoutException e) {
            throw captureAndFail("no security-question fields after choosing another method");
        }
        DriverManager.humanDelay(800, 1800);
    }

    private boolean securityQuestionsShown() {
        return hasElement(SECURITY_ANSWER_INPUT);
    }

    private boolean hasElement(By locator) {
        return !DriverManager.getDriver().findElements(locator).isEmpty();
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