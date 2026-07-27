package MyHooks;


import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.PageFactory;

import com.jj.util.MainClass;

import Pages.FindJob;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;

public class AppHooks extends MainClass{
	
	public static WebDriver driver;
	
	   
	
	@Before
	public void beforeScenario(Scenario scenario) {
//		System.out.println("Started scenario -"+scenario.getName());
		setProperties();
	}
	
	@Before("@add30SecondWait")
	public void beforeScenariowait30Second() {
		System.out.println("Running after each scenario");
	}
	
	@After
	public void afterScenariowait(Scenario scenario) {
//		System.out.println("Eneded scenario - "+scenario.getName());
	}
	
	@BeforeStep
	public void waitBeforeEachStep() {
//		System.out.println("Running before each step");
	}
}
