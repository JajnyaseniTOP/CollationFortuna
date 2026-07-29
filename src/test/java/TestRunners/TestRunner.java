package TestRunners;

import org.junit.runner.RunWith;
import io.cucumber.core.cli.Main;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;

@RunWith(Cucumber.class)
@CucumberOptions(
		features= 

	{"src/test/resources/Features/"},
		/*
	{
	
	"src/test/resources/Features/test1_XeroLogin.feature",
	"src/test/resources/Features/test2_XeroSecurityQuest.feature",
	"src/test/resources/Features/test3_XeroPractiseMan.feature",
	"src/test/resources/Features/test5_XeroTaxReturn.feature",
//	"src/test/resources/Features/5test_ATOFetchingICAStatement.feature",
	},
	*/
	glue={"StepDefinition","MyHooks"},
	plugin = {"pretty"}
		)

public class TestRunner {	
	public static void main(String[] args) {
        Main.main(args);
    }
}