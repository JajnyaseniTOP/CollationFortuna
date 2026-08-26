package Driver_manager;
 
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
 
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
 
/**
* DriverManager — Enhanced with:
*  1. Patched ChromeDriver binary (neutralises Akamai/CDP fingerprint detection)
*  2. Human-like delays & warm-up interactions before automation begins
*/
public class DriverManager {
 
    public static WebDriver driver;
 
    // ─────────────────────────────────────────────────────────────────────────
    //  1.  PATCHED CHROMEDRIVER SETUP
    //      Downloads the standard ChromeDriver then patches the binary so that
    //      the string "cdc_" (a known Selenium marker that WAFs like Akamai scan
    //      for) is replaced with a random innocuous string.
    //      This is the Java equivalent of Python's undetected-chromedriver.
    // ─────────────────────────────────────────────────────────────────────────
 
    private static final String PATCHED_DRIVER_DIR  = System.getProperty("java.io.tmpdir") + File.separator + "patched_chromedriver";
    private static final String PATCHED_DRIVER_PATH = PATCHED_DRIVER_DIR + File.separator + "chromedriver.exe";
 
    /**
     * Downloads the ChromeDriver matching the installed Chrome version,
     * patches the binary to remove the "cdc_" automation marker, and sets
     * the system property so Selenium picks it up automatically.
     */
    private static void setupPatchedChromeDriver() throws Exception {
 
        File patchedFile = new File(PATCHED_DRIVER_PATH);
 
        // Re-use cached patched driver if it already exists
        if (patchedFile.exists()) {
            System.setProperty("webdriver.chrome.driver", PATCHED_DRIVER_PATH);
            System.out.println("[DriverManager] Using cached patched ChromeDriver.");
            return;
        }
 
        new File(PATCHED_DRIVER_DIR).mkdirs();
 
        // ── Step 1: Detect installed Chrome version ───────────────────────
        String chromeVersion = getInstalledChromeVersion();
        System.out.println("[DriverManager] Detected Chrome version: " + chromeVersion);
 
        // ── Step 2: Download matching ChromeDriver ────────────────────────
        String majorVersion   = chromeVersion.split("\\.")[0];
        String downloadUrl    = getChromeDriverDownloadUrl(majorVersion);
        File   zipFile        = new File(PATCHED_DRIVER_DIR + File.separator + "chromedriver.zip");
 
        System.out.println("[DriverManager] Downloading ChromeDriver from: " + downloadUrl);
        downloadFile(downloadUrl, zipFile);
 
        // ── Step 3: Unzip ─────────────────────────────────────────────────
        File rawDriver = unzipChromeDriver(zipFile, PATCHED_DRIVER_DIR);
 
        // ── Step 4: Patch binary (replace "cdc_" marker) ──────────────────
        patchChromeDriverBinary(rawDriver, patchedFile);
 
        // ── Step 5: Make executable (Linux/Mac) ───────────────────────────
        patchedFile.setExecutable(true);
 
        System.setProperty("webdriver.chrome.driver", PATCHED_DRIVER_PATH);
        System.out.println("[DriverManager] Patched ChromeDriver ready at: " + PATCHED_DRIVER_PATH);
    }
 
    /** Reads Chrome version from the Windows registry. */
    private static String getInstalledChromeVersion() throws Exception {
        String[] command = {
            "reg", "query",
            "HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon",
            "/v", "version"
        };
        Process  process = Runtime.getRuntime().exec(command);
        String   output  = new String(process.getInputStream().readAllBytes());
 
        for (String line : output.split("\n")) {
            if (line.contains("version")) {
                return line.trim().split("\\s+")[line.trim().split("\\s+").length - 1].trim();
            }
        }
        throw new RuntimeException("Could not detect Chrome version from registry.");
    }
 
    /** Builds the correct ChromeDriver download URL for Chrome 115+. */
    @SuppressWarnings("deprecation")
	private static String getChromeDriverDownloadUrl(String majorVersion) throws Exception {
 
        // Chrome 115+ uses the new endpoint
        String infoUrl = "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_" + majorVersion;
        URL    url     = new URL(infoUrl);
 
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
 
        String version = new String(conn.getInputStream().readAllBytes()).trim();
 
        return "https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/"
                + version + "/win64/chromedriver-win64.zip";
    }
 
