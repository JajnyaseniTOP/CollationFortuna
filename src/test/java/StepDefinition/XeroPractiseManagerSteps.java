
package StepDefinition;

import com.jj.util.ClientExcel;

import Pages.XeroPractiseManagerPage;
import io.cucumber.java.en.*;

public class XeroPractiseManagerSteps {
	ClientExcel creator = new ClientExcel();
	XeroPractiseManagerPage practise = new XeroPractiseManagerPage();

	@Given("User click on client")
	public void user_click_on_client() {
		//practise.clickClient();
	}

	@When("User click on Practise manager")
	public void user_click_on_practise_manager() {
		practise.clickPracticeManager();
	}

	@Then("User switch the tab")
	public void user_switch_the_tab() throws InterruptedException {
		practise.switchingTabs();
//		practise.enterEmailAddress();
//		practise.enterContinue();
		// No portal switch here any more — FindJob picks the practice from the job
		// number (KPF to FUT, J to FABA) once the job code is known.
	}
	@Then("bot creating excel sheet")
	public void bot_creating_excel() {
		creator.createEmptyExcelSheet();
	}
}
