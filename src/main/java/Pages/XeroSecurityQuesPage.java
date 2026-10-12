package Pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jj.util.DomCapture;
import com.jj.util.MainClass;

import Driver_manager.DriverManager;

/**
 * The security-question step of Xero's two-factor login.
 *
 * <p>Xero does not always render this page the same way — a run on 3 Sep 2026 found
 * the answer input present but no label carrying data-automationid, which a single
 * locator could not survive. Both the question and the answer are therefore looked
 * up through a list of alternatives, and a total miss captures the page to disk so
 * the next fix does not need another manual reproduction.
 */
public class XeroSecurityQuesPage extends MainClass{

	/** Question label, best first. {0} is the field prefix, e.g. "auth-firstanswer". */
	private static final String[] QUESTION_LOCATORS = {
		"//label[@data-automationid='{0}--label']",
		"//*[@data-automationid='{0}--label']",
		"//div[@data-automationid='{0}--container']//label",
		"//div[@data-automationid='{0}--container']"
	};

	/** Answer input, best first. */
	private static final String[] ANSWER_LOCATORS = {
		"//input[@data-automationid='{0}--input']",
		"//div[@data-automationid='{0}--container']//input",
		"//div[@data-automationid='{0}']//input"
	};

	private static final String FIRST = "auth-firstanswer";
	private static final String SECOND = "auth-secondanswer";

	private static final By SUBMIT_ANSWERS = By.xpath(
			"//button[@data-automationid='auth-submitanswersbutton'] | //button[@type='submit']");

	// Constructor
	public XeroSecurityQuesPage() {
		PageFactory.initElements(DriverManager.getDriver(), this);
	}
	public void getPageTitle() {
	}

	public void answerSecurityQuestions() {
		WebElement firstAnswer = find(ANSWER_LOCATORS, FIRST, "first answer box");
		String firstQuestion = readQuestion(FIRST, "first");
		System.out.println("Security question 1: " + firstQuestion);
		firstAnswer.sendKeys(answerFor(firstQuestion));

		WebElement secondAnswer = find(ANSWER_LOCATORS, SECOND, "second answer box");
		String secondQuestion = readQuestion(SECOND, "second");
		System.out.println("Security question 2: " + secondQuestion);
		secondAnswer.sendKeys(answerFor(secondQuestion));
	}

	/**
	 * The question text, read through whichever locator this rendering of the page
	 * happens to offer.
	 */
	private String readQuestion(String field, String position) {
		WebElement label = find(QUESTION_LOCATORS, field, position + " question label");
		String text = label.getText();

		if (text == null || text.trim().isEmpty()) {
			// A container match includes the answer box, so fall back to its own text node.
			text = String.valueOf(((JavascriptExecutor) DriverManager.getDriver())
					.executeScript("return (arguments[0].textContent||'').replace(/\\s+/g,' ').trim();", label));
		}
		return text == null ? "" : text.trim();
	}

	/**
	 * Tries each locator in turn, waiting for the page to settle before giving up.
	 * A total miss writes the page to disk and names the folder in the error.
	 */
	private WebElement find(String[] locators, String field, String description) {
		WebDriverWait pageWait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(30));

		try {
			return pageWait.until(driver -> {
				for (String template : locators) {
					By locator = By.xpath(template.replace("{0}", field));
					for (WebElement candidate : driver.findElements(locator)) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
				}
				return null;
			});
		} catch (Exception e) {
			DomCapture capture = new DomCapture();
			capture.capture(DriverManager.getDriver(), "security-questions-miss");

			StringBuilder tried = new StringBuilder();
			for (String template : locators) {
				tried.append("\n  ").append(template.replace("{0}", field));
			}
			throw new IllegalStateException("Could not find the " + description
					+ " on the two-factor page. Tried:" + tried
					+ "\nThe page as it looked has been captured to: " + capture.getFolder(), e);
		}
	}

	/**
	 * Xero asks two of the three questions, in either order.
	 *
	 * <p>Answers are held here in the source, by choice. Xero picks two of the three
	 * at random and in either order, so they are matched on the question text rather
	 * than on position.
	 */
	private String answerFor(String question) {
		String asked = question == null ? "" : question.trim().toLowerCase();

		if (asked.contains("dream job")) {
			return "Fortuna";
		}
		if (asked.contains("dream car")) {
			return "Fortuna1";
		}
		// "What was the name of your first pet?" — seen and accepted on 3 Sep 2026.
		// Still the catch-all, in case Xero has a fourth question we have not met.
		return "Fortuna2";
	}

	public void clickSubmitButton() {
		WebDriverWait pageWait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(30));
		List<WebElement> buttons = DriverManager.getDriver().findElements(SUBMIT_ANSWERS);
		if (buttons.isEmpty()) {
			throw new IllegalStateException("No submit button on the two-factor page.");
		}
		pageWait.until(ExpectedConditions.elementToBeClickable(buttons.get(0))).click();
	}
}
