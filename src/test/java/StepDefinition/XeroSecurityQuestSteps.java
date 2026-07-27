
package StepDefinition;

import Pages.XeroSecurityQuesPage;
import io.cucumber.java.en.*;

public class XeroSecurityQuestSteps {
	XeroSecurityQuesPage security = new XeroSecurityQuesPage();

	@Given("User wants to enter security answ")
	public void user_wants_to_enter_security_answ() {
	}

	@When("user enter security question answ")
	public void user_enter_security_question_answ() {
		security.answerSecurityQuestions();
	}

	@Then("user subit the Answers")
	public void user_subit_the_answers() {
		security.clickSubmitButton();		
	}
}
