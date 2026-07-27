
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

	/*====================Set Properties===================================*/

	public void setProperties() {
		ATO_USER_NAME=System.getProperty("ATO_Id");
		XERO_USER_NAME=System.getProperty("XERO_Id");
		XERO_PASSWORD=System.getProperty("XERO_Password");
		XERO_SECURITY_QUEST1=System.getProperty("Security_Question_1");
		XERO_SECURITY_ANS1=System.getProperty("Security_Answer_1");
		XERO_SECURITY_QUEST2=System.getProperty("Security_Question_2");
		XERO_SECURITY_ANS2=System.getProperty("Security_Answer_2");
		XERO_SECURITY_QUEST3=System.getProperty("Security_Question_3");
		XERO_SECURITY_ANS3=System.getProperty("Security_Answer_3");
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