    /** Simple HTTP file download. */
    @SuppressWarnings("deprecation")
	private static void downloadFile(String fileUrl, File dest) throws Exception {
        URL url = new URL(fileUrl);
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            in.transferTo(out);
        }
    }
 
    /** Extracts chromedriver.exe from the downloaded ZIP. */
    private static File unzipChromeDriver(File zipFile, String destDir) throws Exception {
        File extracted = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("chromedriver.exe") || entry.getName().endsWith("chromedriver")) {
                    extracted = new File(destDir, "chromedriver_raw.exe");
                    try (FileOutputStream fos = new FileOutputStream(extracted)) {
                        zis.transferTo(fos);
                    }
                }
                zis.closeEntry();
            }
        }
        if (extracted == null) throw new RuntimeException("chromedriver binary not found in ZIP.");
        return extracted;
    }
 
    /**
     * Core patch: reads the raw ChromeDriver binary and replaces every
     * occurrence of the 3-byte sequence "cdc_" with "dog_" (or any other
     * harmless same-length string).  This removes the Selenium marker that
     * Akamai and similar WAFs scan for in the browser's JS environment.
     */
    private static void patchChromeDriverBinary(File input, File output) throws Exception {
        byte[] content     = Files.readAllBytes(input.toPath());
        byte[] searchBytes = "cdc_".getBytes();
        byte[] replaceWith = "dog_".getBytes();   // same length — keeps binary valid
 
        int patchCount = 0;
        for (int i = 0; i <= content.length - searchBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < searchBytes.length; j++) {
                if (content[i + j] != searchBytes[j]) { match = false; break; }
            }
            if (match) {
                System.arraycopy(replaceWith, 0, content, i, replaceWith.length);
                patchCount++;
            }
        }
        Files.write(output.toPath(), content);
        System.out.println("[DriverManager] Patched " + patchCount + " 'cdc_' occurrences in ChromeDriver binary.");
    }
 
 
    // ─────────────────────────────────────────────────────────────────────────
    //  2.  HUMAN-LIKE DELAY UTILITIES
    // ─────────────────────────────────────────────────────────────────────────
 
    /**
     * Sleeps for a random duration between minMs and maxMs milliseconds.
     * Mimics natural human reaction/reading time.
     */
    public static void humanDelay(int minMs, int maxMs) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
 
    /**
     * Simulates a human scrolling the page slightly — a common warm-up
     * action that real users perform before interacting with a login form.
     * Akamai's behavioural analysis looks for this kind of activity.
     */
    public static void humanScroll(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
 
        // Scroll down a little
        int scrollDown = ThreadLocalRandom.current().nextInt(100, 300);
        js.executeScript("window.scrollBy(0, " + scrollDown + ");");
        humanDelay(400, 900);
 
        // Scroll back up slightly — real users often do this
        int scrollUp = ThreadLocalRandom.current().nextInt(30, 100);
        js.executeScript("window.scrollBy(0, -" + scrollUp + ");");
        humanDelay(300, 700);
    }
 
    /**
     * Moves the mouse to a random position on the screen.
     * Helps satisfy Akamai's mouse-movement entropy check.
     */
    public static void humanMouseWiggle(WebDriver driver) {
        try {
            Actions actions = new Actions(driver);
            // Move to a few random co-ordinates (relative moves)
            for (int i = 0; i < ThreadLocalRandom.current().nextInt(2, 5); i++) {
                int xOffset = ThreadLocalRandom.current().nextInt(-50, 50);
                int yOffset = ThreadLocalRandom.current().nextInt(-30, 30);
                actions.moveByOffset(xOffset, yOffset).perform();
                humanDelay(100, 300);
            }
        } catch (Exception ignored) {
            // If no element is in focus, moveByOffset can throw — safe to ignore
        }
    }
 
    /**
     * Full warm-up routine to call AFTER navigating to the page but BEFORE
     * interacting with any form fields.  Simulates a human landing on the
     * page, glancing around, and then starting to fill in the form.
     */
    public static void warmUpPage(WebDriver driver) {
        System.out.println("[DriverManager] Running human warm-up routine...");
 
        humanDelay(1500, 3000);   // Pause as if page is being read
        humanScroll(driver);      // Scroll around slightly
        humanDelay(800, 1800);    // Another natural pause
        humanMouseWiggle(driver); // Move the mouse a bit
        humanDelay(500, 1200);    // Short pause before interacting
 
        System.out.println("[DriverManager] Warm-up complete.");
    }
 
 
    // ─────────────────────────────────────────────────────────────────────────
    //  3.  MAIN DRIVER SETUP
    // ─────────────────────────────────────────────────────────────────────────
 
    public static void setDriver(String browser) {
    if ("Chrome".equalsIgnoreCase(browser)) {
 
            // ── Download & patch ChromeDriver binary ──────────────────────
            try {
                setupPatchedChromeDriver();
            } catch (Exception e) {
                System.err.println("[DriverManager] WARNING: Could not auto-patch ChromeDriver — " +
                        "falling back to system PATH driver. Error: " + e.getMessage());
                // Non-fatal: Selenium will try to find chromedriver on PATH
            }
 
            // ── Chrome preferences ────────────────────────────────────────
            Map<String, Object> prefs = new HashMap<>();
          
            prefs.put("profile.default_content_setting_values.notifications", 2);
            prefs.put("credentials_enable_service", false);
            prefs.put("profile.password_manager_enabled", false);
 
            // ── Chrome options ────────────────────────────────────────────
            ChromeOptions options = new ChromeOptions();
            options.setExperimentalOption("prefs", prefs);
 
            // Stealth / Anti-Detection Flags
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("start-maximized");
            options.addArguments("--disable-infobars");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-popup-blocking");
                options.addArguments("--user-data-dir=" +
                    System.getProperty("java.io.tmpdir") + File.separator +
                    "fortunacollation-chrome-" + UUID.randomUUID());
 
            // Exclude automation indicators
            options.setExperimentalOption("excludeSwitches",
                Arrays.asList("enable-automation", "enable-logging"));
            options.setExperimentalOption("useAutomationExtension", false);
 
            // Realistic user-agent (update the version string to match your Chrome)
            options.addArguments(
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
            );
 
            driver = new ChromeDriver(options);
 
            // ── Post-launch JS stealth patches ────────────────────────────
            JavascriptExecutor js = (JavascriptExecutor) driver;
 
            // Hide the webdriver property
            js.executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
            );
            // Spoof plugin list (empty array is a giveaway for headless/automation)
            js.executeScript(
                "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});"
            );
            // Spoof languages
            js.executeScript(
                "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});"
            );
 
        } else {
            // ── Firefox fallback ──────────────────────────────────────────
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("browser.download.folderList", 2);
        
            options.addPreference("browser.helperApps.neverAsk.saveToDisk",
                    "application/pdf,application/octet-stream,application/vnd.ms-excel");
            options.addPreference("pdfjs.disabled", true);
            options.addArguments("-private");
 
            driver = new FirefoxDriver(options);
        }
    }
 
    public static WebDriver getDriver() {
        return driver;
    }
}