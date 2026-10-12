
package com.jj.util;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import Driver_manager.DriverManager;

public class MainClass {
	
	public MainClass() {
        this.driver = DriverManager.getDriver(); // Ensure DriverManager.getDriver() returns a valid WebDriver instance
    }
	public static WebDriver driver;
	public static String currentDate = LocalDate.now().toString();
	public static String clientName = "Carnarvon Regional Labour Hire Pty Ltd";





	public static String ATO_USER_NAME="";
	public static String USERNAME="";
	public static String SENDER_TO="";
	public static String XERO_USER_NAME="";
	public static String XERO_PASSWORD="";
	public static String XERO_SECURITY_QUEST1="";
	public static String XERO_SECURITY_ANS1="";
	public static String XERO_SECURITY_QUEST2="";
	public static String XERO_SECURITY_ANS2="";
	public static String XERO_SECURITY_QUEST3="";
	public static String XERO_SECURITY_ANS3="";



	public static WebDriverWait wait;

	public  static JavascriptExecutor js;

	/*====================Set up Driver===================================*/

	public static void setupDriver(String browser) {
		DriverManager.setDriver(browser);
	}

	/*====================Credentials===================================*/

	/**
	 * Reads a setting from a JVM system property, falling back to an environment
	 * variable of the same name.
	 *
	 * <p>The environment variable is usually the easier of the two: it is set once on
	 * the machine, rather than added to every run configuration.
	 *
	 * @return the value, or an empty string when neither is set
	 */
	public static String setting(String name) {
		String value = System.getProperty(name);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(name);
		}
		return value == null ? "" : value.trim();
	}

	/** Same as {@link #setting}, with a default for values that are not secret. */
	public static String settingOrDefault(String name, String fallback) {
		String value = setting(name);
		return value.isEmpty() ? fallback : value;
	}

	/**
	 * Reads a required credential, failing with instructions rather than letting the
	 * run get as far as the login form with nothing to type into it.
	 */
	public static String requiredSetting(String name) {
		String value = setting(name);
		if (value.isEmpty()) {
			throw new IllegalStateException("Missing " + name + ". Set it as an environment variable "
					+ "named " + name + ", or pass -D" + name + "=... to the JVM.");
		}
		return value;
	}

	/*====================Set Properties===================================*/

	public void setProperties() {
		clientName = settingOrDefault("CLIENT_NAME", clientName);
		ATO_USER_NAME=setting("ATO_Id");
		XERO_USER_NAME=setting("XERO_Id");
		XERO_PASSWORD=setting("XERO_Password");
		XERO_SECURITY_QUEST1=setting("Security_Question_1");
		XERO_SECURITY_ANS1=setting("Security_Answer_1");
		XERO_SECURITY_QUEST2=setting("Security_Question_2");
		XERO_SECURITY_ANS2=setting("Security_Answer_2");
		XERO_SECURITY_QUEST3=setting("Security_Question_3");
		XERO_SECURITY_ANS3=setting("Security_Answer_3");
	}

	/*====================Launch Site===================================*/

	public static void launchSite(String url) {
		DriverManager.getDriver().get(url);
		DriverManager.getDriver().manage().window().maximize();
		DriverManager.getDriver().manage().timeouts().implicitlyWait(Duration.ofSeconds(20));
		wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(30));
		js = (JavascriptExecutor) DriverManager.getDriver();
	}

	/*====================Close Browser===================================*/

	public void tearDown() {
		DriverManager.getDriver().quit();
	}	
}