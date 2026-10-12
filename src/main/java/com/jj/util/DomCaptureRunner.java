package com.jj.util;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import Driver_manager.DriverManager;

/**
 * Opens a browser and captures every screen you visit.
 *
 * <p>Run it, then drive the site by hand — log in, open Practice Manager, open a
 * job, open the client, open a tax return. Each new screen is written to the
 * capture folder once it stops changing. Close the browser when you are done.
 *
 * <p>Nothing here depends on the project's locators, which is the point: when the
 * site has been redesigned, the automated flow cannot reach the later pages, but
 * you still can.
 *
 * <p>Run from your IDE (Run As -> Java Application). Pass a starting URL as the
 * first argument to begin somewhere other than the Xero login page.
 */
public class DomCaptureRunner {

	private static final String DEFAULT_START_URL = "https://login.xero.com";

	/** How often the page is checked for changes. */
	private static final long POLL_MILLIS = 1500;

	/** Consecutive unchanged polls before a screen counts as settled. */
	private static final int SETTLE_POLLS = 2;

	private static final String SIGNATURE_JS =
			"return document.querySelectorAll('*').length + ':' "
			+ "+ (document.body ? document.body.innerText.length : 0);";

	public static void main(String[] args) {

		String startUrl = args.length > 0 ? args[0] : DEFAULT_START_URL;

		MainClass.setupDriver("Chrome");
		WebDriver driver = DriverManager.getDriver();

		DomCapture capture = new DomCapture();

		printBanner(startUrl, capture);

		try {
			driver.manage().window().maximize();
			driver.get(startUrl);
			watch(driver, capture);
		} catch (Exception e) {
			System.out.println("Capture stopped: " + e.getMessage());
		} finally {
			printSummary(capture);
			try {
				driver.quit();
			} catch (Exception ignored) {
				// The browser was probably closed by hand, which is the normal way to finish.
			}
		}
	}

	/** Polls the browser, capturing each screen once it has settled. */
	private static void watch(WebDriver driver, DomCapture capture) throws InterruptedException {

		Set<String> alreadyCaptured = new HashSet<>();
		Set<String> knownWindows = new HashSet<>();

		String lastSignature = "";
		int unchangedPolls = 0;

		while (true) {

			Thread.sleep(POLL_MILLIS);

			List<String> windows = windowHandles(driver);
			if (windows.isEmpty()) {
				System.out.println();
				System.out.println("Browser closed.");
				return;
			}

			// Xero opens Practice Manager in a new tab — follow it.
			if (!knownWindows.equals(new HashSet<>(windows))) {
				knownWindows = new HashSet<>(windows);
				try {
					driver.switchTo().window(windows.get(windows.size() - 1));
					System.out.println("  following new tab");
				} catch (Exception e) {
					continue;
				}
			}

			String signature = signatureOf(driver);
			if (signature == null) {
				continue;
			}

			if (!signature.equals(lastSignature)) {
				lastSignature = signature;
				unchangedPolls = 0;
				continue;
			}

			unchangedPolls++;

			if (unchangedPolls != SETTLE_POLLS || alreadyCaptured.contains(signature)) {
				continue;
			}

			alreadyCaptured.add(signature);
			capture.capture(driver, labelFor(driver));
		}
	}

	/** URL plus a rough measure of the DOM, so SPA navigation counts as a new screen. */
	private static String signatureOf(WebDriver driver) {
		try {
			Object shape = ((JavascriptExecutor) driver).executeScript(SIGNATURE_JS);
			return driver.getCurrentUrl() + "|" + shape;
		} catch (Exception e) {
			return null;
		}
	}

	private static List<String> windowHandles(WebDriver driver) {
		try {
			return new ArrayList<>(driver.getWindowHandles());
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	/** Names the file after the page title, falling back to the last part of the URL. */
	private static String labelFor(WebDriver driver) {

		try {
			String title = driver.getTitle();
			if (title != null && !title.trim().isEmpty()) {
				return title;
			}
		} catch (Exception ignored) {
			// Fall through to the URL.
		}

		try {
			String url = driver.getCurrentUrl();
			String path = url.replaceAll("^https?://", "").replaceAll("\\?.*$", "");
			return path.isEmpty() ? "page" : path;
		} catch (Exception e) {
			return "page";
		}
	}

	private static void printBanner(String startUrl, DomCapture capture) {
		System.out.println();
		System.out.println("==========================================================");
		System.out.println(" DOM capture");
		System.out.println("==========================================================");
		System.out.println(" Opening : " + startUrl);
		System.out.println(" Saving  : " + capture.getFolder());
		System.out.println();
		System.out.println(" Drive the browser by hand. Every screen that settles is");
		System.out.println(" captured once. Worth visiting:");
		System.out.println("   - the login page and the security questions page");
		System.out.println("   - the organisation / portal switcher");
		System.out.println("   - Practice Manager, and the Jobs list");
		System.out.println("   - a job page (Job No., Cover Letter Notes, Client)");
		System.out.println("   - that job's client page (name, code, group, team)");
		System.out.println("   - the client's Tax Returns list and one open return");
		System.out.println();
		System.out.println(" Close the browser when you are done.");
		System.out.println("==========================================================");
		System.out.println();
	}

	private static void printSummary(DomCapture capture) {
		System.out.println();
		System.out.println("==========================================================");
		System.out.println(" Captured " + capture.getCaptureCount() + " screen(s)");
		System.out.println(" Folder: " + capture.getFolder());
		System.out.println();
		System.out.println(" These files hold whatever was on screen, including real");
		System.out.println(" client data. Check them before sharing.");
		System.out.println("==========================================================");
	}
}
