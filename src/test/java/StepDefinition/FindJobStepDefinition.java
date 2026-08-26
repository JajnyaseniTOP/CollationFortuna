package StepDefinition;

import Pages.FindJob;
import Pages.ATOLoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

public class FindJobStepDefinition {

    private String jobCode;
    FindJob job = new FindJob();
    ATOLoginPage ato=new ATOLoginPage();

    @Given("the job code is {string}")
    public void the_job_code_is(String code) {
        jobCode = code;
    }

    @When("I switch the portal based on the job code")
    public void i_switch_the_portal_based_on_the_job_code() throws InterruptedException {
    	job.switchPortalByPrefix(jobCode);
       
    	
    }

    @Then("the correct portal should be selected")
    public void the_correct_portal_should_be_selected() {
      
    }
}