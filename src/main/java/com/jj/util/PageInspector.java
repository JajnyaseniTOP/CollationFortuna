package com.jj.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;

/**
 * Inspects a page in a browser that is already open, without logging in again.
 *
 * <p>Built for the ATO and Xero screens this project drives. When a run fails at a
 * page that took a myID approval and several minutes of navigation to reach, closing
 * the browser to investigate throws all of that away. This attaches to the running
 * Chrome over its DevTools port instead, and reports what the page actually contains
 * — reading through shadow roots and slots, which is where both sites keep their
 * data.
 *
 * <p><b>Finding the port.</b> Chrome must have been started by ChromeDriver, which
 * this project's {@link Driver_manager.DriverManager} does. The port appears in the
 * run log as {@code debuggerAddress: localhost:NNNNN}; on Windows it can also be
 * found with:
 *
 * <pre>
 *   netstat -ano | findstr LISTENING | findstr 127.0.0.1
 * </pre>
 *
 * <p><b>Usage</b> — every argument is optional except the port:
 *
 * <pre>
 *   PageInspector &lt;host:port&gt; [url] [what]
 *
 *   what = text   the page's readable text          (default)
 *          rows   every table row, shadow and all
 *          links  every clickable element
 *          all    all three
 *          save   write the page to a capture folder
 * </pre>
 *
 * <p>Passing a url navigates there first; leaving it out inspects whatever tab is
 * already open on that site.
 */
public class PageInspector {

	private static final String ROWS_SCRIPT = Dom.DEEP_TEXT + Dom.WALK_SHADOW
			+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
			+ "const out=[];"
			+ "eachShadowRoot(root=>{"
			+ "for(const row of root.querySelectorAll(\"" + Dom.ROW_SELECTOR + "\")){"
			+ "const t=clean(deepText(row));"
			+ "if(t)out.push('  | '+t.slice(0,160));}});"
			+ "return out.length ? out.join('\\n') : '  (no rows on this page)';";

	private static final String LINKS_SCRIPT = Dom.DEEP_TEXT + Dom.WALK_SHADOW
			+ "const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
			+ "const out=[];"
			+ "eachShadowRoot(root=>{"
			+ "for(const el of root.querySelectorAll(\"a,button,[role='button'],[role='link'],[role='menuitem']\")){"
			+ "const t=clean(deepText(el));"
			+ "if(!t)return;"
			+ "const visible=el.getClientRects().length>0;"
			+ "out.push((visible?'  VIS ':'  hid ')+el.tagName.toLowerCase()"
			+ "+(el.id?'#'+el.id:'')"
			+ "+(el.getAttribute('data-automationid')?'[aid='+el.getAttribute('data-automationid')+']':'')"
			+ "+' :: '+t.slice(0,90));}});"
			+ "return out.length ? out.join('\\n') : '  (nothing clickable)';";

	private static final String TEXT_SCRIPT = Dom.DEEP_TEXT
			+ "const main=document.querySelector('#Main')||document.body;"
			+ "return deepText(main).replace(/\\s+/g,' ').trim().slice(0,3000);";

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			System.out.println("Usage: PageInspector <host:port> [url] [text|rows|links|all|save]");
			System.out.println("The port is in the run log as 'debuggerAddress: localhost:NNNNN'.");
			return;
		}

		String debuggerAddress = args[0];
		String url = args.length > 1 && args[1].startsWith("http") ? args[1] : null;
		String what = args.length > 2 ? args[2] : (args.length > 1 && url == null ? args[1] : "text");

		String patched = System.getProperty("java.io.tmpdir") + File.separator
				+ "patched_chromedriver" + File.separator + "chromedriver.exe";
		if (new File(patched).exists()) {
			System.setProperty("webdriver.chrome.driver", patched);
		}

		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOption("debuggerAddress", debuggerAddress);
		WebDriver driver = new ChromeDriver(options);

		try {
			System.out.println("Attached. Open tabs:");
			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				System.out.println("  " + driver.getCurrentUrl());
			}

			if (url != null) {
				focusTabFor(driver, url);
				driver.get(url);
				// Component-built pages populate well after the load event.
				Thread.sleep(12000);
			}

			System.out.println();
			System.out.println("URL   : " + driver.getCurrentUrl());
			System.out.println("Title : " + driver.getTitle());

			JavascriptExecutor js = (JavascriptExecutor) driver;

			if (is(what, "text", "all")) {
				System.out.println("\n===== readable text =====");
				System.out.println(js.executeScript(TEXT_SCRIPT));
			}
			if (is(what, "rows", "all")) {
				System.out.println("\n===== table rows =====");
				System.out.println(js.executeScript(ROWS_SCRIPT));
			}
			if (is(what, "links", "all")) {
				System.out.println("\n===== clickable =====");
				System.out.println(js.executeScript(LINKS_SCRIPT));
			}
			if (is(what, "save", "all")) {
				DomCapture capture = new DomCapture();
				capture.capture(driver, "inspected");
				System.out.println("\nCaptured to: " + capture.getFolder());
			}

		} finally {
			// Detaches; it does not close the browser being inspected.
			driver.quit();
		}
	}

	/** Switches to a tab already on that site, so navigation reuses its session. */
	private static void focusTabFor(WebDriver driver, String url) {
		String host = url.replaceAll("^https?://", "").split("/")[0];
		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (driver.getCurrentUrl().contains(host)) {
				return;
			}
		}
	}

	private static boolean is(String what, String... names) {
		for (String name : names) {
			if (name.equalsIgnoreCase(what)) {
				return true;
			}
		}
		return false;
	}
}
